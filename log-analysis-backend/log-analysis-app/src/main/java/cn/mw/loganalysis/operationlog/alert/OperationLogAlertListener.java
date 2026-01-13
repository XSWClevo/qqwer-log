package cn.mw.loganalysis.operationlog.alert;

import cn.mw.loganalysis.operationlog.entity.UserOperationLog;
import cn.mw.loganalysis.operationlog.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 操作日志告警监听器
 * <p>
 * 监听操作日志事件，检测异常行为并触发告警
 * </p>
 *
 * <h3>告警规则</h3>
 * <ul>
 *   <li>高频失败: 5分钟内同一用户失败 > 20次</li>
 *   <li>异常 IP 登录: 新 IP 登录成功</li>
 *   <li>批量删除: 5分钟内删除操作 > 10次</li>
 *   <li>敏感配置修改: 修改系统配置、数据源连接</li>
 * </ul>
 *
 * @author Claude
 * @since 2026-01-07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationLogAlertListener {

    private final OperationLogService operationLogService;

    /**
     * 监听操作日志事件
     *
     * @param operationLog 操作日志
     */
    @Async("operationLogExecutor")
    @EventListener
    public void handleOperationLogEvent(UserOperationLog operationLog) {
        // 仅对失败操作或敏感操作进行告警检测
        if (!shouldCheckAlert(operationLog)) {
            return;
        }

        try {
            // 检测异常操作
            Map<String, String> alerts = operationLogService.detectAnomalousOperations(
                operationLog.getUserId(),
                operationLog.getIpAddress()
            );

            // 触发告警
            if (!alerts.isEmpty()) {
                alerts.forEach((alertType, message) -> {
                    log.warn("[操作日志告警] Type: {}, Message: {}", alertType, message);
                    // TODO: 集成现有告警模块，发送邮件/钉钉/企业微信通知
                    // alertService.sendAlert(alertType, message);
                });
            }
        } catch (Exception e) {
            log.error("Failed to check operation log alert", e);
        }
    }

    /**
     * 判断是否需要进行告警检测
     */
    private boolean shouldCheckAlert(UserOperationLog operationLog) {
        // 1. 失败操作需要检测
        if (!operationLog.getIsSuccess()) {
            return true;
        }

        // 2. 敏感操作需要检测
        String module = operationLog.getModule();
        String action = operationLog.getAction();

        // 删除操作
        if ("DELETE".equals(operationLog.getOperationType().getCode())) {
            return true;
        }

        // 系统配置修改
        if ("config".equals(module) || "datasource".equals(module)) {
            return true;
        }

        // 新 IP 登录
        if ("user_login".equals(action)) {
            return true;
        }

        return false;
    }
}
