package cn.mw.loganalysis.agent.nlu;

public record AgentIntentDecision(AgentIntent intent,
                           String effectiveMessage,
                           String keyword,
                           String severity,
                           boolean deterministicToolRequest) {
}
