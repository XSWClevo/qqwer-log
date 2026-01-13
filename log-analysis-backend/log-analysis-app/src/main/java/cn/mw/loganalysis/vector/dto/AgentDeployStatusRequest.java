package cn.mw.loganalysis.vector.dto;

import lombok.Data;

/**
 * Agent 部署状态上报请求
 */
@Data
public class AgentDeployStatusRequest {
    private String deploymentId;
    private String configVersion;
    private String status; // success, failed
    private String errorMessage;
}
