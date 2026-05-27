package cn.mw.loganalysis.agent.service;

import org.springframework.core.Ordered;

/**
 * 创建日志解析的槽位处理策略。
 */
interface CreateLogParserSlotHandler extends Ordered {

    /**
     * 判断当前处理器是否需要处理这次槽位上下文。
     */
    boolean supports(CreateLogParserSlotContext context);

    /**
     * 从用户消息中提取或更新当前处理器负责的槽位。
     */
    void fill(CreateLogParserSlotContext context);

    /**
     * 返回槽位处理顺序，数值越小越先执行。
     */
    @Override
    default int getOrder() {
        return 0;
    }
}
