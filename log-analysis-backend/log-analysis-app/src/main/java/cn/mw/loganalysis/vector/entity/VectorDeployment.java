package cn.mw.loganalysis.vector.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Vector配置部署记录实体
 */
@Data
@TableName("vector_deployments")
public class VectorDeployment {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 机器ID
     */
    private String machineId;

    /**
     * 配置ID（可视化配置ID或普通配置ID）
     */
    private String configId;

    /**
     * 配置版本号
     */
    private String configVersion;

    /**
     * 配置内容（YAML）
     */
    private String configContent;

    /**
     * 部署方式: restart/reload
     */
    private String deployMode;

    /**
     * 部署状态: pending/deploying/success/failed
     */
    private String status;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 部署开始时间
     */
    private LocalDateTime startedAt;

    /**
     * 部署完成时间
     */
    private LocalDateTime finishedAt;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 创建人ID
     */
    private String createdBy;
}
