package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.dto.AgentResult;
import cn.mw.loganalysis.stats.dto.LogQueryRequest;
import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 处理“最近一小时总数/多少条”这类确定性日志计数问题。
 */
@Component
@RequiredArgsConstructor
public class LogCountMetricPreflightStrategy implements Text2SqlPreflightStrategy {

    private final DynamicLogQueryService dynamicLogQueryService;

    /**
     * 简单计数不需要 Text2SQL；分组、排行和趋势仍交给对应工具处理。
     */
    @Override
    public boolean supports(AgentExecutionContext context, String query) {
        if (context == null || !StringUtils.equalsIgnoreCase(context.datasourceType(), "clickhouse")) {
            return false;
        }
        String lower = StringUtils.lowerCase(AgentToolSupport.normalizeText(query), Locale.ROOT);
        boolean countRequest = AgentToolSupport.containsAny(lower, "总数", "多少条", "多少", "数量", "条数", "count");
        boolean complexAggregation = AgentToolSupport.containsAny(lower,
                "按", "分组", "排行", "top", "占比", "平均", "avg", "sum", "max", "min", "每小时", "每分钟", "趋势", "时序");
        return countRequest && !complexAggregation;
    }

    /**
     * 使用日志查询服务的 total 字段返回单值指标。
     */
    @Override
    public AgentToolPayload execute(AgentExecutionContext context, String query) {
        long startedAt = System.currentTimeMillis();
        AgentTimeWindow timeWindow = AgentToolSupport.resolveTimeWindow(query, false);

        LogQueryRequest request = new LogQueryRequest();
        request.setDatasourceId(context.datasourceId());
        request.setStartTime(timeWindow.start());
        request.setEndTime(timeWindow.end());
        request.setPageNum(1);
        request.setPageSize(1);

        String severity = AgentIntentTextSupport.extractSeverity(query);
        List<LogQueryRequest.FieldFilter> fieldFilters = buildSeverityFilters(severity);
        if (!fieldFilters.isEmpty()) {
            request.setFieldFilters(fieldFilters);
        }

        Map<String, Object> queryResult = dynamicLogQueryService.queryLogs(context.datasourceId(), request);
        long total = AgentToolSupport.toLong(queryResult.get("total"));
        double executionTime = (System.currentTimeMillis() - startedAt) / 1000.0;

        Map<String, Object> metric = new LinkedHashMap<>();
        metric.put("total", total);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("queryResultType", "metric");
        summary.put("rowCount", 1);
        summary.put("returnedRows", 1);
        summary.put("datasourceType", context.datasourceType());
        summary.put("tableName", dynamicLogQueryService.getTableName(context.datasourceId()));
        summary.put("timeRange", timeWindow.label());
        if (StringUtils.isNotBlank(severity)) {
            summary.put("severity", severity);
        }
        summary.put("sqlGenerationTime", 0D);
        summary.put("sqlExecutionTime", executionTime);
        summary.put("totalExecutionTime", executionTime);
        summary.put("deterministic", true);

        return AgentToolPayload.builder()
                .toolName("text2sql_query")
                .toolLabel("日志总数统计")
                .intent("text2sql")
                .summary(buildSummary(context.datasourceName(), timeWindow, total, severity))
                .durationMs(System.currentTimeMillis() - startedAt)
                .result(AgentResult.builder()
                        .type("text2sql")
                        .sql(buildPseudoSql(timeWindow, severity))
                        .queryResultType("metric")
                        .rawResult(metric)
                        .rows(List.of(metric))
                        .total(1L)
                        .summary(summary)
                        .sqlGenerationTime(0D)
                        .sqlExecutionTime(executionTime)
                        .totalExecutionTime(executionTime)
                        .build())
                .build();
    }

    /**
     * 复用日志查询过滤结构，保证和日志列表查询的 severity 语义一致。
     */
    private List<LogQueryRequest.FieldFilter> buildSeverityFilters(String severity) {
        if (StringUtils.isBlank(severity)) {
            return List.of();
        }
        LogQueryRequest.FieldFilter filter = new LogQueryRequest.FieldFilter();
        filter.setField("severity");
        filter.setType("include");
        filter.setValues(resolveSeverityValues(severity));
        return List.of(filter);
    }

    /**
     * 将自然语言级别映射为当前日志表常见枚举值。
     */
    private List<String> resolveSeverityValues(String severity) {
        String normalized = StringUtils.lowerCase(severity, Locale.ROOT);
        if (AgentToolSupport.containsAny(normalized, "warn", "告警", "警告")) {
            return List.of("warning", "warn", "WARN", "WARNING");
        }
        if (AgentToolSupport.containsAny(normalized, "error", "错误", "异常")) {
            return List.of("error", "ERROR");
        }
        if (AgentToolSupport.containsAny(normalized, "info", "信息")) {
            return List.of("info", "INFO");
        }
        if (AgentToolSupport.containsAny(normalized, "debug", "调试")) {
            return List.of("debug", "DEBUG");
        }
        return List.of(severity);
    }

    /**
     * 构造前端“查看 SQL”区域可读的说明，避免误导用户这是模型生成的 SQL。
     */
    private String buildPseudoSql(AgentTimeWindow timeWindow, String severity) {
        StringBuilder sql = new StringBuilder("SELECT count() AS total FROM <current_table> WHERE timestamp >= '")
                .append(timeWindow.start())
                .append("' AND timestamp <= '")
                .append(timeWindow.end())
                .append("'");
        if (StringUtils.isNotBlank(severity)) {
            sql.append(" AND severity IN (").append(String.join(", ", resolveSeverityValues(severity))).append(")");
        }
        return sql.toString();
    }

    /**
     * 生成助手回复摘要。
     */
    private String buildSummary(String datasourceName, AgentTimeWindow timeWindow, long total, String severity) {
        String severityText = StringUtils.isBlank(severity) ? "" : "，级别 " + severity;
        return String.format("已在 %s 查询 %s%s 的日志总数，共 %d 条。该查询由确定性统计策略执行，未调用 Text2SQL 模型。",
                datasourceName,
                timeWindow.label(),
                severityText,
                total);
    }
}
