package cn.mw.loganalysis.stats.service;

import cn.mw.loganalysis.stats.dto.LogContextRequest;
import cn.mw.loganalysis.stats.dto.LogQueryRequest;
import cn.mw.loganalysis.stats.dto.StatsQueryRequest;
import cn.mw.loganalysis.stats.mapper.ClickHouseQueryMapper;
import cn.mw.loganalysis.stats.mapper.param.ContextLogQueryParam;
import cn.mw.loganalysis.stats.mapper.param.DimensionStatsQueryParam;
import cn.mw.loganalysis.stats.mapper.param.FieldTimeSeriesQueryParam;
import cn.mw.loganalysis.stats.mapper.param.LogQuerySqlParam;
import cn.mw.loganalysis.stats.mapper.param.TimeSeriesQueryParam;
import cn.mw.loganalysis.stats.service.query.support.StatsQueryMapperUtils;
import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认 ClickHouse 统计服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsService {

    private static final String DEFAULT_TABLE = "syslog";

    private final ClickHouseQueryMapper clickHouseQueryMapper;

    /**
     * 查询日志 - 切换到 ClickHouse 数据源
     */
    @DS("clickhouse")
    public Map<String, Object> queryLogs(LogQueryRequest request) {
        log.info("Querying logs from {} to {} using ClickHouse datasource",
                request.getStartTime(), request.getEndTime());

        int pageSize = Math.max(ObjectUtils.defaultIfNull(request.getPageSize(), 100), 1);
        int pageNum = Math.max(ObjectUtils.defaultIfNull(request.getPageNum(), 1), 1);
        LogQuerySqlParam queryParam = LogQuerySqlParam.builder()
                .tableName(StatsQueryMapperUtils.quoteClickHouseIdentifier(DEFAULT_TABLE))
                .startTime(StatsQueryMapperUtils.format(request.getStartTime()))
                .endTime(StatsQueryMapperUtils.format(request.getEndTime()))
                .pageSize(pageSize)
                .offset((pageNum - 1) * pageSize)
                .fieldFilters(StatsQueryMapperUtils.buildClickHouseFieldFilters(request.getFieldFilters()))
                .messageConditions(StatsQueryMapperUtils.buildMessageConditions(request.getMessageConditions()))
                .rawConditions(StatsQueryMapperUtils.buildMessageConditions(request.getRawConditions()))
                .build();

        Long total = clickHouseQueryMapper.countLogs(queryParam);
        List<Map<String, Object>> data = clickHouseQueryMapper.selectLogs(queryParam);
        StatsQueryMapperUtils.normalizeTimestampField(data, "timestamp");

        Map<String, Object> result = new HashMap<>();
        result.put("total", total != null ? total : 0);
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);
        result.put("data", data);

        log.info("Query completed, returned {} records out of {}", data.size(), total);
        return result;
    }

    /**
     * 查询日志上下文 - 获取指定日志前后的相关日志
     */
    @DS("clickhouse")
    public Map<String, Object> queryLogContext(LogContextRequest request) {
        log.info("Querying log context for logId: {}, timestamp: {}",
                request.getLogId(), request.getTimestamp());

        List<Map<String, Object>> beforeLogs = new ArrayList<>();
        List<Map<String, Object>> afterLogs = new ArrayList<>();

        if (request.getBeforeCount() != null && request.getBeforeCount() > 0) {
            beforeLogs = queryContextLogs(request, true, request.getBeforeCount());
        }

        if (request.getAfterCount() != null && request.getAfterCount() > 0) {
            afterLogs = queryContextLogs(request, false, request.getAfterCount());
        }

        Collections.reverse(beforeLogs);

        Map<String, Object> result = new HashMap<>();
        result.put("beforeLogs", beforeLogs);
        result.put("afterLogs", afterLogs);
        result.put("totalBefore", beforeLogs.size());
        result.put("totalAfter", afterLogs.size());

        log.info("Context query completed: {} before, {} after", beforeLogs.size(), afterLogs.size());
        return result;
    }

    private List<Map<String, Object>> queryContextLogs(LogContextRequest request, boolean isBefore, int limit) {
        ContextLogQueryParam queryParam = ContextLogQueryParam.builder()
                .tableName(StatsQueryMapperUtils.quoteClickHouseIdentifier(DEFAULT_TABLE))
                .timestamp(StatsQueryMapperUtils.format(request.getTimestamp()))
                .limit(limit)
                .fieldFilters(StatsQueryMapperUtils.buildClickHouseFieldFilters(request.getFieldFilters()))
                .messageConditions(StatsQueryMapperUtils.buildMessageConditions(request.getMessageConditions()))
                .rawConditions(StatsQueryMapperUtils.buildMessageConditions(request.getRawConditions()))
                .build();

        List<Map<String, Object>> rows = isBefore
                ? clickHouseQueryMapper.selectContextBeforeLogs(queryParam)
                : clickHouseQueryMapper.selectContextAfterLogs(queryParam);
        StatsQueryMapperUtils.normalizeTimestampField(rows, "timestamp");
        return rows;
    }

    /**
     * 统计查询（字段统计）- 使用 ClickHouse 数据源
     */
    @DS("clickhouse")
    public Map<String, Object> queryStats(StatsQueryRequest request) {
        log.info("Query stats with dimensions: {} using ClickHouse datasource", request.getDimensions());

        Map<String, Object> result = new HashMap<>();
        if (request.getDimensions() == null || request.getDimensions().isEmpty()) {
            result.put("dimensions", Collections.emptyList());
            result.put("data", Collections.emptyList());
            return result;
        }

        String tableName = StatsQueryMapperUtils.quoteClickHouseIdentifier(DEFAULT_TABLE);
        String startTime = StatsQueryMapperUtils.format(request.getStartTime());
        String endTime = StatsQueryMapperUtils.format(request.getEndTime());
        Map<String, List<Map<String, Object>>> statsData = new HashMap<>();

        for (String dimension : request.getDimensions()) {
            statsData.put(dimension, clickHouseQueryMapper.selectDimensionStats(DimensionStatsQueryParam.builder()
                    .tableName(tableName)
                    .startTime(startTime)
                    .endTime(endTime)
                    .dimensionExpression(StatsQueryMapperUtils.quoteClickHouseIdentifier(dimension))
                    .build()));
        }

        result.put("dimensions", request.getDimensions());
        result.put("metrics", request.getMetrics());
        result.put("data", statsData);
        return result;
    }

    /**
     * 时间序列查询 - 使用 ClickHouse 数据源
     */
    @DS("clickhouse")
    public Map<String, Object> queryTimeSeries(StatsQueryRequest request) {
        log.info("Query time series with granularity: {} using ClickHouse datasource", request.getGranularity());

        List<Map<String, Object>> series = clickHouseQueryMapper.selectTimeSeries(TimeSeriesQueryParam.builder()
                .tableName(StatsQueryMapperUtils.quoteClickHouseIdentifier(DEFAULT_TABLE))
                .startTime(StatsQueryMapperUtils.format(request.getStartTime()))
                .endTime(StatsQueryMapperUtils.format(request.getEndTime()))
                .timeBucketExpression(StatsQueryMapperUtils.getClickHouseTimeBucketExpression(request.getGranularity()))
                .build());
        StatsQueryMapperUtils.renameAndNormalizeTimestampField(series, "time_bucket", "timestamp");

        Map<String, Object> result = new HashMap<>();
        result.put("granularity", request.getGranularity());
        result.put("series", series);

        log.info("Time series query completed, returned {} data points", series.size());
        return result;
    }

    /**
     * 字段时序查询 - 按时间分组统计特定字段值的数量
     */
    @DS("clickhouse")
    public Map<String, Object> queryFieldTimeSeries(String fieldName, String fieldValue,
                                                    String startTime, String endTime,
                                                    String granularity) {
        log.info("Query field time series: field={}, value={}, granularity={}",
                fieldName, fieldValue, granularity);

        List<Map<String, Object>> series = clickHouseQueryMapper.selectFieldTimeSeries(FieldTimeSeriesQueryParam.builder()
                .tableName(StatsQueryMapperUtils.quoteClickHouseIdentifier(DEFAULT_TABLE))
                .startTime(startTime)
                .endTime(endTime)
                .timeBucketExpression(StatsQueryMapperUtils.getClickHouseTimeBucketExpression(granularity))
                .fieldExpression(StatsQueryMapperUtils.quoteClickHouseIdentifier(fieldName))
                .fieldValue(fieldValue)
                .build());
        StatsQueryMapperUtils.renameAndNormalizeTimestampField(series, "time_bucket", "timestamp");

        Map<String, Object> result = new HashMap<>();
        result.put("field", fieldName);
        result.put("value", fieldValue);
        result.put("granularity", granularity);
        result.put("series", series);

        log.info("Field time series query completed, returned {} data points", series.size());
        return result;
    }

    /**
     * 导出报表
     */
    public String exportReport(LogQueryRequest request, String format) {
        log.info("Exporting report in format: {}", format);

        LogQueryRequest exportRequest = new LogQueryRequest();
        exportRequest.setStartTime(request.getStartTime());
        exportRequest.setEndTime(request.getEndTime());
        exportRequest.setFieldFilters(request.getFieldFilters());
        exportRequest.setMessageConditions(request.getMessageConditions());
        exportRequest.setRawConditions(request.getRawConditions());
        exportRequest.setPageNum(1);
        exportRequest.setPageSize(10000);

        Map<String, Object> queryResult = queryLogs(exportRequest);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) queryResult.get("data");

        String filename = "log_report_" + System.currentTimeMillis() + "." + format;
        String filepath = "/tmp/" + filename;

        try {
            if ("csv".equalsIgnoreCase(format)) {
                exportToCsv(data, filepath);
            } else if ("xlsx".equalsIgnoreCase(format)) {
                exportToExcel(data, filepath);
            }

            log.info("Report exported to: {}", filepath);
            return filename;
        } catch (IOException e) {
            log.error("Failed to export report", e);
            throw new RuntimeException("导出报表失败: " + e.getMessage());
        }
    }

    /**
     * 导出到 CSV
     */
    private void exportToCsv(List<Map<String, Object>> data, String filepath) throws IOException {
        try (FileWriter writer = new FileWriter(filepath)) {
            writer.write("时间戳,级别,来源,主机,服务,用户,消息\n");

            for (Map<String, Object> row : data) {
                writer.write(String.format("%s,%s,%s,%s,%s,%s,\"%s\"\n",
                        row.get("timestamp"),
                        row.get("level"),
                        row.get("source"),
                        row.get("host"),
                        row.get("service"),
                        row.get("user"),
                        row.get("message").toString().replace("\"", "\"\"")
                ));
            }
        }
    }

    /**
     * 导出到 Excel（简化版，使用 CSV 格式）
     */
    private void exportToExcel(List<Map<String, Object>> data, String filepath) throws IOException {
        exportToCsv(data, filepath);
    }
}
