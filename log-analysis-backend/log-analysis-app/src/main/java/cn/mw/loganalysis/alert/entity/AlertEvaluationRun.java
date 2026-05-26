package cn.mw.loganalysis.alert.entity;

import cn.mw.loganalysis.common.handler.PostgresJsonbTypeHandler;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 告警规则单次评估运行记录。
 */
@Data
@TableName(value = "alert_evaluation_runs", autoResultMap = true)
public class AlertEvaluationRun implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ruleId;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime windowStart;

    private LocalDateTime windowEnd;

    private String status;

    private Integer matchedCount;

    private String errorMessage;

    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private Map<String, Object> details;
}
