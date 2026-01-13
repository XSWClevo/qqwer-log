package cn.mw.loganalysis.dashboard.service;

import cn.mw.loganalysis.dashboard.dto.*;
import cn.mw.loganalysis.dashboard.entity.*;
import cn.mw.loganalysis.dashboard.mapper.DashboardMapper;
import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
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
import java.util.stream.Collectors;

/**
 * Dashboard 服务 - 使用 MyBatis Mapper 实现
 * 可作为 DashboardService 的替代实现
 */
@Slf4j
@Service("dashboardMapperService")
@RequiredArgsConstructor
@DS("clickhouse")
public class DashboardMapperService {

    private final DashboardMapper dashboardMapper;
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final SystemInfo systemInfo = new SystemInfo();

    // ==================== 1. 系统指标 ====================

    /**
     * 获取系统指标 (CPU、内存、磁盘、ClickHouse存储)
     */
    @Cacheable(value = "systemMetrics", key = "'mapper_metrics'", unless = "#result == null")
    public SystemMetricsDTO getSystemMetrics() {
        log.info("Fetching system metrics via Mapper...");
        
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
        long[] prevTicks = processor.getSystemCpuLoadTicks();
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        double cpuLoad = processor.getSystemCpuLoadBetweenTicks(prevTicks) * 100;
        double[] loadAverage = processor.getSystemLoadAverage(1);
        
        return SystemMetricsDTO.CpuMetrics.builder()
                .usagePercent(Math.round(cpuLoad * 100.0) / 100.0)
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
                .total(total)
                .used(used)
                .available(available)
                .usagePercent(Math.round(usagePercent * 100.0) / 100.0)
                .build();
    }

    private SystemMetricsDTO.DiskMetrics getDiskMetrics() {
        FileSystem fileSystem = systemInfo.getOperatingSystem().getFileSystem();
        List<OSFileStore> fileStores = fileSystem.getFileStores();
        
        long totalSpace = 0, usableSpace = 0;
        for (OSFileStore store : fileStores) {
            totalSpace += store.getTotalSpace();
            usableSpace += store.getUsableSpace();
        }
        
        long usedSpace = totalSpace - usableSpace;
        double usagePercent = totalSpace > 0 ? (double) usedSpace / totalSpace * 100 : 0;
        
        return SystemMetricsDTO.DiskMetrics.builder()
                .total(totalSpace)
                .used(usedSpace)
                .available(usableSpace)
                .usagePercent(Math.round(usagePercent * 100.0) / 100.0)
                .build();
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
                    .syslogTableSize(0L).totalRows(0L).partitionCount(0).formattedSize("N/A")
                    .build();
        }
    }

    // ==================== 2. 日志趋势 ====================

    /**
     * 获取日志趋势 (按时间和级别聚合)
     */
    @Cacheable(value = "logTrend", key = "'mapper_' + #startTime + '_' + #endTime", unless = "#result == null")
    public LogTrendDTO getLogTrend(String startTime, String endTime, String granularity) {
        log.info("Fetching log trend via Mapper: {} to {}", startTime, endTime);
        
        String actualGranularity = determineGranularity(startTime, endTime, granularity);
        String intervalFunc = getIntervalFunction(actualGranularity);
        
        List<LogTrendRow> rawData = dashboardMapper.getLogTrendByInterval(startTime, endTime, intervalFunc);
        return transformToTrendDTO(rawData, actualGranularity);
    }

    private String determineGranularity(String startTime, String endTime, String granularity) {
        if (granularity != null && !"auto".equals(granularity)) {
            return granularity;
        }
        
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
        Set<String> levelSet = new LinkedHashSet<>();
        Map<String, Map<String, Long>> dataMap = new HashMap<>();
        
        for (LogTrendRow row : rawData) {
            String timestamp = row.getTimeBucket();
            String severity = row.getSeverity() != null ? row.getSeverity() : "UNKNOWN";
            Long count = row.getCnt() != null ? row.getCnt() : 0L;
            
            timestampSet.add(timestamp);
            levelSet.add(severity);
            dataMap.computeIfAbsent(severity, k -> new HashMap<>()).put(timestamp, count);
        }
        
        List<String> timestamps = new ArrayList<>(timestampSet);
        List<LogTrendDTO.LevelSeries> series = new ArrayList<>();
        long totalCount = 0;
        
        for (String severity : levelSet) {
            Map<String, Long> levelData = dataMap.getOrDefault(severity, Collections.emptyMap());
            List<Long> data = new ArrayList<>();
            long levelTotal = 0;
            
            for (String ts : timestamps) {
                Long count = levelData.getOrDefault(ts, 0L);
                data.add(count);
                levelTotal += count;
            }
            
            series.add(LogTrendDTO.LevelSeries.builder()
                    .severity(severity).data(data).total(levelTotal).build());
            totalCount += levelTotal;
        }
        
        return LogTrendDTO.builder()
                .granularity(granularity)
                .timestamps(timestamps)
                .series(series)
                .totalCount(totalCount)
                .build();
    }

    // ==================== 3. Top 实体 ====================

    /**
     * 获取 Top 主机或应用
     */
    @Cacheable(value = "topEntities", key = "'mapper_' + #type + '_' + #startTime + '_' + #endTime", unless = "#result == null")
    public TopEntityDTO getTopEntities(String type, String startTime, String endTime) {
        log.info("Fetching top {} via Mapper", type);
        
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
                            .name(name)
                            .count(count)
                            .percentage(Math.round(percentage * 100.0) / 100.0)
                            .build();
                })
                .collect(Collectors.toList());
        
        return TopEntityDTO.builder()
                .type(type)
                .items(items)
                .totalEntities(totalEntities)
                .build();
    }

    // ==================== 4. 重复异常 ====================

    /**
     * 获取重复出现的异常
     */
    @Cacheable(value = "recurringExceptions", key = "'mapper_' + #startTime + '_' + #endTime", unless = "#result == null")
    public RecurringExceptionDTO getRecurringExceptions(String startTime, String endTime) {
        log.info("Fetching recurring exceptions via Mapper");
        
        List<RecurringExceptionRow> rawData = dashboardMapper.getRecurringExceptions(startTime, endTime, 50);
        
        long totalOccurrences = 0;
        List<RecurringExceptionDTO.ExceptionItem> items = new ArrayList<>();
        
        for (RecurringExceptionRow row : rawData) {
            Long count = row.getCnt() != null ? row.getCnt() : 0L;
            totalOccurrences += count;
            
            items.add(RecurringExceptionDTO.ExceptionItem.builder()
                    .messageHash(row.getMsgHash() != null ? row.getMsgHash() : "")
                    .messageSummary(row.getMsgSummary() != null ? row.getMsgSummary() : "")
                    .count(count)
                    .firstSeen(row.getFirstSeen() != null ? row.getFirstSeen() : "")
                    .lastSeen(row.getLastSeen() != null ? row.getLastSeen() : "")
                    .affectedHosts(row.getAffectedHosts() != null ? row.getAffectedHosts() : 0)
                    .affectedApps(row.getAffectedApps() != null ? row.getAffectedApps() : 0)
                    .severity(row.getSeverity() != null ? row.getSeverity() : "ERROR")
                    .build());
        }
        
        return RecurringExceptionDTO.builder()
                .items(items)
                .totalTypes(items.size())
                .totalOccurrences(totalOccurrences)
                .build();
    }

    // ==================== 5. 最新告警日志 ====================

    /**
     * 获取最新的告警日志 (ERROR/WARN)
     */
    public AlertLogDTO getLatestAlertLogs(String startTime, String endTime, Integer pageNum, Integer pageSize) {
        log.info("Fetching alert logs via Mapper");
        
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
        
        return AlertLogDTO.builder()
                .items(items)
                .total(total != null ? total : 0L)
                .pageNum(pageNum)
                .pageSize(pageSize)
                .build();
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.2f %s", bytes / Math.pow(1024, exp), pre);
    }
}
