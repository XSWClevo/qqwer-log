package cn.mw.loganalysis.extraction.dto;

import lombok.Data;

import java.util.Map;

/**
 * 更新提取规则请求DTO
 */
@Data
public class UpdateExtractionRuleRequest {

    private String name;

    private String description;

    private String ruleType;

    private String pattern;

    private Map<String, String> fieldMappings;

    private Integer priority;

    private Boolean enabled;
}
