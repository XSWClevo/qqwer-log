package cn.mw.loganalysis.vector.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建可视化配置请求
 */
@Data
public class CreateVisualConfigRequest {

    @NotBlank(message = "配置名称不能为空")
    private String name;

    private String description;

    @NotBlank(message = "配置格式不能为空")
    private String format;
}
