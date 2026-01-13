package cn.mw.loganalysis.vector.dto;

import lombok.Data;

/**
 * Agent 心跳请求
 */
@Data
public class AgentHeartbeatRequest {
    private Long agentUptime;
    private Boolean vectorRunning;
    private String status; // online, offline, error
}
