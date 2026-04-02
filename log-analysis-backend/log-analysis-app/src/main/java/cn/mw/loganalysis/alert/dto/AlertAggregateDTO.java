package cn.mw.loganalysis.alert.dto;

import lombok.Data;

/**
 * 聚合定义
 */
@Data
public class AlertAggregateDTO {

    private String function;

    private String field;
}
