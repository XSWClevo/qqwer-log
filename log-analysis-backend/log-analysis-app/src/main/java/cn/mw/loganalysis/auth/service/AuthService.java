package cn.mw.loganalysis.auth.service;

import cn.mw.loganalysis.auth.dto.LoginRequest;
import cn.mw.loganalysis.auth.dto.LoginResponse;
import cn.mw.loganalysis.auth.entity.User;
import cn.mw.loganalysis.auth.mapper.UserMapper;
import cn.mw.loganalysis.auth.security.JwtTokenProvider;
import cn.mw.loganalysis.common.constants.AuthConstants;
import cn.mw.loganalysis.common.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 认证服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, Object> redisTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 用户登录
     */
    public LoginResponse login(LoginRequest request) {
        // 使用 Mapper 的 default 方法查询用户
        User user = userMapper.selectByUsername(request.getUsername());

        if (user == null) {
            throw new UnauthorizedException("用户名或密码错误");
        }

        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("用户名或密码错误");
        }

        // 检查用户是否启用
        if (!user.getEnabled()) {
            throw new UnauthorizedException("用户已被禁用");
        }

        // 生成令牌
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        // 存储会话到Redis
        storeSession(user.getId(), accessToken, refreshToken);

        // 构建响应
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole()
        );

        return new LoginResponse(
                accessToken,
                refreshToken,
                AuthConstants.TOKEN_TYPE_BEARER,
                jwtTokenProvider.getExpirationTime(),
                userInfo
        );
    }


    /**
     * 刷新令牌
     */
    public LoginResponse refreshToken(String refreshToken) {
        // 验证刷新令牌
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new UnauthorizedException("刷新令牌无效或已过期");
        }

        // 获取用户ID
        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);

        // 查询用户
        User user = userMapper.selectById(userId);
        if (user == null || !user.getEnabled()) {
            throw new UnauthorizedException("用户不存在或已被禁用");
        }

        // 生成新的访问令牌
        String newAccessToken = jwtTokenProvider.generateAccessToken(user);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user);

        // 更新会话
        storeSession(user.getId(), newAccessToken, newRefreshToken);

        // 构建响应
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole()
        );

        return new LoginResponse(
                newAccessToken,
                newRefreshToken,
                AuthConstants.TOKEN_TYPE_BEARER,
                jwtTokenProvider.getExpirationTime(),
                userInfo
        );
    }

    /**
     * 登出
     */
    public void logout(Long userId) {
        String sessionKey = AuthConstants.SESSION_KEY_PREFIX + userId;
        redisTemplate.delete(sessionKey);
        log.info("User {} logged out", userId);
    }

    /**
     * 获取用户信息
     */
    public User getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new UnauthorizedException("用户不存在");
        }
        return user;
    }

    /**
     * 存储会话到Redis
     */
    private void storeSession(Long userId, String accessToken, String refreshToken) {
        String sessionKey = AuthConstants.SESSION_KEY_PREFIX + userId;

        // 存储会话信息
        redisTemplate.opsForHash().put(sessionKey, "accessToken", accessToken);
        redisTemplate.opsForHash().put(sessionKey, "refreshToken", refreshToken);
        redisTemplate.opsForHash().put(sessionKey, "loginTime", System.currentTimeMillis());

        // 设置过期时间
        redisTemplate.expire(sessionKey, AuthConstants.SESSION_TTL_DAYS, TimeUnit.DAYS);

        log.info("Session stored for user {}", userId);
    }
}
