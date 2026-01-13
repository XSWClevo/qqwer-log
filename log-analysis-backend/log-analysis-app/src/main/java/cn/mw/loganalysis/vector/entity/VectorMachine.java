package cn.mw.loganalysis.vector.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Vector日志收集器部署的机器实体
 */
@Data
@TableName("vector_machines")
public class VectorMachine {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 机器名称
     */
    private String name;

    /**
     * 主机名
     */
    private String hostname;

    /**
     * IP地址
     */
    private String ipAddress;

    /**
     * SSH端口
     */
    private Integer sshPort;

    /**
     * SSH用户
     */
    private String sshUser;

    /**
     * SSH密钥路径
     */
    private String sshKeyPath;

    /**
     * Agent Token（用于 Agent 认证）
     */
    private String agentToken;

    /**
     * 操作系统类型
     */
    private String osType;

    /**
     * 状态: online/offline/error
     */
    private String status;

    /**
     * Vector版本
     */
    private String vectorVersion;

    /**
     * Agent版本
     */
    private String agentVersion;

    /**
     * Vector安装路径
     */
    private String vectorInstallPath;

    /**
     * Vector配置文件路径
     */
    private String vectorConfigPath;

    /**
     * 管理方式: systemctl/binary
     */
    private String managementMethod;

    /**
     * 最后心跳时间
     */
    private LocalDateTime lastHeartbeat;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 创建人ID
     */
    private String createdBy;
}
