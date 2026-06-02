package cn.mw.loganalysis.agent.support;

import java.time.LocalDateTime;

public record AgentTimeWindow(LocalDateTime start, LocalDateTime end, String label) {
}
