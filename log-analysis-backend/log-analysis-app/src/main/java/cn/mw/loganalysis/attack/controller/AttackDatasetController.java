package cn.mw.loganalysis.attack.controller;

import cn.mw.loganalysis.attack.dto.AttackDatasetQueryRequest;
import cn.mw.loganalysis.attack.dto.CreateAttackDatasetRequest;
import cn.mw.loganalysis.attack.dto.UpdateAttackDatasetRequest;
import cn.mw.loganalysis.attack.entity.AttackLogDataset;
import cn.mw.loganalysis.attack.service.AttackDatasetService;
import cn.mw.loganalysis.common.response.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/attack/datasets")
@RequiredArgsConstructor
public class AttackDatasetController {

    private final AttackDatasetService attackDatasetService;

    @PostMapping
    public Result<AttackLogDataset> create(@Valid @RequestBody CreateAttackDatasetRequest request) {
        return Result.success(attackDatasetService.create(request));
    }

    @PutMapping("/{id}")
    public Result<AttackLogDataset> update(@PathVariable Long id,
                                           @RequestBody UpdateAttackDatasetRequest request) {
        return Result.success(attackDatasetService.update(id, request));
    }

    @GetMapping("/{id}")
    public Result<AttackLogDataset> get(@PathVariable Long id) {
        return Result.success(attackDatasetService.get(id));
    }

    @GetMapping
    public Result<Page<AttackLogDataset>> list(AttackDatasetQueryRequest request) {
        return Result.success(attackDatasetService.list(request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        attackDatasetService.delete(id);
        return Result.success();
    }
}
