package cn.mw.loganalysis.auth.security;

import cn.mw.loganalysis.common.constants.AuthConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 会话活跃超时过滤器
 * <p>
 * 在 JWT 认证通过后，检查用户最后活跃时间是否超过配置的超时阈值。
 * 如果超时则返回 440 状态码，前端据此跳转到登录页。
 * 每次请求通过时会刷新最后活跃时间。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionActivityFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${session.activity-timeout-minutes:30}")
    private long activityTimeoutMinutes;

    private static final String LAST_ACTIVITY_FIELD = "lastActivity";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 仅对已认证的请求进行活跃检查
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Long userId) {

            String sessionKey = AuthConstants.SESSION_KEY_PREFIX + userId;

            // 获取最后活跃时间
            Object lastActivityObj = redisTemplate.opsForHash().get(sessionKey, LAST_ACTIVITY_FIELD);

            if (lastActivityObj != null) {
                long lastActivity = Long.parseLong(lastActivityObj.toString());
                long now = System.currentTimeMillis();
                long elapsedMinutes = (now - lastActivity) / (1000 * 60);

                if (elapsedMinutes >= activityTimeoutMinutes) {
                    log.info("Session activity timeout for user {}, last activity was {} minutes ago",
                            userId, elapsedMinutes);

                    // 清除安全上下文
                    SecurityContextHolder.clearContext();

                    // 返回 440 状态码（自定义：会话超时）
                    response.setStatus(440);
                    response.setContentType("application/json;charset=UTF-8");
                    Map<String, Object> body = Map.of(
                            "code", 440,
                            "message", "会话已过期，请重新登录"
                    );
                    response.getWriter().write(objectMapper.writeValueAsString(body));
                    return;
                }
            }

            // 刷新最后活跃时间
            redisTemplate.opsForHash().put(sessionKey, LAST_ACTIVITY_FIELD, System.currentTimeMillis());
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        // 登录、注册等公开接口不需要活跃检查
        return path.startsWith("/api/auth/login")
                || path.startsWith("/api/auth/register")
                || path.equals("/api/session/heartbeat");
    }
}
