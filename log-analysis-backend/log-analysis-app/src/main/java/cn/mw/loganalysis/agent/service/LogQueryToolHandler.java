package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.dto.AgentResult;
import cn.mw.loganalysis.stats.dto.LogQueryRequest;
import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class LogQueryToolHandler {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final DynamicLogQueryService dynamicLogQueryService;

    public AgentToolPayload handle(AgentExecutionContext context,
                                   String timeRange,
                                   String keyword,
                                   String severity,
                                   Integer limit) {
        AgentTimeWindow timeWindow = AgentToolSupport.resolveTimeWindow(timeRange, false);
        long startedAt = System.currentTimeMillis();

        LogQueryRequest request = new LogQueryRequest();
        request.setDatasourceId(context.datasourceId());
        request.setStartTime(timeWindow.start());
        request.setEndTime(timeWindow.end());
        request.setPageNum(1);
        request.setPageSize(AgentToolSupport.clampLimit(limit, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE));

        List<LogQueryRequest.FieldFilter> fieldFilters = buildSeverityFilters(severity);
        if (!fieldFilters.isEmpty()) {
            request.setFieldFilters(fieldFilters);
        }

        List<LogQueryRequest.MessageCondition> messageConditions = buildMessageConditions(keyword);
        if (!messageConditions.isEmpty()) {
            request.setMessageConditions(messageConditions);
        }

        Map<String, Object> queryResult = dynamicLogQueryService.queryLogs(context.datasourceId(), request);
        List<Map<String, Object>> logs = AgentToolSupport.castList(queryResult.get("data"));
        long total = AgentToolSupport.toLong(queryResult.get("total"));
        Map<String, Long> severitySummary = logs.stream()
                .map(row -> AgentToolSupport.stringify(row.get("severity")))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.groupingBy(value -> value, LinkedHashMap::new, Collectors.counting()));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", total);
        summary.put("returned", logs.size());
        summary.put("severities", severitySummary);
        if (StringUtils.isNotBlank(keyword)) {
            summary.put("keyword", keyword.trim());
        }

        return AgentToolPayload.builder()
                .toolName("query_logs")
                .toolLabel("查询日志列表")
                .intent("logs")
                .summary(buildLogsSummary(context.datasourceName(), timeWindow, total, logs, severitySummary, keyword))
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

    private List<LogQueryRequest.FieldFilter> buildSeverityFilters(String severity) {
        String normalized = AgentToolSupport.normalizeText(severity).toLowerCase(Locale.ROOT);
        if (StringUtils.isBlank(normalized)) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        if (AgentToolSupport.containsAny(normalized, "error", "错误", "异常")) {
            values.add("error");
        }
        if (AgentToolSupport.containsAny(normalized, "warn", "告警", "警告")) {
            values.add("warn");
        }
        if (AgentToolSupport.containsAny(normalized, "info", "信息")) {
            values.add("info");
        }
        if (AgentToolSupport.containsAny(normalized, "debug", "调试")) {
            values.add("debug");
        }

        if (values.isEmpty()) {
            return List.of();
        }

        LogQueryRequest.FieldFilter filter = new LogQueryRequest.FieldFilter();
        filter.setField("levels");
        filter.setType("include");
        filter.setValues(values.stream().distinct().toList());
        return List.of(filter);
    }

    private List<LogQueryRequest.MessageCondition> buildMessageConditions(String keyword) {
        if (StringUtils.isBlank(AgentToolSupport.normalizeText(keyword))) {
            return List.of();
        }

        LogQueryRequest.MessageCondition condition = new LogQueryRequest.MessageCondition();
        condition.setOperator("contains");
        condition.setValue(keyword.trim());
        return List.of(condition);
    }

    private String buildLogsSummary(String datasourceName,
                                    AgentTimeWindow timeWindow,
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

        if (StringUtils.isNotBlank(AgentToolSupport.normalizeText(keyword))) {
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
                    .append(AgentToolSupport.stringify(firstLog.get("timestamp")))
                    .append("，摘要：")
                    .append(AgentToolSupport.truncate(AgentToolSupport.stringify(firstLog.get("message")), 80))
                    .append("。");
        }
        return summary.toString();
    }
}
