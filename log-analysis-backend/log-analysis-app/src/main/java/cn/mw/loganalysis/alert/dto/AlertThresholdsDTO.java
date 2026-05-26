package cn.mw.loganalysis.alert.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Monitor 的 critical / warning / recovery 阈值集合。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertThresholdsDTO {

    private AlertThresholdDTO critical;

    private AlertThresholdDTO warning;

    private AlertThresholdDTO recovery;
}
