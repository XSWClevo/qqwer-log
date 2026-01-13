package cn.mw.loganalysis.vector.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

/**
 * 发送命令请求
 */
@Data
public class SendCommandRequest {
    @NotBlank(message = "机器ID不能为空")
    private String machineId;
    
    @NotBlank(message = "命令类型不能为空")
    private String commandType;  // start_vector, stop_vector, restart_vector, reload_vector
}
