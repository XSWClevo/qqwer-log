package cn.mw.loganalysis.vector.dto;

import lombok.Data;

/**
 * Agent 日志上报请求
 */
@Data
public class AgentLogRequest {
    private String level; // DEBUG, INFO, WARN, ERROR
    private String message;
    private String source; // agent, vector, system
}
