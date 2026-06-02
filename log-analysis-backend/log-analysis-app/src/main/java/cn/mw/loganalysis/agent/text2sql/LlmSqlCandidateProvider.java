package cn.mw.loganalysis.agent.text2sql;

import cn.mw.loganalysis.agent.execution.AgentExecutionContext;
import cn.mw.loganalysis.stats.dto.AiQueryRequest;
import cn.mw.loganalysis.stats.dto.AiQueryResponse;
import cn.mw.loganalysis.stats.service.AiQueryService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 调用 AI service 生成 LLM SQL 候选。
 */
@Component
@RequiredArgsConstructor
public class LlmSqlCandidateProvider implements SqlCandidateProvider {

    private final AiQueryService aiQueryService;

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public String source() {
        return "llm";
    }

    @Override
    public boolean supports(AgentExecutionContext context, String query) {
        return context != null && StringUtils.isNotBlank(query);
    }

    /**
     * 只使用 AI service 生成 SQL，不在这里执行最终查询。
     */
    @Override
    public Optional<SqlCandidate> generate(AgentExecutionContext context, String query) {
        long startedAt = System.currentTimeMillis();
        AiQueryRequest request = new AiQueryRequest();
        request.setQuery(StringUtils.defaultString(StringUtils.trimToNull(query)));
        request.setDatasourceId(context.datasourceId());

        AiQueryResponse response = aiQueryService.generateSqlOnly(request);
        if (!Boolean.TRUE.equals(response.getSuccess()) || StringUtils.isBlank(response.getSql())) {
            throw new IllegalStateException(StringUtils.defaultIfBlank(response.getError(), "LLM SQL 生成失败"));
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sqlGenerationTime", response.getSqlGenerationTime());
        return Optional.of(SqlCandidate.builder()
                .source(source())
                .sql(response.getSql())
                .resultType("list")
                .confidence(0.7D)
                .generationTimeMs(System.currentTimeMillis() - startedAt)
                .metadata(metadata)
                .build());
    }
}
