package cn.mw.loganalysis.alert.notifier;

import cn.mw.loganalysis.alert.entity.AlertEvent;
import cn.mw.loganalysis.alert.entity.AlertNotification;
import cn.mw.loganalysis.alert.entity.AlertRule;
import cn.mw.loganalysis.alert.mapper.AlertNotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 告警通知器。
 * <p>
 * 当前项目还没有邮件、Slack、Webhook 的具体连接配置，所以这里不会伪造发送成功。
 * 页面展示渠道会记录为 SUCCESS，外部渠道会记录为 SKIPPED，后续接入真实客户端时在这里替换为 SUCCESS / FAILED。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertNotifier {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String PAGE_CHANNEL = "page";

    private final AlertNotificationMapper alertNotificationMapper;

    @Async
    public void sendNotifications(AlertRule rule, AlertEvent event, List<Map<String, Object>> samples) {
        List<String> channels = rule.getNotificationChannels();
        if (CollectionUtils.isEmpty(channels)) {
            log.info("Alert rule {} has no notification channels, event {} is recorded for page display",
                    rule.getId(), event.getId());
            recordNotificationSafely(event.getId(), PAGE_CHANNEL, "SUCCESS", "已记录在告警历史页面");
            return;
        }

        String message = renderMessage(rule, event, samples);
        boolean recorded = false;
        for (String channel : channels) {
            if (StringUtils.isBlank(channel)) {
                continue;
            }
            if (StringUtils.equalsIgnoreCase(PAGE_CHANNEL, channel)) {
                log.info("Alert event {} is recorded for page display. Message: {}", event.getId(), message);
                recordNotificationSafely(event.getId(), PAGE_CHANNEL, "SUCCESS", "已记录在告警历史页面");
                recorded = true;
                continue;
            }
            log.info("Notification channel {} is not wired yet, record skipped notification for event {}. Message: {}",
                    channel, event.getId(), message);
            recordNotificationSafely(event.getId(), channel, "SKIPPED", "通知渠道尚未配置真实发送客户端");
            recorded = true;
        }

        if (!recorded) {
            log.info("Alert rule {} has no valid notification channels, event {} is recorded for page display",
                    rule.getId(), event.getId());
            recordNotificationSafely(event.getId(), PAGE_CHANNEL, "SUCCESS", "已记录在告警历史页面");
        }
    }

    private String renderMessage(AlertRule rule, AlertEvent event, List<Map<String, Object>> samples) {
        Map<String, Object> logData = event.getLogData();
        StringBuilder message = new StringBuilder();
        message.append("告警通知\n\n");
        message.append("规则名称: ").append(rule.getName()).append("\n");
        message.append("严重程度: ").append(event.getSeverity()).append("\n");
        message.append("状态: ").append(event.getState()).append("\n");
        message.append("触发时间: ").append(LocalDateTime.now().format(FORMATTER)).append("\n");
        message.append("摘要: ").append(StringUtils.defaultIfBlank(event.getMessage(), "无")).append("\n");

        if (MapUtils.isNotEmpty(logData)) {
            Object actualValue = logData.get("actualValue");
            Object threshold = logData.get("threshold");
            if (actualValue != null) {
                message.append("实际值: ").append(actualValue).append("\n");
            }
            if (threshold != null) {
                message.append("阈值: ").append(threshold).append("\n");
            }
        }
        if (CollectionUtils.isNotEmpty(samples)) {
            message.append("样本数: ").append(samples.size()).append("\n");
        }
        return message.toString();
    }

    private void recordNotificationSafely(Long eventId, String channel, String status, String errorMessage) {
        try {
            recordNotification(eventId, channel, status, errorMessage);
        } catch (Exception e) {
            log.error("Record alert notification failed, eventId={}, channel={}, status={}", eventId, channel, status, e);
        }
    }

    private void recordNotification(Long eventId, String channel, String status, String errorMessage) {
        AlertNotification notification = new AlertNotification();
        notification.setEventId(eventId);
        notification.setChannel(channel);
        notification.setStatus(status);
        notification.setErrorMessage(errorMessage);
        notification.setSentAt(LocalDateTime.now());
        alertNotificationMapper.insert(notification);
    }
}
