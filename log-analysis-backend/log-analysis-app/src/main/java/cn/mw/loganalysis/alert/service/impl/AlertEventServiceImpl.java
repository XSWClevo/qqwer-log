package cn.mw.loganalysis.alert.service.impl;

import cn.mw.loganalysis.alert.dto.AlertEventDTO;
import cn.mw.loganalysis.alert.dto.AlertEventQueryRequest;
import cn.mw.loganalysis.alert.entity.AlertEvent;
import cn.mw.loganalysis.alert.entity.AlertNotification;
import cn.mw.loganalysis.alert.mapper.AlertEventMapper;
import cn.mw.loganalysis.alert.mapper.AlertNotificationMapper;
import cn.mw.loganalysis.alert.service.AlertEventService;
import cn.mw.loganalysis.common.enums.TimeRange;
import cn.mw.loganalysis.common.exception.ResourceNotFoundException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 告警事件服务实现，只展示真实事件和通知记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEventServiceImpl implements AlertEventService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AlertEventMapper alertEventMapper;
    private final AlertNotificationMapper alertNotificationMapper;

    @Override
    public IPage<AlertEventDTO> queryEvents(AlertEventQueryRequest request) {
        Page<AlertEvent> page = new Page<>(
                ObjectUtils.defaultIfNull(request.getPageNum(), 1),
                ObjectUtils.defaultIfNull(request.getPageSize(), 20)
        );

        LocalDateTime startTime = calculateStartTime(request.getTimeRange(), request.getStartTime());
        LocalDateTime endTime = calculateEndTime(request.getEndTime());

        IPage<AlertEvent> eventPage = alertEventMapper.selectPageByCondition(
                page,
                request.getKeyword(),
                request.getSeverity(),
                request.getRuleId(),
                startTime,
                endTime
        );

        return eventPage.convert(this::convertToDTO);
    }

    @Override
    public AlertEventDTO getEventById(Long id) {
        AlertEvent event = alertEventMapper.selectById(id);
        if (event == null) {
            throw new ResourceNotFoundException("告警事件不存在: " + id);
        }

        AlertEventDTO dto = convertToDTO(event);
        dto.setNotificationResults(buildNotificationResults(event.getId()));
        dto.setContextLogs(buildContextLogs(event));
        return dto;
    }

    @Override
    public Map<String, Object> getAlertTrend(String timeRange) {
        LocalDateTime startTime = calculateStartTime(timeRange, null);
        LocalDateTime endTime = LocalDateTime.now();
        List<AlertEvent> events = alertEventMapper.selectList(new LambdaQueryWrapper<AlertEvent>()
                .between(AlertEvent::getTriggeredAt, startTime, endTime)
                .orderByAsc(AlertEvent::getTriggeredAt));

        Map<String, Map<String, Integer>> buckets = new LinkedHashMap<>();
        for (AlertEvent event : events) {
            String bucket = formatBucket(event.getTriggeredAt(), timeRange);
            buckets.computeIfAbsent(bucket, key -> new LinkedHashMap<>());
            String severity = StringUtils.lowerCase(StringUtils.defaultIfBlank(event.getSeverity(), "info"));
            buckets.get(bucket).merge(severity, 1, Integer::sum);
        }

        List<String> timestamps = new ArrayList<>(buckets.keySet());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamps", timestamps);
        result.put("critical", buildSeries(timestamps, buckets, "critical"));
        result.put("warning", buildSeries(timestamps, buckets, "warning"));
        result.put("info", buildSeries(timestamps, buckets, "info"));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acknowledgeEvent(Long id, Long userId) {
        AlertEvent event = alertEventMapper.selectById(id);
        if (event == null) {
            throw new ResourceNotFoundException("告警事件不存在: " + id);
        }
        event.setAcknowledged(true);
        event.setAcknowledgedBy(userId);
        event.setAcknowledgedAt(LocalDateTime.now());
        alertEventMapper.updateById(event);
    }

    private AlertEventDTO convertToDTO(AlertEvent event) {
        return AlertEventDTO.builder()
                .id(event.getId())
                .ruleId(event.getRuleId())
                .ruleName(event.getRuleName())
                .severity(event.getSeverity())
                .state(event.getState())
                .previousState(event.getPreviousState())
                .thresholdLevel(event.getThresholdLevel())
                .message(event.getMessage())
                .logData(event.getLogData())
                .evaluationRunId(event.getEvaluationRunId())
                .triggeredAt(event.getTriggeredAt())
                .acknowledged(event.getAcknowledged())
                .acknowledgedBy(event.getAcknowledgedBy())
                .acknowledgedAt(event.getAcknowledgedAt())
                .triggeredValue(extractTriggeredValue(event))
                .relatedEntity(extractRelatedEntity(event))
                .notificationStatus(determineNotificationStatus(event.getId()))
                .build();
    }

    private String extractTriggeredValue(AlertEvent event) {
        if (MapUtils.isEmpty(event.getLogData())) {
            return "N/A";
        }

        String state = StringUtils.defaultIfBlank(event.getState(), MapUtils.getString(event.getLogData(), "level"));
        if ("NO_DATA".equalsIgnoreCase(state)) {
            return "No Data";
        }
        if ("RECOVERED".equalsIgnoreCase(state)) {
            return "Recovered";
        }

        Object actualValue = event.getLogData().get("actualValue");
        Object threshold = event.getLogData().get("threshold");
        if (actualValue != null && threshold != null) {
            return String.format("%s / 阈值 %s", formatValue(actualValue), formatValue(threshold));
        }
        if (actualValue != null) {
            return formatValue(actualValue);
        }
        return "N/A";
    }

    private String extractRelatedEntity(AlertEvent event) {
        if (MapUtils.isEmpty(event.getLogData())) {
            return "N/A";
        }

        Object groupValuesObj = event.getLogData().get("groupValues");
        if (groupValuesObj instanceof Map<?, ?> groupValues && MapUtils.isNotEmpty(groupValues)) {
            return groupValues.entrySet().stream()
                    .map(entry -> entry.getKey() + ": " + entry.getValue())
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("N/A");
        }

        Object tableName = event.getLogData().get("tableName");
        Object datasourceId = event.getLogData().get("datasourceId");
        return ObjectUtils.defaultIfNull(tableName, ObjectUtils.defaultIfNull(datasourceId, "N/A")).toString();
    }

    private String determineNotificationStatus(Long eventId) {
        List<AlertNotification> notifications = selectNotifications(eventId);
        if (CollectionUtils.isEmpty(notifications)) {
            return "pending";
        }
        boolean hasFailed = notifications.stream()
                .anyMatch(notification -> "FAILED".equalsIgnoreCase(notification.getStatus()));
        boolean hasSent = notifications.stream()
                .anyMatch(notification -> "SUCCESS".equalsIgnoreCase(notification.getStatus()));
        if (hasFailed) {
            return "failed";
        }
        return hasSent ? "sent" : "skipped";
    }

    private List<AlertEventDTO.NotificationResult> buildNotificationResults(Long eventId) {
        return selectNotifications(eventId).stream()
                .map(notification -> AlertEventDTO.NotificationResult.builder()
                        .channel(notification.getChannel())
                        .status(mapNotificationStatus(notification.getStatus()))
                        .message(StringUtils.defaultIfBlank(notification.getErrorMessage(), notification.getStatus()))
                        .sentAt(notification.getSentAt())
                        .build())
                .toList();
    }

    private List<Map<String, Object>> buildContextLogs(AlertEvent event) {
        Object samples = MapUtils.getObject(event.getLogData(), "results");
        if (!(samples instanceof List<?> list)) {
            return List.of();
        }

        List<Map<String, Object>> logs = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> row) {
                logs.add(new LinkedHashMap<>((Map<String, Object>) row));
            }
        }
        return logs;
    }

    private List<AlertNotification> selectNotifications(Long eventId) {
        if (eventId == null) {
            return List.of();
        }
        return alertNotificationMapper.selectList(new LambdaQueryWrapper<AlertNotification>()
                .eq(AlertNotification::getEventId, eventId)
                .orderByAsc(AlertNotification::getSentAt));
    }

    private String mapNotificationStatus(String status) {
        return switch (StringUtils.upperCase(StringUtils.defaultString(status))) {
            case "SUCCESS" -> "success";
            case "FAILED" -> "failed";
            case "SKIPPED" -> "skipped";
            default -> StringUtils.lowerCase(StringUtils.defaultString(status, "pending"));
        };
    }

    private String formatValue(Object value) {
        if (ObjectUtils.isEmpty(value)) {
            return "-";
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue()).stripTrailingZeros().toPlainString();
        }
        try {
            return new BigDecimal(String.valueOf(value)).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return String.valueOf(value);
        }
    }

    private LocalDateTime calculateStartTime(String timeRange, String customStartTime) {
        if (TimeRange.CUSTOM.getCode().equals(timeRange) && StringUtils.isNotBlank(customStartTime)) {
            return LocalDateTime.parse(customStartTime, FORMATTER);
        }
        TimeRange range = TimeRange.fromCode(timeRange);
        return range.getStartTime(LocalDateTime.now());
    }

    private LocalDateTime calculateEndTime(String customEndTime) {
        if (StringUtils.isNotBlank(customEndTime)) {
            return LocalDateTime.parse(customEndTime, FORMATTER);
        }
        return LocalDateTime.now();
    }

    private String formatBucket(LocalDateTime time, String timeRange) {
        if ("30d".equals(timeRange) || "7d".equals(timeRange)) {
            return time.toLocalDate().toString();
        }
        return time.getHour() + ":00";
    }

    private List<Integer> buildSeries(List<String> timestamps, Map<String, Map<String, Integer>> buckets, String severity) {
        return timestamps.stream()
                .map(timestamp -> buckets.getOrDefault(timestamp, Map.of()).getOrDefault(severity, 0))
                .toList();
    }
}
