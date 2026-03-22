package cn.mw.loganalysis.agent.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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

    public AgentToolPayload getSchema() {
        return schemaToolHandler.handle(AgentExecutionContextHolder.require());
    }

    public AgentToolPayload queryLogs(String timeRange, String keyword, String severity, Integer limit) {
        return logQueryToolHandler.handle(AgentExecutionContextHolder.require(), timeRange, keyword, severity, limit);
    }

    public AgentToolPayload queryTimeseries(String timeRange, String granularity) {
        return timeSeriesToolHandler.handle(AgentExecutionContextHolder.require(), timeRange, granularity);
    }

    public AgentToolPayload text2SqlQuery(String query) {
        return text2SqlToolHandler.handle(AgentExecutionContextHolder.require(), query);
    }
}
