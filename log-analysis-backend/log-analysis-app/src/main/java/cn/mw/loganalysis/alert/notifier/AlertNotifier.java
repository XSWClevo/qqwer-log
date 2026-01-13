package cn.mw.loganalysis.alert.notifier;

import cn.mw.loganalysis.alert.entity.AlertEvent;
import cn.mw.loganalysis.alert.entity.AlertNotification;
import cn.mw.loganalysis.alert.entity.AlertRule;
import cn.mw.loganalysis.alert.mapper.AlertNotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 告警通知器
 * 发送告警通知到各个渠道
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertNotifier {

    private final AlertNotificationMapper alertNotificationMapper;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 发送通知（异步）
     */
    @Async
    public void sendNotifications(AlertRule rule, AlertEvent event, List<Map<String, Object>> results) {
        log.info("Sending notifications for alert event: {}", event.getId());
        
        List<String> channels = rule.getNotificationChannels();
        if (channels == null || channels.isEmpty()) {
            log.warn("No notification channels configured for rule: {}", rule.getId());
            return;
        }
        
        String message = renderMessage(rule, event, results);
        
        for (String channel : channels) {
            try {
                boolean success = sendToChannel(channel, message, rule, event);
                recordNotification(event.getId(), channel, success, success ? null : "发送失败");
            } catch (Exception e) {
                log.error("Failed to send notification to channel: {}", channel, e);
                recordNotification(event.getId(), channel, false, e.getMessage());
            }
        }
    }

    /**
     * 发送到指定渠道
     */
    private boolean sendToChannel(String channel, String message, AlertRule rule, AlertEvent event) {
        log.info("Sending notification to channel: {}", channel);
        
        return switch (channel.toLowerCase()) {
            case "slack" -> sendToSlack(message, rule);
            case "email" -> sendToEmail(message, rule);
            case "webhook" -> sendToWebhook(message, rule);
            default -> {
                log.warn("Unsupported notification channel: {}", channel);
                yield false;
            }
        };
    }

    /**
     * 发送到 Slack
     */
    private boolean sendToSlack(String message, AlertRule rule) {
        log.info("Sending to Slack: {}", message);
        
        // TODO: 实现实际的 Slack 通知
        // 使用 Slack Webhook API 或 Slack SDK
        // String webhookUrl = getSlackWebhookUrl();
        // RestTemplate restTemplate = new RestTemplate();
        // Map<String, Object> payload = Map.of("text", message);
        // restTemplate.postForEntity(webhookUrl, payload, String.class);
        
        // 模拟发送成功
        return true;
    }

    /**
     * 发送到 Email
     */
    private boolean sendToEmail(String message, AlertRule rule) {
        log.info("Sending to Email: {}", message);
        
        // TODO: 实现实际的邮件通知
        // 使用 Spring Mail 或其他邮件服务
        // JavaMailSender mailSender;
        // SimpleMailMessage mailMessage = new SimpleMailMessage();
        // mailMessage.setTo("admin@example.com");
        // mailMessage.setSubject("告警: " + rule.getName());
        // mailMessage.setText(message);
        // mailSender.send(mailMessage);
        
        // 模拟发送成功
        return true;
    }

    /**
     * 发送到 Webhook
     */
    private boolean sendToWebhook(String message, AlertRule rule) {
        log.info("Sending to Webhook: {}", message);
        
        // TODO: 实现实际的 Webhook 通知
        // String webhookUrl = getWebhookUrl();
        // RestTemplate restTemplate = new RestTemplate();
        // Map<String, Object> payload = Map.of(
        //     "rule_name", rule.getName(),
        //     "severity", rule.getSeverity(),
        //     "message", message
        // );
        // restTemplate.postForEntity(webhookUrl, payload, String.class);
        
        // 模拟发送成功
        return true;
    }

    /**
     * 渲染消息模板
     */
    private String renderMessage(AlertRule rule, AlertEvent event, List<Map<String, Object>> results) {
        Map<String, Object> condition = rule.getCondition();
        Map<String, Object> firstResult = results.isEmpty() ? Map.of() : results.get(0);
        
        Object valueObj = firstResult.get("value");
        double actualValue = valueObj != null ? convertToDouble(valueObj) : 0;
        
        Object thresholdObj = condition.get("value");
        double threshold = thresholdObj != null ? convertToDouble(thresholdObj) : 0;
        
        String groupBy = (String) condition.get("groupBy");
        String groupValue = groupBy != null ? (String) firstResult.get(groupBy) : "N/A";
        
        StringBuilder message = new StringBuilder();
        message.append("🚨 告警通知\n\n");
        message.append("规则名称: ").append(rule.getName()).append("\n");
        message.append("严重程度: ").append(getSeverityEmoji(rule.getSeverity())).append(" ").append(rule.getSeverity()).append("\n");
        message.append("触发时间: ").append(LocalDateTime.now().format(FORMATTER)).append("\n");
        message.append("实际值: ").append(String.format("%.2f", actualValue)).append("\n");
        message.append("阈值: ").append(String.format("%.2f", threshold)).append("\n");
        
        if (groupBy != null) {
            message.append("分组: ").append(groupBy).append(" = ").append(groupValue).append("\n");
        }
        
        message.append("\n描述: ").append(rule.getDescription() != null ? rule.getDescription() : "无");
        
        return message.toString();
    }

    /**
     * 记录通知结果
     */
    private void recordNotification(Long eventId, String channel, boolean success, String errorMessage) {
        AlertNotification notification = new AlertNotification();
        notification.setEventId(eventId);
        notification.setChannel(channel);
        notification.setStatus(success ? "SUCCESS" : "FAILED");
        notification.setErrorMessage(errorMessage);
        
        alertNotificationMapper.insert(notification);
    }

    /**
     * 获取严重程度表情
     */
    private String getSeverityEmoji(String severity) {
        return switch (severity.toUpperCase()) {
            case "CRITICAL" -> "🔴";
            case "ERROR" -> "🟠";
            case "WARNING" -> "🟡";
            case "INFO" -> "🔵";
            default -> "⚪";
        };
    }

    /**
     * 转换为 double
     */
    private double convertToDouble(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        try {
            return Double.parseDouble(obj.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
