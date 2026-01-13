package cn.mw.loganalysis.vector.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 共享组件实体
 * 存储可被多个配置引用的组件（如共享的 syslog source）
 */
@Data
@TableName("vector_shared_components")
public class SharedComponent {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 组件名称
     */
    private String name;

    /**
     * 组件描述
     */
    private String description;

    /**
     * 组件类型: source, transform, sink
     */
    private String componentType;

    /**
     * Vector 类型: file, syslog, kafka, remap, clickhouse 等
     */
    private String vectorType;

    /**
     * 配置内容 (YAML)
     */
    private String configYaml;

    /**
     * 组件唯一标识（用于 Vector 配置中的组件名）
     */
    private String componentKey;

    /**
     * 是否激活
     */
    private Boolean isActive;

    /**
     * 创建人ID
     */
    private String createdBy;

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
