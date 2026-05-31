package cn.mw.loganalysis.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Dashboard 总览响应。
 * 首页只消费这一份聚合结构，避免前端再拼装旧 syslog 风格数据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardOverviewDTO {

    /**
     * 当前默认选中的日志数据集上下文。
     */
    private DashboardDatasetContextDTO datasetContext;

    /**
     * 当前可切换的数据集列表。
     */
    private List<DashboardDatasetContextDTO> availableDatasets;

    /**
     * 平台级健康指标，不依赖具体日志表。
     */
    private PlatformHealthDTO platformHealth;

    /**
     * 当前数据集在查询时间范围内的关键指标。
     */
    private DashboardLogKpisDTO logKpis;

    /**
     * 日志趋势。
     */
    private LogTrendDTO logTrend;

    /**
     * 级别分布。
     */
    private DashboardDistributionDTO severityDistribution;

    /**
     * Top 主机排行。
     */
    private DashboardTopListDTO topHosts;

    /**
     * Top 应用排行。
     */
    private DashboardTopListDTO topApps;

    /**
     * 高频错误消息。
     */
    private DashboardTopListDTO topErrorMessages;

    /**
     * 最近高风险日志。
     */
    private DashboardRecentLogsDTO recentHighRiskLogs;

    /**
     * 空状态描述。
     */
    private DashboardEmptyStateDTO emptyState;

    /**
     * 局部降级或数据提示。
     */
    private List<DashboardWarningDTO> warnings;

    /**
     * 追踪 ID。
     */
    private String traceId;

    /**
     * 当前数据集支持的展示能力。
     */
    private List<DashboardCapabilityDTO> capabilities;

    /**
     * 指标卡可用的下钻定义。
     */
    private List<DashboardMetricDrilldownDTO> metricDrilldowns;
}
