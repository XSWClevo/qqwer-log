package cn.mw.loganalysis.vector.controller;

import cn.mw.loganalysis.common.response.Result;
import cn.mw.loganalysis.operationlog.annotation.OperationLog;
import cn.mw.loganalysis.operationlog.enums.OperationAction;
import cn.mw.loganalysis.operationlog.enums.OperationModule;
import cn.mw.loganalysis.operationlog.enums.OperationType;
import cn.mw.loganalysis.vector.dto.AddConfigRequest;
import cn.mw.loganalysis.vector.entity.VectorConfig;
import cn.mw.loganalysis.vector.service.VectorConfigService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Vector配置管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/vector/configs")
@RequiredArgsConstructor
public class VectorConfigController {

    private final VectorConfigService vectorConfigService;

    /**
     * 分页查询配置列表
     */
    @GetMapping("/page")
    public Result<Page<VectorConfig>> getConfigPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean isTemplate) {
        Page<VectorConfig> page = vectorConfigService.getConfigPage(pageNum, pageSize, keyword, isTemplate);
        return Result.success(page);
    }

    /**
     * 查询所有配置列表
     */
    @GetMapping("/list")
    public Result<List<VectorConfig>> getConfigList(@RequestParam(required = false) Boolean isTemplate) {
        Page<VectorConfig> page = vectorConfigService.getConfigPage(1, 1000, null, isTemplate);
        return Result.success(page.getRecords());
    }

    /**
     * 查询模板配置列表
     */
    @GetMapping("/templates")
    public Result<List<VectorConfig>> getTemplates() {
        List<VectorConfig> templates = vectorConfigService.getTemplates();
        return Result.success(templates);
    }

    /**
     * 根据ID查询配置详情
     */
    @GetMapping("/{id}")
    public Result<VectorConfig> getConfigById(@PathVariable String id) {
        VectorConfig config = vectorConfigService.getConfigById(id);
        if (config == null) {
            return Result.error("配置不存在");
        }
        return Result.success(config);
    }

    /**
     * 添加配置
     */
    @PostMapping
    @OperationLog(
        module = OperationModule.VECTOR,
        operationType = OperationType.CREATE,
        action = OperationAction.CREATE_VECTOR_CONFIG,
        resourceType = "VectorConfig",
        resourceIdSpEL = "#result.data.id"
    )
    public Result<VectorConfig> addConfig(@Validated @RequestBody AddConfigRequest request) {
        // TODO: 从Security Context获取当前用户ID
        String userId = "system";
        VectorConfig config = vectorConfigService.addConfig(request, userId);
        return Result.success(config);
    }

    /**
     * 更新配置
     */
    @PutMapping("/{id}")
    @OperationLog(
        module = OperationModule.VECTOR,
        operationType = OperationType.UPDATE,
        action = OperationAction.UPDATE_VECTOR_CONFIG,
        resourceType = "VectorConfig",
        resourceIdSpEL = "#id"
    )
    public Result<VectorConfig> updateConfig(@PathVariable String id,
                                              @Validated @RequestBody AddConfigRequest request) {
        VectorConfig config = vectorConfigService.updateConfig(id, request);
        return Result.success(config);
    }

    /**
     * 删除配置
     */
    @DeleteMapping("/{id}")
    @OperationLog(
        module = OperationModule.VECTOR,
        operationType = OperationType.DELETE,
        action = OperationAction.DELETE_VECTOR_CONFIG,
        resourceType = "VectorConfig",
        resourceIdSpEL = "#id"
    )
    public Result<Void> deleteConfig(@PathVariable String id) {
        vectorConfigService.deleteConfig(id);
        return Result.success();
    }

    /**
     * 复制配置
     */
    @PostMapping("/{id}/copy")
    @OperationLog(
        module = OperationModule.VECTOR,
        operationType = OperationType.CREATE,
        action = OperationAction.COPY_VECTOR_CONFIG,
        resourceType = "VectorConfig",
        resourceIdSpEL = "#result.data.id"
    )
    public Result<VectorConfig> copyConfig(@PathVariable String id) {
        // TODO: 从Security Context获取当前用户ID
        String userId = "system";
        VectorConfig config = vectorConfigService.copyConfig(id, userId);
        return Result.success(config);
    }
}
