package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 基于规则模板生成高频统计 SQL 候选。
 */
@Component
@RequiredArgsConstructor
public class TemplateSqlCandidateProvider implements SqlCandidateProvider {

    private final DynamicLogQueryService dynamicLogQueryService;

    /**
     * 模板候选最先生成。
     */
    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public String source() {
        return "template";
    }

    /**
     * 仅处理 ClickHouse 高频统计问题。
     */
    @Override
    public boolean supports(AgentExecutionContext context, String query) {
        return context != null
                && StringUtils.equalsIgnoreCase(context.datasourceType(), "clickhouse")
                && (isSimpleCount(query) || isDimensionCount(query));
    }

    /**
     * 生成模板 SQL 候选。
     */
    @Override
    public Optional<SqlCandidate> generate(AgentExecutionContext context, String query) {
        if (!supports(context, query)) {
            return Optional.empty();
        }
        long startedAt = System.currentTimeMillis();
        AgentTimeWindow timeWindow = AgentToolSupport.resolveTimeWindow(query, false);
        String tableName = dynamicLogQueryService.getTableName(context.datasourceId());
        String sql;
        String resultType;
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("timeRange", timeWindow.label());

        if (isDimensionCount(query)) {
            String dimension = SqlTemplateSupport.resolveDimension(query);
            if (StringUtils.isBlank(dimension)) {
                return Optional.empty();
            }
            sql = "SELECT " + SqlTemplateSupport.quoteIdentifier(dimension) + " AS " + SqlTemplateSupport.quoteIdentifier(dimension)
                    + ", count() AS count FROM " + SqlTemplateSupport.quoteIdentifier(tableName)
                    + SqlTemplateSupport.timeWhere(timeWindow)
                    + " GROUP BY " + SqlTemplateSupport.quoteIdentifier(dimension)
                    + " ORDER BY count DESC LIMIT 10";
            resultType = "category";
            metadata.put("dimension", dimension);
        } else {
            String severity = AgentIntentTextSupport.extractSeverity(query);
            sql = "SELECT count() AS total FROM " + SqlTemplateSupport.quoteIdentifier(tableName)
                    + SqlTemplateSupport.timeWhere(timeWindow)
                    + SqlTemplateSupport.severityClause(severity);
            resultType = "metric";
            if (StringUtils.isNotBlank(severity)) {
                metadata.put("severity", severity);
            }
        }

        return Optional.of(SqlCandidate.builder()
                .source(source())
                .sql(sql)
                .resultType(resultType)
                .confidence(0.95D)
                .generationTimeMs(System.currentTimeMillis() - startedAt)
                .metadata(metadata)
                .build());
    }

    private boolean isSimpleCount(String query) {
        String lower = StringUtils.lowerCase(AgentToolSupport.normalizeText(query), Locale.ROOT);
        boolean countRequest = AgentToolSupport.containsAny(lower, "总数", "多少条", "多少", "数量", "条数", "count");
        boolean complexAggregation = AgentToolSupport.containsAny(lower,
                "按", "分组", "排行", "top", "占比", "平均", "avg", "sum", "max", "min", "每小时", "每分钟", "趋势", "时序");
        return countRequest && !complexAggregation;
    }

    private boolean isDimensionCount(String query) {
        String lower = StringUtils.lowerCase(AgentToolSupport.normalizeText(query), Locale.ROOT);
        return AgentToolSupport.containsAny(lower, "按", "分组")
                && AgentToolSupport.containsAny(lower, "统计", "数量", "总数", "条数", "count")
                && StringUtils.isNotBlank(SqlTemplateSupport.resolveDimension(query));
    }
}
