package cn.mw.loganalysis.alert.entity;

import cn.mw.loganalysis.common.handler.PostgresJsonbTypeHandler;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 告警规则实体类
 */
@Data
@TableName(value = "alert_rules", autoResultMap = true)
public class AlertRule implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    /**
     * 告警条件（JSON格式）
     */
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private Map<String, Object> condition;

    /**
     * 告警级别: INFO, WARNING, ERROR, CRITICAL
     */
    private String severity;

    /**
     * 通知渠道列表
     */
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private List<String> notificationChannels;

    /**
     * 静默期（秒）
     */
    private Integer silencePeriod;

    private Boolean enabled;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
