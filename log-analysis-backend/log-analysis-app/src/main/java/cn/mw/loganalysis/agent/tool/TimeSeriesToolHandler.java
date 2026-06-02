package cn.mw.loganalysis.agent.tool;

import cn.mw.loganalysis.agent.execution.AgentExecutionContext;
import cn.mw.loganalysis.agent.support.AgentTimeWindow;
import cn.mw.loganalysis.agent.support.AgentToolSupport;
import cn.mw.loganalysis.agent.dto.AgentResult;
import cn.mw.loganalysis.stats.dto.StatsQueryRequest;
import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TimeSeriesToolHandler {

    private final DynamicLogQueryService dynamicLogQueryService;

    public AgentToolPayload handle(AgentExecutionContext context, String timeRange, String granularity) {
        AgentTimeWindow timeWindow = AgentToolSupport.resolveTimeWindow(timeRange, true);
        String resolvedGranularity = AgentToolSupport.resolveGranularity(granularity, timeWindow);
        long startedAt = System.currentTimeMillis();

        StatsQueryRequest request = new StatsQueryRequest();
        request.setDatasourceId(context.datasourceId());
        request.setStartTime(timeWindow.start());
        request.setEndTime(timeWindow.end());
        request.setGranularity(resolvedGranularity);

        Map<String, Object> queryResult = dynamicLogQueryService.queryTimeSeries(context.datasourceId(), request);
        List<Map<String, Object>> series = AgentToolSupport.castList(queryResult.get("series"));
        Map<String, Object> summary = buildTimeseriesSummary(series, resolvedGranularity);

        return AgentToolPayload.builder()
                .toolName("query_timeseries")
                .toolLabel("查询日志趋势")
                .intent("timeseries")
                .summary(buildTimeseriesSummaryText(context.datasourceName(), timeWindow, resolvedGranularity, summary))
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

    private Map<String, Object> buildTimeseriesSummary(List<Map<String, Object>> series, String granularity) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("pointCount", series.size());
        summary.put("granularity", granularity);

        long totalCount = 0L;
        long peakCount = 0L;
        String peakTimestamp = null;

        for (Map<String, Object> point : series) {
            long count = AgentToolSupport.toLong(point.get("count"));
            totalCount += count;
            if (count >= peakCount) {
                peakCount = count;
                peakTimestamp = AgentToolSupport.stringify(point.get("timestamp"));
            }
        }

        summary.put("totalCount", totalCount);
        summary.put("peakCount", peakCount);
        summary.put("peakTimestamp", peakTimestamp);
        return summary;
    }

    private String buildTimeseriesSummaryText(String datasourceName,
                                              AgentTimeWindow timeWindow,
                                              String granularity,
                                              Map<String, Object> summary) {
        long pointCount = AgentToolSupport.toLong(summary.get("pointCount"));
        long totalCount = AgentToolSupport.toLong(summary.get("totalCount"));
        long peakCount = AgentToolSupport.toLong(summary.get("peakCount"));
        String peakTimestamp = AgentToolSupport.stringify(summary.get("peakTimestamp"));

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
                StringUtils.isNotBlank(peakTimestamp) ? peakTimestamp : "未知时间点");
    }
}
