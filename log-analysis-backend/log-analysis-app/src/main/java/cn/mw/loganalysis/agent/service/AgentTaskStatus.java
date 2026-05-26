package cn.mw.loganalysis.agent.service;

enum AgentTaskStatus {
    INTENT_DETECTED,
    SLOT_FILLING,
    READY_TO_PREVIEW,
    PREVIEW_GENERATED,
    WAITING_CONFIRM,
    COMMITTED,
    FAILED
}
