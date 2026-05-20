package cn.mw.loganalysis.dashboard.controller;

import cn.mw.loganalysis.common.response.Result;
import cn.mw.loganalysis.dashboard.dto.*;
import cn.mw.loganalysis.dashboard.service.DashboardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Dashboard 控制器 - 提供仪表盘统计 API
 */
@Slf4j
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    @Qualifier("dashboardTaskExecutor")
    @Autowired
    private Executor dashboardExecutor;

    /**
     * 获取系统指标
     * 包含 CPU、内存、磁盘、ClickHouse 存储信息
     */
    @GetMapping("/metrics")
    public Result<SystemMetricsDTO> getSystemMetrics() {
        String traceId = generateTraceId();
        log.info("[{}] Fetching system metrics", traceId);
        
        try {
            SystemMetricsDTO metrics = dashboardService.getSystemMetrics();
            return Result.success(metrics);
        } catch (Exception e) {
            log.error("[{}] Failed to fetch system metrics", traceId, e);
            return Result.error("获取系统指标失败: " + e.getMessage());
        }
    }

    /**
     * 获取日志趋势
     * 按时间粒度和日志级别聚合，适合堆叠柱状图展示
     * 
     * @param startTime 开始时间 (yyyy-MM-dd HH:mm:ss)
     * @param endTime 结束时间 (yyyy-MM-dd HH:mm:ss)
     * @param granularity 时间粒度 (auto/1m/5m/1h/1d)，默认 auto
     */
    @GetMapping("/log-trend")
    public Result<LogTrendDTO> getLogTrend(
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(defaultValue = "auto") String granularity) {
        String traceId = generateTraceId();
        log.info("[{}] Fetching log trend: {} to {}, granularity={}", traceId, startTime, endTime, granularity);
        
        try {
            LogTrendDTO trend = dashboardService.getLogTrend(startTime, endTime, granularity);
            return Result.success(trend);
        } catch (Exception e) {
            log.error("[{}] Failed to fetch log trend", traceId, e);
            return Result.error("获取日志趋势失败: " + e.getMessage());
        }
    }

    /**
     * 获取 Top 实体 (主机或应用)
     * 
     * @param type 实体类型: host (主机) 或 app (应用)
     * @param startTime 开始时间
     * @param endTime 结束时间
     */
    @GetMapping("/top-entities")
    public Result<TopEntityDTO> getTopEntities(
            @RequestParam String type,
            @RequestParam String startTime,
            @RequestParam String endTime) {
        String traceId = generateTraceId();
        log.info("[{}] Fetching top {}: {} to {}", traceId, type, startTime, endTime);
        
        // 参数校验
        if (!"host".equalsIgnoreCase(type) && !"app".equalsIgnoreCase(type)) {
            return Result.badRequest("type 参数必须是 'host' 或 'app'");
        }
        
        try {
            TopEntityDTO topEntities = dashboardService.getTopEntities(type, startTime, endTime);
            return Result.success(topEntities);
        } catch (Exception e) {
            log.error("[{}] Failed to fetch top entities", traceId, e);
            return Result.error("获取 Top 实体失败: " + e.getMessage());
        }
    }

    /**
     * 获取重复出现的异常
     * 聚合相同的异常消息，统计出现频率
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     */
    @GetMapping("/recurring-exceptions")
    public Result<RecurringExceptionDTO> getRecurringExceptions(
            @RequestParam String startTime,
            @RequestParam String endTime) {
        String traceId = generateTraceId();
        log.info("[{}] Fetching recurring exceptions: {} to {}", traceId, startTime, endTime);
        
        try {
            RecurringExceptionDTO exceptions = dashboardService.getRecurringExceptions(startTime, endTime);
            return Result.success(exceptions);
        } catch (Exception e) {
            log.error("[{}] Failed to fetch recurring exceptions", traceId, e);
            return Result.error("获取重复异常失败: " + e.getMessage());
        }
    }

    /**
     * 获取最新告警日志
     * 分页查询 ERROR 和 WARN 级别的日志
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param pageNum 页码 (从1开始)
     * @param pageSize 每页大小
     */
    @GetMapping("/alert-logs")
    public Result<AlertLogDTO> getLatestAlertLogs(
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        String traceId = generateTraceId();
        log.info("[{}] Fetching alert logs: {} to {}, page={}, size={}", 
                 traceId, startTime, endTime, pageNum, pageSize);
        
        // 参数校验
        if (pageNum < 1) pageNum = 1;
        if (pageSize < 1 || pageSize > 100) pageSize = 20;
        
        try {
            AlertLogDTO alertLogs = dashboardService.getLatestAlertLogs(startTime, endTime, pageNum, pageSize);
            return Result.success(alertLogs);
        } catch (Exception e) {
            log.error("[{}] Failed to fetch alert logs", traceId, e);
            return Result.error("获取告警日志失败: " + e.getMessage());
        }
    }

    /**
     * 获取日志管道信息
     * 包含摄入速率、处理延迟、队列堆积等
     */
    @GetMapping("/log-pipeline")
    public Result<LogPipelineDTO> getLogPipeline(
            @RequestParam String startTime,
            @RequestParam String endTime) {
        String traceId = generateTraceId();
        log.info("[{}] Fetching log pipeline: {} to {}", traceId, startTime, endTime);
        
        try {
            LogPipelineDTO pipeline = dashboardService.getLogPipeline(startTime, endTime);
            return Result.success(pipeline);
        } catch (Exception e) {
            log.error("[{}] Failed to fetch log pipeline", traceId, e);
            return Result.error("获取日志管道信息失败: " + e.getMessage());
        }
    }

    /**
     * 获取核心概览
     * 包含今日统计和错误率分布
     */
    @GetMapping("/core-overview")
    public Result<CoreOverviewDTO> getCoreOverview() {
        String traceId = generateTraceId();
        log.info("[{}] Fetching core overview", traceId);
        
        try {
            CoreOverviewDTO overview = dashboardService.getCoreOverview();
            return Result.success(overview);
        } catch (Exception e) {
            log.error("[{}] Failed to fetch core overview", traceId, e);
            return Result.error("获取核心概览失败: " + e.getMessage());
        }
    }

    /**
     * 获取数据库状态
     * 包含 ClickHouse 集群状态、存储、TPS 等
     */
    @GetMapping("/database-status")
    public Result<DatabaseStatusDTO> getDatabaseStatus() {
        String traceId = generateTraceId();
        log.info("[{}] Fetching database status", traceId);
        
        try {
            DatabaseStatusDTO status = dashboardService.getDatabaseStatus();
            return Result.success(status);
        } catch (Exception e) {
            log.error("[{}] Failed to fetch database status", traceId, e);
            return Result.error("获取数据库状态失败: " + e.getMessage());
        }
    }

    /**
     * 获取 Dashboard 概览数据 (聚合多个接口)
     * 一次请求获取所有仪表盘数据，减少前端请求次数
     */
    @PostMapping("/overview")
    public Result<DashboardOverviewDTO> getDashboardOverview(@Valid @RequestBody DashboardQueryRequest request) {
        String traceId = generateTraceId();
        log.info("[{}] Fetching dashboard overview: {} to {}", traceId, request.getStartTime(), request.getEndTime());
        
        try {
            String startTime = request.getStartTime();
            String endTime = request.getEndTime();

            // 使用 supplyAsync + 线程池并行执行所有查询
            // 不使用 @Async 是因为 @DS 动态数据源注解在异步线程中需要显式切换
            CompletableFuture<SystemMetricsDTO> metricsFuture = CompletableFuture.supplyAsync(
                    dashboardService::getSystemMetrics, dashboardExecutor);
            CompletableFuture<LogTrendDTO> trendFuture = CompletableFuture.supplyAsync(
                    () -> dashboardService.getLogTrend(startTime, endTime, request.getGranularity()), dashboardExecutor);
            CompletableFuture<TopEntityDTO> topHostsFuture = CompletableFuture.supplyAsync(
                    () -> dashboardService.getTopEntities("host", startTime, endTime), dashboardExecutor);
            CompletableFuture<TopEntityDTO> topAppsFuture = CompletableFuture.supplyAsync(
                    () -> dashboardService.getTopEntities("app", startTime, endTime), dashboardExecutor);
            CompletableFuture<RecurringExceptionDTO> exceptionsFuture = CompletableFuture.supplyAsync(
                    () -> dashboardService.getRecurringExceptions(startTime, endTime), dashboardExecutor);
            CompletableFuture<AlertLogDTO> alertLogsFuture = CompletableFuture.supplyAsync(
                    () -> dashboardService.getLatestAlertLogs(startTime, endTime, request.getPageNum(), request.getPageSize()), dashboardExecutor);
            CompletableFuture<LogPipelineDTO> pipelineFuture = CompletableFuture.supplyAsync(
                    () -> dashboardService.getLogPipeline(startTime, endTime), dashboardExecutor);
            CompletableFuture<CoreOverviewDTO> coreOverviewFuture = CompletableFuture.supplyAsync(
                    dashboardService::getCoreOverview, dashboardExecutor);
            CompletableFuture<DatabaseStatusDTO> dbStatusFuture = CompletableFuture.supplyAsync(
                    dashboardService::getDatabaseStatus, dashboardExecutor);

            // 等待所有并行任务完成
            CompletableFuture.allOf(
                    metricsFuture, trendFuture, topHostsFuture, topAppsFuture,
                    exceptionsFuture, alertLogsFuture, pipelineFuture,
                    coreOverviewFuture, dbStatusFuture
            ).join();

            // 组装结果
            DashboardOverviewDTO overview = DashboardOverviewDTO.builder()
                    .systemMetrics(metricsFuture.join())
                    .logTrend(trendFuture.join())
                    .logPipeline(pipelineFuture.join())
                    .coreOverview(coreOverviewFuture.join())
                    .databaseStatus(dbStatusFuture.join())
                    .topHosts(topHostsFuture.join())
                    .topApps(topAppsFuture.join())
                    .recurringExceptions(exceptionsFuture.join())
                    .alertLogs(alertLogsFuture.join())
                    .traceId(traceId)
                    .build();

            return Result.success(overview);
        } catch (Exception e) {
            log.error("[{}] Failed to fetch dashboard overview", traceId, e);
            return Result.error("获取仪表盘概览失败: " + e.getMessage());
        }
    }

    /**
     * 生成 TraceId
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
