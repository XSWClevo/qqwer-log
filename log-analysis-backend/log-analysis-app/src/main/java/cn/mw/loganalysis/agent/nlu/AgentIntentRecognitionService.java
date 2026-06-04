package cn.mw.loganalysis.agent.nlu;

import cn.mw.loganalysis.agent.execution.AgentRuntimeContext;
import cn.mw.loganalysis.agent.skill.AgentSkillDecision;
import cn.mw.loganalysis.agent.skill.AgentSkillMatcher;
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
    private final AgentSkillMatcher skillMatcher;

    /**
     * 收集所有意图匹配策略并按 Ordered 顺序排序。
     */
    public AgentIntentRecognitionService(List<AgentIntentMatcher> matchers, AgentSkillMatcher skillMatcher) {
        this.matchers = new ArrayList<>(matchers);
        this.skillMatcher = skillMatcher;
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
        AgentSkillDecision skillDecision = recognizeSkill(context);
        if (skillDecision.hasIntentDecision()) {
            return skillDecision.intentDecision();
        }

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

    /**
     * 仅执行内置技能匹配，并把结果写入运行时上下文。
     */
    public AgentSkillDecision recognizeSkill(AgentRuntimeContext context) {
        AgentSkillDecision existing = context.getSkillDecision();
        if (existing != null) {
            return existing;
        }
        AgentSkillDecision decision = skillMatcher.match(context);
        context.setSkillDecision(decision);
        return decision;
    }
}
