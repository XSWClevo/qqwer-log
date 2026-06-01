package cn.mw.loganalysis.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * TraceId 过滤器
 * 为每个 HTTP 请求生成唯一的 traceId，放入 MDC 中以贯穿整个请求链路的日志输出。
 * 同时将 traceId 写入响应头 X-Trace-Id，方便前端和调试使用。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String UNKNOWN_IP = "unknown";
    private static final Set<String> NOISY_AGENT_ENDPOINTS = Set.of(
            "/api/vector/agents/config",
            "/api/vector/agents/command",
            "/api/vector/agents/heartbeat",
            "/api/vector/agents/config/deploy-status",
            "/api/vector/agents/command/status"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        boolean logRequest = shouldLogRequest(request);
        Exception failure = null;

        try {
            String traceId = extractOrGenerateTraceId(request);
            MDC.put(TRACE_ID_KEY, traceId);
            response.setHeader(TRACE_ID_HEADER, traceId);

            if (logRequest) {
                log.info("HTTP接口调用开始 method={} uri={} clientIp={}",
                        request.getMethod(), request.getRequestURI(), getClientIp(request));
            }

            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException ex) {
            failure = ex;
            throw ex;
        } finally {
            if (logRequest) {
                log.info("HTTP接口调用结束 method={} uri={} status={} cost={}ms{}",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        System.currentTimeMillis() - startTime,
                        ObjectUtils.isNotEmpty(failure) ? " error=" + failure.getClass().getSimpleName() : "");
            }
            MDC.remove(TRACE_ID_KEY);
        }
    }

    /**
     * 优先从请求头获取上游传递的 traceId，否则自动生成
     */
    private String extractOrGenerateTraceId(HttpServletRequest request) {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (StringUtils.isBlank(traceId)) {
            traceId = generateTraceId();
        }
        return traceId;
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private boolean shouldLogRequest(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return StringUtils.startsWith(requestUri, "/api/")
                && !NOISY_AGENT_ENDPOINTS.contains(requestUri);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (isValidClientIp(ip)) {
            return StringUtils.substringBefore(ip, ",").trim();
        }

        ip = request.getHeader("X-Real-IP");
        if (isValidClientIp(ip)) {
            return ip;
        }

        return request.getRemoteAddr();
    }

    private boolean isValidClientIp(String ip) {
        return StringUtils.isNotBlank(ip) && !StringUtils.equalsIgnoreCase(UNKNOWN_IP, ip);
    }
}
