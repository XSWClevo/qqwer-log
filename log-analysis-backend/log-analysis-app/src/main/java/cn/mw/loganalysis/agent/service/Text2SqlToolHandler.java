package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.dto.AgentResult;
import cn.mw.loganalysis.stats.dto.AiQueryResponse;
import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class Text2SqlToolHandler {

    private static final int MAX_TEXT2SQL_ROWS = 200;

    private final SqlCandidateRaceService raceService;
    private final DynamicLogQueryService dynamicLogQueryService;

    public Text2SqlToolHandler(SqlCandidateRaceService raceService,
                               DynamicLogQueryService dynamicLogQueryService) {
        this.raceService = raceService;
        this.dynamicLogQueryService = dynamicLogQueryService;
    }

    public AgentToolPayload handle(AgentExecutionContext context, String query) {
        long startedAt = System.currentTimeMillis();

        if (StringUtils.isBlank(context.datasourceType()) || !"clickhouse".equalsIgnoreCase(context.datasourceType())) {
            throw new IllegalStateException("text2sql_query 仅支持 ClickHouse 数据源，当前数据源类型为 " + context.datasourceType());
        }

        String normalizedQuery = StringUtils.defaultString(StringUtils.trimToNull(query));
        SqlCandidateResult candidateResult = raceService.race(context, normalizedQuery);
        AiQueryResponse response = candidateResult.response();
        if (!Boolean.TRUE.equals(response.getSuccess())) {
            throw new IllegalStateException(StringUtils.isNotBlank(response.getError()) ? response.getError() : "text2sql 查询失败");
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
        summary.put("candidateSource", candidateResult.candidateSource());
        summary.put("candidateRaceMs", candidateResult.raceMs());
        summary.put("validatedCandidates", candidateResult.validatedCandidates());
        summary.put("rejectedCandidates", candidateResult.rejectedCandidates());

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
                    AgentToolSupport.safeDouble(response.getSqlGenerationTime()),
                    AgentToolSupport.safeDouble(response.getSqlExecutionTime()));
        }

        if ("timeseries".equals(queryResultType)) {
            return String.format("已在 %s 上完成自然语言时序查询，共返回 %d 个时间点，当前展示 %d 行。SQL 生成 %.2f 秒，执行 %.2f 秒。",
                    datasourceName,
                    totalRows,
                    displayedRows,
                    AgentToolSupport.safeDouble(response.getSqlGenerationTime()),
                    AgentToolSupport.safeDouble(response.getSqlExecutionTime()));
        }

        if ("category".equals(queryResultType)) {
            return String.format("已在 %s 上完成自然语言聚合查询，共返回 %d 行分组结果，当前展示 %d 行。SQL 生成 %.2f 秒，执行 %.2f 秒。",
                    datasourceName,
                    totalRows,
                    displayedRows,
                    AgentToolSupport.safeDouble(response.getSqlGenerationTime()),
                    AgentToolSupport.safeDouble(response.getSqlExecutionTime()));
        }

        return String.format("已在 %s 上完成自然语言 SQL 查询，共返回 %d 行，当前展示 %d 行。SQL 生成 %.2f 秒，执行 %.2f 秒。",
                datasourceName,
                totalRows,
                displayedRows,
                AgentToolSupport.safeDouble(response.getSqlGenerationTime()),
                AgentToolSupport.safeDouble(response.getSqlExecutionTime()));
    }
}
