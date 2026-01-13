package cn.mw.loganalysis.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String accessToken;
    
    private String refreshToken;
    
    private String tokenType = "Bearer";
    
    private Long expiresIn;
    
    private UserInfo userInfo;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private Long id;
        private String username;
        private String email;
        private String fullName;
        private String role;
    }
}
