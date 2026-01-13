package cn.mw.loganalysis.vector.dto;

import lombok.Data;

/**
 * Agent 注册请求
 */
@Data
public class AgentRegisterRequest {
    private String hostname;
    private String ipAddress;
    private String agentVersion;
    private String vectorVersion;
    private String osType;
    private String osVersion;
    private Integer cpuCores;
    private Long totalMemoryMb;
}
