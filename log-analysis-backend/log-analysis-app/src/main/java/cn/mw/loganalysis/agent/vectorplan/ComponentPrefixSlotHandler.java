package cn.mw.loganalysis.agent.vectorplan;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class ComponentPrefixSlotHandler implements CreateLogParserSlotHandler {

    /**
     * 在表名之后提取组件前缀。
     */
    @Override
    public int getOrder() {
        return 30;
    }

    /**
     * 组件前缀槽位处理器对每轮消息都可尝试提取。
     */
    @Override
    public boolean supports(CreateLogParserSlotContext context) {
        return true;
    }

    /**
     * 从用户消息中提取组件前缀并写入任务帧。
     */
    @Override
    public void fill(CreateLogParserSlotContext context) {
        String componentPrefix = CreateLogParserSlotTextSupport.extractComponentPrefix(context.message());
        if (StringUtils.isNotBlank(componentPrefix)) {
            context.frame().setComponentPrefix(componentPrefix);
        }
    }
}
