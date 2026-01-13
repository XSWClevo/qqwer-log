package cn.mw.loganalysis.vector.dto;

import lombok.Data;

import java.util.Map;

/**
 * Agent 配置响应
 */
@Data
public class AgentConfigResponse {
    private String deploymentId;
    private String version;
    private String yamlContent;           // 兼容旧模式（单文件）
    private Map<String, String> configFiles;  // config-dir 模式（多文件）
    private String deployMode;            // restart, reload
}
