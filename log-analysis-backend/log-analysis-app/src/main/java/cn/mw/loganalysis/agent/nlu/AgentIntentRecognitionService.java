package cn.mw.loganalysis.agent.nlu;

import cn.mw.loganalysis.agent.execution.AgentRuntimeContext;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 意图识别策略编排器。
 */
@Component
public class AgentIntentRecognitionService {

    private final List<AgentIntentMatcher> matchers;

    /**
     * 收集所有意图匹配策略并按 Ordered 顺序排序。
     */
    public AgentIntentRecognitionService(List<AgentIntentMatcher> matchers) {
        this.matchers = new ArrayList<>(matchers);
        AnnotationAwareOrderComparator.sort(this.matchers);
    }

    /**
     * 按优先级匹配规则意图，并把决策结果写回运行时上下文。
     */
    public AgentIntentDecision recognize(AgentRuntimeContext context) {
        AgentIntentDecision decision = decide(context);
        apply(context, decision);
        return decision;
    }

    /**
     * 只计算规则意图决策，不修改运行时上下文。
     */
    AgentIntentDecision decide(AgentRuntimeContext context) {
        for (AgentIntentMatcher matcher : matchers) {
            if (matcher.matches(context)) {
                return matcher.match(context);
            }
        }
        throw new IllegalStateException("未找到可用的意图匹配策略");
    }

    /**
     * 将意图决策写回运行时上下文。
     */
    void apply(AgentRuntimeContext context, AgentIntentDecision decision) {
        context.setIntent(decision.intent());
        context.setEffectiveMessage(decision.effectiveMessage());
        context.setKeyword(decision.keyword());
        context.setSeverity(decision.severity());
        context.setDeterministicToolRequest(decision.deterministicToolRequest());
    }
}
