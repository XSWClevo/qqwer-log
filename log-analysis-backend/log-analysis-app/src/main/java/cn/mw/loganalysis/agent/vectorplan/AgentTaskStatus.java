package cn.mw.loganalysis.agent.vectorplan;

public enum AgentTaskStatus {
    INTENT_DETECTED,
    SLOT_FILLING,
    READY_TO_PREVIEW,
    PREVIEW_GENERATED,
    WAITING_CONFIRM,
    COMMITTED,
    FAILED
}
