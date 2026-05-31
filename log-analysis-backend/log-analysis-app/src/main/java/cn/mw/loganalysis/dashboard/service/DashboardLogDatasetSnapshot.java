package cn.mw.loganalysis.dashboard.service;

import cn.mw.loganalysis.dashboard.dto.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 当前日志数据集的一次聚合快照。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardLogDatasetSnapshot {

    private DashboardLogKpisDTO logKpis;

    private List<DashboardCapabilityDTO> capabilities;

    private List<DashboardMetricDrilldownDTO> metricDrilldowns;

    private LogTrendDTO logTrend;

    private DashboardDistributionDTO severityDistribution;

    private DashboardTopListDTO topHosts;

    private DashboardTopListDTO topApps;

    private DashboardTopListDTO topErrorMessages;

    private DashboardRecentLogsDTO recentHighRiskLogs;

    private DashboardEmptyStateDTO emptyState;
}
