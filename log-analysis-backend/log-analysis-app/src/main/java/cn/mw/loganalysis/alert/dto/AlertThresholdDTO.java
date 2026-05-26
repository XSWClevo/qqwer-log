package cn.mw.loganalysis.alert.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 单个 Monitor 阈值定义。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertThresholdDTO {

    private String level;

    private String operator;

    private BigDecimal threshold;

    private String timeWindow;
}
