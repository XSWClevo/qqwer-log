package cn.mw.loganalysis.agent.service;

import org.springframework.core.Ordered;

import java.util.Map;

/**
 * 意图执行策略。
 */
interface AgentFallbackToolExecutor extends Ordered {

    /**
     * 判断该执行器是否支持当前意图。
     */
    boolean supports(AgentIntent intent);

    /**
     * 执行当前意图对应的工具调用。
     */
    AgentToolPayload execute(AgentRuntimeContext context);

    /**
     * 构造工具调用记录中展示的输入参数。
     */
    Map<String, Object> buildToolInput(AgentRuntimeContext context);

    /**
     * 返回执行器优先级，数值越小越先选择。
     */
    @Override
    default int getOrder() {
        return 0;
    }
}
