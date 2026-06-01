package cn.mw.loganalysis.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 日志级别分布项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSeverityDistributionItemDTO {

    private String severity;

    private Long count;

    private String color;
}
