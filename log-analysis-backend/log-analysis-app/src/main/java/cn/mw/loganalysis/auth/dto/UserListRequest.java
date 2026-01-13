package cn.mw.loganalysis.auth.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 用户列表查询请求DTO
 */
@Data
public class UserListRequest {

    @Min(value = 1, message = "页码必须大于0")
    private Integer pageNum;

    @Min(value = 1, message = "每页条数必须大于0")
    private Integer pageSize;

    private String role;
}
