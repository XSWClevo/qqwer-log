package cn.mw.loganalysis.auth.controller;

import cn.mw.loganalysis.common.constants.AuthConstants;
import cn.mw.loganalysis.common.response.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话管理控制器
 */
@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
public class SessionController {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 心跳接口 - 前端定时调用以刷新会话活跃时间
     */
    @PostMapping("/heartbeat")
    public Result<Void> heartbeat(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            String sessionKey = AuthConstants.SESSION_KEY_PREFIX + userId;
            redisTemplate.opsForHash().put(sessionKey, "lastActivity", System.currentTimeMillis());
        }
        return Result.success();
    }
}
