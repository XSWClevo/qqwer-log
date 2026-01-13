package cn.mw.loganalysis.extraction.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 日志提取规则实体类
 */
@Data
@TableName(value = "extraction_rules", autoResultMap = true)
public class ExtractionRule implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    /**
     * 规则类型：REGEX, GROK, JSON_PATH
     */
    private String ruleType;

    private String pattern;

    /**
     * 字段映射配置
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> fieldMappings;

    /**
     * 优先级（数字越大优先级越高）
     */
    private Integer priority;

    private Boolean enabled;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
