package cn.mw.loganalysis.extraction.controller;

import cn.mw.loganalysis.common.exception.UnauthorizedException;
import cn.mw.loganalysis.common.response.Result;
import cn.mw.loganalysis.extraction.dto.CreateExtractionRuleRequest;
import cn.mw.loganalysis.extraction.dto.TestExtractionRuleRequest;
import cn.mw.loganalysis.extraction.dto.UpdateExtractionRuleRequest;
import cn.mw.loganalysis.extraction.entity.ExtractionRule;
import cn.mw.loganalysis.extraction.service.ExtractionRuleService;
import cn.mw.loganalysis.operationlog.annotation.OperationLog;
import cn.mw.loganalysis.operationlog.enums.OperationAction;
import cn.mw.loganalysis.operationlog.enums.OperationModule;
import cn.mw.loganalysis.operationlog.enums.OperationType;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 日志提取规则控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/extraction/rules")
@RequiredArgsConstructor
public class ExtractionRuleController {

    private final ExtractionRuleService extractionRuleService;

    /**
     * 创建提取规则
     */
    @PostMapping
    @OperationLog(
        module = OperationModule.EXTRACTION,
        operationType = OperationType.CREATE,
        action = OperationAction.CREATE_EXTRACTION_RULE,
        resourceType = "ExtractionRule",
        resourceIdSpEL = "#result.data.id"
    )
    public Result<ExtractionRule> createRule(@Valid @RequestBody CreateExtractionRuleRequest request,
                                             @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                             Authentication authentication) {
        log.info("Creating extraction rule: {}", request.getName());
        ExtractionRule rule = extractionRuleService.createRule(request, resolveUserId(authentication, userId));
        return Result.success(rule);
    }

    /**
     * 更新提取规则
     */
    @PutMapping("/{id}")
    @OperationLog(
        module = OperationModule.EXTRACTION,
        operationType = OperationType.UPDATE,
        action = OperationAction.UPDATE_EXTRACTION_RULE,
        resourceType = "ExtractionRule",
        resourceIdSpEL = "#id"
    )
    public Result<ExtractionRule> updateRule(@PathVariable Long id,
                                             @Valid @RequestBody UpdateExtractionRuleRequest request) {
        log.info("Updating extraction rule: {}", id);
        ExtractionRule rule = extractionRuleService.updateRule(id, request);
        return Result.success(rule);
    }

    /**
     * 删除提取规则
     */
    @DeleteMapping("/{id}")
    @OperationLog(
        module = OperationModule.EXTRACTION,
        operationType = OperationType.DELETE,
        action = OperationAction.DELETE_EXTRACTION_RULE,
        resourceType = "ExtractionRule",
        resourceIdSpEL = "#id"
    )
    public Result<Void> deleteRule(@PathVariable Long id) {
        log.info("Deleting extraction rule: {}", id);
        extractionRuleService.deleteRule(id);
        return Result.success();
    }

    /**
     * 获取提取规则详情
     */
    @GetMapping("/{id}")
    public Result<ExtractionRule> getRule(@PathVariable Long id) {
        ExtractionRule rule = extractionRuleService.getById(id);
        return Result.success(rule);
    }

    /**
     * 分页查询提取规则
     */
    @GetMapping
    public Result<Page<ExtractionRule>> listRules(@RequestParam(defaultValue = "1") int pageNum,
                                                   @RequestParam(defaultValue = "10") int pageSize,
                                                   @RequestParam(required = false) Boolean enabled) {
        Page<ExtractionRule> page = extractionRuleService.listRules(pageNum, pageSize, enabled);
        return Result.success(page);
    }

    /**
     * 测试提取规则
     */
    @PostMapping("/test")
    public Result<Map<String, String>> testRule(@Valid @RequestBody TestExtractionRuleRequest request) {
        log.info("Testing extraction rule - type: {}", request.getRuleType());
        Map<String, String> result = extractionRuleService.testRule(
            request.getRuleType(),
            request.getPattern(),
            request.getTestLog()
        );
        return Result.success(result);
    }

    private Long resolveUserId(Authentication authentication, Long headerUserId) {
        if (authentication != null) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof Long userId) {
                return userId;
            }
        }
        if (headerUserId != null) {
            return headerUserId;
        }
        throw new UnauthorizedException("未获取到当前登录用户信息");
    }
}
