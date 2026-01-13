package cn.mw.loganalysis.vector.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 配置与共享组件的引用关系
 */
@Data
@TableName("vector_config_component_refs")
public class ConfigComponentRef {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 配置ID（可视化配置）
     */
    private String configId;

    /**
     * 共享组件ID
     */
    private String sharedComponentId;

    /**
     * 引用类型: input（作为输入源）, output（作为输出目标）
     */
    private String refType;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
