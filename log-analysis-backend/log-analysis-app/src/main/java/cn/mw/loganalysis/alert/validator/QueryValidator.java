package cn.mw.loganalysis.alert.validator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 查询语句验证器
 * 验证告警规则查询中使用的字段是否有效
 */
@Slf4j
@Component
public class QueryValidator {

    /**
     * syslog 表中的有效字段（数据库实际字段名）
     * 前端和后端统一使用数据库字段名，不再进行映射
     */
    private static final Set<String> VALID_FIELDS = Set.of(
            "id", "severity", "hostname", "appname", "source_type",
            "message", "facility", "procid", "source_ip", "timestamp", "raw"
    );

    /**
     * 字段提取正则表达式
     * 匹配 SQL 查询中的字段名（字母开头，可包含字母、数字、下划线）
     */
    private static final Pattern FIELD_PATTERN = Pattern.compile(
            "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*(?:=|!=|>|>=|<|<=|LIKE|NOT\\s+LIKE|IN|NOT\\s+IN)",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 验证查询语句
     *
     * @param query 查询语句
     * @throws IllegalArgumentException 如果查询包含无效字段
     */
    public void validateQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return;
        }

        log.debug("Validating query: {}", query);

        Set<String> usedFields = extractFields(query);
        Set<String> invalidFields = new HashSet<>();

        for (String field : usedFields) {
            if (!VALID_FIELDS.contains(field.toLowerCase())) {
                invalidFields.add(field);
            }
        }

        if (!invalidFields.isEmpty()) {
            String errorMessage = String.format(
                    "查询包含无效字段: %s. 可用字段: %s",
                    String.join(", ", invalidFields),
                    String.join(", ", VALID_FIELDS)
            );
            log.warn("Query validation failed: {}", errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }

        log.debug("Query validation passed");
    }

    /**
     * 验证 groupBy 字段
     *
     * @param groupBy groupBy 字段名
     * @throws IllegalArgumentException 如果字段无效
     */
    public void validateGroupByField(String groupBy) {
        if (groupBy == null || groupBy.trim().isEmpty()) {
            return;
        }

        String field = groupBy.trim().toLowerCase();
        if (!VALID_FIELDS.contains(field)) {
            String errorMessage = String.format(
                    "无效的 groupBy 字段: %s. 可用字段: %s",
                    groupBy,
                    String.join(", ", VALID_FIELDS)
            );
            log.warn("GroupBy validation failed: {}", errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }

        log.debug("GroupBy field validation passed: {}", groupBy);
    }

    /**
     * 从查询语句中提取字段名
     *
     * @param query 查询语句
     * @return 字段名集合
     */
    private Set<String> extractFields(String query) {
        Set<String> fields = new HashSet<>();
        Matcher matcher = FIELD_PATTERN.matcher(query);

        while (matcher.find()) {
            String field = matcher.group(1);
            // 排除 SQL 关键字
            if (!isSqlKeyword(field)) {
                fields.add(field.toLowerCase());
            }
        }

        return fields;
    }

    /**
     * 判断是否为 SQL 关键字
     *
     * @param word 单词
     * @return 是否为关键字
     */
    private boolean isSqlKeyword(String word) {
        Set<String> keywords = Set.of(
                "and", "or", "not", "like", "in", "between", "is", "null",
                "true", "false", "select", "from", "where", "group", "by",
                "order", "having", "limit", "offset"
        );
        return keywords.contains(word.toLowerCase());
    }

    /**
     * 获取所有有效字段列表
     *
     * @return 字段列表
     */
    public Set<String> getValidFields() {
        return new HashSet<>(VALID_FIELDS);
    }
}
