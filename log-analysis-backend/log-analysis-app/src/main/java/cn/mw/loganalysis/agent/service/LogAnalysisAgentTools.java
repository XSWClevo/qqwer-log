package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.dto.AgentResult;
import cn.mw.loganalysis.stats.dto.AiQueryRequest;
import cn.mw.loganalysis.stats.dto.AiQueryResponse;
import cn.mw.loganalysis.stats.dto.LogQueryRequest;
import cn.mw.loganalysis.stats.dto.StatsQueryRequest;
import cn.mw.loganalysis.stats.service.AiQueryService;
import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import cn.mw.loganalysis.stats.service.query.FieldInfo;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 日志智能助手 Tools。
 *
 * 被 @Tool 标注的方法会暴露给 LangChain4j，当成模型可调用的工具。
 * 参数上的 @P 不是 Spring 注解，它的作用是给 LangChain4j 描述“这个参数代表什么”。
 * 模型看到的是这些描述生成的工具参数 schema，而不是 Java 代码本身。
 */
@Component
@RequiredArgsConstructor
public class LogAnalysisAgentTools {

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern RELATIVE_RANGE_PATTERN = Pattern.compile("最近\\s*([0-9一二两三四五六七八九十半]+)\\s*(分钟|小时|天|周)");
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_TEXT2SQL_ROWS = 200;

    private final DynamicLogQueryService dynamicLogQueryService;
    private final AiQueryService aiQueryService;

    /**
     * 最简单的只读工具：读取当前数据源 schema。
     * datasourceId 不作为显式参数传给模型，而是从请求上下文里读取，避免模型传错数据源。
     */
    @Tool(name = "get_schema", value = "读取当前数据源的字段结构、时间字段、统计维度和内容字段")
    public AgentToolPayload getSchema() {
        AgentExecutionContext context = AgentExecutionContextHolder.require();
        long startedAt = System.currentTimeMillis();

        List<FieldInfo> schema = dynamicLogQueryService.getTableSchema(context.datasourceId());
        List<String> timestampFields = schema.stream()
                .filter(field -> Boolean.TRUE.equals(field.getIsTimestamp()))
                .map(FieldInfo::getName)
                .toList();
        List<String> dimensions = schema.stream()
                .filter(field -> Boolean.TRUE.equals(field.getIsStatsDimension()))
                .map(FieldInfo::getName)
                .toList();
        List<String> contentFields = schema.stream()
                .filter(field -> Boolean.TRUE.equals(field.getIsContentField()))
                .map(FieldInfo::getName)
                .toList();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("fieldCount", schema.size());
        summary.put("timestampFields", timestampFields);
        summary.put("statsDimensions", dimensions);
        summary.put("contentFields", contentFields);

        return AgentToolPayload.builder()
                .toolName("get_schema")
                .toolLabel("读取字段结构")
                .intent("schema")
                .summary(String.format("已获取字段结构，共 %d 个字段；时间字段：%s",
                        schema.size(),
                        joinOrFallback(timestampFields, "未识别")))
                .durationMs(System.currentTimeMillis() - startedAt)
                .result(AgentResult.builder()
                        .type("schema")
                        .schema(schema)
                        .summary(summary)
                        .build())
                .build();
    }

    /**
     * 查询日志明细工具。
     *
     * @P 注解会被 LangChain4j 用来构建工具参数说明：
     * - value: 参数含义，提供给模型理解
     * - required: 是否必填；默认 true
     *
     * 如果没有 @P，模型通常只能看到参数名，工具调用质量会明显下降。
     */
    @Tool(name = "query_logs", value = "查询当前数据源的日志列表。timeRange 传自然语言时间范围，例如 最近1小时、最近24小时、今天、昨天；keyword 可为空；severity 可传 error/warn/info/debug；limit 为返回条数上限")
    public AgentToolPayload queryLogs(@P("自然语言时间范围，例如 最近1小时、最近24小时、今天、昨天。为空时默认最近1小时") String timeRange,
                                      @P("日志关键词，可为空") String keyword,
                                      @P("日志级别，可为空，可传 error、warn、info、debug") String severity,
                                      @P("返回日志条数上限，建议 10 到 50") Integer limit) {
        AgentExecutionContext context = AgentExecutionContextHolder.require();
        TimeWindow timeWindow = resolveTimeWindow(timeRange, false);
        long startedAt = System.currentTimeMillis();

        LogQueryRequest request = new LogQueryRequest();
        request.setDatasourceId(context.datasourceId());
        request.setStartTime(timeWindow.start());
        request.setEndTime(timeWindow.end());
        request.setPageNum(1);
        request.setPageSize(clampLimit(limit));

        List<LogQueryRequest.FieldFilter> fieldFilters = buildSeverityFilters(severity);
        if (!fieldFilters.isEmpty()) {
            request.setFieldFilters(fieldFilters);
        }

        List<LogQueryRequest.MessageCondition> messageConditions = buildMessageConditions(keyword);
        if (!messageConditions.isEmpty()) {
            request.setMessageConditions(messageConditions);
        }

        Map<String, Object> queryResult = dynamicLogQueryService.queryLogs(context.datasourceId(), request);
        List<Map<String, Object>> logs = castList(queryResult.get("data"));
        long total = toLong(queryResult.get("total"));
        Map<String, Long> severitySummary = logs.stream()
                .map(row -> stringify(row.get("severity")))
                .filter(StringUtils::hasText)
                .collect(Collectors.groupingBy(value -> value, LinkedHashMap::new, Collectors.counting()));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", total);
        summary.put("returned", logs.size());
        summary.put("severities", severitySummary);
        if (StringUtils.hasText(keyword)) {
            summary.put("keyword", keyword.trim());
        }

        String summaryText = buildLogsSummary(context.datasourceName(), timeWindow, total, logs, severitySummary, keyword);

        return AgentToolPayload.builder()
                .toolName("query_logs")
                .toolLabel("查询日志列表")
                .intent("logs")
                .summary(summaryText)
                .durationMs(System.currentTimeMillis() - startedAt)
                .result(AgentResult.builder()
                        .type("logs")
                        .timeRangeLabel(timeWindow.label())
                        .logs(logs)
                        .total(total)
                        .pageNum(1)
                        .pageSize(request.getPageSize())
                        .summary(summary)
                        .build())
                .build();
    }

    /**
     * 查询趋势工具。
     * 这里同样依赖 @P 给模型解释 timeRange / granularity 的预期格式。
     */
    @Tool(name = "query_timeseries", value = "查询当前数据源的日志趋势。timeRange 传自然语言时间范围，例如 最近24小时、最近7天；granularity 可传 1m、5m、1h、1d")
    public AgentToolPayload queryTimeseries(@P("自然语言时间范围，例如 最近24小时、最近7天、今天。为空时默认最近24小时") String timeRange,
                                            @P("趋势粒度，可传 1m、5m、1h、1d，或留空自动选择") String granularity) {
        AgentExecutionContext context = AgentExecutionContextHolder.require();
        TimeWindow timeWindow = resolveTimeWindow(timeRange, true);
        String resolvedGranularity = resolveGranularity(granularity, timeWindow);
        long startedAt = System.currentTimeMillis();

        StatsQueryRequest request = new StatsQueryRequest();
        request.setDatasourceId(context.datasourceId());
        request.setStartTime(timeWindow.start());
        request.setEndTime(timeWindow.end());
        request.setGranularity(resolvedGranularity);

        Map<String, Object> queryResult = dynamicLogQueryService.queryTimeSeries(context.datasourceId(), request);
        List<Map<String, Object>> series = castList(queryResult.get("series"));
        Map<String, Object> summary = buildTimeseriesSummary(series, resolvedGranularity);

        String summaryText = buildTimeseriesSummaryText(context.datasourceName(), timeWindow, resolvedGranularity, summary);

        return AgentToolPayload.builder()
                .toolName("query_timeseries")
                .toolLabel("查询日志趋势")
                .intent("timeseries")
                .summary(summaryText)
                .durationMs(System.currentTimeMillis() - startedAt)
                .result(AgentResult.builder()
                        .type("timeseries")
                        .timeRangeLabel(timeWindow.label())
                        .granularity(resolvedGranularity)
                        .series(series)
                        .summary(summary)
                .build())
                .build();
    }

    /**
     * ClickHouse 专用的自然语言统计查询工具。
     *
     * 这里不在 agent 里手写 SQL，而是直接复用现有 text2sql API：
     * 1. AiQueryService 会把自然语言、表名、表结构、数据源类型发给外部 text-to-sql 服务
     * 2. text-to-sql 服务返回 SQL
     * 3. Java 后端继续通过 DynamicLogQueryService.executeRawSQL 执行
     *
     * 对 ClickHouse 而言，只要项目里启用了 MCP，这一步执行仍然会自动走官方 MCP。
     * 也就是说这条链是：自然语言 -> text2sql -> Java 执行层 -> ClickHouse MCP/JDBC。
     */
    @Tool(name = "text2sql_query", value = "当当前数据源是 ClickHouse，且用户要做开放式统计、聚合、排行、按字段分组、多少条、临时做图时，使用自然语言生成 SQL 并执行。query 直接传用户原始问题")
    public AgentToolPayload text2SqlQuery(@P("面向当前 ClickHouse 数据源的自然语言查询原文，例如 最近1天的数据有多少条、按 severity 统计最近24小时数量") String query) {
        AgentExecutionContext context = AgentExecutionContextHolder.require();
        long startedAt = System.currentTimeMillis();

        if (!StringUtils.hasText(context.datasourceType()) || !"clickhouse".equalsIgnoreCase(context.datasourceType())) {
            throw new IllegalStateException("text2sql_query 仅支持 ClickHouse 数据源，当前数据源类型为 " + context.datasourceType());
        }

        AiQueryRequest request = new AiQueryRequest();
        request.setQuery(StringUtils.hasText(query) ? query.trim() : "");
        request.setDatasourceId(context.datasourceId());

        AiQueryResponse response = aiQueryService.query(request);
        if (!Boolean.TRUE.equals(response.getSuccess())) {
            throw new IllegalStateException(StringUtils.hasText(response.getError()) ? response.getError() : "text2sql 查询失败");
        }

        Text2SqlQueryShape shape = normalizeText2SqlResult(response.getResult());
        List<Map<String, Object>> displayRows = limitRows(shape.rows(), MAX_TEXT2SQL_ROWS);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("queryResultType", shape.queryResultType());
        summary.put("rowCount", shape.rows().size());
        summary.put("returnedRows", displayRows.size());
        summary.put("datasourceType", context.datasourceType());
        summary.put("tableName", dynamicLogQueryService.getTableName(context.datasourceId()));
        summary.put("sqlGenerationTime", response.getSqlGenerationTime());
        summary.put("sqlExecutionTime", response.getSqlExecutionTime());
        summary.put("totalExecutionTime", response.getTotalExecutionTime());

        return AgentToolPayload.builder()
                .toolName("text2sql_query")
                .toolLabel("自然语言统计查询")
                .intent("text2sql")
                .summary(buildText2SqlSummary(context.datasourceName(), shape, response, displayRows.size()))
                .durationMs(System.currentTimeMillis() - startedAt)
                .result(AgentResult.builder()
                        .type("text2sql")
                        .sql(response.getSql())
                        .queryResultType(shape.queryResultType())
                        .rawResult(shape.rawResult())
                        .rows(displayRows)
                        .total((long) shape.rows().size())
                        .summary(summary)
                        .sqlGenerationTime(response.getSqlGenerationTime())
                        .sqlExecutionTime(response.getSqlExecutionTime())
                        .totalExecutionTime(response.getTotalExecutionTime())
                        .build())
                .build();
    }

    @SuppressWarnings("unchecked")
    private Text2SqlQueryShape normalizeText2SqlResult(Object rawResult) {
        if (rawResult instanceof List<?> list) {
            List<Map<String, Object>> rows = list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> new LinkedHashMap<>((Map<String, Object>) item))
                    .collect(Collectors.toCollection(ArrayList::new));

            if (rows.isEmpty()) {
                return new Text2SqlQueryShape("list", rawResult, List.of());
            }

            Map<String, Object> firstRow = rows.get(0);
            if (isTimeSeriesResult(firstRow)) {
                return new Text2SqlQueryShape("timeseries", rows, rows);
            }
            if (rows.size() == 1 && isAggregateResult(firstRow)) {
                return new Text2SqlQueryShape("metric", firstRow, rows);
            }
            if (isAggregateResult(firstRow)) {
                return new Text2SqlQueryShape("category", rows, rows);
            }
            return new Text2SqlQueryShape("list", rows, rows);
        }

        if (rawResult instanceof Map<?, ?> map) {
            Map<String, Object> row = new LinkedHashMap<>();
            map.forEach((key, value) -> row.put(String.valueOf(key), value));
            String queryResultType = isAggregateResult(row) ? "metric" : "list";
            return new Text2SqlQueryShape(queryResultType, row, List.of(row));
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("value", rawResult);
        return new Text2SqlQueryShape("metric", rawResult, List.of(row));
    }

    private boolean isTimeSeriesResult(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return false;
        }
        List<String> keys = row.keySet().stream().map(key -> key.toLowerCase(Locale.ROOT)).toList();
        boolean hasTimeField = keys.stream().anyMatch(key -> key.matches(".*(time|date|timestamp|hour|day|month).*"));
        boolean hasAggregateField = keys.stream().anyMatch(key -> key.matches(".*(count|sum|avg|max|min|total|value).*"));
        return hasTimeField && hasAggregateField;
    }

    private boolean isAggregateResult(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return false;
        }
        return row.keySet().stream()
                .map(key -> key.toLowerCase(Locale.ROOT))
                .anyMatch(key -> key.startsWith("count")
                        || key.startsWith("sum")
                        || key.startsWith("avg")
                        || key.startsWith("max")
                        || key.startsWith("min")
                        || key.startsWith("total")
                        || key.startsWith("average")
                        || key.endsWith("_count")
                        || key.endsWith("_sum")
                        || key.endsWith("_avg"));
    }

    private List<Map<String, Object>> limitRows(List<Map<String, Object>> rows, int maxRows) {
        if (rows == null || rows.isEmpty() || rows.size() <= maxRows) {
            return rows == null ? List.of() : rows;
        }
        return new ArrayList<>(rows.subList(0, maxRows));
    }

    private String buildText2SqlSummary(String datasourceName,
                                        Text2SqlQueryShape shape,
                                        AiQueryResponse response,
                                        int displayedRows) {
        int totalRows = shape.rows().size();
        String queryResultType = shape.queryResultType();

        if ("metric".equals(queryResultType) && shape.rawResult() instanceof Map<?, ?> metricRow && !metricRow.isEmpty()) {
            String metricText = metricRow.entrySet().stream()
                    .limit(3)
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("，"));
            return String.format("已在 %s 上完成自然语言统计查询，结果：%s。SQL 生成 %.2f 秒，执行 %.2f 秒。",
                    datasourceName,
                    metricText,
                    safeDouble(response.getSqlGenerationTime()),
                    safeDouble(response.getSqlExecutionTime()));
        }

        if ("timeseries".equals(queryResultType)) {
            return String.format("已在 %s 上完成自然语言时序查询，共返回 %d 个时间点，当前展示 %d 行。SQL 生成 %.2f 秒，执行 %.2f 秒。",
                    datasourceName,
                    totalRows,
                    displayedRows,
                    safeDouble(response.getSqlGenerationTime()),
                    safeDouble(response.getSqlExecutionTime()));
        }

        if ("category".equals(queryResultType)) {
            return String.format("已在 %s 上完成自然语言聚合查询，共返回 %d 行分组结果，当前展示 %d 行。SQL 生成 %.2f 秒，执行 %.2f 秒。",
                    datasourceName,
                    totalRows,
                    displayedRows,
                    safeDouble(response.getSqlGenerationTime()),
                    safeDouble(response.getSqlExecutionTime()));
        }

        return String.format("已在 %s 上完成自然语言 SQL 查询，共返回 %d 行，当前展示 %d 行。SQL 生成 %.2f 秒，执行 %.2f 秒。",
                datasourceName,
                totalRows,
                displayedRows,
                safeDouble(response.getSqlGenerationTime()),
                safeDouble(response.getSqlExecutionTime()));
    }

    private double safeDouble(Double value) {
        return value == null ? 0D : value;
    }

    private TimeWindow resolveTimeWindow(String text, boolean defaultTimeseriesRange) {
        String normalized = normalizeText(text);
        LocalDateTime now = LocalDateTime.now();
        Matcher matcher = RELATIVE_RANGE_PATTERN.matcher(normalized);
        if (matcher.find()) {
            if ("半".equals(matcher.group(1)) && "小时".equals(matcher.group(2))) {
                return new TimeWindow(now.minusMinutes(30), now, "最近30分钟");
            }
            Integer amount = parseChineseNumber(matcher.group(1));
            String unit = matcher.group(2);
            if (amount != null && amount > 0) {
                return switch (unit) {
                    case "分钟" -> new TimeWindow(now.minusMinutes(amount), now, "最近" + amount + "分钟");
                    case "小时" -> new TimeWindow(now.minusHours(amount), now, "最近" + amount + "小时");
                    case "天" -> new TimeWindow(now.minusDays(amount), now, "最近" + amount + "天");
                    case "周" -> new TimeWindow(now.minusWeeks(amount), now, "最近" + amount + "周");
                    default -> defaultWindow(now, defaultTimeseriesRange);
                };
            }
        }

        if (normalized.contains("今天")) {
            return new TimeWindow(LocalDateTime.of(LocalDate.now(), LocalTime.MIN), now, "今天");
        }
        if (normalized.contains("昨天")) {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            return new TimeWindow(LocalDateTime.of(yesterday, LocalTime.MIN), LocalDateTime.of(yesterday, LocalTime.MAX), "昨天");
        }
        if (normalized.contains("最近一周") || normalized.contains("近7天")) {
            return new TimeWindow(now.minusDays(7), now, "最近7天");
        }
        if (normalized.contains("最近24小时")) {
            return new TimeWindow(now.minusHours(24), now, "最近24小时");
        }
        if (normalized.contains("最近1小时") || normalized.contains("近1小时")) {
            return new TimeWindow(now.minusHours(1), now, "最近1小时");
        }
        if (normalized.contains("最近15分钟") || normalized.contains("近15分钟")) {
            return new TimeWindow(now.minusMinutes(15), now, "最近15分钟");
        }

        return defaultWindow(now, defaultTimeseriesRange);
    }

    private TimeWindow defaultWindow(LocalDateTime now, boolean defaultTimeseriesRange) {
        if (defaultTimeseriesRange) {
            return new TimeWindow(now.minusHours(24), now, "最近24小时");
        }
        return new TimeWindow(now.minusHours(1), now, "最近1小时");
    }

    private String resolveGranularity(String granularity, TimeWindow timeWindow) {
        String normalized = normalizeText(granularity).toLowerCase(Locale.ROOT);
        if (Arrays.asList("1m", "5m", "1h", "1d").contains(normalized)) {
            return normalized;
        }
        if (normalized.contains("分钟")) {
            return normalized.contains("5") ? "5m" : "1m";
        }
        if (normalized.contains("小时")) {
            return "1h";
        }
        if (normalized.contains("天")) {
            return "1d";
        }

        long hours = Duration.between(timeWindow.start(), timeWindow.end()).toHours();
        if (hours <= 2) {
            return "1m";
        }
        if (hours <= 24) {
            return "5m";
        }
        if (hours <= 24 * 7L) {
            return "1h";
        }
        return "1d";
    }

    private List<LogQueryRequest.FieldFilter> buildSeverityFilters(String severity) {
        String normalized = normalizeText(severity).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(normalized)) {
            return Collections.emptyList();
        }

        List<String> values = new ArrayList<>();
        if (containsAny(normalized, "error", "错误", "异常")) {
            values.add("error");
        }
        if (containsAny(normalized, "warn", "告警", "警告")) {
            values.add("warn");
        }
        if (containsAny(normalized, "info", "信息")) {
            values.add("info");
        }
        if (containsAny(normalized, "debug", "调试")) {
            values.add("debug");
        }

        if (values.isEmpty()) {
            return Collections.emptyList();
        }

        LogQueryRequest.FieldFilter filter = new LogQueryRequest.FieldFilter();
        filter.setField("levels");
        filter.setType("include");
        filter.setValues(values.stream().distinct().toList());
        return List.of(filter);
    }

    private List<LogQueryRequest.MessageCondition> buildMessageConditions(String keyword) {
        if (!StringUtils.hasText(normalizeText(keyword))) {
            return Collections.emptyList();
        }

        LogQueryRequest.MessageCondition condition = new LogQueryRequest.MessageCondition();
        condition.setOperator("contains");
        condition.setValue(keyword.trim());
        return List.of(condition);
    }

    private String buildLogsSummary(String datasourceName,
                                    TimeWindow timeWindow,
                                    long total,
                                    List<Map<String, Object>> logs,
                                    Map<String, Long> severitySummary,
                                    String keyword) {
        if (total == 0) {
            return String.format("在 %s 中未查到 %s 内匹配的日志。", datasourceName, timeWindow.label());
        }

        StringBuilder summary = new StringBuilder();
        summary.append(String.format("已在 %s 查询 %s 的日志，共 %d 条，当前返回 %d 条。",
                datasourceName, timeWindow.label(), total, logs.size()));

        if (StringUtils.hasText(normalizeText(keyword))) {
            summary.append(" 关键词：").append(keyword.trim()).append("。");
        }
        if (!severitySummary.isEmpty()) {
            String severityText = severitySummary.entrySet().stream()
                    .map(entry -> entry.getKey() + " " + entry.getValue() + "条")
                    .collect(Collectors.joining("，"));
            summary.append(" 当前页级别分布：").append(severityText).append("。");
        }
        if (!logs.isEmpty()) {
            Map<String, Object> firstLog = logs.get(0);
            summary.append(" 最新一条时间：")
                    .append(stringify(firstLog.get("timestamp")))
                    .append("，摘要：")
                    .append(truncate(stringify(firstLog.get("message")), 80))
                    .append("。");
        }
        return summary.toString();
    }

    private Map<String, Object> buildTimeseriesSummary(List<Map<String, Object>> series, String granularity) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("pointCount", series.size());
        summary.put("granularity", granularity);

        long totalCount = 0L;
        long peakCount = 0L;
        String peakTimestamp = null;

        for (Map<String, Object> point : series) {
            long count = toLong(point.get("count"));
            totalCount += count;
            if (count >= peakCount) {
                peakCount = count;
                peakTimestamp = stringify(point.get("timestamp"));
            }
        }

        summary.put("totalCount", totalCount);
        summary.put("peakCount", peakCount);
        summary.put("peakTimestamp", peakTimestamp);
        return summary;
    }

    private String buildTimeseriesSummaryText(String datasourceName,
                                              TimeWindow timeWindow,
                                              String granularity,
                                              Map<String, Object> summary) {
        long pointCount = toLong(summary.get("pointCount"));
        long totalCount = toLong(summary.get("totalCount"));
        long peakCount = toLong(summary.get("peakCount"));
        String peakTimestamp = stringify(summary.get("peakTimestamp"));

        if (pointCount == 0) {
            return String.format("在 %s 中没有查到 %s 的趋势数据。", datasourceName, timeWindow.label());
        }

        return String.format("已生成 %s 在 %s 的日志趋势，粒度 %s，共 %d 个时间点，总日志量 %d，峰值 %d 出现在 %s。",
                datasourceName,
                timeWindow.label(),
                granularity,
                pointCount,
                totalCount,
                peakCount,
                StringUtils.hasText(peakTimestamp) ? peakTimestamp : "未知时间点");
    }

    private int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(limit, MAX_PAGE_SIZE);
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private boolean containsAny(String text, String... keywords) {
        return Arrays.stream(keywords).anyMatch(text::contains);
    }

    private Integer parseChineseNumber(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        return switch (token) {
            case "半" -> 30;
            case "一" -> 1;
            case "二", "两" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> {
                try {
                    yield Integer.parseInt(token);
                } catch (NumberFormatException ex) {
                    yield null;
                }
            }
        };
    }

    private String joinOrFallback(List<String> values, String fallback) {
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        return String.join("、", values);
    }

    private String stringify(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        if (value instanceof List<?>) {
            return ((List<?>) value).stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        return new ArrayList<>();
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private record TimeWindow(LocalDateTime start, LocalDateTime end, String label) {
    }

    private record Text2SqlQueryShape(String queryResultType, Object rawResult, List<Map<String, Object>> rows) {
    }
}
