package cn.mw.loganalysis.alert.executor;

import cn.mw.loganalysis.alert.dto.AlertAggregateDTO;
import cn.mw.loganalysis.alert.dto.AlertConditionDTO;
import cn.mw.loganalysis.alert.dto.AlertDatasetTarget;
import cn.mw.loganalysis.alert.dto.AlertMonitorOptionsDTO;
import cn.mw.loganalysis.alert.dto.AlertThresholdDTO;
import cn.mw.loganalysis.alert.dto.AlertThresholdsDTO;
import cn.mw.loganalysis.stats.service.query.support.StatsQueryMapperUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

    /**
     * 构建查询 SQL
     */
    public String buildQuery(AlertDatasetTarget target, AlertConditionDTO condition) {
        return buildAggregationSql(target, condition, null);
    }

    public String buildQuery(AlertDatasetTarget target, AlertConditionDTO condition, AlertThresholdsDTO thresholds) {
        return buildAggregationSql(target, condition, thresholds);
    }

    /**
     * 构建聚合查询 SQL
     */
    private String buildAggregationSql(AlertDatasetTarget target, AlertConditionDTO condition, AlertThresholdsDTO thresholds) {
        String timeWindow = resolveTimeWindow(condition, thresholds);
        String windowEndExpression = buildWindowEndExpression(condition);
        String windowStartExpression = buildWindowStartExpression(condition, timeWindow);

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
                .append(" >= ").append(windowStartExpression).append(" ");
        sql.append("AND ")
                .append(quoteIdentifier(target.getTimeField()))
                .append(" <= ").append(windowEndExpression).append(" ");

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

    public EvaluationWindow resolveEvaluationWindow(AlertConditionDTO condition, AlertThresholdsDTO thresholds) {
        LocalDateTime endTime = resolveEvaluationEndTime(condition);
        LocalDateTime startTime = calculateStartTime(endTime, resolveTimeWindow(condition, thresholds));
        return new EvaluationWindow(startTime, endTime);
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

    private void appendStructuredFilters(StringBuilder sql, AlertDatasetTarget target, Map<String, Object> filters) {
        if (MapUtils.isEmpty(filters)) {
            return;
        }

        Object fieldFiltersObj = filters.get("fieldFilters");
        if (fieldFiltersObj instanceof List<?> fieldFilters) {
            for (Object item : fieldFilters) {
                if (!(item instanceof Map<?, ?> filter)) {
                    continue;
                }
                String field = String.valueOf(ObjectUtils.defaultIfNull(filter.get("field"), ""));
                Object valuesObj = filter.get("values");
                if (StringUtils.isBlank(field) || !(valuesObj instanceof List<?> values) || CollectionUtils.isEmpty(values)) {
                    continue;
                }

                String fieldExpression = quoteIdentifier(resolveField(target, field));
                String valueList = values.stream()
                        .map(String::valueOf)
                        .map(this::quoteValue)
                        .reduce((left, right) -> left + "," + right)
                        .orElse("");

                if ("exclude".equalsIgnoreCase(String.valueOf(filter.get("type")))) {
                    sql.append("AND ").append(fieldExpression).append(" NOT IN (").append(valueList).append(") ");
                } else {
                    sql.append("AND ").append(fieldExpression).append(" IN (").append(valueList).append(") ");
                }
            }
        }

        appendMessageConditions(sql, target.getMessageField(), filters.get("messageConditions"));
        appendMessageConditions(sql, target.getRawField(), filters.get("rawConditions"));
    }

    private void appendMessageConditions(StringBuilder sql, String fieldName, Object conditionsObj) {
        if (!(conditionsObj instanceof List<?> conditions) || CollectionUtils.isEmpty(conditions) || StringUtils.isBlank(fieldName)) {
            return;
        }

        String fieldExpression = quoteIdentifier(fieldName);
        for (Object item : conditions) {
            if (!(item instanceof Map<?, ?> condition)) {
                continue;
            }
            String conditionValue = String.valueOf(ObjectUtils.defaultIfNull(condition.get("value"), ""));
            if (StringUtils.isBlank(conditionValue)) {
                continue;
            }

            String value = conditionValue.replace("'", "''");
            String operator = StringUtils.lowerCase(String.valueOf(condition.get("operator")));
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

    private int resolveEvaluationDelaySeconds(AlertConditionDTO condition) {
        AlertMonitorOptionsDTO options = condition.getOptions();
        if (options == null) {
            return 0;
        }
        Integer delaySeconds = ObjectUtils.defaultIfNull(options.getEvaluationDelaySeconds(), 0);
        return Math.max(0, delaySeconds);
    }

    private LocalDateTime resolveEvaluationEndTime(AlertConditionDTO condition) {
        return LocalDateTime.now(ZoneOffset.UTC).minusSeconds(resolveEvaluationDelaySeconds(condition));
    }

    private String resolveTimeWindow(AlertConditionDTO condition, AlertThresholdsDTO thresholds) {
        AlertThresholdDTO critical = thresholds != null ? thresholds.getCritical() : null;
        if (critical != null && StringUtils.isNotBlank(critical.getTimeWindow())) {
            return critical.getTimeWindow();
        }
        return condition.getTrigger() != null
                ? StringUtils.defaultIfBlank(condition.getTrigger().getTimeWindow(), "5m")
                : "5m";
    }

    /**
     * 计算开始时间
     */
    private LocalDateTime calculateStartTime(LocalDateTime endTime, String timeWindow) {
        if (StringUtils.isBlank(timeWindow)) {
            return endTime.minusMinutes(5);
        }

        TimeWindowInterval interval = parseTimeWindow(timeWindow);
        return switch (interval.unit()) {
            case "SECOND" -> endTime.minusSeconds(interval.value());
            case "MINUTE" -> endTime.minusMinutes(interval.value());
            case "HOUR" -> endTime.minusHours(interval.value());
            case "DAY" -> endTime.minusDays(interval.value());
            default -> endTime.minusMinutes(interval.value());
        };
    }

    private String buildWindowEndExpression(AlertConditionDTO condition) {
        int delaySeconds = resolveEvaluationDelaySeconds(condition);
        if (delaySeconds <= 0) {
            return "now()";
        }
        return "now() - INTERVAL " + delaySeconds + " SECOND";
    }

    private String buildWindowStartExpression(AlertConditionDTO condition, String timeWindow) {
        TimeWindowInterval interval = parseTimeWindow(timeWindow);
        return buildWindowEndExpression(condition)
                + " - INTERVAL " + interval.value() + " " + interval.unit();
    }

    private TimeWindowInterval parseTimeWindow(String timeWindow) {
        if (StringUtils.isBlank(timeWindow)) {
            return new TimeWindowInterval(5, "MINUTE");
        }

        String numberPart = timeWindow.replaceAll("[^0-9]", "");
        String unitPart = timeWindow.replaceAll("[0-9]", "");
        int value = StringUtils.isNotBlank(numberPart) ? Integer.parseInt(numberPart) : 5;
        String unit = switch (StringUtils.lowerCase(unitPart)) {
            case "s" -> "SECOND";
            case "h" -> "HOUR";
            case "d" -> "DAY";
            default -> "MINUTE";
        };
        return new TimeWindowInterval(Math.max(1, value), unit);
    }

    public record EvaluationWindow(LocalDateTime startTime, LocalDateTime endTime) {
    }

    private record TimeWindowInterval(int value, String unit) {
    }
}
