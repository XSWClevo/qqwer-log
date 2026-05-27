package cn.mw.loganalysis.agent.service;

record AgentIntentDecision(AgentIntent intent,
                           String effectiveMessage,
                           String keyword,
                           String severity,
                           boolean deterministicToolRequest) {
}
