package cn.mw.loganalysis.vector.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Vector配置文件实体
 */
@Data
@TableName("vector_configs")
public class VectorConfig {

    /**
     * 主键ID
     */
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
     * 配置内容(YAML/TOML)
     */
    private String content;

    /**
     * 版本号
     */
    private Integer version;

    /**
     * 是否为模板
     */
    private Boolean isTemplate;

    /**
     * 父配置ID（用于派生）
     */
    private String parentConfigId;

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
