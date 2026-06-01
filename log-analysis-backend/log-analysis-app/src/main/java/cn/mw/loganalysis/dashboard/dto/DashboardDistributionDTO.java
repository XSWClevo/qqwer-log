package cn.mw.loganalysis.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Dashboard 分布类面板。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardDistributionDTO {

    private List<DashboardSeverityDistributionItemDTO> items;
}
