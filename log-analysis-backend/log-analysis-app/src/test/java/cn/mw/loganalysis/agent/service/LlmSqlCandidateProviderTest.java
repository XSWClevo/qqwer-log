package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.stats.dto.AiQueryRequest;
import cn.mw.loganalysis.stats.dto.AiQueryResponse;
import cn.mw.loganalysis.stats.service.AiQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmSqlCandidateProviderTest {

    private final AiQueryService aiQueryService = mock(AiQueryService.class);
    private final LlmSqlCandidateProvider provider = new LlmSqlCandidateProvider(aiQueryService);
    private final AgentExecutionContext context = new AgentExecutionContext(
            "sink-1",
            "syslog_logs_sink",
            "clickhouse",
            1001L,
            "session-1"
    );

    @Test
    void shouldGenerateCandidateWithSqlOnlyAiCall() {
        when(aiQueryService.generateSqlOnly(any(AiQueryRequest.class))).thenReturn(AiQueryResponse.builder()
                .success(true)
                .sql("SELECT * FROM syslog_logs LIMIT 100")
                .sqlGenerationTime(0.42D)
                .totalExecutionTime(0.5D)
                .build());

        Optional<SqlCandidate> candidate = provider.generate(context, " 查询最近日志 ");

        ArgumentCaptor<AiQueryRequest> requestCaptor = ArgumentCaptor.forClass(AiQueryRequest.class);
        verify(aiQueryService).generateSqlOnly(requestCaptor.capture());
        verify(aiQueryService, never()).query(any(AiQueryRequest.class));
        assertThat(requestCaptor.getValue().getQuery()).isEqualTo("查询最近日志");
        assertThat(requestCaptor.getValue().getDatasourceId()).isEqualTo("sink-1");
        assertThat(requestCaptor.getValue().getDatasourceIds()).isNull();

        assertThat(candidate).isPresent();
        assertThat(candidate.get().source()).isEqualTo("llm");
        assertThat(candidate.get().sql()).isEqualTo("SELECT * FROM syslog_logs LIMIT 100");
        assertThat(candidate.get().resultType()).isEqualTo("list");
        assertThat(candidate.get().confidence()).isEqualTo(0.7D);
        assertThat(candidate.get().metadata()).containsEntry("sqlGenerationTime", 0.42D);
    }

    @Test
    void shouldSupportNonblankQuery() {
        assertThat(provider.supports(context, "查询最近日志")).isTrue();
        assertThat(provider.supports(context, " ")).isFalse();
        assertThat(provider.supports(null, "查询最近日志")).isFalse();
    }

    @Test
    void shouldThrowResponseErrorWhenSqlGenerationFails() {
        when(aiQueryService.generateSqlOnly(any(AiQueryRequest.class))).thenReturn(AiQueryResponse.builder()
                .success(false)
                .error("AI服务不可用")
                .build());

        assertThatThrownBy(() -> provider.generate(context, "查询最近日志"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AI服务不可用");

        verify(aiQueryService, never()).query(any(AiQueryRequest.class));
    }
}
