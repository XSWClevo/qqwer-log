package cn.mw.loganalysis.config.controller;

import cn.mw.loganalysis.common.response.Result;
import cn.mw.loganalysis.config.dto.SystemConfigDTO;
import cn.mw.loganalysis.config.dto.UpdateSystemConfigRequest;
import cn.mw.loganalysis.config.service.SystemConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据库配置控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/database-config")
@RequiredArgsConstructor
public class DatabaseConfigController {

    private final SystemConfigService systemConfigService;

    /**
     * 获取指定类型的数据库配置
     */
    @PostMapping("/get")
    public Result<List<SystemConfigDTO>> getConfig(@RequestBody Map<String, String> request) {
        String configType = request.get("configType");
        if (configType == null || configType.isBlank()) {
            return Result.error("配置类型不能为空");
        }
        List<SystemConfigDTO> configs = systemConfigService.getConfigDTOByType(configType);
        return Result.success(configs);
    }

    /**
     * 更新数据库配置
     */
    @PostMapping("/update")
    public Result<Void> updateConfig(@Valid @RequestBody UpdateSystemConfigRequest request) {
        systemConfigService.updateConfig(request);
        return Result.success();
    }

    /**
     * 获取所有支持的配置类型
     */
    @PostMapping("/types")
    public Result<List<String>> getConfigTypes() {
        List<String> types = List.of("clickhouse", "postgresql", "elasticsearch");
        return Result.success(types);
    }
}
