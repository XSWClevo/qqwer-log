package cn.mw.loganalysis.agent.nlu;

import cn.mw.loganalysis.agent.execution.AgentRuntimeContext;
import org.springframework.core.Ordered;

/**
 * 意图匹配策略。
 */
public interface AgentIntentMatcher extends Ordered {

    /**
     * 判断当前上下文是否命中该意图策略。
     */
    boolean matches(AgentRuntimeContext context);

    /**
     * 构造命中的意图决策结果。
     */
    AgentIntentDecision match(AgentRuntimeContext context);

    /**
     * 返回意图匹配优先级，数值越小越先匹配。
     */
    @Override
    default int getOrder() {
        return 0;
    }
}
