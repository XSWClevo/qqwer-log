package cn.mw.loganalysis.vector.controller;

import cn.mw.loganalysis.common.response.Result;
import cn.mw.loganalysis.vector.service.VectorLogService;
import cn.mw.loganalysis.vector.service.VectorLogSseManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Vector 运行日志控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/vector/logs")
@RequiredArgsConstructor
public class VectorLogController {

    private final VectorLogService vectorLogService;
    private final VectorLogSseManager sseManager;

    /**
     * 分页查询日志（默认返回最新数据）
     */
    @GetMapping("/query")
    public Result<Map<String, Object>> queryLogs(
            @RequestParam(required = false) String machineId,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "200") int pageSize) {

        Map<String, Object> result = vectorLogService.queryLogs(
                machineId, fileName, keyword, pageNum, pageSize
        );
        return Result.success(result);
    }

    /**
     * SSE 实时推送日志（基于 Redis Pub/Sub，延迟 <100ms）
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs(
            @RequestParam(required = false) String machineId,
            @RequestParam(required = false) String fileName) {

        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        String emitterId = UUID.randomUUID().toString();

        log.info("SSE 连接建立: {}, machineId={}, fileName={}", emitterId, machineId, fileName);

        sseManager.register(emitterId, emitter, machineId, fileName);

        try {
            emitter.send(SseEmitter.event().name("connected").data(emitterId));
        } catch (IOException e) {
            sseManager.unregister(emitterId);
        }
        return emitter;
    }

    /**
     * 主动关闭 SSE 连接（前端页面离开时调用，支持 POST 以兼容 sendBeacon）
     */
    @RequestMapping(value = "/stream/{emitterId}/close", method = {RequestMethod.POST, RequestMethod.DELETE})
    public Result<Void> closeStream(@PathVariable String emitterId) {
        sseManager.unregister(emitterId);
        log.info("SSE 连接主动关闭: {}", emitterId);
        return Result.success(null);
    }

    /**
     * 获取所有日志文件名列表
     */
    @GetMapping("/files")
    public Result<List<String>> getFileNames() {
        return Result.success(vectorLogService.getDistinctFileNames());
    }

    /**
     * 获取所有机器ID列表
     */
    @GetMapping("/machines")
    public Result<List<String>> getMachineIds() {
        return Result.success(vectorLogService.getDistinctMachineIds());
    }
}
