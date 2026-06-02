package cn.mw.loganalysis.agent.tool;

import cn.mw.loganalysis.agent.execution.AgentExecutionContext;
import cn.mw.loganalysis.agent.text2sql.SqlCandidateRaceService;
import cn.mw.loganalysis.agent.text2sql.SqlCandidateResult;
import cn.mw.loganalysis.agent.dto.AgentResult;
import cn.mw.loganalysis.stats.dto.AiQueryResponse;
import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Text2SqlToolHandlerTest {

    private final SqlCandidateRaceService raceService = mock(SqlCandidateRaceService.class);
    private final DynamicLogQueryService dynamicLogQueryService = mock(DynamicLogQueryService.class);
    private final Text2SqlToolHandler handler = new Text2SqlToolHandler(raceService, dynamicLogQueryService);
    private final AgentExecutionContext clickHouseContext = new AgentExecutionContext(
            "sink-1",
            "syslog_logs_sink",
            "clickhouse",
            1001L,
            "session-1"
    );

    @Test
    void shouldQueryThroughCandidateRaceAndKeepText2SqlCardShape() {
        AiQueryResponse response = AiQueryResponse.builder()
                .success(true)
                .sql("SELECT count() AS total FROM logs")
                .result(List.of(Map.of("total", 42)))
                .sqlGenerationTime(0.03D)
                .sqlExecutionTime(0.12D)
                .totalExecutionTime(0.20D)
                .build();
        SqlCandidateResult raceResult = SqlCandidateResult.builder()
                .response(response)
                .candidateSource("template")
                .raceMs(37L)
                .validatedCandidates(List.of("template"))
                .rejectedCandidates(List.of("history: 未命中"))
                .build();
        when(raceService.race(clickHouseContext, "日志总数")).thenReturn(raceResult);
        when(dynamicLogQueryService.getTableName("sink-1")).thenReturn("logs");

        AgentToolPayload payload = handler.handle(clickHouseContext, "  日志总数  ");

        verify(raceService).race(clickHouseContext, "日志总数");
        AgentResult result = payload.getResult();
        assertThat(payload.getToolName()).isEqualTo("text2sql_query");
        assertThat(payload.getIntent()).isEqualTo("text2sql");
        assertThat(result.getType()).isEqualTo("text2sql");
        assertThat(result.getSql()).isEqualTo("SELECT count() AS total FROM logs");
        assertThat(result.getQueryResultType()).isEqualTo("metric");
        assertThat(result.getRawResult()).isEqualTo(Map.of("total", 42));
        assertThat(result.getRows()).containsExactly(Map.of("total", 42));
        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getSqlGenerationTime()).isEqualTo(0.03D);
        assertThat(result.getSqlExecutionTime()).isEqualTo(0.12D);
        assertThat(result.getTotalExecutionTime()).isEqualTo(0.20D);
        assertThat(result.getSummary())
                .containsEntry("queryResultType", "metric")
                .containsEntry("rowCount", 1)
                .containsEntry("returnedRows", 1)
                .containsEntry("datasourceType", "clickhouse")
                .containsEntry("tableName", "logs")
                .containsEntry("candidateSource", "template")
                .containsEntry("candidateRaceMs", 37L)
                .containsEntry("validatedCandidates", List.of("template"))
                .containsEntry("rejectedCandidates", List.of("history: 未命中"));
    }

    @Test
    void shouldRejectNonClickHouseDatasourceBeforeRace() {
        AgentExecutionContext postgresContext = new AgentExecutionContext(
                "pg-1",
                "postgres",
                "postgresql",
                1001L,
                "session-1"
        );

        assertThatThrownBy(() -> handler.handle(postgresContext, "日志总数"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("仅支持 ClickHouse 数据源");
        verify(raceService, never()).race(postgresContext, "日志总数");
    }
}
