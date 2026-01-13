package cn.mw.loganalysis.vector.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 添加配置请求DTO
 */
@Data
public class AddConfigRequest {

    /**
     * 配置名称
     */
    @NotBlank(message = "配置名称不能为空")
    private String name;

    /**
     * 配置描述
     */
    private String description;

    /**
     * 配置内容(YAML/TOML)
     */
    @NotBlank(message = "配置内容不能为空")
    private String content;

    /**
     * 是否为模板
     */
    private Boolean isTemplate;

    /**
     * 父配置ID（用于派生）
     */
    private String parentConfigId;
}
