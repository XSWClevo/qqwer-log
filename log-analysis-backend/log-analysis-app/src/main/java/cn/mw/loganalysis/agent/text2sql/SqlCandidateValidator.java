package cn.mw.loganalysis.agent.text2sql;

import cn.mw.loganalysis.agent.execution.AgentExecutionContext;
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
    private static final Pattern SELECT_LIST_PATTERN = Pattern.compile(
            "\\bselect\\b\\s+(.+?)\\s+\\bfrom\\b",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern PLAIN_SELECT_IDENTIFIER_PATTERN = Pattern.compile(
            "^(?:[A-Za-z_][A-Za-z0-9_]*\\.)?([A-Za-z_][A-Za-z0-9_]*)$"
    );
    private static final Pattern PLAIN_IDENTIFIER_PATTERN = Pattern.compile(
            "\\b[A-Za-z_][A-Za-z0-9_]*\\b"
    );
    private static final Set<String> SQL_KEYWORDS = Set.of(
            "select", "from", "where", "prewhere", "and", "or", "not", "in", "is", "null", "like",
            "between", "group", "by", "having", "order", "asc", "desc", "limit", "offset", "as",
            "join", "inner", "left", "right", "full", "outer", "on", "case", "when", "then", "else",
            "end", "distinct", "settings", "true", "false", "interval", "minute", "hour", "day", "week",
            "month", "year", "now"
    );
    private static final Set<String> SQL_FUNCTIONS = Set.of(
            "count", "sum", "avg", "min", "max", "toStartOfMinute", "toStartOfHour", "toStartOfDay",
            "toDate", "toDateTime", "toUnixTimestamp", "date_trunc", "lower", "upper", "substring",
            "position", "multiSearchAny", "match", "if", "coalesce", "assumeNotNull", "toString",
            "toInt32", "toInt64", "toFloat64"
    ).stream().map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
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
        if (hasSqlComment(structuralSql)) {
            return SqlCandidateValidationResult.invalid("SQL 不支持注释");
        }
        if (StringUtils.contains(structuralSql, ";")) {
            return SqlCandidateValidationResult.invalid("SQL 只能包含单条查询语句");
        }
        String normalized = sql.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("with")) {
            return SqlCandidateValidationResult.invalid("v1 暂不支持 WITH/CTE 查询");
        }
        if (!normalized.startsWith("select")) {
            return SqlCandidateValidationResult.invalid("只允许 SELECT 查询");
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
        if (!usesKnownFields(context, sql, structuralSql, tableName)) {
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
            if (inSingleQuote && current == '\\') {
                masked.append(' ');
                if (i + 1 < sql.length()) {
                    masked.append(' ');
                    i++;
                }
            } else if (current == '\'' && !inDoubleQuote) {
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

    private boolean hasSqlComment(String structuralSql) {
        return StringUtils.contains(structuralSql, "--") || StringUtils.contains(structuralSql, "/*");
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
            String referenced = unquote(referencedTable);
            if (StringUtils.contains(referenced, ".") || !StringUtils.equalsIgnoreCase(referenced, tableName)) {
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
    private boolean usesKnownFields(AgentExecutionContext context, String sql, String structuralSql, String tableName) {
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
        for (String field : findPlainSelectFields(structuralSql)) {
            if (!fields.contains(field.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        if (!usesOnlyKnownPlainIdentifiers(structuralSql, fields, aliases)) {
            return false;
        }
        return true;
    }

    /**
     * 提取 SELECT 列表中的裸字段名，函数表达式先交给 ClickHouse 执行时报错。
     */
    private List<String> findPlainSelectFields(String sql) {
        Matcher matcher = SELECT_LIST_PATTERN.matcher(sql);
        if (!matcher.find()) {
            return List.of();
        }
        List<String> fields = new ArrayList<>();
        for (String expression : splitTopLevelComma(matcher.group(1))) {
            String candidate = removePlainAlias(StringUtils.trimToEmpty(expression));
            Matcher identifierMatcher = PLAIN_SELECT_IDENTIFIER_PATTERN.matcher(candidate);
            if (identifierMatcher.find()) {
                fields.add(identifierMatcher.group(1));
            }
        }
        return fields;
    }

    private List<String> splitTopLevelComma(String value) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '(') {
                depth++;
            } else if (current == ')' && depth > 0) {
                depth--;
            } else if (current == ',' && depth == 0) {
                parts.add(value.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(value.substring(start));
        return parts;
    }

    private String removePlainAlias(String expression) {
        String withoutAsAlias = expression.replaceFirst("(?i)\\s+as\\s+[A-Za-z_][A-Za-z0-9_]*\\s*$", "");
        String[] tokens = StringUtils.split(withoutAsAlias);
        if (tokens != null && tokens.length == 2 && PLAIN_SELECT_IDENTIFIER_PATTERN.matcher(tokens[0]).matches()) {
            return tokens[0];
        }
        return withoutAsAlias;
    }

    /**
     * 校验 WHERE/GROUP/ORDER 等位置的明显裸字段，避免未知列进入执行阶段。
     */
    private boolean usesOnlyKnownPlainIdentifiers(String sql, Set<String> fields, Set<String> aliases) {
        Set<String> tableAliases = findTableAliases(sql);
        Set<String> selectAliases = findPlainSelectAliases(sql);
        Matcher matcher = PLAIN_IDENTIFIER_PATTERN.matcher(stripBacktickedTokens(sql));
        while (matcher.find()) {
            String token = matcher.group().toLowerCase(Locale.ROOT);
            if (isAllowedPlainIdentifier(token, fields, aliases, tableAliases, selectAliases)) {
                continue;
            }
            return false;
        }
        return true;
    }

    private boolean isAllowedPlainIdentifier(String token,
                                             Set<String> fields,
                                             Set<String> aliases,
                                             Set<String> tableAliases,
                                             Set<String> selectAliases) {
        return fields.contains(token)
                || aliases.contains(token)
                || tableAliases.contains(token)
                || selectAliases.contains(token)
                || SQL_KEYWORDS.contains(token)
                || SQL_FUNCTIONS.contains(token);
    }

    private Set<String> findTableAliases(String sql) {
        List<String> aliases = new ArrayList<>();
        Matcher matcher = TABLE_CLAUSE_PATTERN.matcher(sql);
        while (matcher.find()) {
            collectTableAliases(aliases, matcher.group(2));
        }
        return aliases.stream()
                .map(alias -> alias.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private void collectTableAliases(List<String> aliases, String clause) {
        for (String tablePart : StringUtils.split(clause, ",")) {
            String[] tokens = StringUtils.split(StringUtils.trimToEmpty(tablePart));
            if (tokens != null && tokens.length >= 2) {
                aliases.add(StringUtils.equalsIgnoreCase(tokens[1], "as") && tokens.length >= 3 ? tokens[2] : tokens[1]);
            }
        }
    }

    private Set<String> findPlainSelectAliases(String sql) {
        Set<String> aliases = new java.util.LinkedHashSet<>();
        Matcher matcher = SELECT_LIST_PATTERN.matcher(sql);
        if (!matcher.find()) {
            return aliases;
        }
        Pattern asAliasPattern = Pattern.compile("(?i)\\s+as\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*$");
        for (String expression : splitTopLevelComma(matcher.group(1))) {
            String trimmed = StringUtils.trimToEmpty(expression);
            Matcher asAliasMatcher = asAliasPattern.matcher(trimmed);
            if (asAliasMatcher.find()) {
                aliases.add(asAliasMatcher.group(1).toLowerCase(Locale.ROOT));
                continue;
            }
            String[] tokens = StringUtils.split(trimmed);
            if (tokens != null && tokens.length == 2 && !StringUtils.containsAny(tokens[1], "(", ")")) {
                aliases.add(tokens[1].toLowerCase(Locale.ROOT));
            }
        }
        return aliases;
    }

    private String stripBacktickedTokens(String sql) {
        return BACKTICKED_TOKEN_PATTERN.matcher(sql).replaceAll(" ");
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
