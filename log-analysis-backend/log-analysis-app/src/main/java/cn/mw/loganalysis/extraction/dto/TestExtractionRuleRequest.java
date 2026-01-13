package cn.mw.loganalysis.extraction.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 测试提取规则请求DTO
 */
@Data
public class TestExtractionRuleRequest {

    @NotBlank(message = "规则类型不能为空")
    private String ruleType;

    @NotBlank(message = "匹配模式不能为空")
    private String pattern;

    @NotBlank(message = "测试日志不能为空")
    private String testLog;
}
