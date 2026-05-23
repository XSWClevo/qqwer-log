package cn.mw.loganalysis.attack.controller;

import cn.mw.loganalysis.attack.dto.AttackClassificationQueryRequest;
import cn.mw.loganalysis.attack.dto.AttackClassificationRunRequest;
import cn.mw.loganalysis.attack.dto.AttackClassificationRunResult;
import cn.mw.loganalysis.attack.entity.AttackClassificationRecord;
import cn.mw.loganalysis.attack.service.AttackClassificationService;
import cn.mw.loganalysis.common.response.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/attack/classifications")
@RequiredArgsConstructor
public class AttackClassificationController {

    private final AttackClassificationService attackClassificationService;

    @PostMapping("/query")
    public Result<Page<AttackClassificationRecord>> query(@RequestBody(required = false) AttackClassificationQueryRequest request) {
        return Result.success(attackClassificationService.query(
                ObjectUtils.defaultIfNull(request, new AttackClassificationQueryRequest())));
    }

    @PostMapping("/run")
    public Result<AttackClassificationRunResult> run(@RequestBody(required = false) AttackClassificationRunRequest request) {
        return Result.success(attackClassificationService.run(
                ObjectUtils.defaultIfNull(request, new AttackClassificationRunRequest())));
    }
}
