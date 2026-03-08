package cn.mw.loganalysis.stats.service.query;

import cn.mw.loganalysis.stats.dto.LogContextRequest;
import cn.mw.loganalysis.stats.dto.LogQueryRequest;
import cn.mw.loganalysis.stats.dto.StatsQueryRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PostgreSQL 日志查询策略实现
 * 使用 PostgreSQLOperationStrategy 的共享连接池
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostgreSQLQueryStrategy implements LogQueryStrategy {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PostgreSQLOperationStrategy operationStrategy;

    @Override
    public String getSupportedType() {
        return "postgresql";
    }

    @Override
    public List<FieldInfo> getTableSchema(DatasourceConnectionConfig config) {
        log.info("PostgreSQL getTableSchema: table={}", config.getTable());

        JdbcTemplate jdbcTemplate = getJdbcTemplate(config);
        String tableName = config.getTable();

        // 查询表结构
        String sql = "SELECT column_name, data_type FROM information_schema.columns " +
                     "WHERE table_name = ? ORDER BY ordinal_position";
        
        List<FieldInfo> fields = jdbcTemplate.query(sql, 
            new Object[]{tableName},
            (rs, rowNum) -> {
                String name = rs.getString("column_name");
                String type = rs.getString("data_type");
                
                return FieldInfo.builder()
                        .name(name)
                        .type(type)
                        .label(name)
                        .isTimestamp(isTimestampType(type))
                        .isStatsDimension(isStatsDimensionType(type))
                        .isContentField(isContentField(name))
                        .build();
            }
        );

        log.info("Found {} fields in table {}", fields.size(), tableName);
        return fields;
    }

    private boolean isTimestampType(String type) {
        return type != null && (
            type.contains("timestamp") || 
            type.contains("date") ||
            type.contains("time")
        );
    }

    private boolean isStatsDimensionType(String type) {
        return type != null && (
            type.contains("character") ||
            type.contains("varchar") ||
            type.contains("text") ||
            type.equals("name")
        );
    }

    private boolean isContentField(String name) {
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
        log.info("PostgreSQL queryLogs: table={}, endpoint={}", config.getTable(), config.getEndpoint());

        JdbcTemplate jdbcTemplate = getJdbcTemplate(config);
        String tableName = config.getTable();

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT * FROM ").append(tableName)
           .append(" WHERE timestamp >= ?::timestamp AND timestamp <= ?::timestamp ");

        List<Object> params = new ArrayList<>();
        params.add(request.getStartTime().toString());
        params.add(request.getEndTime().toString());

        addFieldFilters(sql, params, request.getFieldFilters());
        addMessageConditions(sql, params, request.getMessageConditions(), "message");
        addMessageConditions(sql, params, request.getRawConditions(), "raw");

        Long total = queryTotalCount(jdbcTemplate, tableName, request);

        sql.append("ORDER BY timestamp DESC ");
        sql.append("LIMIT ? OFFSET ?");
        params.add(request.getPageSize());
        params.add((request.getPageNum() - 1) * request.getPageSize());

        log.info("PostgreSQL queryLogs data SQL: template={}, params={}, rendered={}",
                sql, params, SqlDebugFormatter.render(sql.toString(), params));

        List<Map<String, Object>> data = jdbcTemplate.queryForList(sql.toString(), params.toArray());

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

    @Override
    public Map<String, Object> queryLogContext(LogContextRequest request, DatasourceConnectionConfig config) {
        log.info("PostgreSQL queryLogContext: logId={}", request.getLogId());

        JdbcTemplate jdbcTemplate = getJdbcTemplate(config);
        String tableName = config.getTable();

        List<Map<String, Object>> beforeLogs = new ArrayList<>();
        List<Map<String, Object>> afterLogs = new ArrayList<>();

        if (request.getBeforeCount() != null && request.getBeforeCount() > 0) {
            String beforeSql = String.format(
                "SELECT * FROM %s WHERE timestamp < ?::timestamp ORDER BY timestamp DESC LIMIT ?",
                tableName
            );
            beforeLogs = jdbcTemplate.queryForList(beforeSql, request.getTimestamp().toString(), request.getBeforeCount());
            Collections.reverse(beforeLogs);
        }

        if (request.getAfterCount() != null && request.getAfterCount() > 0) {
            String afterSql = String.format(
                "SELECT * FROM %s WHERE timestamp > ?::timestamp ORDER BY timestamp ASC LIMIT ?",
                tableName
            );
            afterLogs = jdbcTemplate.queryForList(afterSql, request.getTimestamp().toString(), request.getAfterCount());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("beforeLogs", beforeLogs);
        result.put("afterLogs", afterLogs);
        result.put("totalBefore", beforeLogs.size());
        result.put("totalAfter", afterLogs.size());

        return result;
    }

    @Override
    public Map<String, Object> queryStats(StatsQueryRequest request, DatasourceConnectionConfig config) {
        log.info("PostgreSQL queryStats: dimensions={}", request.getDimensions());

        JdbcTemplate jdbcTemplate = getJdbcTemplate(config);
        String tableName = config.getTable();

        Map<String, Object> result = new HashMap<>();

        if (request.getDimensions() == null || request.getDimensions().isEmpty()) {
            result.put("dimensions", Collections.emptyList());
            result.put("data", Collections.emptyList());
            return result;
        }

        Map<String, List<Map<String, Object>>> statsData = new HashMap<>();

        for (String dimension : request.getDimensions()) {
            String sql = String.format(
                "SELECT %s as value, count(*) as count FROM %s " +
                "WHERE timestamp >= ?::timestamp AND timestamp <= ?::timestamp " +
                "GROUP BY %s ORDER BY count DESC LIMIT 10",
                dimension, tableName, dimension
            );

            List<Map<String, Object>> dimensionData = jdbcTemplate.queryForList(
                sql, request.getStartTime().toString(), request.getEndTime().toString()
            );
            statsData.put(dimension, dimensionData);
        }

        result.put("dimensions", request.getDimensions());
        result.put("metrics", request.getMetrics());
        result.put("data", statsData);

        return result;
    }

    @Override
    public Map<String, Object> queryTimeSeries(StatsQueryRequest request, DatasourceConnectionConfig config) {
        log.info("PostgreSQL queryTimeSeries: granularity={}", request.getGranularity());

        JdbcTemplate jdbcTemplate = getJdbcTemplate(config);
        String tableName = config.getTable();

        String granularityFunc = getGranularityFunction(request.getGranularity());

        String sql = String.format(
            "SELECT %s as time_bucket, count(*) as count FROM %s " +
            "WHERE timestamp >= ?::timestamp AND timestamp <= ?::timestamp " +
            "GROUP BY time_bucket ORDER BY time_bucket",
            granularityFunc, tableName
        );

        List<Map<String, Object>> series = jdbcTemplate.queryForList(
            sql, request.getStartTime().toString(), request.getEndTime().toString()
        );

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

    @Override
    public Object executeRawSQL(String sql, DatasourceConnectionConfig connectionConfig) {
        return null;
    }

    // ==================== 私有方法 ====================

    private JdbcTemplate getJdbcTemplate(DatasourceConnectionConfig config) {
        // 使用 OperationStrategy 的共享连接池
        return operationStrategy.getJdbcTemplate(config);
    }

    private Long queryTotalCount(JdbcTemplate jdbcTemplate, String tableName, LogQueryRequest request) {
        StringBuilder countSql = new StringBuilder();
        countSql.append("SELECT count(*) FROM ").append(tableName)
                .append(" WHERE timestamp >= ?::timestamp AND timestamp <= ?::timestamp");

        List<Object> params = new ArrayList<>();
        params.add(request.getStartTime().toString());
        params.add(request.getEndTime().toString());

        addFieldFilters(countSql, params, request.getFieldFilters());
        addMessageConditions(countSql, params, request.getMessageConditions(), "message");
        addMessageConditions(countSql, params, request.getRawConditions(), "raw");

        log.info("PostgreSQL queryLogs count SQL: template={}, params={}, rendered={}",
                countSql, params, SqlDebugFormatter.render(countSql.toString(), params));
        return jdbcTemplate.queryForObject(countSql.toString(), Long.class, params.toArray());

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
                sql.append("AND ").append(dbField).append(" IN (");
                sql.append(filter.getValues().stream().map(v -> "?").collect(Collectors.joining(", ")));
                sql.append(") ");
                params.addAll(filter.getValues());
            } else if ("exclude".equalsIgnoreCase(filter.getType())) {
                sql.append("AND ").append(dbField).append(" NOT IN (");
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
                    sql.append("AND ").append(fieldName).append(" ILIKE ? ");
                    params.add("%" + value + "%");
                    break;
                case "notContains":
                    sql.append("AND ").append(fieldName).append(" NOT ILIKE ? ");
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
                    sql.append("AND ").append(fieldName).append(" ILIKE ? ");
                    params.add("%" + value + "%");
            }
        }
    }

    private String getGranularityFunction(String granularity) {
        if (granularity == null || "auto".equals(granularity)) {
            return "date_trunc('hour', timestamp)";
        }
        switch (granularity) {
            case "1m": return "date_trunc('minute', timestamp)";
            case "5m": return "date_trunc('minute', timestamp) - (EXTRACT(MINUTE FROM timestamp)::int % 5) * interval '1 minute'";
            case "1h": return "date_trunc('hour', timestamp)";
            case "1d": return "date_trunc('day', timestamp)";
            default: return "date_trunc('hour', timestamp)";
        }
    }
}
