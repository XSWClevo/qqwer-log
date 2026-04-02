package cn.mw.loganalysis.alert.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 告警规则 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRuleDTO {
    
    private Long id;
    
    private String name;
    
    private String description;

    private String ruleType;

    private String scopeType;

    private List<String> categoryCodes;

    private List<String> datasourceIds;

    private List<String> tableNames;

    private Map<String, Object> condition;

    private String evalEvery;

    private Integer consecutiveHits;

    private String severity;

    private List<String> notificationChannels;

    private List<String> dedupKeyFields;

    private String messageTemplate;

    private Integer silencePeriod;

    private Boolean enabled;

    private Long createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 最后触发时间 */
    private LocalDateTime lastTriggeredAt;

    /** 触发次数 */
    private Long triggerCount;

    /** 规则类型 */
    private String type;

    /** 触发条件摘要 */
    private String triggerConditionSummary;
}
