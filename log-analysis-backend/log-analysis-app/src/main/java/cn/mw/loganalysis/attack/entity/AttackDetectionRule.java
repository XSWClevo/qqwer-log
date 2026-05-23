package cn.mw.loganalysis.attack.entity;

import cn.mw.loganalysis.common.handler.PostgresJsonbTypeHandler;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 攻击检测规则。
 * 规则基于标准字段定义，不直接绑定具体日志表字段。
 */
@Data
@TableName(value = "attack_detection_rules", autoResultMap = true)
public class AttackDetectionRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleId;

    private String name;

    private String description;

    private String attackType;

    private String attackSubType;

    private String severity;

    private BigDecimal confidence;

    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private List<String> requiredFields;

    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private List<String> datasourceTypes;

    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private List<String> messagePatterns;

    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private List<String> rawPatterns;

    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private List<String> keywords;

    private String reasonTemplate;

    private String mitreTactic;

    private String mitreTechnique;

    private Boolean enabled;

    private Integer priority;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
