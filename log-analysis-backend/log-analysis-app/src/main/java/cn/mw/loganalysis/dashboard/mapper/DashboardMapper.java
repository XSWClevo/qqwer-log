package cn.mw.loganalysis.dashboard.mapper;

import cn.mw.loganalysis.dashboard.entity.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Dashboard Mapper - ClickHouse 查询
 */
@Mapper
public interface DashboardMapper {

    /**
     * 获取 ClickHouse syslog 表存储大小
     */
    Long getTableStorageSize();

    /**
     * 获取 syslog 表总行数
     */
    Long getTotalRowCount();

    /**
     * 获取分区数量
     */
    Integer getPartitionCount();

    /**
     * 按时间和级别聚合日志趋势
     */
    List<LogTrendRow> getLogTrendByInterval(
            @Param("startTime") String startTime,
            @Param("endTime") String endTime,
            @Param("intervalFunc") String intervalFunc);

    /**
     * 获取 Top 主机
     */
    List<TopEntityRow> getTopHosts(
            @Param("startTime") String startTime,
            @Param("endTime") String endTime,
            @Param("limit") int limit);

    /**
     * 获取 Top 应用
     */
    List<TopEntityRow> getTopApps(
            @Param("startTime") String startTime,
            @Param("endTime") String endTime,
            @Param("limit") int limit);

    /**
     * 获取重复异常
     */
    List<RecurringExceptionRow> getRecurringExceptions(
            @Param("startTime") String startTime,
            @Param("endTime") String endTime,
            @Param("limit") int limit);

    /**
     * 获取告警日志
     */
    List<AlertLogRow> getAlertLogs(
            @Param("startTime") String startTime,
            @Param("endTime") String endTime,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /**
     * 获取告警日志总数
     */
    Long getAlertLogsCount(
            @Param("startTime") String startTime,
            @Param("endTime") String endTime);

    /**
     * 获取时间范围内的日志总数
     */
    Long getLogCountInRange(
            @Param("startTime") String startTime,
            @Param("endTime") String endTime);

    /**
     * 获取不同实体数量
     */
    Long getDistinctEntityCount(
            @Param("field") String field,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime);

    /**
     * 获取摄入速率趋势
     */
    List<IngestRateTrendRow> getIngestRateTrend(
            @Param("startTime") String startTime,
            @Param("endTime") String endTime,
            @Param("limit") int limit);

    /**
     * 获取队列堆积数
     */
    Long getQueueBacklog(
            @Param("startTime") String startTime,
            @Param("endTime") String endTime);

    /**
     * 按级别统计日志数
     */
    List<LogCountByLevelRow> getLogCountByLevel(
            @Param("startTime") String startTime,
            @Param("endTime") String endTime);

    /**
     * 获取存储详细信息
     */
    StorageInfoRow getStorageInfo();

    /**
     * 获取查询统计
     */
    QueryStatsRow getQueryStats();

    /**
     * 获取活跃连接数
     */
    Integer getActiveConnections();
}
