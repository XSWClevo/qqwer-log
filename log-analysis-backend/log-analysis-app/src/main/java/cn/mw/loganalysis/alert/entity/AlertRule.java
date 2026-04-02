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
     * 规则类型: aggregation, anomaly
     */
    private String ruleType;

    /**
     * 作用范围: all, category, datasource, table
     */
    private String scopeType;

    /**
     * 日志分类编码列表
     */
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private List<String> categoryCodes;

    /**
     * 数据源 ID 列表
     */
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private List<String> datasourceIds;

    /**
     * 表名列表
     */
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private List<String> tableNames;

    /**
     * 告警条件（JSON 格式）
     */
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private Map<String, Object> condition;

    /**
     * 评估频率
     */
    private String evalEvery;

    /**
     * 连续命中次数
     */
    private Integer consecutiveHits;

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
     * 去重字段列表
     */
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private List<String> dedupKeyFields;

    /**
     * 告警消息模板
     */
    private String messageTemplate;

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
