package cn.mw.loganalysis.vector.dto;

import lombok.Data;

/**
 * 升级命令响应（给 Agent）
 */
@Data
public class UpgradeCommandResponse {
    private String commandId;
    private String commandType;      // upgrade_agent, upgrade_vector
    private String targetVersion;    // 目标版本
    private String downloadUrl;      // 下载地址
    private String checksum;         // SHA256 校验和
    private Long fileSize;           // 文件大小
}
