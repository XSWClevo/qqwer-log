package cn.mw.loganalysis.alert.executor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 查询构建器
 * 根据规则条件构建 ClickHouse SQL 查询
 */
@Slf4j
@Component
public class QueryBuilder {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 构建查询 SQL
     */
    public String buildQuery(Map<String, Object> condition) {
        String type = (String) condition.getOrDefault("type", "log_query");
        
        return switch (type) {
            case "log_query" -> buildLogQuerySql(condition);
            case "metric_threshold" -> buildMetricThresholdSql(condition);
            case "anomaly" -> buildAnomalySql(condition);
            default -> throw new IllegalArgumentException("Unsupported rule type: " + type);
        };
    }

    /**
     * 构建日志查询类型的 SQL
     */
    private String buildLogQuerySql(Map<String, Object> condition) {
        String query = (String) condition.get("query");
        String metric = (String) condition.getOrDefault("metric", "count");
        String timeWindow = (String) condition.getOrDefault("timeWindow", "5m");
        String groupBy = (String) condition.get("groupBy");
        
        // 不再需要映射 groupBy 字段，前端直接使用数据库字段名
        
        // 计算时间范围
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = calculateStartTime(endTime, timeWindow);
        
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        
        // 添加 GROUP BY 字段
        if (groupBy != null && !groupBy.isEmpty()) {
            sql.append(groupBy).append(", ");
        }
        
        // 添加聚合函数
        sql.append(buildMetricFunction(metric)).append(" as value ");
        sql.append("FROM syslog ");
        sql.append("WHERE timestamp >= '").append(startTime.format(FORMATTER)).append("' ");
        sql.append("AND timestamp <= '").append(endTime.format(FORMATTER)).append("' ");
        
        // 添加查询条件
        if (query != null && !query.isEmpty()) {
            String whereClause = parseQueryToWhereClause(query);
            sql.append("AND ").append(whereClause).append(" ");
        }
        
        // 添加 GROUP BY
        if (groupBy != null && !groupBy.isEmpty()) {
            sql.append("GROUP BY ").append(groupBy);
        }
        
        log.debug("Built SQL: {}", sql);
        return sql.toString();
    }

    /**
     * 构建指标阈值类型的 SQL
     */
    private String buildMetricThresholdSql(Map<String, Object> condition) {
        String metric = (String) condition.get("metric");
        String timeWindow = (String) condition.getOrDefault("timeWindow", "3m");
        
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = calculateStartTime(endTime, timeWindow);
        
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        sql.append(buildMetricFunction(metric)).append(" as value ");
        sql.append("FROM syslog ");
        sql.append("WHERE timestamp >= '").append(startTime.format(FORMATTER)).append("' ");
        sql.append("AND timestamp <= '").append(endTime.format(FORMATTER)).append("'");
        
        return sql.toString();
    }

    /**
     * 构建异常检测类型的 SQL
     */
    private String buildAnomalySql(Map<String, Object> condition) {
        String metric = (String) condition.get("metric");
        String timeWindow = (String) condition.getOrDefault("timeWindow", "10m");
        
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = calculateStartTime(endTime, timeWindow);
        
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        sql.append("avg(").append(metric).append(") as avg_value, ");
        sql.append("stddevPop(").append(metric).append(") as stddev_value ");
        sql.append("FROM syslog ");
        sql.append("WHERE timestamp >= '").append(startTime.format(FORMATTER)).append("' ");
        sql.append("AND timestamp <= '").append(endTime.format(FORMATTER)).append("'");
        
        return sql.toString();
    }

    /**
     * 构建聚合函数
     */
    private String buildMetricFunction(String metric) {
        return switch (metric) {
            case "count" -> "count()";
            case "sum" -> "sum(1)";
            case "avg" -> "avg(1)";
            case "max" -> "max(1)";
            case "min" -> "min(1)";
            default -> "count()";
        };
    }

    /**
     * 解析查询字符串为 WHERE 子句
     * 前端和后端统一使用数据库字段名，不再需要映射
     */
    private String parseQueryToWhereClause(String query) {
        if (query == null || query.isEmpty()) {
            return "1=1";
        }
        
        // 直接返回查询字符串，不再进行字段映射
        return query;
    }

    /**
     * 映射字段名（已废弃，保留用于向后兼容）
     * 现在前端直接使用数据库字段名，不再需要映射
     */
    @Deprecated
    private String mapFieldName(String fieldName) {
        // 不再进行映射，直接返回原字段名
        return fieldName;
    }

    /**
     * 计算开始时间
     */
    private LocalDateTime calculateStartTime(LocalDateTime endTime, String timeWindow) {
        if (timeWindow == null || timeWindow.isEmpty()) {
            return endTime.minusMinutes(5);
        }
        
        // 解析时间窗口 (例如: "5m", "1h", "1d")
        int value = Integer.parseInt(timeWindow.replaceAll("[^0-9]", ""));
        String unit = timeWindow.replaceAll("[0-9]", "");
        
        return switch (unit) {
            case "s" -> endTime.minusSeconds(value);
            case "m" -> endTime.minusMinutes(value);
            case "h" -> endTime.minusHours(value);
            case "d" -> endTime.minusDays(value);
            default -> endTime.minusMinutes(value);
        };
    }
}
