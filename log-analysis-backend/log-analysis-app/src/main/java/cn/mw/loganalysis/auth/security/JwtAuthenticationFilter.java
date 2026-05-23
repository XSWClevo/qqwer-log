package cn.mw.loganalysis.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT认证过滤器
 * 从请求头中提取JWT令牌并验证
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            // 从请求头获取JWT令牌
            String jwt = getJwtFromRequest(request);

            if (StringUtils.isNotBlank(jwt) && jwtTokenProvider.validateToken(jwt)) {
                // 从令牌中获取用户ID
                Long userId = jwtTokenProvider.getUserIdFromToken(jwt);
                String role = jwtTokenProvider.getRoleFromToken(jwt);

                // 创建认证对象
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
                );

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // 设置到安全上下文
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("Set Authentication for user: {}", userId);
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头中提取JWT令牌
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.startsWith(bearerToken, "Bearer ")) {
            return bearerToken.substring(7);
        }

        // 浏览器 EventSource 不能设置 Authorization header，SSE 只允许通过查询参数携带用户 JWT。
        if (StringUtils.startsWith(request.getRequestURI(), "/api/vector/logs/stream")) {
            String queryToken = request.getParameter("access_token");
            if (StringUtils.isNotBlank(queryToken)) {
                return queryToken;
            }
        }
        return null;
    }
}
