package cn.mw.loganalysis.alert.controller;

import cn.mw.loganalysis.alert.dto.CreateAlertRuleRequest;
import cn.mw.loganalysis.alert.entity.AlertEvent;
import cn.mw.loganalysis.alert.entity.AlertRule;
import cn.mw.loganalysis.alert.service.AlertService;
import cn.mw.loganalysis.common.response.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 告警控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @PostMapping("/rules")
    public Result<AlertRule> createRule(@Valid @RequestBody CreateAlertRuleRequest request,
                                        @RequestHeader("X-User-Id") Long userId) {
        AlertRule rule = alertService.createRule(request, userId);
        return Result.success(rule);
    }

    @GetMapping("/rules/{id}")
    public Result<AlertRule> getRule(@PathVariable Long id) {
        return Result.success(alertService.getById(id));
    }

    @GetMapping("/rules")
    public Result<Page<AlertRule>> listRules(@RequestParam(defaultValue = "1") int pageNum,
                                              @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(alertService.listRules(pageNum, pageSize));
    }

    @DeleteMapping("/rules/{id}")
    public Result<Void> deleteRule(@PathVariable Long id) {
        alertService.deleteRule(id);
        return Result.success();
    }

    @GetMapping("/events")
    public Result<Page<AlertEvent>> listEvents(@RequestParam(defaultValue = "1") int pageNum,
                                                @RequestParam(defaultValue = "10") int pageSize,
                                                @RequestParam(required = false) Long ruleId) {
        return Result.success(alertService.listEvents(pageNum, pageSize, ruleId));
    }
}
