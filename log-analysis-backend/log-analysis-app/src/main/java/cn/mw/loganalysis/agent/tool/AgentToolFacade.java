package cn.mw.loganalysis.agent.tool;

import cn.mw.loganalysis.agent.execution.AgentExecutionContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 智能助手统一工具入口。
 *
 * 规则回退和 LangChain4j @Tool 都通过这层调用，
 * 避免两边各自维护一套 schema/logs/timeseries/text2sql 实现。
 */
@Component
@RequiredArgsConstructor
public class AgentToolFacade {

    private final SchemaToolHandler schemaToolHandler;
    private final LogQueryToolHandler logQueryToolHandler;
    private final TimeSeriesToolHandler timeSeriesToolHandler;
    private final Text2SqlToolHandler text2SqlToolHandler;
    private final VectorComponentPlanToolHandler vectorComponentPlanToolHandler;

    /**
     * 查询当前数据源的字段结构。
     */
    public AgentToolPayload getSchema() {
        return schemaToolHandler.handle(AgentExecutionContextHolder.require());
    }

    /**
     * 查询日志明细列表。
     */
    public AgentToolPayload queryLogs(String timeRange, String keyword, String severity, Integer limit) {
        return logQueryToolHandler.handle(AgentExecutionContextHolder.require(), timeRange, keyword, severity, limit);
    }

    /**
     * 查询日志趋势数据。
     */
    public AgentToolPayload queryTimeseries(String timeRange, String granularity) {
        return timeSeriesToolHandler.handle(AgentExecutionContextHolder.require(), timeRange, granularity);
    }

    /**
     * 执行自然语言统计查询。
     */
    public AgentToolPayload text2SqlQuery(String query) {
        return text2SqlToolHandler.handle(AgentExecutionContextHolder.require(), query);
    }

    /**
     * 兼容旧工具签名，生成不含 Source 配置的 Vector 组件预览。
     */
    public AgentToolPayload previewVectorComponents(String logSample,
                                                    String datasourceId,
                                                    String tableName,
                                                    String regexPattern) {
        return previewVectorComponents(logSample, datasourceId, tableName, regexPattern, null, null);
    }

    /**
     * 生成包含 Source 配置的 Vector 组件预览。
     */
    public AgentToolPayload previewVectorComponents(String logSample,
                                                    String datasourceId,
                                                    String tableName,
                                                    String regexPattern,
                                                    String sourceType,
                                                    Map<String, Object> sourceConfig) {
        return vectorComponentPlanToolHandler.preview(
                AgentExecutionContextHolder.require(),
                logSample,
                datasourceId,
                tableName,
                regexPattern,
                sourceType,
                sourceConfig
        );
    }
}
