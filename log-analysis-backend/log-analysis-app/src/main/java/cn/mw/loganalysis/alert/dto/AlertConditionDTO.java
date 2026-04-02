package cn.mw.loganalysis.alert.dto;

import lombok.Data;

import java.util.List;

/**
 * 结构化告警条件
 */
@Data
public class AlertConditionDTO {

    /**
     * 兼容旧版的原始查询表达式
     */
    private String query;

    private AlertFilterDTO filters;

    private AlertAggregateDTO aggregate;

    private List<String> groupBy;

    private AlertTriggerDTO trigger;
}
