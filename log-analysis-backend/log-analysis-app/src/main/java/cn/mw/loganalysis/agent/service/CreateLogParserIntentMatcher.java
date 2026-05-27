package cn.mw.loganalysis.agent.service;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 创建日志解析意图优先匹配。
 */
@Component
@RequiredArgsConstructor
public class CreateLogParserIntentMatcher implements AgentIntentMatcher {

    private final CreateLogParserTaskService createLogParserTaskService;

    /**
     * 让创建日志解析意图优先于日志查询等通用意图。
     */
    @Override
    public int getOrder() {
        return 0;
    }

    /**
     * 判断是否是创建日志解析请求，或当前会话是否正在补槽。
     */
    @Override
    public boolean matches(AgentRuntimeContext context) {
        return isCreateLogParserIntentText(context.getEffectiveMessage())
                || createLogParserTaskService.shouldContinueSlotFilling(context.getRequest(), context.getUserId());
    }

    /**
     * 返回 CREATE_LOG_PARSER 意图决策。
     */
    @Override
    public AgentIntentDecision match(AgentRuntimeContext context) {
        return new AgentIntentDecision(
                AgentIntent.CREATE_LOG_PARSER,
                context.getEffectiveMessage(),
                null,
                null,
                true
        );
    }

    /**
     * 使用关键词和短正则判断文本是否表达创建日志解析意图。
     */
    private boolean isCreateLogParserIntentText(String message) {
        String lower = StringUtils.lowerCase(message, Locale.ROOT);
        return AgentToolSupport.containsAny(lower,
                "创建日志解析", "创建这个日志的解析", "创建这条日志的解析", "日志解析",
                "解析这个日志", "解析这条日志", "解析规则", "生成解析", "生成正则",
                "接入日志", "采集日志", "入库解析", "创建入库", "生成remap",
                "vector组件", "vector 组件", "remap", "sink", "建表", "入库", "日志样本")
                || Pattern.compile("(创建|生成|配置|接入|采集).{0,12}(日志|log).{0,12}(解析|正则|入库|组件|表)")
                .matcher(lower)
                .find()
                || Pattern.compile("(日志|log).{0,12}(解析|正则|入库).{0,12}(创建|生成|配置)")
                .matcher(lower)
                .find();
    }
}
