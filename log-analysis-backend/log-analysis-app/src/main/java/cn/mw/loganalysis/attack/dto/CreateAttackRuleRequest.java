package cn.mw.loganalysis.attack.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateAttackRuleRequest {

    @NotBlank(message = "规则ID不能为空")
    private String ruleId;

    @NotBlank(message = "规则名称不能为空")
    private String name;

    private String description;

    @NotBlank(message = "攻击类型不能为空")
    private String attackType;

    private String attackSubType;

    private String severity;

    private BigDecimal confidence;

    private List<String> requiredFields;

    private List<String> datasourceTypes;

    private List<String> messagePatterns;

    private List<String> rawPatterns;

    private List<String> keywords;

    private String reasonTemplate;

    private String mitreTactic;

    private String mitreTechnique;

    private Boolean enabled;

    private Integer priority;
}
