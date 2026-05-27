package cn.mw.loganalysis.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 默认观察者：记录规则 Agent 链路节点。
 */
@Slf4j
@Component
public class LoggingAgentFlowObserver implements AgentFlowObserver {

    /**
     * 将工作流节点事件写入日志，失败事件使用 warn 级别。
     */
    @Override
    public void onEvent(AgentFlowEventType eventType, AgentRuntimeContext context, Throwable error) {
        if (AgentFlowEventType.TOOL_EXECUTION_FAILED.equals(eventType)) {
            log.warn("规则 Agent 节点失败, event={}, intent={}, datasourceId={}, message={}, reason={}",
                    eventType,
                    context != null ? context.getIntent() : null,
                    context != null && context.getRequest() != null ? context.getRequest().getDatasourceId() : null,
                    context != null && context.getRequest() != null ? context.getRequest().getMessage() : null,
                    error != null ? error.getMessage() : null);
            return;
        }
        log.debug("规则 Agent 节点完成, event={}, intent={}, datasourceId={}",
                eventType,
                context != null ? context.getIntent() : null,
                context != null && context.getRequest() != null ? context.getRequest().getDatasourceId() : null);
    }
}
