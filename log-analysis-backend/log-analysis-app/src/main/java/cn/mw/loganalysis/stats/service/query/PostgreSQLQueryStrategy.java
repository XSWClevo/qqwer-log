package cn.mw.loganalysis.stats.service.query;

import cn.mw.loganalysis.stats.dto.LogContextRequest;
import cn.mw.loganalysis.stats.dto.LogQueryRequest;
import cn.mw.loganalysis.stats.dto.StatsQueryRequest;
import cn.mw.loganalysis.stats.mapper.PostgreSqlQueryMapper;
import cn.mw.loganalysis.stats.mapper.param.ContextLogQueryParam;
import cn.mw.loganalysis.stats.mapper.param.DimensionStatsQueryParam;
import cn.mw.loganalysis.stats.mapper.param.LogQuerySqlParam;
import cn.mw.loganalysis.stats.mapper.param.TimeSeriesQueryParam;
import cn.mw.loganalysis.stats.service.query.support.DynamicMyBatisUtils;
import cn.mw.loganalysis.stats.service.query.support.StatsQueryMapperUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PostgreSQL 日志查询策略实现
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostgreSQLQueryStrategy implements LogQueryStrategy {

    private final PostgreSQLOperationStrategy operationStrategy;

    @Override
    public String getSupportedType() {
        return "postgresql";
    }

    @Override
    public List<FieldInfo> getTableSchema(DatasourceConnectionConfig config) {
        log.info("PostgreSQL getTableSchema: table={}", config.getTable());
        return DynamicMyBatisUtils.execute(
                getSqlSessionFactory(config),
                PostgreSqlQueryMapper.class,
                mapper -> StatsQueryMapperUtils.toFieldInfoList(
                        mapper.selectTableSchemaRows(config.getTable()),
                        this::isTimestampType,
                        this::isStatsDimensionType,
                        this::isContentField
                )
        );
    }

    @Override
    public Map<String, Object> queryLogs(LogQueryRequest request, DatasourceConnectionConfig config) {
        log.info("PostgreSQL queryLogs: table={}, endpoint={}", config.getTable(), config.getEndpoint());

        String tableName = StatsQueryMapperUtils.quotePostgreSqlIdentifier(config.getTable());
        int pageSize = Math.max(ObjectUtils.defaultIfNull(request.getPageSize(), 100), 1);
        int pageNum = Math.max(ObjectUtils.defaultIfNull(request.getPageNum(), 1), 1);
        LogQuerySqlParam queryParam = LogQuerySqlParam.builder()
                .tableName(tableName)
                .startTime(StatsQueryMapperUtils.format(request.getStartTime()))
                .endTime(StatsQueryMapperUtils.format(request.getEndTime()))
                .pageSize(pageSize)
                .offset((pageNum - 1) * pageSize)
                .fieldFilters(StatsQueryMapperUtils.buildPostgreSqlFieldFiltersRaw(request.getFieldFilters()))
                .messageConditions(StatsQueryMapperUtils.buildMessageConditions(request.getMessageConditions()))
                .rawConditions(StatsQueryMapperUtils.buildMessageConditions(request.getRawConditions()))
                .build();

        return DynamicMyBatisUtils.execute(getSqlSessionFactory(config), PostgreSqlQueryMapper.class, mapper -> {
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
        log.info("PostgreSQL queryLogContext: logId={}", request.getLogId());

        String tableName = StatsQueryMapperUtils.quotePostgreSqlIdentifier(config.getTable());
        String timestamp = StatsQueryMapperUtils.format(request.getTimestamp());

        return DynamicMyBatisUtils.execute(getSqlSessionFactory(config), PostgreSqlQueryMapper.class, mapper -> {
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
        log.info("PostgreSQL queryStats: dimensions={}", request.getDimensions());

        Map<String, Object> result = new HashMap<>();
        if (request.getDimensions() == null || request.getDimensions().isEmpty()) {
            result.put("dimensions", Collections.emptyList());
            result.put("data", Collections.emptyList());
            return result;
        }

        String tableName = StatsQueryMapperUtils.quotePostgreSqlIdentifier(config.getTable());
        String startTime = StatsQueryMapperUtils.format(request.getStartTime());
        String endTime = StatsQueryMapperUtils.format(request.getEndTime());

        Map<String, List<Map<String, Object>>> statsData = DynamicMyBatisUtils.execute(
                getSqlSessionFactory(config),
                PostgreSqlQueryMapper.class,
                mapper -> {
                    Map<String, List<Map<String, Object>>> data = new HashMap<>();
                    for (String dimension : request.getDimensions()) {
                        data.put(dimension, mapper.selectDimensionStats(DimensionStatsQueryParam.builder()
                                .tableName(tableName)
                                .startTime(startTime)
                                .endTime(endTime)
                                .dimensionExpression(StatsQueryMapperUtils.quotePostgreSqlIdentifier(dimension))
                                .build()));
                    }
                    return data;
                }
        );

        result.put("dimensions", request.getDimensions());
        result.put("metrics", request.getMetrics());
        result.put("data", statsData);
        return result;
    }

    @Override
    public Map<String, Object> queryTimeSeries(StatsQueryRequest request, DatasourceConnectionConfig config) {
        log.info("PostgreSQL queryTimeSeries: granularity={}", request.getGranularity());

        TimeSeriesQueryParam queryParam = TimeSeriesQueryParam.builder()
                .tableName(StatsQueryMapperUtils.quotePostgreSqlIdentifier(config.getTable()))
                .startTime(StatsQueryMapperUtils.format(request.getStartTime()))
                .endTime(StatsQueryMapperUtils.format(request.getEndTime()))
                .timeBucketExpression(StatsQueryMapperUtils.getPostgreSqlTimeBucketExpression(request.getGranularity()))
                .build();

        List<Map<String, Object>> series = DynamicMyBatisUtils.execute(
                getSqlSessionFactory(config),
                PostgreSqlQueryMapper.class,
                mapper -> mapper.selectTimeSeries(queryParam)
        );
        StatsQueryMapperUtils.renameAndNormalizeTimestampField(series, "time_bucket", "timestamp");

        Map<String, Object> result = new HashMap<>();
        result.put("granularity", request.getGranularity());
        result.put("series", series);
        return result;
    }

    @Override
    public Object executeRawSQL(String sql, DatasourceConnectionConfig connectionConfig) {
        return null;
    }

    private SqlSessionFactory getSqlSessionFactory(DatasourceConnectionConfig config) {
        return operationStrategy.getSqlSessionFactory(config);
    }

    private boolean isTimestampType(String type) {
        return type != null && (
                type.contains("timestamp")
                        || type.contains("date")
                        || type.contains("time")
        );
    }

    private boolean isStatsDimensionType(String type) {
        return type != null && (
                type.contains("character")
                        || type.contains("varchar")
                        || type.contains("text")
                        || type.equals("name")
        );
    }

    private boolean isContentField(String name) {
        return name != null && (
                name.equalsIgnoreCase("message")
                        || name.equalsIgnoreCase("raw")
                        || name.equalsIgnoreCase("content")
                        || name.equalsIgnoreCase("body")
                        || name.equalsIgnoreCase("log")
                        || name.equalsIgnoreCase("text")
        );
    }
}
