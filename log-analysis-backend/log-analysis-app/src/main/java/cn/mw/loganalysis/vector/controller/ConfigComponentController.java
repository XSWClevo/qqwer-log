package cn.mw.loganalysis.vector.controller;

import cn.mw.loganalysis.common.response.Result;
import cn.mw.loganalysis.vector.dto.ComponentReferenceDTO;
import cn.mw.loganalysis.vector.dto.ConfigComponentRequest;
import cn.mw.loganalysis.vector.dto.GenerateYamlRequest;
import cn.mw.loganalysis.vector.entity.ConfigComponent;
import cn.mw.loganalysis.vector.service.ConfigComponentService;
import cn.mw.loganalysis.vector.service.ComponentReferenceService;
import cn.mw.loganalysis.vector.service.ComponentYamlGeneratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vector/components")
@RequiredArgsConstructor
public class ConfigComponentController {

    private final ConfigComponentService componentService;
    private final ComponentYamlGeneratorService yamlGeneratorService;
    private final ComponentReferenceService referenceService;

    @GetMapping
    public Result<List<ConfigComponent>> getList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String componentType) {
        return Result.success(componentService.getList(keyword, componentType));
    }

    @GetMapping("/{id}")
    public Result<ConfigComponent> getById(@PathVariable String id) {
        ConfigComponent component = componentService.getById(id);
        if (component == null) {
            return Result.notFound("组件不存在");
        }
        return Result.success(component);
    }

    @PostMapping
    public Result<ConfigComponent> create(@Validated @RequestBody ConfigComponentRequest request) {
        return Result.success(componentService.create(request, "system"));
    }

    @PutMapping("/{id}")
    public Result<ConfigComponent> update(@PathVariable String id,
                                          @Validated @RequestBody ConfigComponentRequest request) {
        return Result.success(componentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        componentService.delete(id);
        return Result.success();
    }

    /**
     * 根据可视化配置生成 YAML 预览
     */
    @PostMapping("/generate-yaml")
    public Result<Map<String, String>> generateYaml(@Validated @RequestBody GenerateYamlRequest request) {
        String yaml = yamlGeneratorService.generateYaml(
                request.getComponentType(),
                request.getVectorType(),
                request.getVisualConfig()
        );
        return Result.success(Map.of("yaml", yaml));
    }

    /**
     * 获取所有组件的引用情况
     */
    @GetMapping("/references")
    public Result<Map<String, ComponentReferenceDTO>> getAllReferences() {
        return Result.success(referenceService.getAllComponentReferences());
    }

    /**
     * 获取指定组件的引用情况
     */
    @GetMapping("/{id}/references")
    public Result<ComponentReferenceDTO> getReferences(@PathVariable String id) {
        ComponentReferenceDTO dto = referenceService.getComponentReferences(id);
        if (dto == null) {
            return Result.notFound("组件不存在");
        }
        return Result.success(dto);
    }

    /**
     * 获取组件引用数量（排除指定配置）
     * 用于前端判断组件是否被其他配置引用
     */
    @GetMapping("/reference-counts")
    public Result<Map<String, Integer>> getReferenceCounts(
            @RequestParam(required = false) String excludeConfigId) {
        return Result.success(referenceService.getComponentReferenceCountsExcluding(excludeConfigId));
    }

    /**
     * 获取可查询的数据源列表（queryable=true 的 Sink 组件）
     */
    @GetMapping("/queryable")
    public Result<List<ConfigComponent>> getQueryableDataSources() {
        return Result.success(componentService.getQueryableDataSources());
    }

    /**
     * 更新组件的可查询状态
     */
    @PutMapping("/{id}/queryable")
    public Result<Void> updateQueryable(@PathVariable String id, @RequestParam Boolean queryable) {
        componentService.updateQueryable(id, queryable);
        return Result.success();
    }
}
