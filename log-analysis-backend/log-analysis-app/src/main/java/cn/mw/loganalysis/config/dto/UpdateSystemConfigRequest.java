package cn.mw.loganalysis.config.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * 更新系统配置请求
 */
@Data
public class UpdateSystemConfigRequest {

    /**
     * 配置类型（clickhouse/postgresql/elasticsearch）
     */
    @NotBlank(message = "配置类型不能为空")
    private String configType;

    /**
     * 配置项（key-value 对）
     */
    private Map<String, String> configs;
}
