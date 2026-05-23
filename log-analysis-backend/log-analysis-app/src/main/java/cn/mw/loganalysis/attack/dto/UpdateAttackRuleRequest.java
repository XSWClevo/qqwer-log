package cn.mw.loganalysis.attack.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class UpdateAttackRuleRequest {

    private String name;

    private String description;

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
