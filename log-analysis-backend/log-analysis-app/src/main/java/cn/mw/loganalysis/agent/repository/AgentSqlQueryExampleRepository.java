package cn.mw.loganalysis.agent.repository;

import cn.mw.loganalysis.agent.entity.AgentSqlQueryExample;
import cn.mw.loganalysis.agent.mapper.AgentSqlQueryExampleMapper;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 智能助手 SQL 查询经验仓储。
 */
@Repository
@DS("postgres")
@RequiredArgsConstructor
public class AgentSqlQueryExampleRepository {

    private static final long MAX_RECENT_EXAMPLES = 50L;

    private final AgentSqlQueryExampleMapper exampleMapper;

    /**
     * 查询当前用户和数据源最近成功的 SQL 经验。
     */
    public List<AgentSqlQueryExample> findRecent(Long userId, String datasourceId) {
        if (ObjectUtils.isEmpty(userId) || StringUtils.isBlank(datasourceId)) {
            return Collections.emptyList();
        }
        Page<AgentSqlQueryExample> page = new Page<>(1, MAX_RECENT_EXAMPLES, false);
        return exampleMapper.selectPage(
                page,
                Wrappers.<AgentSqlQueryExample>lambdaQuery()
                        .eq(AgentSqlQueryExample::getUserId, userId)
                        .eq(AgentSqlQueryExample::getDatasourceId, StringUtils.trim(datasourceId))
                        .orderByDesc(AgentSqlQueryExample::getUpdatedAt)
        ).getRecords();
    }

    /**
     * 保存成功 SQL 经验，重复问题更新命中信息。
     */
    public void saveSuccess(Long userId,
                            String datasourceId,
                            String datasourceType,
                            String question,
                            String normalizedQuestion,
                            String sql,
                            String resultType) {
        if (ObjectUtils.isEmpty(userId)
                || StringUtils.isAnyBlank(datasourceId, datasourceType, question, normalizedQuestion, sql)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        AgentSqlQueryExample existing = exampleMapper.selectOne(
                Wrappers.<AgentSqlQueryExample>lambdaQuery()
                        .eq(AgentSqlQueryExample::getUserId, userId)
                        .eq(AgentSqlQueryExample::getDatasourceId, datasourceId)
                        .eq(AgentSqlQueryExample::getNormalizedQuestion, normalizedQuestion)
                        .last("LIMIT 1")
        );
        if (existing == null) {
            exampleMapper.insert(AgentSqlQueryExample.builder()
                    .userId(userId)
                    .datasourceId(datasourceId)
                    .datasourceType(datasourceType)
                    .question(question)
                    .normalizedQuestion(normalizedQuestion)
                    .sqlTemplate(sql)
                    .resultType(resultType)
                    .hitCount(0)
                    .lastUsedAt(now)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
            return;
        }

        exampleMapper.update(
                null,
                Wrappers.<AgentSqlQueryExample>lambdaUpdate()
                        .eq(AgentSqlQueryExample::getId, existing.getId())
                        .set(AgentSqlQueryExample::getQuestion, question)
                        .set(AgentSqlQueryExample::getSqlTemplate, sql)
                        .set(AgentSqlQueryExample::getResultType, resultType)
                        .set(AgentSqlQueryExample::getLastUsedAt, now)
                        .set(AgentSqlQueryExample::getUpdatedAt, now)
        );
    }

    /**
     * 记录历史候选被再次采用。
     */
    public void markUsed(Long id) {
        if (id == null) {
            return;
        }
        AgentSqlQueryExample existing = exampleMapper.selectById(id);
        if (existing == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        exampleMapper.update(
                null,
                Wrappers.<AgentSqlQueryExample>lambdaUpdate()
                        .eq(AgentSqlQueryExample::getId, id)
                        .set(AgentSqlQueryExample::getHitCount, ObjectUtils.defaultIfNull(existing.getHitCount(), 0) + 1)
                        .set(AgentSqlQueryExample::getLastUsedAt, now)
                        .set(AgentSqlQueryExample::getUpdatedAt, now)
        );
    }
}
