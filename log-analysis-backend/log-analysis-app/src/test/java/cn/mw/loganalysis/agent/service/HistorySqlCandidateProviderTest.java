package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.entity.AgentSqlQueryExample;
import cn.mw.loganalysis.agent.repository.AgentSqlQueryExampleRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HistorySqlCandidateProviderTest {

    private final AgentSqlQueryExampleRepository repository = mock(AgentSqlQueryExampleRepository.class);
    private final SqlQuestionNormalizer normalizer = new SqlQuestionNormalizer();
    private final HistorySqlCandidateProvider provider = new HistorySqlCandidateProvider(repository, normalizer);
    private final AgentExecutionContext context = new AgentExecutionContext(
            "sink-1",
            "syslog_logs_sink",
            "clickhouse",
            1001L,
            "session-1"
    );

    @Test
    void shouldReturnHistoryCandidateForSimilarNormalizedQuestion() {
        String normalizedQuestion = normalizer.normalize("按 severity 统计最近1小时日志数量");
        when(repository.findRecent(1001L, "sink-1")).thenReturn(List.of(example(normalizedQuestion)));

        Optional<SqlCandidate> candidate = provider.generate(context, "按 severity 统计最近24小时日志数量");

        assertThat(candidate).isPresent();
        assertThat(candidate.get().source()).isEqualTo("history");
        assertThat(candidate.get().sql()).isEqualTo("SELECT severity, count() FROM logs GROUP BY severity");
        assertThat(candidate.get().resultType()).isEqualTo("category");
        assertThat(candidate.get().confidence()).isGreaterThanOrEqualTo(0.58D);
        assertThat(candidate.get().metadata())
                .containsEntry("exampleId", 7L)
                .containsEntry("normalizedQuestion", normalizedQuestion)
                .containsKey("similarity");
    }

    @Test
    void shouldReturnEmptyWhenSimilarityIsLow() {
        String normalizedQuestion = normalizer.normalize("按 severity 统计最近1小时日志数量");
        when(repository.findRecent(1001L, "sink-1")).thenReturn(List.of(example(normalizedQuestion)));

        Optional<SqlCandidate> candidate = provider.generate(context, "查询 message 包含 timeout 的最新明细");

        assertThat(candidate).isEmpty();
    }

    @Test
    void shouldSupportOnlyWhenUserIdDatasourceIdAndQueryExist() {
        AgentExecutionContext missingUser = new AgentExecutionContext(
                "sink-1",
                "syslog_logs_sink",
                "clickhouse",
                null,
                "session-1"
        );
        AgentExecutionContext missingDatasource = new AgentExecutionContext(
                "",
                "syslog_logs_sink",
                "clickhouse",
                1001L,
                "session-1"
        );

        assertThat(provider.supports(context, "按 severity 统计日志数量")).isTrue();
        assertThat(provider.supports(missingUser, "按 severity 统计日志数量")).isFalse();
        assertThat(provider.supports(missingDatasource, "按 severity 统计日志数量")).isFalse();
        assertThat(provider.supports(context, " ")).isFalse();
    }

    @Test
    void shouldKeepUnspacedChineseTimeRangeQuestionsSimilar() {
        String previous = normalizer.normalize("按severity统计最近1小时日志数量");
        String current = normalizer.normalize("按severity统计最近24小时日志数量");

        assertThat(normalizer.similarity(previous, current)).isGreaterThanOrEqualTo(0.58D);
    }

    private AgentSqlQueryExample example(String normalizedQuestion) {
        return AgentSqlQueryExample.builder()
                .id(7L)
                .userId(1001L)
                .datasourceId("sink-1")
                .datasourceType("clickhouse")
                .question("按 severity 统计最近1小时日志数量")
                .normalizedQuestion(normalizedQuestion)
                .sqlTemplate("SELECT severity, count() FROM logs GROUP BY severity")
                .resultType("category")
                .hitCount(3)
                .lastUsedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
