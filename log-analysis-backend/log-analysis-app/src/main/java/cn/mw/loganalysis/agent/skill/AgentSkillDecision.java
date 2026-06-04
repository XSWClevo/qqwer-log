package cn.mw.loganalysis.agent.skill;

import cn.mw.loganalysis.agent.nlu.AgentIntent;
import cn.mw.loganalysis.agent.nlu.AgentIntentDecision;

import java.util.List;

/**
 * 技能匹配结果。
 */
public record AgentSkillDecision(
        String skillId,
        String displayName,
        AgentIntent targetIntent,
        int score,
        boolean clarificationRequired,
        String clarificationMessage,
        List<String> suggestions,
        AgentIntentDecision intentDecision
) {

    /**
     * 空匹配。
     */
    public static AgentSkillDecision none() {
        return new AgentSkillDecision(null, null, null, 0, false, null, List.of(), null);
    }

    /**
     * 命中可执行技能。
     */
    public static AgentSkillDecision intent(AgentSkillDefinition definition,
                                            int score,
                                            String effectiveMessage) {
        return new AgentSkillDecision(
                definition.id(),
                definition.displayName(),
                definition.targetIntent(),
                score,
                false,
                null,
                List.of(),
                new AgentIntentDecision(
                        definition.targetIntent(),
                        effectiveMessage,
                        null,
                        null,
                        definition.deterministicToolRequest()
                )
        );
    }

    /**
     * 命中澄清分支。
     */
    public static AgentSkillDecision clarification(String message, List<String> suggestions) {
        return new AgentSkillDecision(null, null, null, 0, true, message, suggestions, null);
    }

    /**
     * 是否有可直接应用的内部意图。
     */
    public boolean hasIntentDecision() {
        return intentDecision != null;
    }

    /**
     * 是否需要先向用户澄清。
     */
    public boolean requiresClarification() {
        return clarificationRequired;
    }
}
