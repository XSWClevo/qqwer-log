package cn.mw.loganalysis.alert.entity;

import cn.mw.loganalysis.common.handler.PostgresJsonbTypeHandler;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 告警事件实体类
 */
@Data
@TableName(value = "alert_events", autoResultMap = true)
public class AlertEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ruleId;

    private String ruleName;

    private String severity;

    private String state;

    private String previousState;

    private String thresholdLevel;

    private String message;

    /**
     * 触发告警的日志数据
     */
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private Map<String, Object> logData;

    private Long evaluationRunId;

    private LocalDateTime triggeredAt;

    private Boolean acknowledged;

    private Long acknowledgedBy;

    private LocalDateTime acknowledgedAt;
}
