package cn.mw.loganalysis.agent.text2sql;

import cn.mw.loganalysis.agent.execution.AgentExecutionContext;
import cn.mw.loganalysis.agent.entity.AgentSqlQueryExample;
import cn.mw.loganalysis.agent.repository.AgentSqlQueryExampleRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从历史成功 SQL 经验中生成候选。
 */
@Component
@RequiredArgsConstructor
public class HistorySqlCandidateProvider implements SqlCandidateProvider {

    private static final double MIN_SIMILARITY = 0.58D;
    private static final Pattern RELATIVE_TIME_RANGE_PATTERN = Pattern.compile(
            "(最近|近|过去)?\\s*([0-9一二两三四五六七八九十半]+)\\s*(分钟|小时|天|周)\\s*(内)?"
    );

    private final AgentSqlQueryExampleRepository exampleRepository;
    private final SqlQuestionNormalizer questionNormalizer;

    /**
     * 历史候选在模板候选之后生成。
     */
    @Override
    public int getOrder() {
        return 20;
    }

    /**
     * 返回历史候选来源标识。
     */
    @Override
    public String source() {
        return "history";
    }

    /**
     * 历史候选需要登录用户和数据源。
     */
    @Override
    public boolean supports(AgentExecutionContext context, String query) {
        return context != null
                && context.userId() != null
                && StringUtils.isNotBlank(context.datasourceId())
                && StringUtils.isNotBlank(query);
    }

    /**
     * 查找最相似的成功 SQL 模板。
     */
    @Override
    public Optional<SqlCandidate> generate(AgentExecutionContext context, String query) {
        if (!supports(context, query)) {
            return Optional.empty();
        }
        long startedAt = System.currentTimeMillis();
        String normalized = questionNormalizer.normalize(query);
        return exampleRepository.findRecent(context.userId(), context.datasourceId()).stream()
                .filter(example -> hasCompatibleRelativeTimeRange(example.getQuestion(), query))
                .map(example -> new ScoredExample(example, questionNormalizer.similarity(normalized, example.getNormalizedQuestion())))
                .filter(scored -> scored.score() >= MIN_SIMILARITY)
                .max(Comparator.comparingDouble(ScoredExample::score))
                .map(scored -> buildCandidate(scored, startedAt));
    }

    /**
     * 历史 SQL 仍是已展开 SQL，v1 只允许相同相对时间范围复用，避免静默返回旧窗口数据。
     */
    private boolean hasCompatibleRelativeTimeRange(String previousQuestion, String currentQuestion) {
        List<String> previousRanges = extractRelativeTimeRanges(previousQuestion);
        List<String> currentRanges = extractRelativeTimeRanges(currentQuestion);
        if (previousRanges.isEmpty() && currentRanges.isEmpty()) {
            return true;
        }
        return previousRanges.equals(currentRanges);
    }

    private List<String> extractRelativeTimeRanges(String question) {
        Matcher matcher = RELATIVE_TIME_RANGE_PATTERN.matcher(StringUtils.defaultString(question));
        List<String> ranges = new java.util.ArrayList<>();
        while (matcher.find()) {
            ranges.add(matcher.group(2) + matcher.group(3));
        }
        return ranges;
    }

    /**
     * 将历史记录转成候选 SQL。
     */
    private SqlCandidate buildCandidate(ScoredExample scored, long startedAt) {
        AgentSqlQueryExample example = scored.example();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("exampleId", example.getId());
        metadata.put("similarity", scored.score());
        metadata.put("normalizedQuestion", example.getNormalizedQuestion());
        return SqlCandidate.builder()
                .source(source())
                .sql(example.getSqlTemplate())
                .resultType(StringUtils.defaultIfBlank(example.getResultType(), "list"))
                .confidence(scored.score())
                .generationTimeMs(System.currentTimeMillis() - startedAt)
                .metadata(metadata)
                .build();
    }

    private record ScoredExample(AgentSqlQueryExample example, double score) {
    }
}
