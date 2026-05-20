package cn.mw.loganalysis.vector.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 部署配置请求DTO
 */
@Data
public class DeployConfigRequest {

    /**
     * 目标机器ID列表
     */
    @NotEmpty(message = "目标机器不能为空")
    private List<String> hostIds;

    /**
     * 配置ID
     */
    @NotBlank(message = "配置ID不能为空")
    private String configId;

    /**
     * 部署方式: restart/reload
     */
    private String deployMode = "restart";
}
