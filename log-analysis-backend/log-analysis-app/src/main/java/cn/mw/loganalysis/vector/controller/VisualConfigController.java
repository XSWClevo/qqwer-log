package cn.mw.loganalysis.vector.controller;

import cn.mw.loganalysis.common.response.Result;
import cn.mw.loganalysis.vector.dto.CreateVisualConfigRequest;
import cn.mw.loganalysis.vector.dto.UpdateVisualConfigRequest;
import cn.mw.loganalysis.vector.dto.ValidateConfigRequest;
import cn.mw.loganalysis.vector.dto.ValidateConfigResponse;
import cn.mw.loganalysis.vector.entity.VisualConfig;
import cn.mw.loganalysis.vector.service.VisualConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 可视化配置管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/vector/visual-configs")
@RequiredArgsConstructor
public class VisualConfigController {

    private final VisualConfigService visualConfigService;

    /**
     * 查询配置列表
     */
    @GetMapping
    public Result<List<VisualConfig>> getConfigList(@RequestParam(required = false) String keyword) {
        List<VisualConfig> configs = visualConfigService.getConfigList(keyword);
        return Result.success(configs);
    }

    /**
     * 根据ID查询配置详情
     */
    @GetMapping("/{id}")
    public Result<VisualConfig> getConfigById(@PathVariable String id) {
        VisualConfig config = visualConfigService.getConfigById(id);
        if (config == null) {
            return Result.notFound("配置不存在");
        }
        return Result.success(config);
    }

    /**
     * 创建配置
     */
    @PostMapping
    public Result<VisualConfig> createConfig(@Validated @RequestBody CreateVisualConfigRequest request) {
        String userId = "system";
        VisualConfig config = visualConfigService.createConfig(request, userId);
        return Result.success(config);
    }

    /**
     * 更新配置
     */
    @PutMapping("/{id}")
    public Result<VisualConfig> updateConfig(@PathVariable String id,
                                              @RequestBody UpdateVisualConfigRequest request) {
        VisualConfig config = visualConfigService.updateConfig(id, request);
        return Result.success(config);
    }

    /**
     * 删除配置
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteConfig(@PathVariable String id) {
        visualConfigService.deleteConfig(id);
        return Result.success();
    }

    /**
     * 校验配置（使用 Vector 命令行）
     */
    @PostMapping("/validate")
    public Result<ValidateConfigResponse> validateConfig(@RequestBody ValidateConfigRequest request) {
        ValidateConfigResponse response = visualConfigService.validateConfig(request.getContent());
        return Result.success(response);
    }

    /**
     * 导出配置
     */
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> exportConfig(@PathVariable String id) {
        VisualConfig config = visualConfigService.getConfigById(id);
        if (config == null) {
            return ResponseEntity.notFound().build();
        }

        String content = config.getContent() != null ? config.getContent() : "";
        String filename = config.getName() + ".yaml";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", filename);

        return ResponseEntity.ok()
                .headers(headers)
                .body(content.getBytes(StandardCharsets.UTF_8));
    }
}
