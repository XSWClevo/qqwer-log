package cn.mw.loganalysis.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dashboard 指标下钻定义。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardMetricDrilldownDTO {

    private String metricKey;

    private String title;

    private String description;

    private String unit;
}
