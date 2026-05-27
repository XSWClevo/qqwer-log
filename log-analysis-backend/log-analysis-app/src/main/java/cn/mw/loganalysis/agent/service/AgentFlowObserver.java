package cn.mw.loganalysis.agent.service;

/**
 * Agent 链路观察者。
 */
interface AgentFlowObserver {

    /**
     * 接收 Agent 工作流节点事件。
     */
    void onEvent(AgentFlowEventType eventType, AgentRuntimeContext context, Throwable error);
}
