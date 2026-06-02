package cn.mw.loganalysis.agent.vectorplan;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;

@Component
public class SourceTypeSlotHandler implements CreateLogParserSlotHandler {

    /**
     * 在 Source 配置提取前先识别 Source 类型。
     */
    @Override
    public int getOrder() {
        return 40;
    }

    /**
     * Source 类型槽位处理器对每轮消息都可尝试提取。
     */
    @Override
    public boolean supports(CreateLogParserSlotContext context) {
        return true;
    }

    /**
     * 提取 file/syslog/socket/kafka 类型，并在类型切换时清空旧配置。
     */
    @Override
    public void fill(CreateLogParserSlotContext context) {
        String sourceType = CreateLogParserSlotTextSupport.extractSourceType(context.message());
        if (StringUtils.isBlank(sourceType)) {
            return;
        }
        if (StringUtils.isNotBlank(context.frame().getSourceType())
                && !StringUtils.equals(context.frame().getSourceType(), sourceType)) {
            context.frame().setSourceConfig(new LinkedHashMap<>());
        }
        context.frame().setSourceType(sourceType);
    }
}
