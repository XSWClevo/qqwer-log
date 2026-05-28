package cn.mw.loganalysis.agent.service;

import org.springframework.core.Ordered;

/**
 * Text2SQL 前置执行策略。
 *
 * 对确定性很强的统计问题先用代码执行，避免把简单查询交给大模型生成 SQL。
 */
interface Text2SqlPreflightStrategy extends Ordered {

    /**
     * 判断当前自然语言查询是否可以由该策略直接处理。
     */
    boolean supports(AgentExecutionContext context, String query);

    /**
     * 执行确定性查询，并返回与 Text2SQL 卡片兼容的工具结果。
     */
    AgentToolPayload execute(AgentExecutionContext context, String query);

    /**
     * 返回策略优先级，数值越小越先执行。
     */
    @Override
    default int getOrder() {
        return 0;
    }
}
