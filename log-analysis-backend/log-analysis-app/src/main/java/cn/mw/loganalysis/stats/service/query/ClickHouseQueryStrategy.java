package cn.mw.loganalysis.stats.service.query;

import cn.mw.loganalysis.common.util.DateTimeUtils;
import cn.mw.loganalysis.stats.dto.LogContextRequest;
import cn.mw.loganalysis.stats.dto.LogQueryRequest;
import cn.mw.loganalysis.stats.dto.StatsQueryRequest;
import cn.mw.loganalysis.stats.mapper.ClickHouseQueryMapper;
import cn.mw.loganalysis.stats.mapper.param.ContextLogQueryParam;
import cn.mw.loganalysis.stats.mapper.param.DimensionStatsQueryParam;
import cn.mw.loganalysis.stats.mapper.param.LogQuerySqlParam;
import cn.mw.loganalysis.stats.mapper.param.TimeSeriesQueryParam;
import cn.mw.loganalysis.stats.service.query.support.DynamicMyBatisUtils;
import cn.mw.loganalysis.stats.service.query.support.StatsQueryMapperUtils;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * ClickHouse 日志查询策略实现
 * 使用 ClickHouseOperationStrategy 的共享连接池
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClickHouseQueryStrategy implements LogQueryStrategy {

    // 查询超时时间（秒）
    private static final int QUERY_TIMEOUT_SECONDS = 30;

    // 使用 ClickHouseOperationStrategy 的共享连接池
    private final ClickHouseOperationStrategy operationStrategy;
    private final ClickHouseMcpQueryService clickHouseMcpQueryService;

    // 自定义线程池用于统计查询
    private final ExecutorService statsQueryExecutor = new ThreadPoolExecutor(
        4,                                          // 核心线程数
        10,                                         // 最大线程数
        60L,                                        // 空闲线程存活时间
        TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(100),             // 队列容量
        new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("stats-query-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        },
        new ThreadPoolExecutor.CallerRunsPolicy()   // 拒绝策略：调用者运行
    );

    @Override
    public String getSupportedType() {
        return "clickhouse";
    }

    @Override
    public List<FieldInfo> getTableSchema(DatasourceConnectionConfig config) {
        log.info("ClickHouse getTableSchema: table={}", config.getTable());


        String database = StringUtils.hasText(config.getDatabase()) ? config.getDatabase() : "default";
        List<FieldInfo> fields = DynamicMyBatisUtils.execute(
                getSqlSessionFactory(config),
                ClickHouseQueryMapper.class,
                mapper -> StatsQueryMapperUtils.toFieldInfoList(
                        mapper.selectTableSchemaRows(database, config.getTable()),
                        this::isTimestampType,
                        this::isStatsDimensionType,
                        this::isContentField
                )
        );

        log.info("Found {} fields in table {}", fields.size(), config.getTable());
        return fields;
    }

    private boolean isTimestampType(String type) {
        return type != null && (
            type.contains("DateTime") || 
            type.contains("Date") ||
            type.equalsIgnoreCase("timestamp")
        );
    }

    private boolean isStatsDimensionType(String type) {
        // 字符串类型和枚举类型适合做统计维度
        return type != null && (
            type.contains("String") ||
            type.contains("Enum") ||
            type.contains("LowCardinality")
        );
    }

    private boolean isContentField(String name) {
        // 常见的内容字段名
        return name != null && (
            name.equalsIgnoreCase("message") ||
            name.equalsIgnoreCase("raw") ||
            name.equalsIgnoreCase("content") ||
            name.equalsIgnoreCase("body") ||
            name.equalsIgnoreCase("log") ||
            name.equalsIgnoreCase("text")
        );
    }

    @Override
    public Map<String, Object> queryLogs(LogQueryRequest request, DatasourceConnectionConfig config) {
        log.info("ClickHouse queryLogs: table={}, endpoint={}", config.getTable(), config.getEndpoint());

        if (shouldUseMcp(request.getUseMcp(), config)) {
            try {
                return queryLogsViaMcp(request, config);
            } catch (Exception ex) {
                if (clickHouseMcpQueryService.isFallbackToJdbcOnError()) {
                    log.warn("ClickHouse MCP 查询日志失败，回退 MyBatis: {}", ex.getMessage());
                } else {
                    throw ex;
                }
            }
        }

        int pageSize = Math.max(ObjectUtils.defaultIfNull(request.getPageSize(), 100), 1);
        int pageNum = Math.max(ObjectUtils.defaultIfNull(request.getPageNum(), 1), 1);
        LogQuerySqlParam queryParam = LogQuerySqlParam.builder()
                .tableName(StatsQueryMapperUtils.quoteClickHouseIdentifier(config.getTable()))
                .startTime(DateTimeUtils.format(request.getStartTime()))
                .endTime(DateTimeUtils.format(request.getEndTime()))
                .pageSize(pageSize)
                .offset((pageNum - 1) * pageSize)
                .fieldFilters(StatsQueryMapperUtils.buildClickHouseFieldFiltersRaw(request.getFieldFilters()))
                .messageConditions(StatsQueryMapperUtils.buildMessageConditions(request.getMessageConditions()))
                .rawConditions(StatsQueryMapperUtils.buildMessageConditions(request.getRawConditions()))
                .build();

        return DynamicMyBatisUtils.execute(getSqlSessionFactory(config), ClickHouseQueryMapper.class, mapper -> {
            Long total = mapper.countLogs(queryParam);
            List<Map<String, Object>> data = mapper.selectLogs(queryParam);
            StatsQueryMapperUtils.normalizeTimestampField(data, "timestamp");

            Map<String, Object> result = new HashMap<>();
            result.put("total", total != null ? total : 0);
            result.put("pageNum", pageNum);
            result.put("pageSize", pageSize);
            result.put("data", data);
            return result;
        });
    }

    @Override
    public Map<String, Object> queryLogContext(LogContextRequest request, DatasourceConnectionConfig config) {
        log.info("ClickHouse queryLogContext: logId={}", request.getLogId());

        if (shouldUseMcp(request.getUseMcp(), config)) {
            try {
                return queryLogContextViaMcp(request, config);
            } catch (Exception ex) {
                if (clickHouseMcpQueryService.isFallbackToJdbcOnError()) {
                    log.warn("ClickHouse MCP 查询上下文失败，回退 MyBatis: {}", ex.getMessage());
                } else {
                    throw ex;
                }
            }
        }

        String tableName = StatsQueryMapperUtils.quoteClickHouseIdentifier(config.getTable());
        String timestamp = DateTimeUtils.format(request.getTimestamp());

        return DynamicMyBatisUtils.execute(getSqlSessionFactory(config), ClickHouseQueryMapper.class, mapper -> {
            List<Map<String, Object>> beforeLogs = new ArrayList<>();
            List<Map<String, Object>> afterLogs = new ArrayList<>();

            if (request.getBeforeCount() != null && request.getBeforeCount() > 0) {
                beforeLogs = mapper.selectContextBeforeLogs(ContextLogQueryParam.builder()
                        .tableName(tableName)
                        .timestamp(timestamp)
                        .limit(request.getBeforeCount())
                        .fieldFilters(Collections.emptyList())
                        .messageConditions(Collections.emptyList())
                        .rawConditions(Collections.emptyList())
                        .build());
                Collections.reverse(beforeLogs);
                StatsQueryMapperUtils.normalizeTimestampField(beforeLogs, "timestamp");
            }

            if (request.getAfterCount() != null && request.getAfterCount() > 0) {
                afterLogs = mapper.selectContextAfterLogs(ContextLogQueryParam.builder()
                        .tableName(tableName)
                        .timestamp(timestamp)
                        .limit(request.getAfterCount())
                        .fieldFilters(Collections.emptyList())
                        .messageConditions(Collections.emptyList())
                        .rawConditions(Collections.emptyList())
                        .build());
                StatsQueryMapperUtils.normalizeTimestampField(afterLogs, "timestamp");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("beforeLogs", beforeLogs);
            result.put("afterLogs", afterLogs);
            result.put("totalBefore", beforeLogs.size());
            result.put("totalAfter", afterLogs.size());
            return result;
        });
    }

    @Override
    public Map<String, Object> queryStats(StatsQueryRequest request, DatasourceConnectionConfig config) {
        log.info("ClickHouse queryStats: dimensions={}", request.getDimensions());

        if (shouldUseMcp(request.getUseMcp(), config)) {
            try {
                return queryStatsViaMcp(request, config);
            } catch (Exception ex) {
                if (clickHouseMcpQueryService.isFallbackToJdbcOnError()) {
                    log.warn("ClickHouse MCP 统计查询失败，回退 MyBatis: {}", ex.getMessage());
                } else {
                    throw ex;
                }
            }
        }

        // 1. 参数验证
        if (request.getDimensions() == null || request.getDimensions().isEmpty()) {
            return buildEmptyResult();
        }

        // 2. 过滤不适合统计的字段
        List<String> validDimensions = request.getDimensions().stream()
            .filter(dim -> !isInvalidStatsDimension(dim))
            .toList();

        if (validDimensions.isEmpty()) {
            log.warn("所有维度都不适合统计: {}", request.getDimensions());
            return buildEmptyResult();
        }

        // 3. 校验字段是否存在于目标表中，过滤不存在的字段
        Set<String> tableColumns = getTableColumnNames(config);
        if (!tableColumns.isEmpty()) {
            List<String> existingDimensions = validDimensions.stream()
                .filter(tableColumns::contains)
                .toList();

            if (existingDimensions.size() < validDimensions.size()) {
                List<String> missingFields = validDimensions.stream()
                    .filter(dim -> !tableColumns.contains(dim))
                    .toList();
                log.warn("以下维度字段在表 {} 中不存在，已跳过: {}", config.getTable(), missingFields);
            }

            validDimensions = existingDimensions;
            if (validDimensions.isEmpty()) {
                log.warn("所有维度字段在表 {} 中都不存在: {}", config.getTable(), request.getDimensions());
                return buildEmptyResult();
            }
        }

        if (validDimensions.size() < request.getDimensions().size()) {
            log.warn("过滤了无效的维度: {} -> {}", request.getDimensions(), validDimensions);
        }

        String tableName = StatsQueryMapperUtils.quoteClickHouseIdentifier(config.getTable());
        String startTime = DateTimeUtils.format(request.getStartTime());
        String endTime = DateTimeUtils.format(request.getEndTime());
        List<String> finalDimensions = validDimensions;

        Map<String, List<Map<String, Object>>> statsData = DynamicMyBatisUtils.execute(
                getSqlSessionFactory(config),
                ClickHouseQueryMapper.class,
                mapper -> {
                    Map<String, List<Map<String, Object>>> data = new HashMap<>();
                    for (String dimension : finalDimensions) {
                        data.put(dimension, mapper.selectDimensionStats(DimensionStatsQueryParam.builder()
                                .tableName(tableName)
                                .startTime(startTime)
                                .endTime(endTime)
                                .dimensionExpression(StatsQueryMapperUtils.quoteClickHouseIdentifier(dimension))
                                .build()));
                    }
                    return data;
                }
        );

        // 4. 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("dimensions", finalDimensions);
        result.put("metrics", request.getMetrics());
        result.put("data", statsData);

        return result;
    }

    /**
     * 判断字段是否不适合做统计维度
     * 不适合的字段：id（唯一值）、message/raw（长文本）、timestamp（时间戳）
     */
    private boolean isInvalidStatsDimension(String dimension) {
        if (dimension == null) {
            return true;
        }

        String lower = dimension.toLowerCase();
        return lower.equals("id") ||
               lower.equals("message") ||
               lower.equals("raw") ||
               lower.equals("timestamp") ||
               lower.contains("_id") ||
               lower.contains("uuid");
    }

    /**
     * 获取表的所有字段名集合（用于校验维度字段是否存在）
     */
    private Set<String> getTableColumnNames(DatasourceConnectionConfig config) {
        try {
            List<FieldInfo> schema = getTableSchema(config);
            return schema.stream()
                .map(FieldInfo::getName)
                .collect(Collectors.toSet());
        } catch (Exception ex) {
            log.warn("获取表 {} 的字段列表失败，跳过字段校验: {}", config.getTable(), ex.getMessage());
            return Collections.emptySet();
        }
    }

    /**
     * 构建空结果
     */
    private Map<String, Object> buildEmptyResult() {
        Map<String, Object> result = new HashMap<>();
        result.put("dimensions", Collections.emptyList());
        result.put("data", Collections.emptyList());
        return result;
    }

    @Override
    public Map<String, Object> queryTimeSeries(StatsQueryRequest request, DatasourceConnectionConfig config) {
        log.info("ClickHouse queryTimeSeries: granularity={}", request.getGranularity());

        if (shouldUseMcp(request.getUseMcp(), config)) {
            try {
                return queryTimeSeriesViaMcp(request, config);
            } catch (Exception ex) {
                if (clickHouseMcpQueryService.isFallbackToJdbcOnError()) {
                    log.warn("ClickHouse MCP 时序查询失败，回退 MyBatis: {}", ex.getMessage());
                } else {
                    throw ex;
                }
            }
        }

        List<Map<String, Object>> series = DynamicMyBatisUtils.execute(
                getSqlSessionFactory(config),
                ClickHouseQueryMapper.class,
                mapper -> mapper.selectTimeSeries(TimeSeriesQueryParam.builder()
                        .tableName(StatsQueryMapperUtils.quoteClickHouseIdentifier(config.getTable()))
                        .startTime(DateTimeUtils.format(request.getStartTime()))
                        .endTime(DateTimeUtils.format(request.getEndTime()))
                        .timeBucketExpression(StatsQueryMapperUtils.getClickHouseTimeBucketExpression(request.getGranularity()))
                        .build())
        );
        StatsQueryMapperUtils.renameAndNormalizeTimestampField(series, "time_bucket", "timestamp");

        Map<String, Object> result = new HashMap<>();
        result.put("granularity", request.getGranularity());
        result.put("series", series);

        return result;
    }

    // ==================== 私有方法 ====================

    private boolean shouldUseMcp(Boolean useMcp, DatasourceConnectionConfig config) {
        if (!Boolean.TRUE.equals(useMcp)) {
            return false;
        }
        return clickHouseMcpQueryService.shouldUse(config);
    }

    private SqlSessionFactory getSqlSessionFactory(DatasourceConnectionConfig config) {
        return operationStrategy.getSqlSessionFactory(config);
    }

    private List<FieldInfo> getTableSchemaViaMcp(DatasourceConnectionConfig config) {
        String database = StringUtils.hasText(config.getDatabase()) ? config.getDatabase() : "default";
        String sql = SqlDebugFormatter.render(
                "SELECT name, type FROM system.columns WHERE database = ? AND table = ? ORDER BY position",
                List.of(database, config.getTable())
        );
        log.info("ClickHouse MCP schema SQL: {}", sql);

        List<Map<String, Object>> rows = clickHouseMcpQueryService.executeSelect(sql, config);
        List<FieldInfo> fields = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String name = stringify(row.get("name"));
            String type = stringify(row.get("type"));
            fields.add(FieldInfo.builder()
                    .name(name)
                    .type(type)
                    .label(name)
                    .isTimestamp(isTimestampType(type))
                    .isStatsDimension(isStatsDimensionType(type))
                    .isContentField(isContentField(name))
                    .build());
        }
        log.info("Found {} fields in table {} via MCP", fields.size(), config.getTable());
        return fields;
    }

    private Map<String, Object> queryLogsViaMcp(LogQueryRequest request, DatasourceConnectionConfig config) {
        String tableName = config.getTable();

        StringBuilder countSql = new StringBuilder();
        countSql.append("SELECT count(*) FROM ").append(tableName)
                .append(" WHERE timestamp >= ? AND timestamp <= ?");
        List<Object> countParams = new ArrayList<>();
        countParams.add(DateTimeUtils.format(request.getStartTime()));
        countParams.add(DateTimeUtils.format(request.getEndTime()));
        addFieldFilters(countSql, countParams, request.getFieldFilters());
        addMessageConditions(countSql, countParams, request.getMessageConditions(), "message");
        addMessageConditions(countSql, countParams, request.getRawConditions(), "raw");
        String renderedCountSql = SqlDebugFormatter.render(countSql.toString(), countParams);
        log.info("ClickHouse MCP queryLogs count SQL: {}", renderedCountSql);
        Long total = clickHouseMcpQueryService.executeCount(renderedCountSql, config);

        StringBuilder dataSql = new StringBuilder();
        dataSql.append("SELECT * FROM ").append(tableName)
                .append(" WHERE timestamp >= ? AND timestamp <= ? ");
        List<Object> dataParams = new ArrayList<>();
        dataParams.add(DateTimeUtils.format(request.getStartTime()));
        dataParams.add(DateTimeUtils.format(request.getEndTime()));
        addFieldFilters(dataSql, dataParams, request.getFieldFilters());
        addMessageConditions(dataSql, dataParams, request.getMessageConditions(), "message");
        addMessageConditions(dataSql, dataParams, request.getRawConditions(), "raw");
        dataSql.append("ORDER BY timestamp DESC ");
        dataSql.append("LIMIT ? OFFSET ?");
        dataParams.add(request.getPageSize());
        dataParams.add((request.getPageNum() - 1) * request.getPageSize());
        String renderedDataSql = SqlDebugFormatter.render(dataSql.toString(), dataParams);
        log.info("ClickHouse MCP queryLogs data SQL: {}", renderedDataSql);

        List<Map<String, Object>> data = clickHouseMcpQueryService.executeSelect(renderedDataSql, config);
        data.forEach(row -> {
            if (row.get("timestamp") != null) {
                row.put("timestamp", row.get("timestamp").toString());
            }
        });

        Map<String, Object> result = new HashMap<>();
        result.put("total", total != null ? total : 0);
        result.put("pageNum", request.getPageNum());
        result.put("pageSize", request.getPageSize());
        result.put("data", data);
        return result;
    }

    private Map<String, Object> queryLogContextViaMcp(LogContextRequest request, DatasourceConnectionConfig config) {
        String tableName = config.getTable();
        List<Map<String, Object>> beforeLogs = new ArrayList<>();
        List<Map<String, Object>> afterLogs = new ArrayList<>();

        if (request.getBeforeCount() != null && request.getBeforeCount() > 0) {
            String beforeSql = SqlDebugFormatter.render(
                    String.format("SELECT * FROM %s WHERE timestamp < ? ORDER BY timestamp DESC LIMIT ?", tableName),
                    List.of(request.getTimestamp(), request.getBeforeCount())
            );
            log.info("ClickHouse MCP context before SQL: {}", beforeSql);
            beforeLogs = clickHouseMcpQueryService.executeSelect(beforeSql, config);
            Collections.reverse(beforeLogs);
        }

        if (request.getAfterCount() != null && request.getAfterCount() > 0) {
            String afterSql = SqlDebugFormatter.render(
                    String.format("SELECT * FROM %s WHERE timestamp > ? ORDER BY timestamp ASC LIMIT ?", tableName),
                    List.of(request.getTimestamp(), request.getAfterCount())
            );
            log.info("ClickHouse MCP context after SQL: {}", afterSql);
            afterLogs = clickHouseMcpQueryService.executeSelect(afterSql, config);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("beforeLogs", beforeLogs);
        result.put("afterLogs", afterLogs);
        result.put("totalBefore", beforeLogs.size());
        result.put("totalAfter", afterLogs.size());
        return result;
    }

    private Map<String, Object> queryStatsViaMcp(StatsQueryRequest request, DatasourceConnectionConfig config) {
        if (request.getDimensions() == null || request.getDimensions().isEmpty()) {
            return buildEmptyResult();
        }

        List<String> validDimensions = request.getDimensions().stream()
                .filter(dim -> !isInvalidStatsDimension(dim))
                .toList();
        if (validDimensions.isEmpty()) {
            return buildEmptyResult();
        }

        String tableName = config.getTable();
        String sql = validDimensions.stream()
                .map(dim -> String.format(
                        "SELECT '%s' as dimension, `%s` as value, count(*) as count FROM %s WHERE timestamp >= ? AND timestamp <= ? GROUP BY `%s` ORDER BY count DESC LIMIT 10",
                        dim, dim, tableName, dim
                ))
                .collect(Collectors.joining(" UNION ALL "));
        List<Object> params = new ArrayList<>();
        for (int i = 0; i < validDimensions.size(); i++) {
            params.add(request.getStartTime());
            params.add(request.getEndTime());
        }
        String renderedSql = SqlDebugFormatter.render(sql, params);
        log.info("ClickHouse MCP stats SQL: {}", renderedSql);

        List<Map<String, Object>> results = clickHouseMcpQueryService.executeSelect(renderedSql, config);
        Map<String, List<Map<String, Object>>> statsData = new HashMap<>();
        for (String dimension : validDimensions) {
            statsData.put(dimension, new ArrayList<>());
        }
        for (Map<String, Object> row : results) {
            String dimension = stringify(row.get("dimension"));
            Map<String, Object> data = new HashMap<>();
            data.put("value", row.get("value"));
            data.put("count", row.get("count"));
            List<Map<String, Object>> dimensionList = statsData.get(dimension);
            if (dimensionList != null) {
                dimensionList.add(data);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("dimensions", validDimensions);
        result.put("metrics", request.getMetrics());
        result.put("data", statsData);
        return result;
    }

    private Map<String, Object> queryTimeSeriesViaMcp(StatsQueryRequest request, DatasourceConnectionConfig config) {
        String tableName = config.getTable();
        String granularityFunc = getGranularityFunction(request.getGranularity());
        String sql = SqlDebugFormatter.render(
                String.format("SELECT %s as time_bucket, count(*) as count FROM %s WHERE timestamp >= ? AND timestamp <= ? GROUP BY time_bucket ORDER BY time_bucket",
                        granularityFunc, tableName),
                List.of(request.getStartTime(), request.getEndTime())
        );
        log.info("ClickHouse MCP timeseries SQL: {}", sql);

        List<Map<String, Object>> series = clickHouseMcpQueryService.executeSelect(sql, config);
        series.forEach(point -> {
            if (point.get("time_bucket") != null) {
                point.put("timestamp", point.get("time_bucket").toString());
                point.remove("time_bucket");
            }
        });

        Map<String, Object> result = new HashMap<>();
        result.put("granularity", request.getGranularity());
        result.put("series", series);
        return result;
    }

    private void addFieldFilters(StringBuilder sql, List<Object> params, List<LogQueryRequest.FieldFilter> filters) {
        if (filters == null || filters.isEmpty()) {
            return;
        }

        for (LogQueryRequest.FieldFilter filter : filters) {
            if (filter.getValues() == null || filter.getValues().isEmpty()) {
                continue;
            }

            String dbField = mapFieldToDbColumn(filter.getField());

            if ("include".equalsIgnoreCase(filter.getType())) {
                // 使用反引号包裹字段名，支持中文和特殊字符
                sql.append("AND `").append(dbField).append("` IN (");
                sql.append(filter.getValues().stream().map(v -> "?").collect(Collectors.joining(", ")));
                sql.append(") ");
                params.addAll(filter.getValues());
            } else if ("exclude".equalsIgnoreCase(filter.getType())) {
                // 使用反引号包裹字段名，支持中文和特殊字符
                sql.append("AND `").append(dbField).append("` NOT IN (");
                sql.append(filter.getValues().stream().map(v -> "?").collect(Collectors.joining(", ")));
                sql.append(") ");
                params.addAll(filter.getValues());
            }
        }
    }

    private String mapFieldToDbColumn(String field) {
        if (field == null) return field;
        switch (field) {
            case "levels": return "severity";
            case "sources": case "source_types": return "source_type";
            case "hosts": case "hostnames": return "hostname";
            case "services": case "appnames": return "appname";
            case "facilities": return "facility";
            case "procids": return "procid";
            case "sourceIps": return "source_ip";
            default: return field;
        }
    }

    private void addMessageConditions(StringBuilder sql, List<Object> params,
                                       List<LogQueryRequest.MessageCondition> conditions, String fieldName) {
        if (conditions == null || conditions.isEmpty()) {
            return;
        }

        for (LogQueryRequest.MessageCondition condition : conditions) {
            if (condition == null || !StringUtils.hasText(condition.getValue())) {
                continue;
            }

            String operator = condition.getOperator();
            String value = condition.getValue();

            switch (operator) {
                case "contains":
                    sql.append("AND ").append(fieldName).append(" LIKE ? ");
                    params.add("%" + value + "%");
                    break;
                case "notContains":
                    sql.append("AND ").append(fieldName).append(" NOT LIKE ? ");
                    params.add("%" + value + "%");
                    break;
                case "equals":
                    sql.append("AND ").append(fieldName).append(" = ? ");
                    params.add(value);
                    break;
                case "notEquals":
                    sql.append("AND ").append(fieldName).append(" != ? ");
                    params.add(value);
                    break;
                default:
                    sql.append("AND ").append(fieldName).append(" LIKE ? ");
                    params.add("%" + value + "%");
            }
        }
    }

    private String getGranularityFunction(String granularity) {
        if (granularity == null || "auto".equals(granularity)) {
            return "toStartOfHour(timestamp)";
        }
        switch (granularity) {
            case "1m": return "toStartOfMinute(timestamp)";
            case "5m": return "toStartOfFiveMinutes(timestamp)";
            case "1h": return "toStartOfHour(timestamp)";
            case "1d": return "toStartOfDay(timestamp)";
            default: return "toStartOfHour(timestamp)";
        }
    }

    /**
     * 关闭线程池（在应用关闭时调用）
     */
    @PreDestroy
    public void shutdown() {
        log.info("关闭统计查询线程池");
        statsQueryExecutor.shutdown();
        try {
            if (!statsQueryExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("线程池未能在 10 秒内关闭，强制关闭");
                statsQueryExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("等待线程池关闭时被中断", e);
            statsQueryExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 执行原始SQL查询
     */
    @Override
    public Object executeRawSQL(String sql, DatasourceConnectionConfig connectionConfig) {
        log.info("执行原始SQL: {}", sql);

        if (clickHouseMcpQueryService.shouldUse(connectionConfig) && isReadOnlySql(sql)) {
            try {
                return clickHouseMcpQueryService.executeSelect(sql, connectionConfig);
            } catch (Exception ex) {
                if (clickHouseMcpQueryService.isFallbackToJdbcOnError()) {
                    log.warn("ClickHouse MCP 执行原始 SQL 失败，回退 JDBC: {}", ex.getMessage());
                } else {
                    throw ex;
                }
            }
        }

        return executeRawSQLViaJdbc(sql, connectionConfig);
    }

    /**
     * 直接通过 JDBC 执行原始 SQL。
     * Dashboard 首页这类高频固定查询不应为每条 SQL 启动一次 MCP 进程。
     */
    public Object executeRawSQLViaJdbc(String sql, DatasourceConnectionConfig connectionConfig) {
        String jdbcUrl = buildJdbcUrl(connectionConfig);
        String username = connectionConfig.getUsername() != null ? connectionConfig.getUsername() : "default";
        String password = connectionConfig.getPassword() != null ? connectionConfig.getPassword() : "";

        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            List<Map<String, Object>> results = new ArrayList<>();
            boolean singleColumn = columnCount == 1;
            Object singleValue = null;
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnName(i);
                    Object value = rs.getObject(i);
                    row.put(columnName, value);
                    if (singleColumn && results.isEmpty()) {
                        singleValue = value;
                    }
                }
                results.add(row);
            }

            if (singleColumn && results.size() == 1) {
                return singleValue;
            }
            return results;

        } catch (Exception e) {
            log.error("执行SQL失败: {}", sql, e);
            throw new RuntimeException("执行SQL失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建 JDBC URL
     */
    private String buildJdbcUrl(DatasourceConnectionConfig config) {
        String endpoint = config.getEndpoint();
        String database = config.getDatabase();

        if (endpoint.startsWith("jdbc:")) {
            return endpoint;
        }

        StringBuilder url = new StringBuilder("jdbc:clickhouse://");
        url.append(endpoint);
        if (StringUtils.hasText(database)) {
            url.append("/").append(database);
        }

        return url.toString();
    }

    private String stringify(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean isReadOnlySql(String sql) {
        if (!StringUtils.hasText(sql)) {
            return false;
        }
        String normalized = sql.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("select")
                || normalized.startsWith("show")
                || normalized.startsWith("describe")
                || normalized.startsWith("explain")
                || normalized.startsWith("with");
    }
}
