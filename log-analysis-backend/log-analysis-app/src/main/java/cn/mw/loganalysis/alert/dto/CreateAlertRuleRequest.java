package cn.mw.loganalysis.alert.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 创建告警规则请求DTO
 */
@Data
public class CreateAlertRuleRequest {

    @NotBlank(message = "规则名称不能为空")
    private String name;

    private String description;

    private String ruleType = "aggregation";

    private String scopeType = "all";

    private List<String> categoryCodes;

    private List<String> datasourceIds;

    private List<String> tableNames;

    @NotNull(message = "告警条件不能为空")
    private Map<String, Object> condition;

    private String evalEvery = "1m";

    private Integer consecutiveHits = 1;

    @NotBlank(message = "告警级别不能为空")
    private String severity;

    private List<String> notificationChannels;

    private List<String> dedupKeyFields;

    private String messageTemplate;

    private Integer silencePeriod = 300;

    private Boolean enabled = true;
}
