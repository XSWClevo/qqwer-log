package cn.mw.loganalysis.vector.service;

import cn.mw.loganalysis.vector.entity.SharedComponent;
import cn.mw.loganalysis.vector.mapper.SharedComponentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 共享组件服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SharedComponentService {

    private final SharedComponentMapper sharedComponentMapper;

    /**
     * 获取所有共享组件
     */
    public List<SharedComponent> getAll() {
        return sharedComponentMapper.selectByType(null);
    }

    /**
     * 根据类型获取共享组件
     */
    public List<SharedComponent> getByType(String componentType) {
        return sharedComponentMapper.selectByType(componentType);
    }

    /**
     * 根据ID获取共享组件
     */
    public SharedComponent getById(String id) {
        return sharedComponentMapper.selectById(id);
    }

    /**
     * 根据 componentKey 获取共享组件
     */
    public SharedComponent getByKey(String componentKey) {
        return sharedComponentMapper.selectByKey(componentKey);
    }

    /**
     * 创建共享组件
     */
    @Transactional(rollbackFor = Exception.class)
    public SharedComponent create(SharedComponent component) {
        // 检查 componentKey 是否已存在
        if (sharedComponentMapper.selectByKey(component.getComponentKey()) != null) {
            throw new RuntimeException("组件标识已存在: " + component.getComponentKey());
        }
        
        component.setIsActive(true);
        sharedComponentMapper.insert(component);
        log.info("创建共享组件: {}", component.getName());
        return component;
    }

    /**
     * 更新共享组件
     */
    @Transactional(rollbackFor = Exception.class)
    public SharedComponent update(String id, SharedComponent component) {
        SharedComponent existing = sharedComponentMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("共享组件不存在");
        }
        
        // 检查 componentKey 是否被其他组件使用
        SharedComponent byKey = sharedComponentMapper.selectByKey(component.getComponentKey());
        if (byKey != null && !byKey.getId().equals(id)) {
            throw new RuntimeException("组件标识已被其他组件使用: " + component.getComponentKey());
        }
        
        component.setId(id);
        sharedComponentMapper.updateById(component);
        log.info("更新共享组件: {}", component.getName());
        return component;
    }

    /**
     * 删除共享组件（检查依赖）
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        SharedComponent component = sharedComponentMapper.selectById(id);
        if (component == null) {
            throw new RuntimeException("共享组件不存在");
        }
        
        // 检查是否有配置引用该组件
        int refCount = sharedComponentMapper.countReferences(id);
        if (refCount > 0) {
            List<String> configNames = sharedComponentMapper.selectReferencingConfigNames(id);
            throw new RuntimeException("无法删除：该组件被 " + refCount + " 个配置引用 (" + 
                    String.join(", ", configNames) + ")");
        }
        
        sharedComponentMapper.deleteById(id);
        log.info("删除共享组件: {}", component.getName());
    }

    /**
     * 获取组件的引用数量
     */
    public int getReferenceCount(String id) {
        return sharedComponentMapper.countReferences(id);
    }

    /**
     * 获取引用该组件的配置名称
     */
    public List<String> getReferencingConfigNames(String id) {
        return sharedComponentMapper.selectReferencingConfigNames(id);
    }
}
