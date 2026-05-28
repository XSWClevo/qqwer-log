package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.stats.dto.StatsQueryRequest;
import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DimensionCountPreflightStrategyTest {

    private final DynamicLogQueryService dynamicLogQueryService = mock(DynamicLogQueryService.class);
    private final DimensionCountPreflightStrategy strategy = new DimensionCountPreflightStrategy(dynamicLogQueryService);
    private final AgentExecutionContext context = new AgentExecutionContext(
            "sink-1",
            "syslog_logs_sink",
            "clickhouse",
            1L,
            "session-1"
    );

    @Test
    void shouldHandleSeverityCountWithoutText2SqlModel() {
        Map<String, Object> stats = Map.of("data", Map.of("severity", List.of(
                Map.of("value", "ERROR", "count", 7L),
                Map.of("value", "INFO", "count", 3L)
        )));
        when(dynamicLogQueryService.queryStats(eq("sink-1"), any(StatsQueryRequest.class))).thenReturn(stats);
        when(dynamicLogQueryService.getTableName("sink-1")).thenReturn("syslog_logs");

        AgentToolPayload payload = strategy.execute(context, "按 severity 统计最近24小时数量");

        assertThat(payload.getToolLabel()).isEqualTo("日志分组统计");
        assertThat(payload.getResult().getQueryResultType()).isEqualTo("category");
        assertThat(payload.getResult().getRawResult()).isEqualTo(List.of(
                Map.of("severity", "ERROR", "count", 7L),
                Map.of("severity", "INFO", "count", 3L)
        ));
        assertThat(payload.getResult().getSqlGenerationTime()).isZero();
        assertThat(payload.getSummary()).contains("未调用 Text2SQL 模型");
    }

    @Test
    void shouldMatchKnownDimensionCountOnly() {
        assertThat(strategy.supports(context, "按 severity 统计最近24小时数量")).isTrue();
        assertThat(strategy.supports(context, "按主机统计最近1小时日志条数")).isTrue();
        assertThat(strategy.supports(context, "top 10 主机日志数量")).isFalse();
        assertThat(strategy.supports(context, "最近一小时日志总数")).isFalse();
    }
}
