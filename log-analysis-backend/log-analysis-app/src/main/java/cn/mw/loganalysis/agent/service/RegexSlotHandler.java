package cn.mw.loganalysis.agent.service;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class RegexSlotHandler implements CreateLogParserSlotHandler {

    /**
     * 在 Source 配置之后提取用户显式提供的正则。
     */
    @Override
    public int getOrder() {
        return 60;
    }

    /**
     * 正则槽位处理器对每轮消息都可尝试提取。
     */
    @Override
    public boolean supports(CreateLogParserSlotContext context) {
        return true;
    }

    /**
     * 从用户消息中提取 regexPattern 并写入任务帧。
     */
    @Override
    public void fill(CreateLogParserSlotContext context) {
        String regexPattern = CreateLogParserSlotTextSupport.extractRegexPattern(context.message());
        if (StringUtils.isNotBlank(regexPattern)) {
            context.frame().setRegexPattern(regexPattern);
        }
    }
}
