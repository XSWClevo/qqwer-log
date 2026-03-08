package cn.mw.loganalysis.auth.controller;

import cn.mw.loganalysis.auth.dto.LoginRequest;
import cn.mw.loganalysis.auth.dto.LoginResponse;
import cn.mw.loganalysis.auth.dto.RefreshTokenRequest;
import cn.mw.loganalysis.auth.entity.User;
import cn.mw.loganalysis.auth.service.AuthService;
import cn.mw.loganalysis.common.exception.UnauthorizedException;
import cn.mw.loganalysis.common.response.Result;
import cn.mw.loganalysis.operationlog.annotation.OperationLog;
import cn.mw.loganalysis.operationlog.enums.OperationAction;
import cn.mw.loganalysis.operationlog.enums.OperationModule;
import cn.mw.loganalysis.operationlog.enums.OperationType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    @OperationLog(
        module = OperationModule.AUTH,
        operationType = OperationType.LOGIN,
        action = OperationAction.USER_LOGIN,
        resourceType = "User",
        resourceIdSpEL = "#result.data.userInfo.id",
        sensitiveFields = {"password"}
    )
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt for user: {}", request.getUsername());
        LoginResponse response = authService.login(request);
        return Result.success(response);
    }

    /**
     * 刷新令牌
     */
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("Token refresh request");
        LoginResponse response = authService.refreshToken(request.getRefreshToken());
        return Result.success(response);
    }

    /**
     * 用户登出
     */
    @PostMapping("/logout")
    @OperationLog(
        module = OperationModule.AUTH,
        operationType = OperationType.LOGOUT,
        action = OperationAction.USER_LOGOUT,
        resourceType = "User",
        resourceIdSpEL = "#userId != null ? #userId : (#authentication != null ? #authentication.principal : null)"
    )
    public Result<Void> logout(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            Authentication authentication) {
        Long currentUserId = resolveUserId(authentication, userId);
        log.info("Logout request for user: {}", currentUserId);
        authService.logout(currentUserId);
        return Result.success();
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/user/info")
    public Result<User> getUserInfo(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            Authentication authentication) {
        User user = authService.getUserInfo(resolveUserId(authentication, userId));
        return Result.success(user);
    }

    private Long resolveUserId(Authentication authentication, Long headerUserId) {
        if (authentication != null) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof Long userId) {
                return userId;
            }
        }
        if (headerUserId != null) {
            return headerUserId;
        }
        throw new UnauthorizedException("未获取到当前登录用户信息");
    }
}
