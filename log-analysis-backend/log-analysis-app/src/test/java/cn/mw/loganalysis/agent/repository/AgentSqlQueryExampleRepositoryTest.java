package cn.mw.loganalysis.agent.repository;

import cn.mw.loganalysis.agent.mapper.AgentSqlQueryExampleMapper;
import org.apache.ibatis.annotations.Insert;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.assertj.core.api.Assertions.assertThat;

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
    void shouldDelegateDuplicateSuccessHitIncrementToMapperUpsert() {
        repository.saveSuccess(1001L, "sink-1", "clickhouse", "查日志总数",
                "查日志总数", "SELECT count() FROM logs", "metric");

        verify(mapper).upsertSuccess(eq(1001L), eq("sink-1"), eq("clickhouse"),
                eq("查日志总数"), eq("查日志总数"),
                eq("SELECT count() FROM logs"), eq("metric"), any(LocalDateTime.class));
    }

    @Test
    void shouldIncrementHitCountOnDuplicateSuccessUpsert() throws NoSuchMethodException {
        Method method = AgentSqlQueryExampleMapper.class.getMethod(
                "upsertSuccess",
                Long.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                LocalDateTime.class
        );
        String insertSql = String.join("\n", Arrays.asList(method.getAnnotation(Insert.class).value()));

        assertThat(insertSql).contains("hit_count = agent_sql_query_examples.hit_count + 1");
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
