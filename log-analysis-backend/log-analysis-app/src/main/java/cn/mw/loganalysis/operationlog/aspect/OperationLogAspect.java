package cn.mw.loganalysis.operationlog.aspect;

import cn.mw.loganalysis.auth.security.JwtTokenProvider;
import cn.mw.loganalysis.common.response.Result;
import cn.mw.loganalysis.operationlog.annotation.OperationLog;
import cn.mw.loganalysis.operationlog.entity.UserOperationLog;
import cn.mw.loganalysis.operationlog.service.OperationLogService;
import cn.mw.loganalysis.operationlog.util.SensitiveDataMasker;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Random;

/**
 * 操作日志 AOP 切面
 * <p>
 * 拦截所有带 @OperationLog 注解的方法，自动记录操作日志
 * </p>
 *
 * @author Claude
 * @since 2026-01-07
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogService operationLogService;
    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final Random random = new Random();

    /**
     * 定义切点: 所有带 @OperationLog 注解的方法
     */
    @Pointcut("@annotation(cn.mw.loganalysis.operationlog.annotation.OperationLog)")
    public void operationLogPointcut() {
    }

    /**
     * 环绕通知: 记录操作日志
     */
    @Around("operationLogPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long invocationId = System.currentTimeMillis();
        log.info("========= [AOP-{}] Around method called for: {}", invocationId, joinPoint.getSignature().getName());

        // 获取注解
        OperationLog annotation = getAnnotation(joinPoint);
        if (annotation == null) {
            log.info("========= [AOP-{}] No annotation found, proceeding without logging", invocationId);
            return joinPoint.proceed();
        }

        // 采样控制 (高频接口优化)
        if (!shouldRecord(annotation.samplingRate())) {
            log.info("========= [AOP-{}] Skipped by sampling rate", invocationId);
            return joinPoint.proceed();
        }

        // 记录开始时间
        long startTime = System.currentTimeMillis();

        // 获取 HTTP 请求信息
        HttpServletRequest request = getHttpServletRequest();

        // 构建日志对象
        UserOperationLog operationLog = buildOperationLog(annotation, joinPoint, request);
        log.info("========= [AOP-{}] OperationLog object built", invocationId);

        Object result = null;
        Throwable exception = null;

        try {
            log.info("========= [AOP-{}] Calling joinPoint.proceed()...", invocationId);
            // 执行目标方法
            result = joinPoint.proceed();
            log.info("========= [AOP-{}] joinPoint.proceed() returned, result type: {}",
                invocationId, result != null ? result.getClass().getSimpleName() : "null");

            // 记录用户信息
            Long userId = getUserId();
            operationLog.setUserId(userId);

            // 记录成功信息
            operationLog.setIsSuccess(true);
            operationLog.setResponseStatus(200);

            // 提取资源 ID (从返回值中)
            operationLog.setResourceId(updateResourceId(annotation, joinPoint, result));

            log.info("========= [AOP-{}] Resource ID extracted: {}", invocationId, operationLog.getResourceId());

            // 记录响应消息
            if (result instanceof Result<?> resultObj) {
                operationLog.setResponseStatus(resultObj.getCode());
                operationLog.setResponseMessage(resultObj.getMessage());
            }

        } catch (Throwable e) {
            exception = e;
            log.error("========= [AOP-{}] Exception caught: {}", invocationId, e.getMessage());

            // 记录失败信息
            operationLog.setIsSuccess(false);
            operationLog.setResponseStatus(500);
            operationLog.setErrorMessage(e.getMessage());

            throw e;
        } finally {
            // 记录执行耗时
            long executionTime = System.currentTimeMillis() - startTime;
            operationLog.setExecutionTime((int) executionTime);

            log.info("========= [AOP-{}] Calling asyncSaveLog...", invocationId);
            // 异步保存日志
            asyncSaveLog(operationLog);
        }

        log.info("========= [AOP-{}] Around method completed", invocationId);
        return result;
    }

    private Long getUserId() {
        return 1L;
    }

    /**
     * 获取方法上的 @OperationLog 注解
     */
    private OperationLog getAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        return method.getAnnotation(OperationLog.class);
    }

    /**
     * 采样控制: 判断是否应该记录本次操作
     */
    private boolean shouldRecord(double samplingRate) {
        if (samplingRate >= 1.0) {
            return true;
        }
        if (samplingRate <= 0.0) {
            return false;
        }
        return random.nextDouble() < samplingRate;
    }

    /**
     * 获取 HTTP 请求对象
     */
    private HttpServletRequest getHttpServletRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    /**
     * 构建操作日志对象
     */
    private UserOperationLog buildOperationLog(
        OperationLog annotation,
        ProceedingJoinPoint joinPoint,
        HttpServletRequest request
    ) {
        UserOperationLog operationLog = new UserOperationLog();

        // 获取当前用户信息
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !(authentication.getPrincipal() instanceof String)) {
            // Principal 是 userId (Long 类型)
            if (authentication.getPrincipal() instanceof Long) {
                Long userId = (Long) authentication.getPrincipal();
                operationLog.setUserId(userId);

                // 尝试从 HTTP 请求头获取 JWT Token 来提取 username
                if (request != null) {
                    String jwt = getJwtFromRequest(request);
                    if (jwt != null) {
                        try {
                            String username = jwtTokenProvider.getUsernameFromToken(jwt);
                            operationLog.setUsername(username);
                        } catch (Exception e) {
                            log.debug("Failed to extract username from JWT", e);
                        }
                    }
                }
            }
        }

        // 设置操作信息
        operationLog.setOperationType(annotation.operationType());
        operationLog.setModule(annotation.module().getCode());
        operationLog.setAction(annotation.action().getCode());
        operationLog.setResourceType(annotation.resourceType());

        // 提取资源 ID (从请求参数中)
        if (StringUtils.hasText(annotation.resourceIdSpEL())) {
            try {
                String resourceId = parseSpEL(annotation.resourceIdSpEL(), joinPoint, null);
                operationLog.setResourceId(resourceId);
            } catch (Exception e) {
                operationLog.setResourceId(null);
            }
        }

        // 记录请求信息
        if (request != null) {
            operationLog.setRequestMethod(request.getMethod());
            operationLog.setRequestUrl(request.getRequestURI());
            operationLog.setIpAddress(getClientIp(request));
            operationLog.setUserAgent(request.getHeader("User-Agent"));
        }

        // 记录请求参数 (脱敏)
        if (annotation.recordParams()) {
            Object[] args = joinPoint.getArgs();
            if (args != null && args.length > 0) {
                // 只记录第一个参数 (通常是 Request DTO)
                Object maskedParams = SensitiveDataMasker.mask(args[0], annotation.sensitiveFields());
                operationLog.setRequestParams(maskedParams);
            }
        }

        operationLog.setCreatedAt(LocalDateTime.now());

        return operationLog;
    }

    /**
     * 从请求头中提取 JWT Token
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * 更新资源 ID（从返回值中提取）
     */
    private String updateResourceId(OperationLog annotation, ProceedingJoinPoint joinPoint, Object result) {
        if (!StringUtils.hasText(annotation.resourceIdSpEL())) {
            return "";
        }

        // 如果 result 为 null，无法提取 resourceId
        if (result == null) {
            log.debug("Result is null, cannot extract resourceId");
            return "";
        }

        try {
            String resourceId = parseSpEL(annotation.resourceIdSpEL(), joinPoint, result);
            return resourceId != null ? resourceId : "";
        } catch (Exception e) {
            log.warn("Failed to parse resourceId SpEL expression: {} (result type: {})",
                annotation.resourceIdSpEL(), result.getClass().getSimpleName(), e);
            return "";
        }
    }

    /**
     * 解析 SpEL 表达式
     *
     * @param spelExpression SpEL 表达式
     * @param joinPoint 连接点
     * @param result 方法返回值
     * @return 解析结果
     */
    private String parseSpEL(String spelExpression, ProceedingJoinPoint joinPoint, Object result) {
        try {
            EvaluationContext context = new StandardEvaluationContext();

            // 设置方法参数
            Object[] args = joinPoint.getArgs();
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] parameterNames = signature.getParameterNames();

            for (int i = 0; i < args.length; i++) {
                context.setVariable("p" + i, args[i]);
                if (parameterNames != null && i < parameterNames.length) {
                    context.setVariable(parameterNames[i], args[i]);
                }
            }

            // 设置返回值
            if (result != null) {
                context.setVariable("result", result);
                // 解析表达式
                Object value = parser.parseExpression(spelExpression).getValue(context);
                return value != null ? value.toString() : null;
            }
            return null;

        } catch (Exception e) {
            log.warn("Failed to parse SpEL expression: {}", spelExpression, e);
            return null;
        }
    }

    /**
     * 获取客户端真实 IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // 处理多个 IP 的情况 (取第一个)
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }

    /**
     * 保存日志 (临时改为同步，用于调试)
     */
    @Async("operationLogExecutor")  // 临时注释，改为同步执行
    public void asyncSaveLog(UserOperationLog operationLog) {
        try {
            log.info("========= [OperationLog] Saving: user={}, action={}, module={}, resourceId={}",
                operationLog.getUsername(), operationLog.getAction(), operationLog.getModule(), operationLog.getResourceId());

            operationLogService.saveLog(operationLog);

            log.info("========= [OperationLog] Saved successfully, id={}", operationLog.getId());
        } catch (Exception e) {
            log.error("========= [OperationLog] Failed to save", e);
        }
    }
}
