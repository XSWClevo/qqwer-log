package cn.mw.loganalysis.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dashboard 空状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardEmptyStateDTO {

    private String title;

    private String description;

    private String actionLabel;

    private String actionRoute;
}
