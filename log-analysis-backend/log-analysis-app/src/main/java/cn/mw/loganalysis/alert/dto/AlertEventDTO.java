package cn.mw.loganalysis.alert.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 告警事件 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEventDTO {
    
    private Long id;
    
    private Long ruleId;
    
    private String ruleName;
    
    private String severity;

    private String state;

    private String previousState;

    private String thresholdLevel;
    
    private String message;
    
    private Map<String, Object> logData;

    private Long evaluationRunId;
    
    private LocalDateTime triggeredAt;
    
    private Boolean acknowledged;
    
    private Long acknowledgedBy;
    
    private LocalDateTime acknowledgedAt;
    
    /** 触发值 */
    private String triggeredValue;
    
    /** 相关实体 */
    private String relatedEntity;
    
    /** 通知状态 */
    private String notificationStatus;
    
    /** 通知结果列表 */
    private List<NotificationResult> notificationResults;
    
    /** 上下文日志 */
    private List<Map<String, Object>> contextLogs;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationResult {
        private String channel;
        private String status;
        private String message;
        private LocalDateTime sentAt;
    }
}
