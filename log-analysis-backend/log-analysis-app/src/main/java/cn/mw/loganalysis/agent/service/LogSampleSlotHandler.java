package cn.mw.loganalysis.agent.service;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class LogSampleSlotHandler implements CreateLogParserSlotHandler {

    /**
     * 让日志样本优先进入任务帧，供后续预览使用。
     */
    @Override
    public int getOrder() {
        return 10;
    }

    /**
     * 当任务帧缺少样本，或用户重新表达创建意图时尝试提取样本。
     */
    @Override
    public boolean supports(CreateLogParserSlotContext context) {
        return StringUtils.isBlank(context.frame().getLogSample())
                || CreateLogParserSlotTextSupport.containsCreateParserIntent(context.message());
    }

    /**
     * 从用户消息中提取原始日志样本并写入任务帧。
     */
    @Override
    public void fill(CreateLogParserSlotContext context) {
        String logSample = CreateLogParserSlotTextSupport.extractLogSample(context.message());
        if (StringUtils.isNotBlank(logSample)) {
            context.frame().setLogSample(logSample);
        }
    }
}
