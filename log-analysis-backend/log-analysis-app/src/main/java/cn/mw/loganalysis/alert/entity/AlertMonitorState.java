package cn.mw.loganalysis.alert.entity;

import cn.mw.loganalysis.common.handler.PostgresJsonbTypeHandler;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Monitor 当前状态，按规则、数据源、表和分组维度维护。
 */
@Data
@TableName(value = "alert_monitor_states", autoResultMap = true)
public class AlertMonitorState implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ruleId;

    private String datasourceId;

    private String tableName;

    private String groupKey;

    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private Map<String, Object> groupValues;

    private String state;

    private String previousState;

    private BigDecimal lastValue;

    private BigDecimal lastThreshold;

    private LocalDateTime lastEvaluatedAt;

    private LocalDateTime lastStateChangedAt;

    private LocalDateTime lastNotifiedAt;

    private Integer renotifyCount;

    private LocalDateTime noDataSince;

    private LocalDateTime updatedAt;
}
