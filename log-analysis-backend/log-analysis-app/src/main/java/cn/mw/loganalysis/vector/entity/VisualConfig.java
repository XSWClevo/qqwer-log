package cn.mw.loganalysis.vector.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 可视化配置实体
 */
@Data
@TableName("vector_visual_configs")
public class VisualConfig {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 配置名称
     */
    private String name;

    /**
     * 配置描述
     */
    private String description;

    /**
     * 配置格式: namespace_yaml, yaml, toml
     */
    private String format;

    /**
     * 流程图数据 (JSON字符串)
     */
    @TableField("flow_data")
    private String graphData;

    /**
     * 生成的配置内容 (YAML/TOML)
     */
    private String content;

    /**
     * 节点数量
     */
    private Integer nodeCount;

    /**
     * 管道名称（用于 config-dir 目录名）
     */
    private String pipelineName;

    /**
     * 部署模式: pipeline（独立管道）, shared（使用共享组件）
     */
    private String deployMode;

    /**
     * 是否激活（部署时是否包含）
     */
    private Boolean isActive;

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
