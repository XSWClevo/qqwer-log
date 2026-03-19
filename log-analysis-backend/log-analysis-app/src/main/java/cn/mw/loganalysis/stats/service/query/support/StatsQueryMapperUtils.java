package cn.mw.loganalysis.stats.service.query.support;

import cn.mw.loganalysis.common.util.DateTimeUtils;
import cn.mw.loganalysis.stats.dto.LogQueryRequest;
import cn.mw.loganalysis.stats.mapper.param.SqlFieldFilterParam;
import cn.mw.loganalysis.stats.mapper.param.SqlMessageConditionParam;
import cn.mw.loganalysis.stats.service.query.FieldInfo;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Stats MyBatis 查询辅助工具
 */
public final class StatsQueryMapperUtils {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(DateTimeUtils.PATTERN_DATETIME);

    private StatsQueryMapperUtils() {
    }

    public static String quoteClickHouseIdentifier(String identifier) {
        return quoteQualifiedIdentifier(identifier, "`");
    }

    public static String quotePostgreSqlIdentifier(String identifier) {
        return quoteQualifiedIdentifier(identifier, "\"");
    }

    public static String qualifyClickHouseTable(String database, String tableName) {
        if (StringUtils.contains(tableName, '.')) {
            return quoteClickHouseIdentifier(tableName);
        }
        if (StringUtils.isBlank(database)) {
            return quoteClickHouseIdentifier(tableName);
        }
        return quoteClickHouseIdentifier(database) + "." + quoteClickHouseIdentifier(tableName);
    }

    public static List<SqlFieldFilterParam> buildClickHouseFieldFilters(List<LogQueryRequest.FieldFilter> filters) {
        return buildFieldFilters(filters, StatsQueryMapperUtils::quoteClickHouseIdentifier);
    }

    public static List<SqlFieldFilterParam> buildPostgreSqlFieldFilters(List<LogQueryRequest.FieldFilter> filters) {
        return buildFieldFilters(filters, StatsQueryMapperUtils::quotePostgreSqlIdentifier);
    }

    public static List<SqlMessageConditionParam> buildMessageConditions(List<LogQueryRequest.MessageCondition> conditions) {
        if (CollectionUtils.isEmpty(conditions)) {
            return Collections.emptyList();
        }

        List<SqlMessageConditionParam> results = new ArrayList<>();
        for (LogQueryRequest.MessageCondition condition : conditions) {
            if (ObjectUtils.isEmpty(condition) || StringUtils.isBlank(condition.getValue())) {
                continue;
            }

            results.add(SqlMessageConditionParam.builder()
                    .operator(StringUtils.lowerCase(condition.getOperator()))
                    .value(condition.getValue())
                    .build());
        }
        return results;
    }

    public static String getClickHouseTimeBucketExpression(String granularity) {
        if (StringUtils.isBlank(granularity) || StringUtils.equals(granularity, "auto")) {
            return "toStartOfHour(timestamp)";
        }
        return switch (granularity) {
            case "1m" -> "toStartOfMinute(timestamp)";
            case "5m" -> "toStartOfFiveMinutes(timestamp)";
            case "1h" -> "toStartOfHour(timestamp)";
            case "1d" -> "toStartOfDay(timestamp)";
            default -> "toStartOfHour(timestamp)";
        };
    }

    public static String getPostgreSqlTimeBucketExpression(String granularity) {
        if (StringUtils.isBlank(granularity) || StringUtils.equals(granularity, "auto")) {
            return "date_trunc('hour', timestamp)";
        }
        return switch (granularity) {
            case "1m" -> "date_trunc('minute', timestamp)";
            case "5m" -> "date_trunc('minute', timestamp) - (EXTRACT(MINUTE FROM timestamp)::int % 5) * interval '1 minute'";
            case "1h" -> "date_trunc('hour', timestamp)";
            case "1d" -> "date_trunc('day', timestamp)";
            default -> "date_trunc('hour', timestamp)";
        };
    }

    public static String format(LocalDateTime time) {
        return DateTimeUtils.format(time);
    }

    public static void normalizeTimestampField(List<Map<String, Object>> rows, String fieldName) {
        renameAndNormalizeTimestampField(rows, fieldName, fieldName);
    }

    public static void renameAndNormalizeTimestampField(List<Map<String, Object>> rows, String sourceField, String targetField) {
        if (CollectionUtils.isEmpty(rows)) {
            return;
        }

        for (Map<String, Object> row : rows) {
            Object rawValue = row.get(sourceField);
            if (ObjectUtils.isEmpty(rawValue)) {
                continue;
            }

            row.put(targetField, formatTimestamp(rawValue));
            if (!StringUtils.equals(sourceField, targetField)) {
                row.remove(sourceField);
            }
        }
    }

    public static List<FieldInfo> toFieldInfoList(List<Map<String, Object>> rows,
                                                  Predicate<String> timestampChecker,
                                                  Predicate<String> statsDimensionChecker,
                                                  Predicate<String> contentFieldChecker) {
        if (CollectionUtils.isEmpty(rows)) {
            return Collections.emptyList();
        }

        return rows.stream()
                .map(row -> {
                    String name = String.valueOf(row.get("name"));
                    String type = String.valueOf(row.get("type"));
                    return FieldInfo.builder()
                            .name(name)
                            .type(type)
                            .label(name)
                            .isTimestamp(timestampChecker.test(type))
                            .isStatsDimension(statsDimensionChecker.test(type))
                            .isContentField(contentFieldChecker.test(name))
                            .build();
                })
                .collect(Collectors.toList());
    }

    private static List<SqlFieldFilterParam> buildFieldFilters(List<LogQueryRequest.FieldFilter> filters,
                                                               java.util.function.Function<String, String> quoter) {
        if (CollectionUtils.isEmpty(filters)) {
            return Collections.emptyList();
        }

        List<SqlFieldFilterParam> results = new ArrayList<>();
        for (LogQueryRequest.FieldFilter filter : filters) {
            if (ObjectUtils.isEmpty(filter)
                    || StringUtils.isBlank(filter.getField())
                    || CollectionUtils.isEmpty(filter.getValues())) {
                continue;
            }

            results.add(SqlFieldFilterParam.builder()
                    .columnExpression(quoter.apply(mapFieldToDbColumn(filter.getField())))
                    .type(StringUtils.lowerCase(filter.getType()))
                    .values(filter.getValues())
                    .build());
        }
        return results;
    }

    private static String mapFieldToDbColumn(String field) {
        String normalized = StringUtils.lowerCase(field);
        return switch (StringUtils.defaultString(normalized)) {
            case "levels", "level" -> "severity";
            case "sources", "source", "source_types", "source_type" -> "source_type";
            case "hosts", "host", "hostnames", "hostname" -> "hostname";
            case "services", "service", "appnames", "appname" -> "appname";
            case "facilities", "facility" -> "facility";
            case "procids", "procid" -> "procid";
            case "sourceips", "sourceip", "source_ip" -> "source_ip";
            default -> field;
        };
    }

    private static String quoteQualifiedIdentifier(String identifier, String quoteChar) {
        if (StringUtils.isBlank(identifier)) {
            throw new IllegalArgumentException("标识符不能为空");
        }

        String[] parts = StringUtils.splitPreserveAllTokens(identifier, '.');
        return Arrays.stream(parts)
                .map(part -> {
                    if (StringUtils.isBlank(part)) {
                        throw new IllegalArgumentException("非法标识符: " + identifier);
                    }
                    return quoteChar + StringUtils.replace(part, quoteChar, quoteChar + quoteChar) + quoteChar;
                })
                .collect(Collectors.joining("."));
    }

    private static String formatTimestamp(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().format(FORMATTER);
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.format(FORMATTER);
        }
        return String.valueOf(value);
    }
}
