package cn.mw.loganalysis.alert.controller;

import cn.mw.loganalysis.alert.dto.*;
import cn.mw.loganalysis.alert.service.AlertRuleService;
import cn.mw.loganalysis.common.response.Result;
import cn.mw.loganalysis.operationlog.annotation.OperationLog;
import cn.mw.loganalysis.operationlog.enums.OperationAction;
import cn.mw.loganalysis.operationlog.enums.OperationModule;
import cn.mw.loganalysis.operationlog.enums.OperationType;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 告警规则控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/alert/rules")
@RequiredArgsConstructor
public class AlertRuleController {

    private final AlertRuleService alertRuleService;

    /**
     * 查询告警规则列表
     */
    @GetMapping
    public Result<IPage<AlertRuleDTO>> queryRules(AlertRuleQueryRequest request) {
        log.info("Query alert rules: {}", request);
        try {
            IPage<AlertRuleDTO> result = alertRuleService.queryRules(request);
            return Result.success(result);
        } catch (Exception e) {
            log.error("Failed to query alert rules", e);
            return Result.error("查询告警规则失败: " + e.getMessage());
        }
    }

    /**
     * 获取规则详情
     */
    @GetMapping("/{id}")
    public Result<AlertRuleDTO> getRuleById(@PathVariable Long id) {
        log.info("Get alert rule by id: {}", id);
        try {
            AlertRuleDTO result = alertRuleService.getRuleById(id);
            return Result.success(result);
        } catch (Exception e) {
            log.error("Failed to get alert rule", e);
            return Result.error("获取告警规则失败: " + e.getMessage());
        }
    }

    /**
     * 创建告警规则
     */
    @PostMapping
    @OperationLog(
        module = OperationModule.ALERT,
        operationType = OperationType.CREATE,
        action = OperationAction.CREATE_ALERT_RULE,
        resourceType = "AlertRule",
        resourceIdSpEL = "#result.data.id"
    )
    public Result<AlertRuleDTO> createRule(@Valid @RequestBody CreateAlertRuleRequest request) {
        log.info("Create alert rule: {}", request.getName());
        try {
            AlertRuleDTO result = alertRuleService.createRule(request);
            return Result.success(result);
        } catch (Exception e) {
            log.error("Failed to create alert rule", e);
            return Result.error("创建告警规则失败: " + e.getMessage());
        }
    }

    /**
     * 更新告警规则
     */
    @PutMapping("/{id}")
    @OperationLog(
        module = OperationModule.ALERT,
        operationType = OperationType.UPDATE,
        action = OperationAction.UPDATE_ALERT_RULE,
        resourceType = "AlertRule",
        resourceIdSpEL = "#id"
    )
    public Result<AlertRuleDTO> updateRule(
            @PathVariable Long id,
            @RequestBody UpdateAlertRuleRequest request) {
        log.info("Update alert rule: {}", id);
        try {
            AlertRuleDTO result = alertRuleService.updateRule(id, request);
            return Result.success(result);
        } catch (Exception e) {
            log.error("Failed to update alert rule", e);
            return Result.error("更新告警规则失败: " + e.getMessage());
        }
    }

    /**
     * 删除告警规则
     */
    @DeleteMapping("/{id}")
    @OperationLog(
        module = OperationModule.ALERT,
        operationType = OperationType.DELETE,
        action = OperationAction.DELETE_ALERT_RULE,
        resourceType = "AlertRule",
        resourceIdSpEL = "#id"
    )
    public Result<Void> deleteRule(@PathVariable Long id) {
        log.info("Delete alert rule: {}", id);
        try {
            alertRuleService.deleteRule(id);
            return Result.success(null);
        } catch (Exception e) {
            log.error("Failed to delete alert rule", e);
            return Result.error("删除告警规则失败: " + e.getMessage());
        }
    }

    /**
     * 切换规则状态
     */
    @PatchMapping("/{id}/status")
    @OperationLog(
        module = OperationModule.ALERT,
        operationType = OperationType.UPDATE,
        action = OperationAction.TOGGLE_ALERT_RULE_STATUS,
        resourceType = "AlertRule",
        resourceIdSpEL = "#id"
    )
    public Result<AlertRuleDTO> toggleStatus(
            @PathVariable Long id,
            @RequestParam Boolean enabled) {
        log.info("Toggle alert rule status: {} to {}", id, enabled);
        try {
            AlertRuleDTO result = alertRuleService.toggleRuleStatus(id, enabled);
            return Result.success(result);
        } catch (Exception e) {
            log.error("Failed to toggle alert rule status", e);
            return Result.error("切换规则状态失败: " + e.getMessage());
        }
    }

    /**
     * 复制规则
     */
    @PostMapping("/{id}/duplicate")
    @OperationLog(
        module = OperationModule.ALERT,
        operationType = OperationType.CREATE,
        action = OperationAction.DUPLICATE_ALERT_RULE,
        resourceType = "AlertRule",
        resourceIdSpEL = "#result.data.id"
    )
    public Result<AlertRuleDTO> duplicateRule(@PathVariable Long id) {
        log.info("Duplicate alert rule: {}", id);
        try {
            AlertRuleDTO result = alertRuleService.duplicateRule(id);
            return Result.success(result);
        } catch (Exception e) {
            log.error("Failed to duplicate alert rule", e);
            return Result.error("复制规则失败: " + e.getMessage());
        }
    }

    /**
     * 测试规则
     */
    @PostMapping("/{id}/test")
    @OperationLog(
        module = OperationModule.ALERT,
        operationType = OperationType.EXECUTE,
        action = OperationAction.TEST_ALERT_RULE,
        resourceType = "AlertRule",
        resourceIdSpEL = "#id"
    )
    public Result<Map<String, Object>> testRule(@PathVariable Long id) {
        log.info("Test alert rule: {}", id);
        try {
            Map<String, Object> result = alertRuleService.testRule(id);
            return Result.success(result);
        } catch (Exception e) {
            log.error("Failed to test alert rule", e);
            return Result.error("测试规则失败: " + e.getMessage());
        }
    }
}
