package cn.mw.loganalysis.config.controller;

import cn.mw.loganalysis.common.response.Result;
import cn.mw.loganalysis.config.dto.UpdateConfigRequest;
import cn.mw.loganalysis.config.entity.ConfigHistory;
import cn.mw.loganalysis.config.entity.SystemConfig;
import cn.mw.loganalysis.config.service.SystemConfigService;
import cn.mw.loganalysis.operationlog.annotation.OperationLog;
import cn.mw.loganalysis.operationlog.enums.OperationAction;
import cn.mw.loganalysis.operationlog.enums.OperationModule;
import cn.mw.loganalysis.operationlog.enums.OperationType;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统配置控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService configService;

    /**
     * 获取所有配置
     */
    @PostMapping("/settings/list")
    public Result<List<SystemConfig>> getAllConfigs() {
        return Result.success(configService.getAllConfigs());
    }

    /**
     * 获取指定配置
     */
    @PostMapping("/settings/get")
    public Result<SystemConfig> getConfig(@RequestBody Map<String, String> request) {
        String key = request.get("key");
        return Result.success(configService.getByKey(key));
    }

    /**
     * 更新配置
     */
    @PostMapping("/settings/update")
    @OperationLog(
        module = OperationModule.CONFIG,
        operationType = OperationType.CONFIG,
        action = OperationAction.UPDATE_SYSTEM_CONFIG,
        resourceType = "SystemConfig",
        resourceIdSpEL = "#request.key"
    )
    public Result<SystemConfig> updateConfig(@Valid @RequestBody Map<String, Object> request,
                                              @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        String key = (String) request.get("key");
        String configValue = (String) request.get("configValue");

        log.info("Updating config: key={}, value={}", key, configValue);

        UpdateConfigRequest updateRequest = new UpdateConfigRequest();
        updateRequest.setConfigValue(configValue);

        SystemConfig config = configService.updateConfig(key, updateRequest, userId != null ? userId : 0L);
        return Result.success(config);
    }

    /**
     * 批量更新配置
     */
    @PostMapping("/settings/batch-update")
    @OperationLog(
        module = OperationModule.CONFIG,
        operationType = OperationType.CONFIG,
        action = OperationAction.UPDATE_SYSTEM_CONFIG,
        resourceType = "SystemConfig"
    )
    public Result<Void> batchUpdateConfig(@RequestBody Map<String, Object> request,
                                           @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> configs = (List<Map<String, String>>) request.get("configs");

        log.info("Batch updating {} configs", configs.size());

        for (Map<String, String> config : configs) {
            String key = config.get("key");
            String value = config.get("value");

            UpdateConfigRequest updateRequest = new UpdateConfigRequest();
            updateRequest.setConfigValue(value);

            configService.updateConfig(key, updateRequest, userId != null ? userId : 0L);
        }

        return Result.success();
    }

    /**
     * 获取配置历史
     */
    @PostMapping("/history/list")
    public Result<Page<ConfigHistory>> getHistory(@RequestBody Map<String, Object> request) {
        String configKey = (String) request.get("configKey");
        Integer pageNum = request.get("pageNum") != null ? (Integer) request.get("pageNum") : 1;
        Integer pageSize = request.get("pageSize") != null ? (Integer) request.get("pageSize") : 10;
        return Result.success(configService.getHistory(configKey, pageNum, pageSize));
    }
}
