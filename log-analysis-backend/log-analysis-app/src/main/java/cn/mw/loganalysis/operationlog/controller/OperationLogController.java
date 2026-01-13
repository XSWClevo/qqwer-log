package cn.mw.loganalysis.operationlog.controller;

import cn.mw.loganalysis.common.response.Result;
import cn.mw.loganalysis.operationlog.dto.request.QueryOperationLogRequest;
import cn.mw.loganalysis.operationlog.dto.response.OperationLogDTO;
import cn.mw.loganalysis.operationlog.dto.response.OperationStatsDTO;
import cn.mw.loganalysis.operationlog.service.OperationLogService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 操作日志 Controller
 *
 * @author Claude
 * @since 2026-01-07
 */
@Slf4j
@RestController
@RequestMapping("/api/operation-logs")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogService operationLogService;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 分页查询操作日志
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @PostMapping("/list")
    public Result<Page<OperationLogDTO>> listOperationLogs(@RequestBody @Validated QueryOperationLogRequest request) {
        Page<OperationLogDTO> page = operationLogService.queryLogs(request);
        return Result.success(page);
    }

    /**
     * 获取操作日志详情
     *
     * @param id 日志ID
     * @return 日志详情
     */
    @GetMapping("/{id}")
    public Result<OperationLogDTO> getOperationLog(@PathVariable Long id) {
        OperationLogDTO log = operationLogService.getLogById(id);
        if (log == null) {
            return Result.error("操作日志不存在");
        }
        return Result.success(log);
    }

    /**
     * 获取某用户最近的操作日志
     *
     * @param userId 用户ID
     * @param limit 数量限制
     * @return 操作日志列表
     */
    @GetMapping("/user/{userId}/recent")
    public Result<List<OperationLogDTO>> getRecentLogsByUserId(
        @PathVariable Long userId,
        @RequestParam(defaultValue = "10") int limit
    ) {
        List<OperationLogDTO> logs = operationLogService.getRecentLogsByUserId(userId, limit);
        return Result.success(logs);
    }

    /**
     * 统计按操作类型分组
     *
     * @param startTime 开始时间 (格式: yyyy-MM-dd HH:mm:ss)
     * @param endTime 结束时间 (格式: yyyy-MM-dd HH:mm:ss)
     * @return 统计结果
     */
    @PostMapping("/stats/by-operation-type")
    public Result<List<OperationStatsDTO>> statsByOperationType(
        @RequestParam(required = false) String startTime,
        @RequestParam(required = false) String endTime
    ) {
        // 默认查询最近 7 天
        if (startTime == null || endTime == null) {
            LocalDateTime now = LocalDateTime.now();
            endTime = now.format(FORMATTER);
            startTime = now.minusDays(7).format(FORMATTER);
        }

        List<OperationStatsDTO> stats = operationLogService.statsByOperationType(startTime, endTime);
        return Result.success(stats);
    }

    /**
     * 统计按模块分组
     *
     * @param startTime 开始时间 (格式: yyyy-MM-dd HH:mm:ss)
     * @param endTime 结束时间 (格式: yyyy-MM-dd HH:mm:ss)
     * @return 统计结果
     */
    @PostMapping("/stats/by-module")
    public Result<List<OperationStatsDTO>> statsByModule(
        @RequestParam(required = false) String startTime,
        @RequestParam(required = false) String endTime
    ) {
        // 默认查询最近 7 天
        if (startTime == null || endTime == null) {
            LocalDateTime now = LocalDateTime.now();
            endTime = now.format(FORMATTER);
            startTime = now.minusDays(7).format(FORMATTER);
        }

        List<OperationStatsDTO> stats = operationLogService.statsByModule(startTime, endTime);
        return Result.success(stats);
    }

    /**
     * 统计按用户分组 (TOP N 活跃用户)
     *
     * @param startTime 开始时间 (格式: yyyy-MM-dd HH:mm:ss)
     * @param endTime 结束时间 (格式: yyyy-MM-dd HH:mm:ss)
     * @param limit TOP N 用户
     * @return 统计结果
     */
    @PostMapping("/stats/by-user")
    public Result<List<OperationStatsDTO>> statsByUser(
        @RequestParam(required = false) String startTime,
        @RequestParam(required = false) String endTime,
        @RequestParam(defaultValue = "10") int limit
    ) {
        // 默认查询最近 7 天
        if (startTime == null || endTime == null) {
            LocalDateTime now = LocalDateTime.now();
            endTime = now.format(FORMATTER);
            startTime = now.minusDays(7).format(FORMATTER);
        }

        List<OperationStatsDTO> stats = operationLogService.statsByUser(startTime, endTime, limit);
        return Result.success(stats);
    }
}
