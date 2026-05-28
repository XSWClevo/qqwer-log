package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.repository.AgentSqlQueryExampleRepository;
import cn.mw.loganalysis.stats.dto.AiQueryResponse;
import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqlCandidateRaceServiceTest {

    private final SqlCandidateValidator validator = mock(SqlCandidateValidator.class);
    private final DynamicLogQueryService dynamicLogQueryService = mock(DynamicLogQueryService.class);
    private final AgentSqlQueryExampleRepository repository = mock(AgentSqlQueryExampleRepository.class);
    private final SqlQuestionNormalizer normalizer = new SqlQuestionNormalizer();
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final AgentExecutionContext context = new AgentExecutionContext(
            "sink-1",
            "syslog_logs_sink",
            "clickhouse",
            1001L,
            "session-1"
    );

    @AfterEach
    void shutdownExecutor() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(1, TimeUnit.SECONDS);
    }

    @Test
    void shouldLetTemplateCandidateWinBeforeLlmStarts() {
        AtomicBoolean llmCalled = new AtomicBoolean(false);
        SqlCandidate template = candidate("template", "SELECT count() FROM logs", "metric", Map.of());
        StubProvider templateProvider = new StubProvider("template", 10, template);
        StubProvider llmProvider = new StubProvider("llm", 100, () -> {
            llmCalled.set(true);
            return Optional.of(candidate("llm", "SELECT * FROM logs", "list", Map.of()));
        });
        when(validator.validate(context, template))
                .thenReturn(SqlCandidateValidationResult.valid("SELECT count() FROM logs LIMIT 200"));
        when(dynamicLogQueryService.executeRawSQL("sink-1", "SELECT count() FROM logs LIMIT 200"))
                .thenReturn(List.of(Map.of("total", 12)));

        SqlCandidateResult result = service(List.of(llmProvider, templateProvider))
                .race(context, "日志总数");

        assertThat(result.candidateSource()).isEqualTo("template");
        assertThat(result.response().getSuccess()).isTrue();
        assertThat(result.response().getSql()).isEqualTo("SELECT count() FROM logs LIMIT 200");
        assertThat(result.response().getResult()).isEqualTo(List.of(Map.of("total", 12)));
        assertThat(result.validatedCandidates()).containsExactly("template");
        assertThat(result.rejectedCandidates()).isEmpty();
        assertThat(llmCalled).isFalse();
    }

    @Test
    void shouldUseLlmWhenCheapCandidatesDoNotMatch() {
        SqlCandidate llm = candidate("llm", "SELECT * FROM logs", "list", Map.of());
        StubProvider templateProvider = new StubProvider("template", 10, Optional::empty);
        StubProvider llmProvider = new StubProvider("llm", 100, llm);
        when(validator.validate(context, llm))
                .thenReturn(SqlCandidateValidationResult.valid("SELECT * FROM logs LIMIT 200"));
        when(dynamicLogQueryService.executeRawSQL("sink-1", "SELECT * FROM logs LIMIT 200"))
                .thenReturn(List.of(Map.of("message", "ok")));

        SqlCandidateResult result = service(List.of(llmProvider, templateProvider))
                .race(context, "查询最近日志");

        assertThat(result.candidateSource()).isEqualTo("llm");
        assertThat(result.response().getSql()).isEqualTo("SELECT * FROM logs LIMIT 200");
        assertThat(result.validatedCandidates()).containsExactly("llm");
        assertThat(result.rejectedCandidates()).contains("template: 未生成候选 SQL");
    }

    @Test
    void shouldRejectInvalidCandidateAndUseNextCandidate() {
        SqlCandidate invalid = candidate("template", "DROP TABLE logs", "list", Map.of());
        SqlCandidate history = candidate("history", "SELECT * FROM logs", "list", Map.of("exampleId", 7L));
        StubProvider templateProvider = new StubProvider("template", 10, invalid);
        StubProvider historyProvider = new StubProvider("history", 20, () -> {
            sleep(40);
            return Optional.of(history);
        });
        when(validator.validate(context, invalid))
                .thenReturn(SqlCandidateValidationResult.invalid("只允许 SELECT/WITH 查询"));
        when(validator.validate(context, history))
                .thenReturn(SqlCandidateValidationResult.valid("SELECT * FROM logs LIMIT 200"));
        when(dynamicLogQueryService.executeRawSQL("sink-1", "SELECT * FROM logs LIMIT 200"))
                .thenReturn(List.of(Map.of("message", "ok")));

        SqlCandidateResult result = service(List.of(historyProvider, templateProvider))
                .race(context, "查询最近日志");

        assertThat(result.candidateSource()).isEqualTo("history");
        assertThat(result.validatedCandidates()).containsExactly("history");
        assertThat(result.rejectedCandidates()).containsExactly("template: 只允许 SELECT/WITH 查询");
    }

    @Test
    void shouldMarkHistoryCandidateUsedAndNotSaveSuccess() {
        SqlCandidate history = candidate("history", "SELECT * FROM logs", "list", Map.of("exampleId", 7L));
        StubProvider historyProvider = new StubProvider("history", 20, history);
        when(validator.validate(context, history))
                .thenReturn(SqlCandidateValidationResult.valid("SELECT * FROM logs LIMIT 200"));
        when(dynamicLogQueryService.executeRawSQL("sink-1", "SELECT * FROM logs LIMIT 200"))
                .thenReturn(List.of(Map.of("message", "ok")));

        SqlCandidateResult result = service(List.of(historyProvider))
                .race(context, "查询最近日志");

        assertThat(result.candidateSource()).isEqualTo("history");
        verify(repository).markUsed(7L);
        verify(repository, never()).saveSuccess(eq(1001L), eq("sink-1"), eq("clickhouse"),
                eq("查询最近日志"), eq(normalizer.normalize("查询最近日志")),
                eq("SELECT * FROM logs LIMIT 200"), eq("list"));
    }

    @Test
    void shouldSaveSuccessfulNonHistoryCandidate() {
        SqlCandidate template = candidate("template", "SELECT count() FROM logs", "metric", Map.of());
        StubProvider templateProvider = new StubProvider("template", 10, template);
        when(validator.validate(context, template))
                .thenReturn(SqlCandidateValidationResult.valid("SELECT count() FROM logs LIMIT 200"));
        when(dynamicLogQueryService.executeRawSQL("sink-1", "SELECT count() FROM logs LIMIT 200"))
                .thenReturn(List.of(Map.of("total", 12)));

        SqlCandidateResult result = service(List.of(templateProvider))
                .race(context, "日志总数");

        assertThat(result.candidateSource()).isEqualTo("template");
        verify(repository).saveSuccess(1001L, "sink-1", "clickhouse",
                "日志总数", normalizer.normalize("日志总数"),
                "SELECT count() FROM logs LIMIT 200", "metric");
        verify(repository, never()).markUsed(7L);
    }

    @Test
    void shouldThrowClearReasonsWhenAllCheapCandidatesFail() {
        SqlCandidate invalid = candidate("template", "DROP TABLE logs", "list", Map.of());
        StubProvider templateProvider = new StubProvider("template", 10, invalid);
        when(validator.validate(context, invalid))
                .thenReturn(SqlCandidateValidationResult.invalid("只允许 SELECT/WITH 查询"));

        long startedAt = System.currentTimeMillis();

        assertThatThrownBy(() -> service(List.of(templateProvider)).race(context, "查询最近日志"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("所有 SQL 候选均失败")
                .hasMessageContaining("template: 只允许 SELECT/WITH 查询");
        assertThat(System.currentTimeMillis() - startedAt).isLessThan(1_000L);
    }

    private SqlCandidateRaceService service(List<SqlCandidateProvider> providers) {
        return new SqlCandidateRaceService(providers, validator, dynamicLogQueryService, repository, normalizer, executor);
    }

    private SqlCandidate candidate(String source, String sql, String resultType, Map<String, Object> metadata) {
        return SqlCandidate.builder()
                .source(source)
                .sql(sql)
                .resultType(resultType)
                .confidence(0.9D)
                .generationTimeMs(1L)
                .metadata(metadata)
                .build();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class StubProvider implements SqlCandidateProvider {

        private final String source;
        private final int order;
        private final CandidateSupplier supplier;
        private final AtomicInteger calls = new AtomicInteger();

        private StubProvider(String source, int order, SqlCandidate candidate) {
            this(source, order, () -> Optional.of(candidate));
        }

        private StubProvider(String source, int order, CandidateSupplier supplier) {
            this.source = source;
            this.order = order;
            this.supplier = supplier;
        }

        @Override
        public String source() {
            return source;
        }

        @Override
        public boolean supports(AgentExecutionContext context, String query) {
            return true;
        }

        @Override
        public Optional<SqlCandidate> generate(AgentExecutionContext context, String query) {
            calls.incrementAndGet();
            return supplier.get();
        }

        @Override
        public int getOrder() {
            return order;
        }
    }

    @FunctionalInterface
    private interface CandidateSupplier {
        Optional<SqlCandidate> get();
    }
}
