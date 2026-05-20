package cn.mw.loganalysis.dashboard.service;

import cn.mw.loganalysis.dashboard.dto.*;
import cn.mw.loganalysis.dashboard.entity.*;
import cn.mw.loganalysis.dashboard.mapper.DashboardMapper;
import com.baomidou.dynamic.datasource.annotation.DS;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Dashboard 服务 - 提供仪表盘统计数据
 * 使用 MyBatis Mapper 进行数据库查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
@DS("clickhouse")
public class DashboardService {

    private final DashboardMapper dashboardMapper;
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final SystemInfo systemInfo = new SystemInfo();

    /**
     * CPU 使用率后台采样器
     * 每 3 秒采样一次，接口直接读取最新值，无需 sleep 等待
     */
    private static final AtomicReference<Double> latestCpuUsage = new AtomicReference<>(0.0);
    private static volatile long[] prevCpuTicks;
    private static Thread cpuSamplerThread;

    @PostConstruct
    public void startCpuSampler() {
        CentralProcessor processor = systemInfo.getHardware().getProcessor();
        prevCpuTicks = processor.getSystemCpuLoadTicks();

        cpuSamplerThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(3000);
                    long[] currentTicks = processor.getSystemCpuLoadTicks();
                    double cpuLoad = processor.getSystemCpuLoadBetweenTicks(prevCpuTicks) * 100;
                    latestCpuUsage.set(Math.round(cpuLoad * 100.0) / 100.0);
                    prevCpuTicks = currentTicks;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "cpu-sampler");
        cpuSamplerThread.setDaemon(true);
        cpuSamplerThread.start();
        log.info("CPU 后台采样器已启动 (间隔3秒)");
    }

    @PreDestroy
    public void stopCpuSampler() {
        if (cpuSamplerThread != null) {
            cpuSamplerThread.interrupt();
        }
    }

    // ==================== 1. 系统指标 ====================

    @Cacheable(value = "systemMetrics", key = "'metrics'", unless = "#result == null")
    public SystemMetricsDTO getSystemMetrics() {
        log.info("Fetching system metrics...");
        
        HardwareAbstractionLayer hal = systemInfo.getHardware();
        
        return SystemMetricsDTO.builder()
                .cpu(getCpuMetrics(hal.getProcessor()))
                .memory(getMemoryMetrics(hal.getMemory()))
                .disk(getDiskMetrics())
                .clickhouseStorage(getClickHouseStorageMetrics())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    private SystemMetricsDTO.CpuMetrics getCpuMetrics(CentralProcessor processor) {
        double[] loadAverage = processor.getSystemLoadAverage(1);

        return SystemMetricsDTO.CpuMetrics.builder()
                .usagePercent(latestCpuUsage.get())
                .cores(processor.getLogicalProcessorCount())
                .loadAverage(loadAverage.length > 0 ? loadAverage[0] : 0.0)
                .build();
    }

    private SystemMetricsDTO.MemoryMetrics getMemoryMetrics(GlobalMemory memory) {
        long total = memory.getTotal();
        long available = memory.getAvailable();
        long used = total - available;
        double usagePercent = (double) used / total * 100;
        
        return SystemMetricsDTO.MemoryMetrics.builder()
                .total(total).used(used).available(available)
                .usagePercent(Math.round(usagePercent * 100.0) / 100.0)
                .build();
    }

    private SystemMetricsDTO.DiskMetrics getDiskMetrics() {
        FileSystem fileSystem = systemInfo.getOperatingSystem().getFileSystem();
        List<OSFileStore> fileStores = fileSystem.getFileStores();

        // 过滤虚拟文件系统，只保留真实本地磁盘分区
        // macOS APFS 容器中多个卷（Data、System等）共享同一块物理磁盘空间，
        // 它们的 totalSpace 完全相同，需要按 totalSpace 去重，只计算一次
        Set<Long> seenTotalSizes = new HashSet<>();
        long totalSpace = 0, usableSpace = 0;

        for (OSFileStore store : fileStores) {
            String type = store.getType();
            String mount = store.getMount();

            // 跳过虚拟文件系统和非本地文件系统
            if (isVirtualFileSystem(type, mount)) {
                continue;
            }

            long storeTotalSpace = store.getTotalSpace();
            if (storeTotalSpace <= 0) {
                continue;
            }

            // APFS 容器的多个卷共享相同的 totalSpace，只统计一次
            if (!seenTotalSizes.add(storeTotalSpace)) {
                continue;
            }

            totalSpace += storeTotalSpace;
            usableSpace += store.getUsableSpace();
        }

        long usedSpace = totalSpace - usableSpace;
        double usagePercent = totalSpace > 0 ? (double) usedSpace / totalSpace * 100 : 0;

        return SystemMetricsDTO.DiskMetrics.builder()
                .total(totalSpace).used(usedSpace).available(usableSpace)
                .usagePercent(Math.round(usagePercent * 100.0) / 100.0)
                .build();
    }

    /**
     * 判断是否为虚拟/伪文件系统或非主磁盘挂载
     * 只保留根分区(/)和数据卷(/System/Volumes/Data)作为主磁盘
     */
    private boolean isVirtualFileSystem(String fsType, String mountPoint) {
        if (org.apache.commons.lang3.StringUtils.isBlank(fsType)
                || org.apache.commons.lang3.StringUtils.isBlank(mountPoint)) {
            return true;
        }

        // 常见虚拟文件系统类型
        String typeLower = fsType.toLowerCase();
        if (typeLower.equals("devfs") || typeLower.equals("tmpfs") || typeLower.equals("proc")
                || typeLower.equals("sysfs") || typeLower.equals("devtmpfs")
                || typeLower.equals("overlay") || typeLower.equals("squashfs")
                || typeLower.equals("autofs") || typeLower.equals("nullfs")
                || typeLower.equals("fusefs") || typeLower.equals("osxfuse")) {
            return true;
        }

        // 只保留根分区和 /System/Volumes/Data，其他全部跳过
        // 这确保 macOS APFS 只统计一次主磁盘
        if (mountPoint.equals("/") || mountPoint.equals("/System/Volumes/Data")) {
            return false;
        }

        // Linux: 只保留挂载在 / 或 /home、/data 等常见数据目录的分区
        if (mountPoint.equals("/home") || mountPoint.equals("/data")
                || mountPoint.equals("/var") || mountPoint.equals("/opt")) {
            return false;
        }

        // 其他所有挂载点视为非主磁盘（包括 /Volumes/*, /System/Volumes/VM,
        // /System/Volumes/Preboot, OrbStack, AppTranslocation, DMG 等）
        return true;
    }

    private SystemMetricsDTO.StorageMetrics getClickHouseStorageMetrics() {
        try {
            Long tableSize = dashboardMapper.getTableStorageSize();
            Long totalRows = dashboardMapper.getTotalRowCount();
            Integer partitionCount = dashboardMapper.getPartitionCount();
            
            return SystemMetricsDTO.StorageMetrics.builder()
                    .syslogTableSize(tableSize != null ? tableSize : 0L)
                    .totalRows(totalRows != null ? totalRows : 0L)
                    .partitionCount(partitionCount != null ? partitionCount : 0)
                    .formattedSize(formatBytes(tableSize != null ? tableSize : 0L))
                    .build();
        } catch (Exception e) {
            log.error("Failed to get ClickHouse storage metrics", e);
            return SystemMetricsDTO.StorageMetrics.builder()
                    .syslogTableSize(0L).totalRows(0L).partitionCount(0).formattedSize("N/A").build();
        }
    }

    // ==================== 2. 日志趋势 ====================

    @Cacheable(value = "logTrend", key = "'latest'", unless = "#result == null")
    public LogTrendDTO getLogTrend(String startTime, String endTime, String granularity) {
        log.info("Fetching log trend from {} to {} with granularity {}", startTime, endTime, granularity);
        
        String actualGranularity = determineGranularity(startTime, endTime, granularity);
        String intervalFunc = getIntervalFunction(actualGranularity);
        
        List<LogTrendRow> rawData = dashboardMapper.getLogTrendByInterval(startTime, endTime, intervalFunc);
        return transformToTrendDTO(rawData, actualGranularity);
    }

    private String determineGranularity(String startTime, String endTime, String granularity) {
        if (granularity != null && !"auto".equals(granularity)) return granularity;
        
        try {
            LocalDateTime start = LocalDateTime.parse(startTime, FORMATTER);
            LocalDateTime end = LocalDateTime.parse(endTime, FORMATTER);
            long hours = Duration.between(start, end).toHours();
            
            if (hours <= 1) return "1m";
            else if (hours <= 6) return "5m";
            else return "1h";
        } catch (Exception e) {
            return "1h";
        }
    }

    private String getIntervalFunction(String granularity) {
        return switch (granularity) {
            case "1m" -> "toStartOfMinute(timestamp)";
            case "5m" -> "toStartOfFiveMinutes(timestamp)";
            case "1h" -> "toStartOfHour(timestamp)";
            case "1d" -> "toStartOfDay(timestamp)";
            default -> "toStartOfHour(timestamp)";
        };
    }

    private LogTrendDTO transformToTrendDTO(List<LogTrendRow> rawData, String granularity) {
        Set<String> timestampSet = new LinkedHashSet<>();
        Map<String, Map<String, Long>> dataMap = new HashMap<>();
        
        for (LogTrendRow row : rawData) {
            String timestamp = row.getTimeBucket();
            String severity = normalizeSeverity(row.getSeverity() != null ? row.getSeverity() : "UNKNOWN");
            Long count = row.getCnt() != null ? row.getCnt() : 0L;
            
            timestampSet.add(timestamp);
            dataMap.computeIfAbsent(severity, k -> new HashMap<>()).merge(timestamp, count, Long::sum);
        }
        
        List<String> timestamps = new ArrayList<>(timestampSet);
        List<LogTrendDTO.LevelSeries> series = new ArrayList<>();
        long totalCount = 0;
        
        for (String level : dataMap.keySet()) {
            Map<String, Long> levelData = dataMap.get(level);
            List<Long> data = timestamps.stream().map(ts -> levelData.getOrDefault(ts, 0L)).collect(Collectors.toList());
            long levelTotal = data.stream().mapToLong(Long::longValue).sum();
            
            series.add(LogTrendDTO.LevelSeries.builder().severity(level).data(data).total(levelTotal).build());
            totalCount += levelTotal;
        }
        
        return LogTrendDTO.builder().granularity(granularity).timestamps(timestamps).series(series).totalCount(totalCount).build();
    }

    // ==================== 3. Top 实体 ====================

    @Cacheable(value = "topEntities", key = "#type", unless = "#result == null")
    public TopEntityDTO getTopEntities(String type, String startTime, String endTime) {
        log.info("Fetching top {} from {} to {}", type, startTime, endTime);
        
        List<TopEntityRow> rawData = "host".equalsIgnoreCase(type) 
                ? dashboardMapper.getTopHosts(startTime, endTime, 10)
                : dashboardMapper.getTopApps(startTime, endTime, 10);
        
        Long total = dashboardMapper.getLogCountInRange(startTime, endTime);
        total = total != null ? total : 1L;
        
        String field = "host".equalsIgnoreCase(type) ? "hostname" : "appname";
        Long totalEntities = dashboardMapper.getDistinctEntityCount(field, startTime, endTime);
        
        final long finalTotal = total;
        List<TopEntityDTO.EntityCount> items = rawData.stream()
                .map(row -> {
                    String name = row.getName() != null ? row.getName() : "unknown";
                    Long count = row.getCnt() != null ? row.getCnt() : 0L;
                    double percentage = (double) count / finalTotal * 100;
                    return TopEntityDTO.EntityCount.builder()
                            .name(name).count(count).percentage(Math.round(percentage * 100.0) / 100.0).build();
                })
                .collect(Collectors.toList());
        
        return TopEntityDTO.builder().type(type).items(items).totalEntities(totalEntities).build();
    }

    // ==================== 4. 重复异常 ====================

    @Cacheable(value = "recurringExceptions", key = "'latest'", unless = "#result == null")
    public RecurringExceptionDTO getRecurringExceptions(String startTime, String endTime) {
        log.info("Fetching recurring exceptions from {} to {}", startTime, endTime);
        
        List<RecurringExceptionRow> rawData = dashboardMapper.getRecurringExceptions(startTime, endTime, 50);
        
        long totalOccurrences = 0;
        List<RecurringExceptionDTO.ExceptionItem> items = new ArrayList<>();
        
        for (RecurringExceptionRow row : rawData) {
            Long count = row.getCnt() != null ? row.getCnt() : 0L;
            totalOccurrences += count;
            
            String messageSummary = row.getMsgSummary() != null ? row.getMsgSummary() : "";
            
            items.add(RecurringExceptionDTO.ExceptionItem.builder()
                    .messageHash(row.getMsgHash() != null ? row.getMsgHash() : "")
                    .messageSummary(messageSummary)
                    .exceptionClassName(extractExceptionClassName(messageSummary))
                    .service(row.getService() != null ? row.getService() : "unknown")
                    .count(count)
                    .firstSeen(row.getFirstSeen() != null ? row.getFirstSeen() : "")
                    .lastSeen(row.getLastSeen() != null ? row.getLastSeen() : "")
                    .affectedHosts(row.getAffectedHosts() != null ? row.getAffectedHosts() : 0)
                    .affectedApps(row.getAffectedApps() != null ? row.getAffectedApps() : 0)
                    .severity(row.getSeverity() != null ? row.getSeverity() : "ERROR")
                    .build());
        }
        
        return RecurringExceptionDTO.builder().items(items).totalTypes(items.size()).totalOccurrences(totalOccurrences).build();
    }

    // ==================== 5. 告警日志 ====================

    public AlertLogDTO getLatestAlertLogs(String startTime, String endTime, Integer pageNum, Integer pageSize) {
        log.info("Fetching latest alert logs from {} to {}, page {}, size {}", startTime, endTime, pageNum, pageSize);
        
        int offset = (pageNum - 1) * pageSize;
        List<AlertLogRow> rawData = dashboardMapper.getAlertLogs(startTime, endTime, offset, pageSize);
        Long total = dashboardMapper.getAlertLogsCount(startTime, endTime);
        
        List<AlertLogDTO.AlertLogItem> items = rawData.stream()
                .map(row -> AlertLogDTO.AlertLogItem.builder()
                        .id(row.getId() != null ? row.getId() : "")
                        .timestamp(row.getTimestamp() != null ? row.getTimestamp() : "")
                        .severity(row.getSeverity() != null ? row.getSeverity() : "")
                        .hostname(row.getHostname() != null ? row.getHostname() : "")
                        .appName(row.getAppName() != null ? row.getAppName() : "")
                        .message(row.getMessage() != null ? row.getMessage() : "")
                        .raw(row.getRaw() != null ? row.getRaw() : "")
                        .sourceType(row.getSourceType() != null ? row.getSourceType() : "")
                        .build())
                .collect(Collectors.toList());
        
        return AlertLogDTO.builder().items(items).total(total != null ? total : 0L).pageNum(pageNum).pageSize(pageSize).build();
    }

    // ==================== 6. 日志管道 ====================

    public LogPipelineDTO getLogPipeline(String startTime, String endTime) {
        log.info("Fetching log pipeline info from {} to {}", startTime, endTime);
        
        try {
            List<IngestRateTrendRow> trendData = dashboardMapper.getIngestRateTrend(startTime, endTime, 10);
            
            double currentRate = 0;
            List<LogPipelineDTO.RateTrend> trends = new ArrayList<>();
            
            for (IngestRateTrendRow row : trendData) {
                Long count = row.getCnt() != null ? row.getCnt() : 0L;
                String minute = row.getMinute() != null ? row.getMinute() : "";
                double rate = count / 60.0;
                
                trends.add(LogPipelineDTO.RateTrend.builder()
                        .timestamp(minute).count(count).rate(Math.round(rate * 100.0) / 100.0).build());
                
                if (trends.size() == 1) currentRate = rate;
            }
            
            Long backlog = dashboardMapper.getQueueBacklog(startTime, endTime);
            long processingDelay = currentRate > 0 ? (long) ((backlog != null ? backlog : 0) / currentRate) : 0;
            
            String status = processingDelay > 5000 ? "error" : processingDelay > 1000 ? "warning" : "healthy";
            
            return LogPipelineDTO.builder()
                    .ingestRatePerSecond(Math.round(currentRate * 100.0) / 100.0)
                    .ingestRateTrends(trends)
                    .processingDelayMs(processingDelay)
                    .queueBacklog(backlog != null ? backlog : 0L)
                    .status(status)
                    .build();
        } catch (Exception e) {
            log.error("Failed to get log pipeline info", e);
            return LogPipelineDTO.builder()
                    .ingestRatePerSecond(0.0).ingestRateTrends(new ArrayList<>())
                    .processingDelayMs(0L).queueBacklog(0L).status("unknown").build();
        }
    }

    // ==================== 7. 核心概览 ====================

    public CoreOverviewDTO getCoreOverview() {
        log.info("Fetching core overview");
        
        try {
            LocalDateTime now = LocalDateTime.now();
            String startTime = now.withHour(0).withMinute(0).withSecond(0).format(FORMATTER);
            String endTime = now.withHour(23).withMinute(59).withSecond(59).format(FORMATTER);
            
            List<LogCountByLevelRow> data = dashboardMapper.getLogCountByLevel(startTime, endTime);
            
            long infoCount = 0, warnCount = 0, errorCount = 0, fatalCount = 0, totalCount = 0;
            
            for (LogCountByLevelRow row : data) {
                String severity = row.getSeverity() != null ? row.getSeverity().toUpperCase() : "";
                Long count = row.getCnt() != null ? row.getCnt() : 0L;
                totalCount += count;
                
                switch (normalizeSeverity(severity)) {
                    case "INFO" -> infoCount += count;
                    case "WARN" -> warnCount += count;
                    case "ERROR" -> errorCount += count;
                    case "FATAL" -> fatalCount += count;
                }
            }
            
            double infoRate = totalCount > 0 ? (double) infoCount / totalCount * 100 : 0;
            double warnRate = totalCount > 0 ? (double) warnCount / totalCount * 100 : 0;
            double errorRate = totalCount > 0 ? (double) errorCount / totalCount * 100 : 0;
            double fatalRate = totalCount > 0 ? (double) fatalCount / totalCount * 100 : 0;
            long exceptionCount = errorCount + fatalCount;
            double exceptionRate = totalCount > 0 ? (double) exceptionCount / totalCount * 100 : 0;
            
            return CoreOverviewDTO.builder()
                    .todayTotalLogs(totalCount)
                    .infoCount(infoCount).warnCount(warnCount).errorCount(errorCount).fatalCount(fatalCount)
                    .infoRate(Math.round(infoRate * 100.0) / 100.0)
                    .warnRate(Math.round(warnRate * 100.0) / 100.0)
                    .errorRate(Math.round(errorRate * 100.0) / 100.0)
                    .fatalRate(Math.round(fatalRate * 100.0) / 100.0)
                    .exceptionCount(exceptionCount)
                    .exceptionRate(Math.round(exceptionRate * 100.0) / 100.0)
                    .build();
        } catch (Exception e) {
            log.error("Failed to get core overview", e);
            return CoreOverviewDTO.builder()
                    .todayTotalLogs(0L).infoCount(0L).warnCount(0L).errorCount(0L).fatalCount(0L)
                    .infoRate(0.0).warnRate(0.0).errorRate(0.0).fatalRate(0.0)
                    .exceptionCount(0L).exceptionRate(0.0).build();
        }
    }

    // ==================== 8. 数据库状态 ====================

    public DatabaseStatusDTO getDatabaseStatus() {
        log.info("Fetching database status");
        
        try {
            StorageInfoRow storageData = dashboardMapper.getStorageInfo();
            Long usedBytes = storageData != null && storageData.getUsedBytes() != null ? storageData.getUsedBytes() : 0L;
            Integer partitionCount = storageData != null && storageData.getPartitionCount() != null ? storageData.getPartitionCount() : 0;
            Long totalRows = storageData != null && storageData.getTotalRows() != null ? storageData.getTotalRows() : 0L;
            
            long totalBytes = 1024L * 1024L * 1024L * 1024L; // 1TB
            double usagePercent = (double) usedBytes / totalBytes * 100;
            
            QueryStatsRow tpsData = dashboardMapper.getQueryStats();
            Long queryCount = tpsData != null && tpsData.getQueryCount() != null ? tpsData.getQueryCount() : 0L;
            Long insertCount = tpsData != null && tpsData.getInsertCount() != null ? tpsData.getInsertCount() : 0L;
            
            Integer activeConnections = dashboardMapper.getActiveConnections();
            
            String clusterStatus = usagePercent > 90 ? "error" : usagePercent > 80 ? "warning" : "healthy";
            
            return DatabaseStatusDTO.builder()
                    .clusterStatus(clusterStatus)
                    .storageUsedGb(Math.round((double) usedBytes / (1024 * 1024 * 1024) * 100.0) / 100.0)
                    .storageTotalGb(1024.0)
                    .storageUsagePercent(Math.round(usagePercent * 100.0) / 100.0)
                    .queryTps(Math.round(queryCount / 60.0 * 100.0) / 100.0)
                    .insertTps(Math.round(insertCount / 60.0 * 100.0) / 100.0)
                    .activeConnections(activeConnections != null ? activeConnections : 0)
                    .replicaCount(1).shardCount(1)
                    .totalRows(totalRows).totalPartitions(partitionCount)
                    .lastUpdateTime(LocalDateTime.now().format(FORMATTER))
                    .build();
        } catch (Exception e) {
            log.error("Failed to get database status", e);
            return DatabaseStatusDTO.builder()
                    .clusterStatus("unknown").storageUsedGb(0.0).storageTotalGb(1024.0).storageUsagePercent(0.0)
                    .queryTps(0.0).insertTps(0.0).activeConnections(0)
                    .replicaCount(1).shardCount(1).totalRows(0L).totalPartitions(0)
                    .lastUpdateTime(LocalDateTime.now().format(FORMATTER)).build();
        }
    }

    // ==================== 异步方法 ====================

    @Async
    public CompletableFuture<SystemMetricsDTO> getSystemMetricsAsync() {
        return CompletableFuture.completedFuture(getSystemMetrics());
    }

    @Async
    public CompletableFuture<LogTrendDTO> getLogTrendAsync(String startTime, String endTime, String granularity) {
        return CompletableFuture.completedFuture(getLogTrend(startTime, endTime, granularity));
    }

    @Async
    public CompletableFuture<TopEntityDTO> getTopEntitiesAsync(String type, String startTime, String endTime) {
        return CompletableFuture.completedFuture(getTopEntities(type, startTime, endTime));
    }

    @Async
    public CompletableFuture<RecurringExceptionDTO> getRecurringExceptionsAsync(String startTime, String endTime) {
        return CompletableFuture.completedFuture(getRecurringExceptions(startTime, endTime));
    }

    @Async
    public CompletableFuture<AlertLogDTO> getLatestAlertLogsAsync(String startTime, String endTime, Integer pageNum, Integer pageSize) {
        return CompletableFuture.completedFuture(getLatestAlertLogs(startTime, endTime, pageNum, pageSize));
    }

    @Async
    public CompletableFuture<LogPipelineDTO> getLogPipelineAsync(String startTime, String endTime) {
        return CompletableFuture.completedFuture(getLogPipeline(startTime, endTime));
    }

    @Async
    public CompletableFuture<CoreOverviewDTO> getCoreOverviewAsync() {
        return CompletableFuture.completedFuture(getCoreOverview());
    }

    @Async
    public CompletableFuture<DatabaseStatusDTO> getDatabaseStatusAsync() {
        return CompletableFuture.completedFuture(getDatabaseStatus());
    }

    // ==================== 工具方法 ====================

    private String normalizeSeverity(String severity) {
        if (severity == null) return "UNKNOWN";
        return switch (severity.toUpperCase()) {
            case "INFO", "INFORMATIONAL" -> "INFO";
            case "WARN", "WARNING" -> "WARN";
            case "ERROR", "ERR" -> "ERROR";
            case "FATAL", "CRITICAL", "CRIT", "EMERG", "EMERGENCY", "ALERT" -> "FATAL";
            case "DEBUG", "TRACE" -> "DEBUG";
            default -> severity.toUpperCase();
        };
    }

    private String extractExceptionClassName(String message) {
        if (message == null || message.isEmpty()) return "Unknown";
        
        String[] patterns = {"([a-zA-Z0-9.]*Exception)", "([a-zA-Z0-9.]*Error)", "([a-zA-Z0-9.]*Throwable)"};
        
        for (String patternStr : patterns) {
            Matcher m = Pattern.compile(patternStr).matcher(message);
            if (m.find()) {
                String className = m.group(1);
                int lastDot = className.lastIndexOf('.');
                return lastDot >= 0 ? className.substring(lastDot + 1) : className;
            }
        }
        return "Unknown";
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.2f %s", bytes / Math.pow(1024, exp), pre);
    }
}
