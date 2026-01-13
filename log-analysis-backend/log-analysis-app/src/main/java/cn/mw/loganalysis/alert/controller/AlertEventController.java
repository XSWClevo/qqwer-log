package cn.mw.loganalysis.alert.controller;

import cn.mw.loganalysis.alert.dto.AlertEventDTO;
import cn.mw.loganalysis.alert.dto.AlertEventQueryRequest;
import cn.mw.loganalysis.alert.service.AlertEventService;
import cn.mw.loganalysis.common.response.Result;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 告警事件控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/alert/events")
@RequiredArgsConstructor
public class AlertEventController {

    private final AlertEventService alertEventService;

    /**
     * 查询告警事件列表
     */
    @GetMapping
    public Result<IPage<AlertEventDTO>> queryEvents(AlertEventQueryRequest request) {
        log.info("Query alert events: {}", request);
        try {
            IPage<AlertEventDTO> result = alertEventService.queryEvents(request);
            return Result.success(result);
        } catch (Exception e) {
            log.error("Failed to query alert events", e);
            return Result.error("查询告警事件失败: " + e.getMessage());
        }
    }

    /**
     * 获取事件详情
     */
    @GetMapping("/{id}")
    public Result<AlertEventDTO> getEventById(@PathVariable Long id) {
        log.info("Get alert event by id: {}", id);
        try {
            AlertEventDTO result = alertEventService.getEventById(id);
            return Result.success(result);
        } catch (Exception e) {
            log.error("Failed to get alert event", e);
            return Result.error("获取告警事件失败: " + e.getMessage());
        }
    }

    /**
     * 获取告警趋势
     */
    @GetMapping("/trend")
    public Result<Map<String, Object>> getAlertTrend(@RequestParam(defaultValue = "24h") String timeRange) {
        log.info("Get alert trend for time range: {}", timeRange);
        try {
            Map<String, Object> result = alertEventService.getAlertTrend(timeRange);
            return Result.success(result);
        } catch (Exception e) {
            log.error("Failed to get alert trend", e);
            return Result.error("获取告警趋势失败: " + e.getMessage());
        }
    }

    /**
     * 确认告警
     */
    @PostMapping("/{id}/acknowledge")
    public Result<Void> acknowledgeEvent(@PathVariable Long id) {
        log.info("Acknowledge alert event: {}", id);
        try {
            // TODO: 从当前用户获取 userId
            alertEventService.acknowledgeEvent(id, 1L);
            return Result.success(null);
        } catch (Exception e) {
            log.error("Failed to acknowledge alert event", e);
            return Result.error("确认告警失败: " + e.getMessage());
        }
    }
}
