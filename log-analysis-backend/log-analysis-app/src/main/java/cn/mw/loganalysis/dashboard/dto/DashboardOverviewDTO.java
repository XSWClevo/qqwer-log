package cn.mw.loganalysis.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dashboard 概览聚合 DTO
 * 一次请求返回所有仪表盘数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardOverviewDTO {
    
    /** 系统指标 */
    private SystemMetricsDTO systemMetrics;
    
    /** 日志趋势 */
    private LogTrendDTO logTrend;
    
    /** 日志管道 */
    private LogPipelineDTO logPipeline;
    
    /** 核心概览 */
    private CoreOverviewDTO coreOverview;
    
    /** 数据库状态 */
    private DatabaseStatusDTO databaseStatus;
    
    /** Top 主机 */
    private TopEntityDTO topHosts;
    
    /** Top 应用 */
    private TopEntityDTO topApps;
    
    /** 重复异常 */
    private RecurringExceptionDTO recurringExceptions;
    
    /** 告警日志 */
    private AlertLogDTO alertLogs;
    
    /** 追踪ID */
    private String traceId;
}
