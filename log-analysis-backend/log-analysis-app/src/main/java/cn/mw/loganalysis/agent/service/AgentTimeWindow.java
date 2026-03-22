package cn.mw.loganalysis.agent.service;

import java.time.LocalDateTime;

record AgentTimeWindow(LocalDateTime start, LocalDateTime end, String label) {
}
