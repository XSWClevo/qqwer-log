package cn.mw.loganalysis.agent.service;

import org.springframework.core.Ordered;

/**
 * 上下文增强节点。
 */
interface AgentContextEnhancer extends Ordered {

    /**
     * 在 AgentRuntimeContext 上补充当前节点负责的上下文信息。
     */
    void enhance(AgentRuntimeContext context);

    /**
     * 返回增强器执行顺序，数值越小越先执行。
     */
    @Override
    default int getOrder() {
        return 0;
    }
}
