package cn.mw.loganalysis.alert.dto;

import lombok.Data;

import java.util.List;

/**
 * 更新告警规则请求 DTO
 */
@Data
public class UpdateAlertRuleRequest {

    private String name;

    private String description;

    private String ruleType;

    private String scopeType;

    private List<String> categoryCodes;

    private List<String> datasourceIds;

    private List<String> tableNames;

    private AlertConditionDTO condition;

    private AlertThresholdsDTO thresholds;

    private AlertMonitorOptionsDTO monitorOptions;

    private String evalEvery;

    private Integer consecutiveHits;

    private String severity;

    private List<String> notificationChannels;

    private String messageTemplate;

    private Integer silencePeriod;

    private Boolean enabled;
}
