package cn.mw.loganalysis.alert.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 触发定义
 */
@Data
public class AlertTriggerDTO {

    private String operator;

    private BigDecimal threshold;

    private String timeWindow;
}
