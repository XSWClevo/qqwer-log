package cn.mw.loganalysis.logsource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 信任日志源请求
 *
 * @author Claude
 * @since 2026-01-23
 */
@Data
public class TrustLogSourceRequest {

    /**
     * 日志源IP地址
     */
    @NotBlank(message = "IP地址不能为空")
    @Pattern(regexp = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$",
            message = "IP地址格式不正确")
    private String sourceIp;

    /**
     * 主机名（可选）
     */
    private String hostname;

    /**
     * 描述信息
     */
    private String description;

    /**
     * 备注
     */
    private String remark;
}
