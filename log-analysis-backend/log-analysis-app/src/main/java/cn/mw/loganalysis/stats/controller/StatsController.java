package cn.mw.loganalysis.stats.controller;

import cn.mw.loganalysis.common.response.Result;
import cn.mw.loganalysis.stats.dto.AiQueryRequest;
import cn.mw.loganalysis.stats.dto.AiQueryResponse;
import cn.mw.loganalysis.stats.dto.LogContextRequest;
import cn.mw.loganalysis.stats.dto.LogQueryRequest;
import cn.mw.loganalysis.stats.dto.StatsQueryRequest;
import cn.mw.loganalysis.stats.service.AiQueryService;
import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import cn.mw.loganalysis.stats.service.StatsService;
import cn.mw.loganalysis.stats.service.query.FieldInfo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 统计控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;
    private final DynamicLogQueryService dynamicLogQueryService;
    private final AiQueryService aiQueryService;

    /**
     * 获取数据源的表结构（字段列表）
     * 用于前端动态显示可用的筛选字段和统计维度
     */
    @GetMapping("/datasource/{datasourceId}/schema")
    public Result<List<FieldInfo>> getTableSchema(@PathVariable String datasourceId) {
        log.info("Get table schema for datasource: {}", datasourceId);
        return Result.success(dynamicLogQueryService.getTableSchema(datasourceId));
    }

    /**
     * 查询日志
     * 支持动态数据源：如果传入 datasourceId，则使用动态数据源查询
     */
    @PostMapping("/logs/query")
    public Result<Map<String, Object>> queryLogs(@Valid @RequestBody LogQueryRequest request) {
        log.info("Query logs request received:");
        log.info("  - datasourceId: {}", request.getDatasourceId());
        log.info("  - startTime: {}", request.getStartTime());
        log.info("  - endTime: {}", request.getEndTime());
        log.info("  - fieldFilters: {}", request.getFieldFilters());
        log.info("  - messageConditions: {}", request.getMessageConditions());
        log.info("  - rawConditions: {}", request.getRawConditions());
        log.info("  - pageNum: {}, pageSize: {}", request.getPageNum(), request.getPageSize());
        
        // 如果指定了数据源ID，使用动态查询服务
        if (StringUtils.hasText(request.getDatasourceId())) {
            return Result.success(dynamicLogQueryService.queryLogs(request.getDatasourceId(), request));
        }
        
        // 否则使用默认的 ClickHouse syslog 表
        return Result.success(statsService.queryLogs(request));
    }

    /**
     * 查询日志上下文
     */
    @PostMapping("/logs/context")
    public Result<Map<String, Object>> queryLogContext(@Valid @RequestBody LogContextRequest request) {
        log.info("Query log context request received for logId: {}, datasourceId: {}", 
                 request.getLogId(), request.getDatasourceId());
        
        if (StringUtils.hasText(request.getDatasourceId())) {
            return Result.success(dynamicLogQueryService.queryLogContext(request.getDatasourceId(), request));
        }
        
        return Result.success(statsService.queryLogContext(request));
    }

    /**
     * 统计查询
     */
    @PostMapping("/query")
    public Result<Map<String, Object>> queryStats(@Valid @RequestBody StatsQueryRequest request) {
        log.info("Stats query request received: dimensions={}, startTime={}, endTime={}, datasourceId={}",
                 request.getDimensions(), request.getStartTime(), request.getEndTime(), request.getDatasourceId());
        
        Map<String, Object> result;
        if (StringUtils.hasText(request.getDatasourceId())) {
            result = dynamicLogQueryService.queryStats(request.getDatasourceId(), request);
        } else {
            result = statsService.queryStats(request);
        }
        
        log.info("Stats query result: {}", result);
        return Result.success(result);
    }

    /**
     * 时间序列查询
     */
    @PostMapping("/timeseries")
    public Result<Map<String, Object>> queryTimeSeries(@Valid @RequestBody StatsQueryRequest request) {
        log.info("Time series query request received, datasourceId: {}", request.getDatasourceId());
        
        if (StringUtils.hasText(request.getDatasourceId())) {
            return Result.success(dynamicLogQueryService.queryTimeSeries(request.getDatasourceId(), request));
        }
        
        return Result.success(statsService.queryTimeSeries(request));
    }

    /**
     * 字段时序查询
     */
    @PostMapping("/field-timeseries")
    public Result<Map<String, Object>> queryFieldTimeSeries(
            @RequestParam String field,
            @RequestParam String value,
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(defaultValue = "1h") String granularity) {
        log.info("Field time series query: field={}, value={}", field, value);
        return Result.success(statsService.queryFieldTimeSeries(field, value, startTime, endTime, granularity));
    }

    /**
     * 导出报表
     */
    @PostMapping("/export")
    public Result<String> exportReport(@Valid @RequestBody LogQueryRequest request,
                                        @RequestParam(defaultValue = "csv") String format) {
        log.info("Export report in format: {}", format);
        String reportFile = statsService.exportReport(request, format);
        return Result.success(reportFile);
    }

    /**
     * AI自然语言查询
     * 使用LangChain和Claude API将自然语言转换为SQL并执行
     */
    @PostMapping("/logs/ai-query")
    public Result<AiQueryResponse> aiQuery(@Valid @RequestBody AiQueryRequest request) {
        log.info("AI query request received: {}", request.getQuery());
        AiQueryResponse response = aiQueryService.query(request);
        return Result.success(response);
    }
}
