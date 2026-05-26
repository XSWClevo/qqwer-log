package cn.mw.loganalysis.alert.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 结构化告警条件
 */
@Data
public class AlertConditionDTO {

    private String query;

    private Map<String, Object> filters;

    private AlertAggregateDTO aggregate;

    private List<String> groupBy;

    private AlertTriggerDTO trigger;

    private AlertMonitorOptionsDTO options;
}
