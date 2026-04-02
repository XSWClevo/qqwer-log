package cn.mw.loganalysis.alert.executor;

import cn.mw.loganalysis.alert.dto.AlertAggregateDTO;
import cn.mw.loganalysis.alert.dto.AlertConditionDTO;
import cn.mw.loganalysis.alert.dto.AlertDatasetTarget;
import cn.mw.loganalysis.alert.dto.AlertFilterDTO;
import cn.mw.loganalysis.stats.dto.LogQueryRequest;
import cn.mw.loganalysis.stats.service.query.support.StatsQueryMapperUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    public String buildQuery(AlertDatasetTarget target, AlertConditionDTO condition) {
        return buildAggregationSql(target, condition);
    }

    /**
     * 构建聚合查询 SQL
     */
    private String buildAggregationSql(AlertDatasetTarget target, AlertConditionDTO condition) {
        String timeWindow = condition.getTrigger() != null
                ? StringUtils.defaultIfBlank(condition.getTrigger().getTimeWindow(), "5m")
                : "5m";
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = calculateStartTime(endTime, timeWindow);

        List<String> groupByFields = buildGroupByFields(target, condition.getGroupBy());
        AlertAggregateDTO aggregate = condition.getAggregate() != null ? condition.getAggregate() : new AlertAggregateDTO();

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        if (CollectionUtils.isNotEmpty(groupByFields)) {
            sql.append(String.join(", ", groupByFields)).append(", ");
        }

        sql.append(buildMetricFunction(target, aggregate)).append(" as value ");
        sql.append("FROM ").append(buildTableExpression(target)).append(" ");
        sql.append("WHERE ")
                .append(quoteIdentifier(target.getTimeField()))
                .append(" >= '").append(startTime.format(FORMATTER)).append("' ");
        sql.append("AND ")
                .append(quoteIdentifier(target.getTimeField()))
                .append(" <= '").append(endTime.format(FORMATTER)).append("' ");

        appendStructuredFilters(sql, target, condition.getFilters());
        if (StringUtils.isNotBlank(condition.getQuery())) {
            String whereClause = parseQueryToWhereClause(condition.getQuery(), target);
            sql.append("AND ").append(whereClause).append(" ");
        }

        if (CollectionUtils.isNotEmpty(groupByFields)) {
            sql.append("GROUP BY ").append(String.join(", ", groupByFields)).append(" ");
        }

        log.debug("Built SQL: {}", sql);
        return sql.toString();
    }

    /**
     * 构建聚合函数
     */
    private String buildMetricFunction(AlertDatasetTarget target, AlertAggregateDTO aggregate) {
        String metric = StringUtils.defaultIfBlank(aggregate.getFunction(), "count");
        String aggregateField = resolveField(target, aggregate.getField());

        return switch (metric) {
            case "count" -> "count()";
            case "distinct", "unique" -> "uniqExact(" + quoteIdentifier(aggregateField) + ")";
            case "sum" -> "sum(" + quoteIdentifier(aggregateField) + ")";
            case "avg" -> "avg(" + quoteIdentifier(aggregateField) + ")";
            case "max" -> "max(" + quoteIdentifier(aggregateField) + ")";
            case "min" -> "min(" + quoteIdentifier(aggregateField) + ")";
            default -> "count()";
        };
    }

    /**
     * 解析查询字符串为 WHERE 子句
     */
    private String parseQueryToWhereClause(String query, AlertDatasetTarget target) {
        if (StringUtils.isBlank(query)) {
            return "1=1";
        }

        String whereClause = query;
        if (MapUtils.isNotEmpty(target.getFieldMapping())) {
            List<Map.Entry<String, String>> mappings = new ArrayList<>(target.getFieldMapping().entrySet());
            mappings.sort(Comparator.comparingInt(entry -> -entry.getKey().length()));
            for (Map.Entry<String, String> entry : mappings) {
                whereClause = whereClause.replaceAll("\\b" + entry.getKey() + "\\b", entry.getValue());
            }
        }
        return whereClause;
    }

    private void appendStructuredFilters(StringBuilder sql, AlertDatasetTarget target, AlertFilterDTO filters) {
        if (filters == null) {
            return;
        }

        if (CollectionUtils.isNotEmpty(filters.getFieldFilters())) {
            for (LogQueryRequest.FieldFilter filter : filters.getFieldFilters()) {
                if (filter == null || StringUtils.isBlank(filter.getField()) || CollectionUtils.isEmpty(filter.getValues())) {
                    continue;
                }

                String fieldExpression = quoteIdentifier(resolveField(target, filter.getField()));
                String valueList = filter.getValues().stream()
                        .map(this::quoteValue)
                        .reduce((left, right) -> left + "," + right)
                        .orElse("");

                if ("exclude".equalsIgnoreCase(filter.getType())) {
                    sql.append("AND ").append(fieldExpression).append(" NOT IN (").append(valueList).append(") ");
                } else {
                    sql.append("AND ").append(fieldExpression).append(" IN (").append(valueList).append(") ");
                }
            }
        }

        appendMessageConditions(sql, target.getMessageField(), filters.getMessageConditions());
        appendMessageConditions(sql, target.getRawField(), filters.getRawConditions());
    }

    private void appendMessageConditions(StringBuilder sql, String fieldName, List<LogQueryRequest.MessageCondition> conditions) {
        if (CollectionUtils.isEmpty(conditions) || StringUtils.isBlank(fieldName)) {
            return;
        }

        String fieldExpression = quoteIdentifier(fieldName);
        for (LogQueryRequest.MessageCondition condition : conditions) {
            if (condition == null || StringUtils.isBlank(condition.getValue())) {
                continue;
            }

            String value = condition.getValue().replace("'", "''");
            String operator = StringUtils.lowerCase(condition.getOperator());
            switch (operator) {
                case "notcontains" -> sql.append("AND ").append(fieldExpression)
                        .append(" NOT LIKE '%").append(value).append("%' ");
                case "equals" -> sql.append("AND ").append(fieldExpression)
                        .append(" = '").append(value).append("' ");
                case "notequals" -> sql.append("AND ").append(fieldExpression)
                        .append(" != '").append(value).append("' ");
                default -> sql.append("AND ").append(fieldExpression)
                        .append(" LIKE '%").append(value).append("%' ");
            }
        }
    }

    private List<String> buildGroupByFields(AlertDatasetTarget target, List<String> groupBy) {
        if (CollectionUtils.isEmpty(groupBy)) {
            return List.of();
        }

        return groupBy.stream()
                .filter(StringUtils::isNotBlank)
                .map(field -> quoteIdentifier(resolveField(target, field)))
                .toList();
    }

    private String buildTableExpression(AlertDatasetTarget target) {
        if (StringUtils.isBlank(target.getDatabaseName())) {
            return StatsQueryMapperUtils.quoteClickHouseIdentifier(target.getTableName());
        }
        return StatsQueryMapperUtils.qualifyClickHouseTable(target.getDatabaseName(), target.getTableName());
    }

    private String resolveField(AlertDatasetTarget target, String logicalField) {
        if (StringUtils.isBlank(logicalField) || "*".equals(logicalField)) {
            return logicalField;
        }
        if (MapUtils.isEmpty(target.getFieldMapping())) {
            return logicalField;
        }
        return StringUtils.defaultIfBlank(target.getFieldMapping().get(logicalField), logicalField);
    }

    private String quoteIdentifier(String fieldName) {
        if (StringUtils.isBlank(fieldName) || "*".equals(fieldName)) {
            return fieldName;
        }
        return StatsQueryMapperUtils.quoteClickHouseIdentifier(fieldName);
    }

    private String quoteValue(String value) {
        return "'" + StringUtils.replace(StringUtils.defaultString(value), "'", "''") + "'";
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
