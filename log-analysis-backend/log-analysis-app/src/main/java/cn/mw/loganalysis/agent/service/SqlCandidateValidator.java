package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import cn.mw.loganalysis.stats.service.query.FieldInfo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Text2SQL 候选 SQL 安全校验。
 */
@Component
@RequiredArgsConstructor
public class SqlCandidateValidator {

    private static final Pattern FORBIDDEN_SQL_PATTERN = Pattern.compile(
            "\\b(insert|update|delete|drop|alter|truncate|create|replace|rename|grant|revoke|attach|detach|optimize|system|kill)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TABLE_CLAUSE_PATTERN = Pattern.compile(
            "\\b(from|join)\\b(.+?)(?=\\b(?:where|prewhere|group\\s+by|having|order\\s+by|limit|settings|union|join|on)\\b|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^`?([A-Za-z0-9_\\.]+)`?");
    private static final Pattern DERIVED_TABLE_PATTERN = Pattern.compile("\\b(?:from|join)\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIMIT_BY_PATTERN = Pattern.compile("\\blimit\\b.+?\\bby\\b", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern LIMIT_CLAUSE_PATTERN = Pattern.compile(
            "\\blimit\\s+(?:(\\d+)\\s*,\\s*)?(\\d+)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BACKTICKED_TOKEN_PATTERN = Pattern.compile("`([^`]+)`");
    private static final Pattern BACKTICKED_ALIAS_PATTERN = Pattern.compile("(?i)\\bas\\s+`([^`]+)`");
    private static final Pattern BACKTICKED_IMPLICIT_ALIAS_PATTERN = Pattern.compile("\\)\\s+`([^`]+)`");
    private static final int DEFAULT_LIMIT = 200;

    private final DynamicLogQueryService dynamicLogQueryService;

    /**
     * 校验候选 SQL，并在缺少 LIMIT 时补默认限制。
     */
    public SqlCandidateValidationResult validate(AgentExecutionContext context, SqlCandidate candidate) {
        if (candidate == null || StringUtils.isBlank(candidate.sql())) {
            return SqlCandidateValidationResult.invalid("候选 SQL 为空");
        }
        String sql = stripTrailingSemicolon(candidate.sql());
        String structuralSql = maskStringLiterals(sql);
        if (StringUtils.contains(structuralSql, ";")) {
            return SqlCandidateValidationResult.invalid("SQL 只能包含单条查询语句");
        }
        String normalized = sql.toLowerCase(Locale.ROOT);
        if (!(normalized.startsWith("select") || normalized.startsWith("with"))) {
            return SqlCandidateValidationResult.invalid("只允许 SELECT/WITH 查询");
        }
        if (FORBIDDEN_SQL_PATTERN.matcher(structuralSql).find()) {
            return SqlCandidateValidationResult.invalid("SQL 包含禁止的写入或 DDL 关键字");
        }
        if (DERIVED_TABLE_PATTERN.matcher(structuralSql).find()) {
            return SqlCandidateValidationResult.invalid("不支持子查询或派生表");
        }
        if (LIMIT_BY_PATTERN.matcher(structuralSql).find()) {
            return SqlCandidateValidationResult.invalid("不支持 LIMIT BY");
        }
        String tableName = dynamicLogQueryService.getTableName(context.datasourceId());
        if (!referencesOnlyCurrentTable(structuralSql, tableName)) {
            return SqlCandidateValidationResult.invalid("SQL 查询表不属于当前数据源");
        }
        if (!usesKnownFields(context, sql, tableName)) {
            return SqlCandidateValidationResult.invalid("SQL 包含当前表不存在的字段");
        }
        return SqlCandidateValidationResult.valid(ensureLimit(sql, structuralSql));
    }

    /**
     * 去掉尾部分号，避免追加 LIMIT 时产生空语句。
     */
    private String stripTrailingSemicolon(String sql) {
        String trimmed = StringUtils.trim(sql);
        while (StringUtils.endsWith(trimmed, ";")) {
            trimmed = StringUtils.trim(StringUtils.removeEnd(trimmed, ";"));
        }
        return trimmed;
    }

    /**
     * 屏蔽字符串字面量，避免把日志内容误判为 SQL 关键字。
     */
    private String maskStringLiterals(String sql) {
        StringBuilder masked = new StringBuilder(sql.length());
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i < sql.length(); i++) {
            char current = sql.charAt(i);
            if (current == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                masked.append(' ');
            } else if (current == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                masked.append(' ');
            } else if (inSingleQuote || inDoubleQuote) {
                masked.append(' ');
            } else {
                masked.append(current);
            }
        }
        return masked.toString();
    }

    /**
     * SQL 中所有 FROM/JOIN 表都必须指向当前数据源表。
     */
    private boolean referencesOnlyCurrentTable(String sql, String tableName) {
        List<String> referencedTables = findReferencedTables(sql);
        if (CollectionUtils.isEmpty(referencedTables)) {
            return false;
        }
        for (String referencedTable : referencedTables) {
            String referenced = unquote(lastNamePart(referencedTable));
            if (!StringUtils.equalsIgnoreCase(referenced, tableName)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 提取 FROM/JOIN 后的表名，包含逗号多表写法。
     */
    private List<String> findReferencedTables(String sql) {
        List<String> tables = new ArrayList<>();
        Matcher matcher = TABLE_CLAUSE_PATTERN.matcher(sql);
        while (matcher.find()) {
            String clauseType = matcher.group(1);
            String tableClause = matcher.group(2);
            if (StringUtils.equalsIgnoreCase(clauseType, "join")) {
                addFirstTable(tables, tableClause);
            } else {
                for (String tablePart : StringUtils.split(tableClause, ",")) {
                    addFirstTable(tables, tablePart);
                }
            }
        }
        return tables;
    }

    private void addFirstTable(List<String> tables, String tablePart) {
        Matcher matcher = TABLE_NAME_PATTERN.matcher(StringUtils.trimToEmpty(tablePart));
        if (matcher.find()) {
            tables.add(matcher.group(1));
        }
    }

    /**
     * 轻量字段校验：只校验反引号字段，避免误伤函数别名和字符串字面量。
     */
    private boolean usesKnownFields(AgentExecutionContext context, String sql, String tableName) {
        List<FieldInfo> schema = dynamicLogQueryService.getTableSchema(context.datasourceId());
        if (CollectionUtils.isEmpty(schema)) {
            return true;
        }
        Set<String> fields = schema.stream()
                .map(FieldInfo::getName)
                .filter(StringUtils::isNotBlank)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        fields.add(tableName.toLowerCase(Locale.ROOT));

        Set<String> aliases = findBacktickedAliases(sql);
        Matcher matcher = BACKTICKED_TOKEN_PATTERN.matcher(sql);
        while (matcher.find()) {
            String token = lastNamePart(matcher.group(1)).toLowerCase(Locale.ROOT);
            if (!fields.contains(token) && !aliases.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private Set<String> findBacktickedAliases(String sql) {
        return BACKTICKED_ALIAS_PATTERN.matcher(sql)
                .results()
                .map(match -> lastNamePart(match.group(1)).toLowerCase(Locale.ROOT))
                .collect(Collectors.collectingAndThen(Collectors.toSet(), aliases -> {
                    BACKTICKED_IMPLICIT_ALIAS_PATTERN.matcher(sql)
                            .results()
                            .map(match -> lastNamePart(match.group(1)).toLowerCase(Locale.ROOT))
                            .forEach(aliases::add);
                    return aliases;
                }));
    }

    private long parseLimitRowCount(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return DEFAULT_LIMIT + 1L;
        }
    }

    /**
     * 缺少 LIMIT 时自动补默认限制，避免大结果拖慢助手。
     */
    private String ensureLimit(String sql, String structuralSql) {
        Matcher matcher = LIMIT_CLAUSE_PATTERN.matcher(structuralSql);
        if (!matcher.find()) {
            return sql + " LIMIT " + DEFAULT_LIMIT;
        }

        long rowCount = parseLimitRowCount(matcher.group(2));
        if (rowCount <= DEFAULT_LIMIT) {
            return sql;
        }

        return sql.substring(0, matcher.start()) + "LIMIT " + DEFAULT_LIMIT + sql.substring(matcher.end());
    }

    private String lastNamePart(String value) {
        String normalized = StringUtils.defaultString(value);
        int dot = normalized.lastIndexOf('.');
        return dot >= 0 ? normalized.substring(dot + 1) : normalized;
    }

    private String unquote(String value) {
        return StringUtils.removeEnd(StringUtils.removeStart(value, "`"), "`");
    }
}
