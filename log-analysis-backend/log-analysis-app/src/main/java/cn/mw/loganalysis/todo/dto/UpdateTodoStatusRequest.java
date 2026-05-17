package cn.mw.loganalysis.todo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新待办状态请求
 */
@Data
public class UpdateTodoStatusRequest {

    @NotBlank(message = "状态不能为空")
    private String status;
}
