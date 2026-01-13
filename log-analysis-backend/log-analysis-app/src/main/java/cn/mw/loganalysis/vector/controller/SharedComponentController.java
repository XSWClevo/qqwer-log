package cn.mw.loganalysis.vector.controller;

import cn.mw.loganalysis.common.response.Result;
import cn.mw.loganalysis.vector.entity.SharedComponent;
import cn.mw.loganalysis.vector.service.SharedComponentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 共享组件管理 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/vector/shared-components")
@RequiredArgsConstructor
public class SharedComponentController {

    private final SharedComponentService sharedComponentService;

    /**
     * 获取所有共享组件
     */
    @GetMapping
    public Result<List<SharedComponent>> getAll(@RequestParam(required = false) String componentType) {
        List<SharedComponent> components;
        if (componentType != null && !componentType.isEmpty()) {
            components = sharedComponentService.getByType(componentType);
        } else {
            components = sharedComponentService.getAll();
        }
        return Result.success(components);
    }

    /**
     * 根据ID获取共享组件
     */
    @GetMapping("/{id}")
    public Result<SharedComponent> getById(@PathVariable String id) {
        SharedComponent component = sharedComponentService.getById(id);
        if (component == null) {
            return Result.notFound("共享组件不存在");
        }
        return Result.success(component);
    }

    /**
     * 创建共享组件
     */
    @PostMapping
    public Result<SharedComponent> create(@Validated @RequestBody SharedComponent component) {
        try {
            SharedComponent created = sharedComponentService.create(component);
            return Result.success(created);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新共享组件
     */
    @PutMapping("/{id}")
    public Result<SharedComponent> update(@PathVariable String id, @Validated @RequestBody SharedComponent component) {
        try {
            SharedComponent updated = sharedComponentService.update(id, component);
            return Result.success(updated);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除共享组件
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        try {
            sharedComponentService.delete(id);
            return Result.success(null);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取组件的引用信息
     */
    @GetMapping("/{id}/references")
    public Result<Map<String, Object>> getReferences(@PathVariable String id) {
        int count = sharedComponentService.getReferenceCount(id);
        List<String> configNames = sharedComponentService.getReferencingConfigNames(id);
        
        Map<String, Object> result = new HashMap<>();
        result.put("count", count);
        result.put("configNames", configNames);
        
        return Result.success(result);
    }
}
