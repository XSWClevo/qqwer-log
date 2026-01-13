package cn.mw.loganalysis.config.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新配置请求DTO
 */
@Data
public class UpdateConfigRequest {

    @NotBlank(message = "配置值不能为空")
    private String configValue;

    private String description;
}
