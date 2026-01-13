package cn.mw.loganalysis.vector.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

/**
 * VRL 表达式执行请求
 */
@Data
public class VrlExecuteRequest {
    
    /**
     * 日志样本
     */
    @NotBlank(message = "日志样本不能为空")
    private String logSample;
    
    /**
     * 解析方式: parse_json, parse_syslog, parse_regex, parse_kv, parse_grok
     */
    @NotBlank(message = "解析方式不能为空")
    private String parseMethod;
    
    /**
     * 正则表达式（parse_regex 时必填）
     */
    private String regexPattern;
    
    /**
     * Grok 模式（parse_grok 时必填）
     */
    private String grokPattern;
    
    /**
     * 自定义 VRL 脚本（custom 时使用）
     */
    private String customVrl;
}
