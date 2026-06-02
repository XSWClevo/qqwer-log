package cn.mw.loganalysis.agent.execution;

/**
 * Agent 链路观察者。
 */
public interface AgentFlowObserver {

    /**
     * 接收 Agent 工作流节点事件。
     */
    void onEvent(AgentFlowEventType eventType, AgentRuntimeContext context, Throwable error);
}
