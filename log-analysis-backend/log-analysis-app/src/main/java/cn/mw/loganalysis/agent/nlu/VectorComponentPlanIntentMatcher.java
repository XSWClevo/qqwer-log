package cn.mw.loganalysis.agent.nlu;

import cn.mw.loganalysis.agent.execution.AgentRuntimeContext;
import cn.mw.loganalysis.agent.support.AgentToolSupport;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class VectorComponentPlanIntentMatcher implements AgentIntentMatcher {

    /**
     * 在创建日志解析之后匹配更通用的 Vector 组件预览意图。
     */
    @Override
    public int getOrder() {
        return 10;
    }

    /**
     * 判断消息是否表达直接生成 Vector 组件预览。
     */
    @Override
    public boolean matches(AgentRuntimeContext context) {
        String lower = StringUtils.lowerCase(context.getEffectiveMessage(), Locale.ROOT);
        return AgentToolSupport.containsAny(lower,
                "vector组件", "vector 组件", "创建组件", "生成组件", "解析组件", "生成正则",
                "生成remap", "remap", "sink", "建表", "入库", "日志样本");
    }

    /**
     * 返回 VECTOR_COMPONENT_PLAN 意图决策。
     */
    @Override
    public AgentIntentDecision match(AgentRuntimeContext context) {
        return new AgentIntentDecision(AgentIntent.VECTOR_COMPONENT_PLAN, context.getEffectiveMessage(), null, null, true);
    }
}
