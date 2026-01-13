package cn.mw.loganalysis.vector.dto;

import lombok.Data;

/**
 * Agent 注册响应
 */
@Data
public class AgentRegisterResponse {
    private String hostId;
    private String token;
}
