package cn.mw.loganalysis.alert.service;

import cn.mw.loganalysis.alert.dto.AlertEventDTO;
import cn.mw.loganalysis.alert.dto.AlertEventQueryRequest;
import cn.mw.loganalysis.alert.entity.AlertEvent;
import cn.mw.loganalysis.alert.mapper.AlertEventMapper;
import cn.mw.loganalysis.common.enums.TimeRange;
import cn.mw.loganalysis.common.exception.ResourceNotFoundException;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 告警事件服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEventService {

    private final AlertEventMapper alertEventMapper;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 查询告警事件列表
     */
    public IPage<AlertEventDTO> queryEvents(AlertEventQueryRequest request) {
        log.info("Querying alert events with request: {}", request);

        Page<AlertEvent> page = new Page<>(request.getPageNum(), request.getPageSize());

        // 计算时间范围
        LocalDateTime startTime = calculateStartTime(request.getTimeRange(), request.getStartTime());
        LocalDateTime endTime = calculateEndTime(request.getEndTime());

        // 使用 Mapper 的 default 方法
        IPage<AlertEvent> eventPage = alertEventMapper.selectPageByCondition(
                page,
                request.getKeyword(),
                request.getSeverity(),
                request.getRuleId(),
                startTime,
                endTime
        );

        // 转换为 DTO
        return eventPage.convert(this::convertToDTO);
    }

    /**
     * 获取事件详情
     */
    public AlertEventDTO getEventById(Long id) {
        log.info("Getting alert event by id: {}", id);

        AlertEvent event = alertEventMapper.selectById(id);
        if (event == null) {
            throw new ResourceNotFoundException("告警事件不存在: " + id);
        }
        
        AlertEventDTO dto = convertToDTO(event);
        
        // 添加通知结果
        dto.setNotificationResults(buildNotificationResults(event));
        
        // 添加上下文日志
        dto.setContextLogs(buildContextLogs(event));
        
        return dto;
    }

    /**
     * 获取告警趋势数据
     */
    public Map<String, Object> getAlertTrend(String timeRange) {
        log.info("Getting alert trend for time range: {}", timeRange);
        
        LocalDateTime startTime = calculateStartTime(timeRange, null);
        LocalDateTime endTime = LocalDateTime.now();
        
        // TODO: 实现实际的趋势统计逻辑
        Map<String, Object> result = new HashMap<>();
        result.put("timestamps", List.of("00:00", "04:00", "08:00", "12:00", "16:00", "20:00", "24:00"));
        result.put("critical", List.of(5, 3, 8, 12, 15, 10, 6));
        result.put("warning", List.of(8, 6, 10, 15, 20, 12, 9));
        
        return result;
    }

    /**
     * 确认告警
     */
    public void acknowledgeEvent(Long id, Long userId) {
        log.info("Acknowledging alert event: {} by user: {}", id, userId);
        
        AlertEvent event = alertEventMapper.selectById(id);
        if (event == null) {
            throw new RuntimeException("告警事件不存在");
        }
        
        event.setAcknowledged(true);
        event.setAcknowledgedBy(userId);
        event.setAcknowledgedAt(LocalDateTime.now());
        
        alertEventMapper.updateById(event);
    }

    /**
     * 转换为 DTO
     */
    private AlertEventDTO convertToDTO(AlertEvent event) {
        return AlertEventDTO.builder()
                .id(event.getId())
                .ruleId(event.getRuleId())
                .ruleName(event.getRuleName())
                .severity(event.getSeverity())
                .message(event.getMessage())
                .logData(event.getLogData())
                .triggeredAt(event.getTriggeredAt())
                .acknowledged(event.getAcknowledged())
                .acknowledgedBy(event.getAcknowledgedBy())
                .acknowledgedAt(event.getAcknowledgedAt())
                .triggeredValue(extractTriggeredValue(event))
                .relatedEntity(extractRelatedEntity(event))
                .notificationStatus(determineNotificationStatus(event))
                .build();
    }

    /**
     * 提取触发值
     */
    private String extractTriggeredValue(AlertEvent event) {
        if (event.getLogData() == null) {
            return "N/A";
        }
        
        Object count = event.getLogData().get("count");
        Object threshold = event.getLogData().get("threshold");
        
        if (count != null && threshold != null) {
            return String.format("Count: %s (阈值: >%s)", count, threshold);
        }
        
        return "N/A";
    }

    /**
     * 提取相关实体
     */
    private String extractRelatedEntity(AlertEvent event) {
        if (event.getLogData() == null) {
            return "N/A";
        }
        
        Object host = event.getLogData().get("host");
        Object service = event.getLogData().get("service");
        
        if (host != null) {
            return "host: " + host;
        }
        if (service != null) {
            return "service: " + service;
        }
        
        return "N/A";
    }

    /**
     * 确定通知状态
     */
    private String determineNotificationStatus(AlertEvent event) {
        // TODO: 从实际的通知记录中获取状态
        // 这里简化处理，随机返回
        return Math.random() > 0.2 ? "sent" : "failed";
    }

    /**
     * 构建通知结果列表
     */
    private List<AlertEventDTO.NotificationResult> buildNotificationResults(AlertEvent event) {
        List<AlertEventDTO.NotificationResult> results = new ArrayList<>();
        
        // TODO: 从实际的通知记录表中查询
        // 这里使用 Mock 数据
        results.add(AlertEventDTO.NotificationResult.builder()
                .channel("email")
                .status("success")
                .message("Email to dev-ops@example.com")
                .sentAt(event.getTriggeredAt().plusSeconds(5))
                .build());
        
        results.add(AlertEventDTO.NotificationResult.builder()
                .channel("slack")
                .status("success")
                .message("Slack #alerts channel")
                .sentAt(event.getTriggeredAt().plusSeconds(3))
                .build());
        
        if (Math.random() > 0.7) {
            results.add(AlertEventDTO.NotificationResult.builder()
                    .channel("webhook")
                    .status("failed")
                    .message("Webhook to Jira: 连接超时")
                    .sentAt(event.getTriggeredAt().plusSeconds(10))
                    .build());
        }
        
        return results;
    }

    /**
     * 构建上下文日志
     */
    private List<Map<String, Object>> buildContextLogs(AlertEvent event) {
        List<Map<String, Object>> logs = new ArrayList<>();
        
        // TODO: 从 ClickHouse 查询实际的上下文日志
        // 这里使用 Mock 数据
        for (int i = 0; i < 5; i++) {
            Map<String, Object> log = new HashMap<>();
            log.put("timestamp", event.getTriggeredAt().plusSeconds(i).format(FORMATTER));
            log.put("severity", "ERROR");
            log.put("message", "HTTP 500 Internal Server Error: Database connection timeout");
            logs.add(log);
        }

        return logs;
    }

    /**
     * 计算开始时间
     */
    private LocalDateTime calculateStartTime(String timeRange, String customStartTime) {
        // 自定义时间范围
        if (TimeRange.CUSTOM.getCode().equals(timeRange) && customStartTime != null) {
            return LocalDateTime.parse(customStartTime, FORMATTER);
        }

        // 使用枚举计算时间范围
        LocalDateTime now = LocalDateTime.now();
        TimeRange range = TimeRange.fromCode(timeRange);
        return range.getStartTime(now);
    }

    /**
     * 计算结束时间
     */
    private LocalDateTime calculateEndTime(String customEndTime) {
        if (customEndTime != null) {
            return LocalDateTime.parse(customEndTime, FORMATTER);
        }
        return LocalDateTime.now();
    }
}
