package cn.mw.loganalysis.config.controller;

import cn.mw.loganalysis.common.response.Result;
import cn.mw.loganalysis.config.dto.FieldConfigRequest;
import cn.mw.loganalysis.config.dto.FieldConfigResponse;
import cn.mw.loganalysis.config.service.UserFieldConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 用户字段配置Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/field-config")
@RequiredArgsConstructor
public class UserFieldConfigController {

    private final UserFieldConfigService userFieldConfigService;

    /**
     * 获取用户字段配置
     */
    @GetMapping("/{configType}")
    public Result<FieldConfigResponse> getFieldConfig(
            @PathVariable String configType,
            @RequestParam(defaultValue = "admin") String username) {
        log.info("获取用户字段配置: username={}, configType={}", username, configType);
        FieldConfigResponse response = userFieldConfigService.getFieldConfig(username, configType);
        return Result.success(response);
    }

    /**
     * 保存用户字段配置
     */
    @PostMapping
    public Result<Void> saveFieldConfig(
            @Validated @RequestBody FieldConfigRequest request,
            @RequestParam(defaultValue = "admin") String username) {
        log.info("保存用户字段配置: username={}, request={}", username, request);
        userFieldConfigService.saveFieldConfig(username, request);
        return Result.success();
    }

    /**
     * 重置用户字段配置
     */
    @DeleteMapping("/{configType}")
    public Result<Void> resetFieldConfig(
            @PathVariable String configType,
            @RequestParam(defaultValue = "admin") String username) {
        log.info("重置用户字段配置: username={}, configType={}", username, configType);
        userFieldConfigService.resetFieldConfig(username, configType);
        return Result.success();
    }
}
