package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.stats.dto.LogQueryRequest;
import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LogCountMetricPreflightStrategyTest {

    private final DynamicLogQueryService dynamicLogQueryService = mock(DynamicLogQueryService.class);
    private final LogCountMetricPreflightStrategy strategy = new LogCountMetricPreflightStrategy(dynamicLogQueryService);
    private final AgentExecutionContext context = new AgentExecutionContext(
            "sink-1",
            "syslog_logs_sink",
            "clickhouse",
            1L,
            "session-1"
    );

    @Test
    void shouldHandleSimpleLogCountWithoutText2SqlModel() {
        when(dynamicLogQueryService.queryLogs(eq("sink-1"), any(LogQueryRequest.class)))
                .thenReturn(Map.of("total", 42L));
        when(dynamicLogQueryService.getTableName("sink-1")).thenReturn("syslog_logs");

        AgentToolPayload payload = strategy.execute(context, "查最近一小时的日志数据总数");

        assertThat(payload.getToolName()).isEqualTo("text2sql_query");
        assertThat(payload.getToolLabel()).isEqualTo("日志总数统计");
        assertThat(payload.getResult().getType()).isEqualTo("text2sql");
        assertThat(payload.getResult().getQueryResultType()).isEqualTo("metric");
        assertThat(payload.getResult().getRawResult()).isEqualTo(Map.of("total", 42L));
        assertThat(payload.getResult().getSqlGenerationTime()).isZero();
        assertThat(payload.getSummary()).contains("未调用 Text2SQL 模型");
    }

    @Test
    void shouldMatchSimpleCountButSkipGroupedAggregation() {
        assertThat(strategy.supports(context, "查最近一小时的日志数据总数")).isTrue();
        assertThat(strategy.supports(context, "最近一小时错误日志总数")).isTrue();
        assertThat(strategy.supports(context, "按 severity 统计最近24小时数量")).isFalse();
        assertThat(strategy.supports(context, "top 10 主机日志数量")).isFalse();
    }
}
