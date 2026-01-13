package cn.mw.loganalysis.config.dto;

import lombok.Data;

/**
 * 系统配置 DTO
 */
@Data
public class SystemConfigDTO {

    /**
     * 配置键
     */
    private String configKey;

    /**
     * 配置值
     */
    private String configValue;

    /**
     * 配置描述
     */
    private String description;
}
