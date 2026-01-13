package cn.mw.loganalysis.vector.controller;

import cn.mw.loganalysis.common.response.Result;
import cn.mw.loganalysis.vector.dto.VrlExecuteRequest;
import cn.mw.loganalysis.vector.dto.VrlExecuteResponse;
import cn.mw.loganalysis.vector.service.VrlExecuteService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * VRL 表达式执行控制器
 */
@RestController
@RequestMapping("/api/vector/vrl")
@RequiredArgsConstructor
public class VrlController {

    private final VrlExecuteService vrlExecuteService;

    /**
     * 执行 VRL 表达式解析日志
     */
    @PostMapping("/execute")
    public Result<VrlExecuteResponse> execute(@Validated @RequestBody VrlExecuteRequest request) {
        VrlExecuteResponse response = vrlExecuteService.execute(request);
        return Result.success(response);
    }
}
