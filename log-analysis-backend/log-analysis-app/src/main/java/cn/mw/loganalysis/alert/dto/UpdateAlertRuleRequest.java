package cn.mw.loganalysis.alert.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 更新告警规则请求 DTO
 */
@Data
public class UpdateAlertRuleRequest {
    
    private String name;
    
    private String description;
    
    private Map<String, Object> condition;
    
    private String severity;
    
    private List<String> notificationChannels;
    
    private Integer silencePeriod;
    
    private Boolean enabled;
}
