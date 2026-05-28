package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.dto.AgentResult;
import cn.mw.loganalysis.stats.dto.StatsQueryRequest;
import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 处理“按 severity/host/app 统计数量”这类确定性分组计数问题。
 */
@Component
@RequiredArgsConstructor
public class DimensionCountPreflightStrategy implements Text2SqlPreflightStrategy {

    private final DynamicLogQueryService dynamicLogQueryService;

    /**
     * 分组计数可以直接走统计服务，不需要大模型生成 SQL。
     */
    @Override
    public boolean supports(AgentExecutionContext context, String query) {
        if (context == null || !StringUtils.equalsIgnoreCase(context.datasourceType(), "clickhouse")) {
            return false;
        }
        String lower = StringUtils.lowerCase(AgentToolSupport.normalizeText(query), Locale.ROOT);
        return AgentToolSupport.containsAny(lower, "按", "分组")
                && AgentToolSupport.containsAny(lower, "统计", "数量", "总数", "条数", "count")
                && StringUtils.isNotBlank(resolveDimension(query));
    }

    /**
     * 使用 queryStats 返回前端分类统计卡可直接渲染的数据。
     */
    @Override
    public AgentToolPayload execute(AgentExecutionContext context, String query) {
        long startedAt = System.currentTimeMillis();
        AgentTimeWindow timeWindow = AgentToolSupport.resolveTimeWindow(query, false);
        String dimension = resolveDimension(query);

        StatsQueryRequest request = new StatsQueryRequest();
        request.setDatasourceId(context.datasourceId());
        request.setStartTime(timeWindow.start());
        request.setEndTime(timeWindow.end());
        request.setDimensions(List.of(dimension));
        request.setMetrics(List.of("count"));

        Map<String, Object> queryResult = dynamicLogQueryService.queryStats(context.datasourceId(), request);
        List<Map<String, Object>> rows = normalizeRows(queryResult, dimension);
        double executionTime = (System.currentTimeMillis() - startedAt) / 1000.0;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("queryResultType", "category");
        summary.put("rowCount", rows.size());
        summary.put("returnedRows", rows.size());
        summary.put("datasourceType", context.datasourceType());
        summary.put("tableName", dynamicLogQueryService.getTableName(context.datasourceId()));
        summary.put("timeRange", timeWindow.label());
        summary.put("dimension", dimension);
        summary.put("sqlGenerationTime", 0D);
        summary.put("sqlExecutionTime", executionTime);
        summary.put("totalExecutionTime", executionTime);
        summary.put("deterministic", true);

        return AgentToolPayload.builder()
                .toolName("text2sql_query")
                .toolLabel("日志分组统计")
                .intent("text2sql")
                .summary(buildSummary(context.datasourceName(), timeWindow, dimension, rows))
                .durationMs(System.currentTimeMillis() - startedAt)
                .result(AgentResult.builder()
                        .type("text2sql")
                        .sql(buildPseudoSql(timeWindow, dimension))
                        .queryResultType("category")
                        .rawResult(rows)
                        .rows(rows)
                        .total((long) rows.size())
                        .summary(summary)
                        .sqlGenerationTime(0D)
                        .sqlExecutionTime(executionTime)
                        .totalExecutionTime(executionTime)
                        .build())
                .build();
    }

    /**
     * 从自然语言中解析当前支持的常用统计维度。
     */
    private String resolveDimension(String query) {
        String lower = StringUtils.lowerCase(AgentToolSupport.normalizeText(query), Locale.ROOT);
        if (AgentToolSupport.containsAny(lower, "severity", "level", "级别", "等级")) {
            return "severity";
        }
        if (AgentToolSupport.containsAny(lower, "hostname", "host", "主机")) {
            return "hostname";
        }
        if (AgentToolSupport.containsAny(lower, "appname", "service", "服务", "应用")) {
            return "appname";
        }
        if (AgentToolSupport.containsAny(lower, "facility")) {
            return "facility";
        }
        if (AgentToolSupport.containsAny(lower, "source_type", "来源类型")) {
            return "source_type";
        }
        return null;
    }

    /**
     * 将 queryStats 的 data[dimension] 归一化为 category 图表需要的行数组。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeRows(Map<String, Object> queryResult, String dimension) {
        Object data = queryResult != null ? queryResult.get("data") : null;
        Object rawRows = data instanceof Map<?, ?> map ? map.get(dimension) : null;
        if (!(rawRows instanceof List<?> list)) {
            return List.of();
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put(dimension, row.get("value"));
            normalized.put("count", row.get("count"));
            rows.add(normalized);
        }
        return rows;
    }

    /**
     * 构造前端“查看 SQL”区域可读的说明，避免误导用户这是模型生成的 SQL。
     */
    private String buildPseudoSql(AgentTimeWindow timeWindow, String dimension) {
        return "SELECT " + dimension + ", count() AS count FROM <current_table> WHERE timestamp >= '"
                + timeWindow.start()
                + "' AND timestamp <= '"
                + timeWindow.end()
                + "' GROUP BY "
                + dimension
                + " ORDER BY count DESC LIMIT 10";
    }

    /**
     * 生成助手回复摘要。
     */
    private String buildSummary(String datasourceName, AgentTimeWindow timeWindow, String dimension, List<Map<String, Object>> rows) {
        return String.format("已在 %s 按 %s 统计 %s 的日志数量，共返回 %d 个分组。该查询由确定性统计策略执行，未调用 Text2SQL 模型。",
                datasourceName,
                dimension,
                timeWindow.label(),
                rows.size());
    }
}
