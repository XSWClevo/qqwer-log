package cn.mw.loganalysis.extraction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 创建提取规则请求DTO
 */
@Data
public class CreateExtractionRuleRequest {

    @NotBlank(message = "规则名称不能为空")
    private String name;

    private String description;

    @NotBlank(message = "规则类型不能为空")
    private String ruleType;

    @NotBlank(message = "匹配模式不能为空")
    private String pattern;

    @NotNull(message = "字段映射不能为空")
    private Map<String, String> fieldMappings;

    private Integer priority = 0;

    private Boolean enabled = true;
}
