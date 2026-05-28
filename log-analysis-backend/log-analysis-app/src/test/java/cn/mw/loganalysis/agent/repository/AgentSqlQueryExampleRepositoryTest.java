package cn.mw.loganalysis.agent.repository;

import cn.mw.loganalysis.agent.mapper.AgentSqlQueryExampleMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AgentSqlQueryExampleRepositoryTest {

    private final AgentSqlQueryExampleMapper mapper = mock(AgentSqlQueryExampleMapper.class);
    private final AgentSqlQueryExampleRepository repository = new AgentSqlQueryExampleRepository(mapper);

    @Test
    void shouldUpsertSuccessWhenRequiredFieldsExist() {
        repository.saveSuccess(1001L, "sink-1", "clickhouse", "按 severity 统计日志数量",
                "按 severity 统计日志数量", "SELECT 1", "metric");

        verify(mapper).upsertSuccess(eq(1001L), eq("sink-1"), eq("clickhouse"),
                eq("按 severity 统计日志数量"), eq("按 severity 统计日志数量"),
                eq("SELECT 1"), eq("metric"), any(LocalDateTime.class));
    }

    @Test
    void shouldNoopWhenRequiredSuccessStoreFieldsAreMissing() {
        repository.saveSuccess(1001L, "sink-1", "", "按 severity 统计日志数量",
                "按 severity 统计日志数量", "SELECT 1", "metric");
        repository.saveSuccess(1001L, "sink-1", "clickhouse", " ",
                "按 severity 统计日志数量", "SELECT 1", "metric");
        repository.saveSuccess(null, "sink-1", "clickhouse", "按 severity 统计日志数量",
                "按 severity 统计日志数量", "SELECT 1", "metric");

        verifyNoInteractions(mapper);
    }

    @Test
    void shouldIncrementHitCountAtomicallyWhenMarkedUsed() {
        repository.markUsed(7L);

        verify(mapper).incrementHitCount(eq(7L), any(LocalDateTime.class));
    }

    @Test
    void shouldNoopWhenMarkUsedIdIsMissing() {
        repository.markUsed(null);

        verifyNoInteractions(mapper);
    }
}
