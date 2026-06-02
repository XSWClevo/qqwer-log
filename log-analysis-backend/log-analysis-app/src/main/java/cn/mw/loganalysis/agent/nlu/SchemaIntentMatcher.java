package cn.mw.loganalysis.agent.nlu;

import cn.mw.loganalysis.agent.execution.AgentRuntimeContext;
import cn.mw.loganalysis.agent.support.AgentToolSupport;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class SchemaIntentMatcher implements AgentIntentMatcher {

    /**
     * 在创建/Vector 意图之后匹配字段结构查询。
     */
    @Override
    public int getOrder() {
        return 20;
    }

    /**
     * 判断消息是否在询问字段或表结构。
     */
    @Override
    public boolean matches(AgentRuntimeContext context) {
        String lower = StringUtils.lowerCase(context.getEffectiveMessage(), Locale.ROOT);
        return AgentToolSupport.containsAny(lower, "字段", "表结构", "schema", "有哪些列", "哪些字段", "列结构");
    }

    /**
     * 返回 SCHEMA 意图决策。
     */
    @Override
    public AgentIntentDecision match(AgentRuntimeContext context) {
        return new AgentIntentDecision(AgentIntent.SCHEMA, context.getEffectiveMessage(), null, null, true);
    }
}
