package cn.mw.loganalysis.vector.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Vector配置组件模板
 */
@Data
@TableName("vector_config_components")
public class ConfigComponent {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 组件名称
     */
    private String name;

    /**
     * 组件类型: source/transform/sink
     */
    private String componentType;

    /**
     * Vector类型（如 file, kafka, remap）
     */
    private String vectorType;

    /**
     * 配置YAML模板
     */
    private String configYaml;

    /**
     * 可视化配置数据（JSON格式）
     */
    private String visualData;

    /**
     * 组件描述
     */
    private String description;

    /**
     * 是否为模板（系统内置）
     */
    private Boolean isTemplate;

    /**
     * 是否可作为查询数据源（仅 Sink 组件有效）
     */
    private Boolean queryable;

    /**
     * 数据源显示名称（用于日志搜索页面展示）
     */
    private String displayName;

    /**
     * 关联的数据源ID（仅 Sinks 组件使用）
     */
    private String datasourceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private String createdBy;
}
