package cn.mw.loganalysis.auth.config;

import cn.mw.loganalysis.auth.security.JwtAuthenticationFilter;
import cn.mw.loganalysis.auth.security.SessionActivityFilter;
import cn.mw.loganalysis.common.response.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security配置
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SessionActivityFilter sessionActivityFilter;
    private final ObjectMapper objectMapper;

    /**
     * 密码编码器
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 安全过滤器链配置
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 禁用CSRF（使用JWT时不需要）
                .csrf(AbstractHttpConfigurer::disable)

                // 启用CORS（使用CorsConfig中的配置）
                .cors(cors -> cors.configure(http))

                // 仅使用 JWT，不启用 Spring Security 默认表单登录/Basic 登录入口
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)

                // 配置会话管理为无状态
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 未认证/无权限时返回统一 JSON，避免默认 HTML 响应影响前端处理
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        Result.unauthorized("未登录或登录已过期")))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeJsonError(response, HttpServletResponse.SC_FORBIDDEN,
                                        Result.forbidden("无权访问该接口")))
                )

                // 配置授权规则：只放行登录、刷新令牌、Agent 自身调用和安装包下载，其余接口默认需要登录
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error", "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh").permitAll()

                        // Vector Agent 机器侧接口使用 agent token，不走用户 JWT 登录态
                        .requestMatchers(HttpMethod.POST,
                                "/api/vector/agents/register",
                                "/api/vector/agents/heartbeat",
                                "/api/vector/agents/config/deploy-status",
                                "/api/vector/agents/metrics",
                                "/api/vector/agents/logs",
                                "/api/vector/agents/command/status"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/vector/agents/config",
                                "/api/vector/agents/install-script",
                                "/api/vector/agents/download",
                                "/api/vector/agents/command"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/vector/packages/download/*",
                                "/api/vector/packages/download-bundle"
                        ).permitAll()

                        .anyRequest().authenticated()
                )

                // 添加JWT认证过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // 添加会话活跃超时过滤器（在JWT认证之后执行）
                .addFilterAfter(sessionActivityFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    private void writeJsonError(HttpServletResponse response, int status, Result<Void> body) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
