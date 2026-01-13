package cn.mw.loganalysis.vector.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 服务操作请求DTO
 */
@Data
public class ServiceOperationRequest {

    /**
     * 机器ID
     */
    @NotBlank(message = "机器ID不能为空")
    private String machineId;

    /**
     * 操作类型: start/stop/restart/reload/status
     */
    @NotBlank(message = "操作类型不能为空")
    @Pattern(regexp = "^(start|stop|restart|reload|status)$", message = "操作类型必须为 start/stop/restart/reload/status 之一")
    private String operationType;
}
