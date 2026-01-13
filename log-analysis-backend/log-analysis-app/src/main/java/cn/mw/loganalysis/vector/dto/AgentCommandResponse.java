package cn.mw.loganalysis.vector.dto;

import lombok.Data;

/**
 * Agent 命令响应
 */
@Data
public class AgentCommandResponse {
    private String commandId;
    private String commandType;  // start_vector, stop_vector, restart_vector, reload_vector, upgrade_agent, upgrade_vector
    
    // 升级命令专用字段
    private String targetVersion;    // 目标版本
    private String downloadUrl;      // 下载地址
    private String checksum;         // SHA256 校验和
    private Long fileSize;           // 文件大小
}
