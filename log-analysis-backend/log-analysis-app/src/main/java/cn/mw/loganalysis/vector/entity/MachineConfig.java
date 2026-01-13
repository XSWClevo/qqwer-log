package cn.mw.loganalysis.vector.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 机器与配置的部署关系
 * 支持一台机器部署多个配置（多个 pipeline）
 */
@Data
@TableName("vector_machine_configs")
public class MachineConfig {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 机器ID
     */
    private String machineId;

    /**
     * 配置ID
     */
    private String configId;

    /**
     * 部署状态: pending, deployed, failed
     */
    private String status;

    /**
     * 已部署的版本
     */
    private String deployedVersion;

    /**
     * 部署时间
     */
    private LocalDateTime deployedAt;

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
}
