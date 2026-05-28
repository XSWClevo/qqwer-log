package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TemplateSqlCandidateProviderTest {

    private final DynamicLogQueryService dynamicLogQueryService = mock(DynamicLogQueryService.class);
    private final TemplateSqlCandidateProvider provider = new TemplateSqlCandidateProvider(dynamicLogQueryService);
    private final AgentExecutionContext clickHouseContext = new AgentExecutionContext(
            "sink-1",
            "syslog_logs_sink",
            "clickhouse",
            1L,
            "session-1"
    );

    @Test
    void shouldGenerateSimpleCountCandidate() {
        when(dynamicLogQueryService.getTableName("sink-1")).thenReturn("syslog_logs");

        Optional<SqlCandidate> candidate = provider.generate(clickHouseContext, "查最近一小时的日志数据总数");

        assertThat(candidate).isPresent();
        assertThat(candidate.get().source()).isEqualTo("template");
        assertThat(candidate.get().resultType()).isEqualTo("metric");
        assertThat(candidate.get().sql()).contains("SELECT count() AS total FROM `syslog_logs`");
        assertThat(candidate.get().sql()).contains("`timestamp` >=");
        assertThat(candidate.get().confidence()).isGreaterThanOrEqualTo(0.9D);
    }

    @Test
    void shouldGenerateDimensionCountCandidate() {
        when(dynamicLogQueryService.getTableName("sink-1")).thenReturn("syslog_logs");

        Optional<SqlCandidate> candidate = provider.generate(clickHouseContext, "按 severity 统计最近24小时数量");

        assertThat(candidate).isPresent();
        assertThat(candidate.get().source()).isEqualTo("template");
        assertThat(candidate.get().resultType()).isEqualTo("category");
        assertThat(candidate.get().sql()).contains("SELECT `severity` AS `severity`, count() AS count");
        assertThat(candidate.get().sql()).contains("GROUP BY `severity`");
        assertThat(candidate.get().sql()).contains("ORDER BY count DESC LIMIT 10");
    }

    @Test
    void shouldSupportOnlyClickHouseHighFrequencyQueries() {
        AgentExecutionContext postgreSqlContext = new AgentExecutionContext(
                "sink-1",
                "syslog_logs_sink",
                "postgresql",
                1L,
                "session-1"
        );

        assertThat(provider.supports(clickHouseContext, "查最近一小时的日志数据总数")).isTrue();
        assertThat(provider.supports(clickHouseContext, "按 severity 统计最近24小时数量")).isTrue();
        assertThat(provider.supports(clickHouseContext, "查询 message 包含 error 的明细")).isFalse();
        assertThat(provider.supports(postgreSqlContext, "查最近一小时的日志数据总数")).isFalse();
    }

    @Test
    void shouldProtectMetadataFromExternalMutation() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("timeRange", "最近一小时");

        SqlCandidate candidate = SqlCandidate.builder()
                .source("template")
                .sql("SELECT count() AS total FROM `syslog_logs`")
                .resultType("metric")
                .confidence(0.95D)
                .metadata(metadata)
                .build();
        metadata.put("timeRange", "changed");

        assertThat(candidate.metadata()).containsEntry("timeRange", "最近一小时");
    }
}
