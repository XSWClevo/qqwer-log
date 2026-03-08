package cn.mw.loganalysis.stats.service;

import cn.mw.loganalysis.stats.dto.LogContextRequest;
import cn.mw.loganalysis.stats.dto.LogQueryRequest;
import cn.mw.loganalysis.stats.dto.StatsQueryRequest;
import cn.mw.loganalysis.stats.service.query.SqlDebugFormatter;
import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.FileWriter;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 统计服务 - 使用动态数据源
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    private final JdbcTemplate jdbcTemplate;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 查询日志 - 切换到ClickHouse数据源
     */
    @DS("clickhouse")
    public Map<String, Object> queryLogs(LogQueryRequest request) {
        log.info("Querying logs from {} to {} using ClickHouse datasource",
                 request.getStartTime(), request.getEndTime());

        // 构建SQL查询
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ")
           .append("id, timestamp, severity, hostname, appname, source_type, message, facility, procid, source_ip, raw ")
           .append("FROM syslog ")
           .append("WHERE timestamp >= ? AND timestamp <= ? ");

        List<Object> params = new ArrayList<>();
        params.add(request.getStartTime());
        params.add(request.getEndTime());

        // 添加字段过滤器（统一处理包含和排除）
        addFieldFilters(sql, params, request.getFieldFilters());

        // 添加message字段查询条件
        addMessageConditions(sql, params, request.getMessageConditions(), "message");

        // 添加raw字段查询条件
        addMessageConditions(sql, params, request.getRawConditions(), "raw");

        // 查询总数
        Long total = queryTotalCount(request);

        // 添加排序和分页
        sql.append("ORDER BY timestamp DESC ");
        sql.append("LIMIT ? OFFSET ?");
        params.add(request.getPageSize());
        params.add((request.getPageNum() - 1) * request.getPageSize());

        log.info("Default ClickHouse queryLogs data SQL: template={}, params={}, rendered={}",
                sql, params, SqlDebugFormatter.render(sql.toString(), params));

        // 执行查询
        List<Map<String, Object>> data = jdbcTemplate.query(
                sql.toString(),
                params.toArray(),
                (rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("timestamp", rs.getTimestamp("timestamp").toLocalDateTime().format(FORMATTER));
                    row.put("severity", rs.getString("severity"));
                    row.put("source_type", rs.getString("source_type"));
                    row.put("message", rs.getString("message"));
                    row.put("hostname", rs.getString("hostname"));
                    row.put("appname", rs.getString("appname"));
                    row.put("facility", rs.getString("facility"));
                    row.put("procid", rs.getString("procid"));
                    row.put("source_ip", rs.getString("source_ip"));
                    row.put("raw", rs.getString("raw"));
                    return row;
                }
        );

        // 构建响应
        Map<String, Object> result = new HashMap<>();
        result.put("total", total != null ? total : 0);
        result.put("pageNum", request.getPageNum());
        result.put("pageSize", request.getPageSize());
        result.put("data", data);

        log.info("Query completed, returned {} records out of {}", data.size(), total);
        return result;
    }

    /**
     * 查询日志上下文 - 获取指定日志前后的相关日志
     */
    @DS("clickhouse")
    public Map<String, Object> queryLogContext(LogContextRequest request) {
        log.info("Querying log context for logId: {}, timestamp: {}", 
                 request.getLogId(), request.getTimestamp());

        List<Map<String, Object>> beforeLogs = new ArrayList<>();
        List<Map<String, Object>> afterLogs = new ArrayList<>();

        // 查询目标日志之前的日志（时间早于目标日志）
        if (request.getBeforeCount() != null && request.getBeforeCount() > 0) {
            beforeLogs = queryContextLogs(request, true, request.getBeforeCount());
        }

        // 查询目标日志之后的日志（时间晚于目标日志）
        if (request.getAfterCount() != null && request.getAfterCount() > 0) {
            afterLogs = queryContextLogs(request, false, request.getAfterCount());
        }

        // 反转 beforeLogs 使其按时间正序排列
        Collections.reverse(beforeLogs);

        // 构建响应
        Map<String, Object> result = new HashMap<>();
        result.put("beforeLogs", beforeLogs);
        result.put("afterLogs", afterLogs);
        result.put("totalBefore", beforeLogs.size());
        result.put("totalAfter", afterLogs.size());

        log.info("Context query completed: {} before, {} after", beforeLogs.size(), afterLogs.size());
        return result;
    }

    /**
     * 查询上下文日志的内部方法
     * @param request 上下文查询请求
     * @param isBefore true表示查询之前的日志，false表示查询之后的日志
     * @param limit 查询条数
     */
    private List<Map<String, Object>> queryContextLogs(LogContextRequest request, boolean isBefore, int limit) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ")
           .append("id, timestamp, severity, hostname, appname, source_type, message, facility, procid, source_ip, raw ")
           .append("FROM syslog ")
           .append("WHERE ");

        List<Object> params = new ArrayList<>();

        // 时间范围条件
        if (isBefore) {
            sql.append("timestamp < ? ");
            params.add(request.getTimestamp());
        } else {
            sql.append("timestamp > ? ");
            params.add(request.getTimestamp());
        }

        // 添加字段过滤器
        addFieldFilters(sql, params, request.getFieldFilters());

        // 添加message字段查询条件
        addMessageConditions(sql, params, request.getMessageConditions(), "message");

        // 添加raw字段查询条件
        addMessageConditions(sql, params, request.getRawConditions(), "raw");

        // 排序和限制
        if (isBefore) {
            sql.append("ORDER BY timestamp DESC ");
        } else {
            sql.append("ORDER BY timestamp ASC ");
        }
        sql.append("LIMIT ?");
        params.add(limit);

        log.debug("Executing context SQL: {}", sql);

        // 执行查询
        return jdbcTemplate.query(
                sql.toString(),
                params.toArray(),
                (rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("timestamp", rs.getTimestamp("timestamp").toLocalDateTime().format(FORMATTER));
                    row.put("severity", rs.getString("severity"));
                    row.put("source_type", rs.getString("source_type"));
                    row.put("message", rs.getString("message"));
                    row.put("hostname", rs.getString("hostname"));
                    row.put("appname", rs.getString("appname"));
                    row.put("facility", rs.getString("facility"));
                    row.put("procid", rs.getString("procid"));
                    row.put("source_ip", rs.getString("source_ip"));
                    row.put("raw", rs.getString("raw"));
                    return row;
                }
        );
    }

    /**
     * 添加字段过滤器（统一处理包含和排除）
     */
    private void addFieldFilters(StringBuilder sql, List<Object> params, List<LogQueryRequest.FieldFilter> fieldFilters) {
        if (fieldFilters == null || fieldFilters.isEmpty()) {
            return;
        }

        for (LogQueryRequest.FieldFilter filter : fieldFilters) {
            if (filter.getValues() == null || filter.getValues().isEmpty()) {
                continue;
            }

            // 映射字段名到数据库字段
            String dbField = mapFieldToDbColumn(filter.getField());

            if ("include".equalsIgnoreCase(filter.getType())) {
                // 包含过滤：field IN (values)
                sql.append("AND ").append(dbField).append(" IN (");
                sql.append(filter.getValues().stream()
                        .map(v -> "?")
                        .collect(Collectors.joining(", ")));
                sql.append(") ");
                params.addAll(filter.getValues());
            } else if ("exclude".equalsIgnoreCase(filter.getType())) {
                // 排除过滤：field NOT IN (values)
                sql.append("AND ").append(dbField).append(" NOT IN (");
                sql.append(filter.getValues().stream()
                        .map(v -> "?")
                        .collect(Collectors.joining(", ")));
                sql.append(") ");
                params.addAll(filter.getValues());
            }
        }
    }

    /**
     * 映射前端字段名到数据库列名
     */
    private String mapFieldToDbColumn(String field) {
        if (field == null) {
            return field;
        }
        switch (field) {
            case "levels":
                return "severity";
            case "sources":
                return "source_type";
            case "hosts":
                return "hostname";
            case "services":
                return "appname";
            case "facilities":
                return "facility";
            case "procids":
                return "procid";
            case "sourceIps":
                return "source_ip";
            default:
                return field;
        }
    }

    /**
     * 添加包含过滤器
     */
    private void addIncludeFilters(StringBuilder sql, List<Object> params, List<String> values, String field) {
        if (values != null && !values.isEmpty()) {
            sql.append("AND ").append(field).append(" IN (");
            sql.append(values.stream()
                    .map(v -> "?")
                    .collect(Collectors.joining(", ")));
            sql.append(") ");
            params.addAll(values);
        }
    }

    /**
     * 添加message/raw字段查询条件
     */
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
                    // 默认使用包含查询
                    sql.append("AND ").append(fieldName).append(" LIKE ? ");
                    params.add("%" + value + "%");
            }
        }
    }

    /**
     * 查询总数 - 使用ClickHouse数据源
     */
    @DS("clickhouse")
    private Long queryTotalCount(LogQueryRequest request) {
        StringBuilder countSql = new StringBuilder("SELECT count(*) FROM syslog WHERE timestamp >= ? AND timestamp <= ?");
        List<Object> countParams = new ArrayList<>();
        countParams.add(request.getStartTime());
        countParams.add(request.getEndTime());

        // 添加字段过滤器
        addFieldFilters(countSql, countParams, request.getFieldFilters());

        // 添加message条件
        addMessageConditions(countSql, countParams, request.getMessageConditions(), "message");

        // 添加raw条件
        addMessageConditions(countSql, countParams, request.getRawConditions(), "raw");

        log.info("Default ClickHouse queryLogs count SQL: template={}, params={}, rendered={}",
                countSql, countParams, SqlDebugFormatter.render(countSql.toString(), countParams));
        return jdbcTemplate.queryForObject(countSql.toString(), Long.class, countParams.toArray());
    }

    /**
     * 统计查询（字段统计）- 使用ClickHouse数据源
     */
    @DS("clickhouse")
    public Map<String, Object> queryStats(StatsQueryRequest request) {
        log.info("Query stats with dimensions: {} using ClickHouse datasource", request.getDimensions());

        Map<String, Object> result = new HashMap<>();

        if (request.getDimensions() == null || request.getDimensions().isEmpty()) {
            result.put("dimensions", Collections.emptyList());
            result.put("data", Collections.emptyList());
            return result;
        }

        // 查询每个维度的统计数据
        Map<String, List<Map<String, Object>>> statsData = new HashMap<>();

        for (String dimension : request.getDimensions()) {
            // 映射维度字段名
            String fieldName = mapDimensionField(dimension);

            String sql = String.format(
                "SELECT %s as value, count(*) as count " +
                "FROM syslog " +
                "WHERE timestamp >= ? AND timestamp <= ? " +
                "GROUP BY %s " +
                "ORDER BY count DESC " +
                "LIMIT 10",
                fieldName, fieldName
            );

            List<Map<String, Object>> dimensionData = jdbcTemplate.query(
                sql,
                new Object[]{request.getStartTime(), request.getEndTime()},
                (rs, rowNum) -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("value", rs.getString("value"));
                    row.put("count", rs.getLong("count"));
                    return row;
                }
            );

            statsData.put(dimension, dimensionData);
        }

        result.put("dimensions", request.getDimensions());
        result.put("metrics", request.getMetrics());
        result.put("data", statsData);

        return result;
    }

    /**
     * 映射前端维度字段到数据库字段
     * 已废弃：现在前端直接使用数据库字段名
     */
    @Deprecated
    private String mapDimensionField(String dimension) {
        // 不再进行映射，直接返回
        return dimension;
    }

    /**
     * 时间序列查询 - 使用ClickHouse数据源
     */
    @DS("clickhouse")
    public Map<String, Object> queryTimeSeries(StatsQueryRequest request) {
        log.info("Query time series with granularity: {} using ClickHouse datasource", request.getGranularity());

        // 根据粒度选择聚合函数
        String granularityFunc = getGranularityFunction(request.getGranularity());

        String sql = String.format(
            "SELECT %s as time_bucket, count(*) as count " +
            "FROM syslog " +
            "WHERE timestamp >= ? AND timestamp <= ? " +
            "GROUP BY time_bucket " +
            "ORDER BY time_bucket",
            granularityFunc
        );

        List<Map<String, Object>> series = jdbcTemplate.query(
            sql,
            new Object[]{request.getStartTime(), request.getEndTime()},
            (rs, rowNum) -> {
                Map<String, Object> point = new HashMap<>();
                point.put("timestamp", rs.getTimestamp("time_bucket").toLocalDateTime().format(FORMATTER));
                point.put("count", rs.getLong("count"));
                return point;
            }
        );

        Map<String, Object> result = new HashMap<>();
        result.put("granularity", request.getGranularity());
        result.put("series", series);

        log.info("Time series query completed, returned {} data points", series.size());
        return result;
    }

    /**
     * 字段时序查询 - 按时间分组统计特定字段值的数量
     */
    @DS("clickhouse")
    public Map<String, Object> queryFieldTimeSeries(String fieldName, String fieldValue,
                                                     String startTime, String endTime,
                                                     String granularity) {
        log.info("Query field time series: field={}, value={}, granularity={}",
                 fieldName, fieldValue, granularity);

        // 映射字段名
        String dbFieldName = mapDimensionField(fieldName);

        // 根据粒度选择聚合函数
        String granularityFunc = getGranularityFunction(granularity);

        String sql = String.format(
            "SELECT %s as time_bucket, count(*) as count " +
            "FROM syslog " +
            "WHERE timestamp >= ? AND timestamp <= ? AND %s = ? " +
            "GROUP BY time_bucket " +
            "ORDER BY time_bucket",
            granularityFunc, dbFieldName
        );

        List<Map<String, Object>> series = jdbcTemplate.query(
            sql,
            new Object[]{startTime, endTime, fieldValue},
            (rs, rowNum) -> {
                Map<String, Object> point = new HashMap<>();
                point.put("timestamp", rs.getTimestamp("time_bucket").toLocalDateTime().format(FORMATTER));
                point.put("count", rs.getLong("count"));
                return point;
            }
        );

        Map<String, Object> result = new HashMap<>();
        result.put("field", fieldName);
        result.put("value", fieldValue);
        result.put("granularity", granularity);
        result.put("series", series);

        log.info("Field time series query completed, returned {} data points", series.size());
        return result;
    }

    /**
     * 导出报表
     */
    public String exportReport(LogQueryRequest request, String format) {
        log.info("Exporting report in format: {}", format);

        // 查询所有数据（不分页）
        LogQueryRequest exportRequest = new LogQueryRequest();
        exportRequest.setStartTime(request.getStartTime());
        exportRequest.setEndTime(request.getEndTime());
        exportRequest.setFieldFilters(request.getFieldFilters());
        exportRequest.setMessageConditions(request.getMessageConditions());
        exportRequest.setRawConditions(request.getRawConditions());
        exportRequest.setPageNum(1);
        exportRequest.setPageSize(10000); // 限制最大导出数量

        Map<String, Object> queryResult = queryLogs(exportRequest);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) queryResult.get("data");

        // 生成文件
        String filename = "log_report_" + System.currentTimeMillis() + "." + format;
        String filepath = "/tmp/" + filename;

        try {
            if ("csv".equalsIgnoreCase(format)) {
                exportToCsv(data, filepath);
            } else if ("xlsx".equalsIgnoreCase(format)) {
                exportToExcel(data, filepath);
            }

            log.info("Report exported to: {}", filepath);
            return filename;
        } catch (IOException e) {
            log.error("Failed to export report", e);
            throw new RuntimeException("导出报表失败: " + e.getMessage());
        }
    }

    /**
     * 导出到CSV
     */
    private void exportToCsv(List<Map<String, Object>> data, String filepath) throws IOException {
        try (FileWriter writer = new FileWriter(filepath)) {
            // 写入CSV头部
            writer.write("时间戳,级别,来源,主机,服务,用户,消息\n");

            // 写入数据
            for (Map<String, Object> row : data) {
                writer.write(String.format("%s,%s,%s,%s,%s,%s,\"%s\"\n",
                    row.get("timestamp"),
                    row.get("level"),
                    row.get("source"),
                    row.get("host"),
                    row.get("service"),
                    row.get("user"),
                    row.get("message").toString().replace("\"", "\"\"") // CSV转义
                ));
            }
        }
    }

    /**
     * 导出到Excel（简化版，使用CSV格式）
     */
    private void exportToExcel(List<Map<String, Object>> data, String filepath) throws IOException {
        // 简化处理：使用CSV格式
        exportToCsv(data, filepath);
    }

    /**
     * 根据粒度获取聚合函数
     */
    private String getGranularityFunction(String granularity) {
        if (granularity == null || "auto".equals(granularity)) {
            return "toStartOfHour(timestamp)";
        }

        switch (granularity) {
            case "1m":
                return "toStartOfMinute(timestamp)";
            case "5m":
                return "toStartOfFiveMinutes(timestamp)";
            case "1h":
                return "toStartOfHour(timestamp)";
            case "1d":
                return "toStartOfDay(timestamp)";
            default:
                return "toStartOfHour(timestamp)";
        }
    }
}
