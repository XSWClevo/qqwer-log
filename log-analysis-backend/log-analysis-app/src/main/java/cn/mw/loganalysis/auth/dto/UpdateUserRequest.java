package cn.mw.loganalysis.auth.dto;

import jakarta.validation.constraints.Email;
import lombok.Data;

/**
 * 更新用户请求DTO
 */
@Data
public class UpdateUserRequest {

    @Email(message = "邮箱格式不正确")
    private String email;

    private String fullName;

    private String role;

    private Boolean enabled;
}
