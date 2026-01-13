package cn.mw.loganalysis.config.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 配置历史实体类
 */
@Data
@TableName("config_history")
public class ConfigHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String configKey;

    private String oldValue;

    private String newValue;

    private Long changedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime changedAt;
}
