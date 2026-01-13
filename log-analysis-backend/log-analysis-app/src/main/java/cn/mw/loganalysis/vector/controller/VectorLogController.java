package cn.mw.loganalysis.vector.controller;

import cn.mw.loganalysis.common.response.Result;
import cn.mw.loganalysis.vector.entity.VectorLog;
import cn.mw.loganalysis.vector.service.VectorLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Vector 日志控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/vector/logs")
@RequiredArgsConstructor
public class VectorLogController {

    private final VectorLogService vectorLogService;

    // 存储所有活跃的 SSE 连接
    private final Map<String, SseEmitter> sseEmitters = new ConcurrentHashMap<>();

    // 定时任务执行器
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    /**
     * 查询日志列表（分页）
     *
     * @param machineId 机器ID（可选）
     * @param logLevel  日志级别（可选）
     * @param keyword   关键词（可选）
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param pageNum   页码
     * @param pageSize  每页大小
     * @return 日志列表
     */
    @GetMapping("/query")
    public Result<Map<String, Object>> queryLogs(
            @RequestParam(required = false) String machineId,
            @RequestParam(required = false) String logLevel,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "100") int pageSize) {

        // 如果没有指定时间范围，默认查询最近1小时
        if (startTime == null) {
            startTime = LocalDateTime.now().minusHours(1);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }

        Map<String, Object> result = vectorLogService.queryLogs(
                machineId, logLevel, keyword, startTime, endTime, pageNum, pageSize
        );

        return Result.success(result);
    }

    /**
     * SSE 实时推送日志
     * 客户端通过 EventSource 连接此接口，服务器会持续推送新日志
     *
     * @param machineId 机器ID（可选）
     * @param logLevel  日志级别（可选）
     * @return SSE Emitter
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs(
            @RequestParam(required = false) String machineId,
            @RequestParam(required = false) String logLevel) {

        // 创建 SSE Emitter，超时时间30分钟
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        String emitterId = UUID.randomUUID().toString();
        sseEmitters.put(emitterId, emitter);

        log.info("新的 SSE 连接建立: {}, machineId={}, logLevel={}", emitterId, machineId, logLevel);

        // 连接完成或超时时移除
        emitter.onCompletion(() -> {
            sseEmitters.remove(emitterId);
            log.info("SSE 连接完成: {}", emitterId);
        });

        emitter.onTimeout(() -> {
            sseEmitters.remove(emitterId);
            log.info("SSE 连接超时: {}", emitterId);
        });

        emitter.onError((ex) -> {
            sseEmitters.remove(emitterId);
            log.error("SSE 连接错误: {}", emitterId, ex);
        });

        // 发送初始连接成功消息
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("连接成功"));
        } catch (IOException e) {
            log.error("发送初始消息失败", e);
        }

        // 启动定时任务，每2秒查询一次新日志
        LocalDateTime[] lastTimestamp = {LocalDateTime.now()};

        scheduler.scheduleAtFixedRate(() -> {
            try {
                List<VectorLog> newLogs = vectorLogService.getLogsAfter(
                        lastTimestamp[0], machineId, logLevel
                );

                if (!newLogs.isEmpty()) {
                    // 更新最后时间戳
                    lastTimestamp[0] = newLogs.get(newLogs.size() - 1).getTimestamp();

                    // 发送新日志
                    for (VectorLog log : newLogs) {
                        emitter.send(SseEmitter.event()
                                .name("log")
                                .data(log));
                    }
                }
            } catch (IOException e) {
                log.error("发送日志失败，关闭连接: {}", emitterId, e);
                sseEmitters.remove(emitterId);
                emitter.completeWithError(e);
            } catch (Exception e) {
                log.error("查询新日志失败: {}", emitterId, e);
            }
        }, 0, 2, TimeUnit.SECONDS);

        return emitter;
    }

    /**
     * 获取所有主机名列表
     */
    @GetMapping("/hostnames")
    public Result<List<String>> getHostnames() {
        List<String> hostnames = vectorLogService.getDistinctHostnames();
        return Result.success(hostnames);
    }

    /**
     * 获取所有IP地址列表
     */
    @GetMapping("/ip-addresses")
    public Result<List<String>> getIpAddresses() {
        List<String> ipAddresses = vectorLogService.getDistinctIpAddresses();
        return Result.success(ipAddresses);
    }

    /**
     * 获取活跃的 SSE 连接数
     */
    @GetMapping("/connections")
    public Result<Map<String, Object>> getConnections() {
        Map<String, Object> result = Map.of(
                "activeConnections", sseEmitters.size(),
                "connectionIds", sseEmitters.keySet()
        );
        return Result.success(result);
    }
}
