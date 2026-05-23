package cn.mw.loganalysis.attack.controller;

import cn.mw.loganalysis.attack.dto.AttackRuleQueryRequest;
import cn.mw.loganalysis.attack.dto.CreateAttackRuleRequest;
import cn.mw.loganalysis.attack.dto.UpdateAttackRuleRequest;
import cn.mw.loganalysis.attack.entity.AttackDetectionRule;
import cn.mw.loganalysis.attack.service.AttackRuleService;
import cn.mw.loganalysis.common.response.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/attack/rules")
@RequiredArgsConstructor
public class AttackRuleController {

    private final AttackRuleService attackRuleService;

    @PostMapping
    public Result<AttackDetectionRule> create(@Valid @RequestBody CreateAttackRuleRequest request) {
        return Result.success(attackRuleService.create(request));
    }

    @PutMapping("/{id}")
    public Result<AttackDetectionRule> update(@PathVariable Long id,
                                              @RequestBody UpdateAttackRuleRequest request) {
        return Result.success(attackRuleService.update(id, request));
    }

    @GetMapping("/{id}")
    public Result<AttackDetectionRule> get(@PathVariable Long id) {
        return Result.success(attackRuleService.get(id));
    }

    @GetMapping
    public Result<Page<AttackDetectionRule>> list(AttackRuleQueryRequest request) {
        return Result.success(attackRuleService.list(request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        attackRuleService.delete(id);
        return Result.success();
    }
}
