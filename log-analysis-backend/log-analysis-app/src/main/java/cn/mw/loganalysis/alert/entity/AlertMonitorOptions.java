package cn.mw.loganalysis.alert.entity;

import cn.mw.loganalysis.common.handler.PostgresJsonbTypeHandler;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Datadog Monitor 风格的评估与通知选项。
 */
@Data
@TableName(value = "alert_monitor_options", autoResultMap = true)
public class AlertMonitorOptions implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ruleId;

    private Boolean notifyNoData;

    private String noDataTimeframe;

    private Boolean requireFullWindow;

    private Integer evaluationDelaySeconds;

    private Integer newGroupDelaySeconds;

    private Integer renotifyIntervalMinutes;

    private Integer renotifyOccurrences;

    private Boolean includeTags;

    private String priority;

    private String team;

    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private List<String> tags;

    private String alertMode;

    private String escalationMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
