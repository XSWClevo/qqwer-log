package cn.mw.loganalysis.auth.security;

import cn.mw.loganalysis.common.constants.AuthConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
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

    private static final String ACCESS_TOKEN_FIELD = "accessToken";
    private static final String LAST_ACTIVITY_FIELD = "lastActivity";
    private static final int SESSION_TIMEOUT_STATUS = 440;

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
            String accessToken = getAccessTokenFromRequest(request);

            Object storedAccessTokenObj = redisTemplate.opsForHash().get(sessionKey, ACCESS_TOKEN_FIELD);
            Object lastActivityObj = redisTemplate.opsForHash().get(sessionKey, LAST_ACTIVITY_FIELD);

            if (ObjectUtils.isEmpty(storedAccessTokenObj) || ObjectUtils.isEmpty(lastActivityObj)) {
                log.info("Session not found for authenticated user {}", userId);
                writeUnauthorized(response, "登录状态已失效，请重新登录");
                return;
            }

            if (!StringUtils.equals(accessToken, storedAccessTokenObj.toString())) {
                log.info("Session token mismatch for user {}", userId);
                writeUnauthorized(response, "登录状态已失效，请重新登录");
                return;
            }

            long lastActivity = parseLastActivity(lastActivityObj);
            long now = System.currentTimeMillis();
            long elapsedMinutes = (now - lastActivity) / (1000 * 60);

            if (lastActivity <= 0 || elapsedMinutes >= activityTimeoutMinutes) {
                log.info("Session activity timeout for user {}, last activity was {} minutes ago",
                        userId, elapsedMinutes);

                redisTemplate.delete(sessionKey);
                SecurityContextHolder.clearContext();

                // 返回 440 状态码（自定义：会话超时）
                response.setStatus(SESSION_TIMEOUT_STATUS);
                response.setContentType("application/json;charset=UTF-8");
                Map<String, Object> body = Map.of(
                        "code", SESSION_TIMEOUT_STATUS,
                        "message", "会话已过期，请重新登录"
                );
                response.getWriter().write(objectMapper.writeValueAsString(body));
                return;
            }

            // 刷新最后活跃时间
            redisTemplate.opsForHash().put(sessionKey, LAST_ACTIVITY_FIELD, now);
            redisTemplate.expire(sessionKey, activityTimeoutMinutes, TimeUnit.MINUTES);
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        return HttpMethod.OPTIONS.matches(method)
                || path.equals("/error")
                || StringUtils.startsWith(path, "/actuator/")
                || path.equals("/api/auth/login")
                || path.equals("/api/auth/refresh")
                || isPublicAgentEndpoint(method, path)
                || isPublicPackageEndpoint(method, path);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> body = Map.of(
                "code", HttpServletResponse.SC_UNAUTHORIZED,
                "message", message
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private long parseLastActivity(Object lastActivityObj) {
        try {
            return Long.parseLong(lastActivityObj.toString());
        } catch (NumberFormatException e) {
            log.warn("Invalid lastActivity value: {}", lastActivityObj);
            return 0;
        }
    }

    private String getAccessTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.startsWith(bearerToken, "Bearer ")) {
            return bearerToken.substring(7);
        }
        if (StringUtils.startsWith(request.getRequestURI(), "/api/vector/logs/stream")) {
            return request.getParameter("access_token");
        }
        return null;
    }

    private boolean isPublicAgentEndpoint(String method, String path) {
        return (HttpMethod.POST.matches(method) && (
                path.equals("/api/vector/agents/register")
                        || path.equals("/api/vector/agents/heartbeat")
                        || path.equals("/api/vector/agents/config/deploy-status")
                        || path.equals("/api/vector/agents/metrics")
                        || path.equals("/api/vector/agents/logs")
                        || path.equals("/api/vector/agents/command/status")))
                || (HttpMethod.GET.matches(method) && (
                path.equals("/api/vector/agents/config")
                        || path.equals("/api/vector/agents/install-script")
                        || path.equals("/api/vector/agents/download")
                        || path.equals("/api/vector/agents/command")));
    }

    private boolean isPublicPackageEndpoint(String method, String path) {
        return HttpMethod.GET.matches(method)
                && (StringUtils.startsWith(path, "/api/vector/packages/download/")
                || path.equals("/api/vector/packages/download-bundle"));
    }
}
