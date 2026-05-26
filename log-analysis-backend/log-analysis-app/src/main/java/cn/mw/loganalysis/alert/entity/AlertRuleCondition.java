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
import java.util.Map;

/**
 * 告警规则查询与聚合条件。
 */
@Data
@TableName(value = "alert_rule_conditions", autoResultMap = true)
public class AlertRuleCondition implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ruleId;

    private String query;

    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private Map<String, Object> filters;

    private String aggregateFunction;

    private String aggregateField;

    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private List<String> groupBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
