package cn.mw.loganalysis.vector.dto;

import lombok.Data;

/**
 * Agent 命令状态上报请求
 */
@Data
public class AgentCommandStatusRequest {
    private String commandId;
    private String status;  // executing, success, failed
    private String errorMessage;
}
