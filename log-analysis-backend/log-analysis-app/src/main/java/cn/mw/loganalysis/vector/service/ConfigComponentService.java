package cn.mw.loganalysis.vector.service;

import cn.mw.loganalysis.vector.dto.ConfigComponentRequest;
import cn.mw.loganalysis.vector.entity.ConfigComponent;
import cn.mw.loganalysis.vector.mapper.ConfigComponentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigComponentService {

    private final ConfigComponentMapper componentMapper;

    public List<ConfigComponent> getList(String keyword, String componentType) {
        return componentMapper.selectByCondition(keyword, componentType);
    }

    public ConfigComponent getById(String id) {
        return componentMapper.selectById(id);
    }

    /**
     * 智能助手和日志查询入口只能使用显式标记为可查询的 Sink 组件。
     */
    public ConfigComponent getQueryableDataSourceById(String id) {
        if (StringUtils.isBlank(id)) {
            return null;
        }
        ConfigComponent component = componentMapper.selectById(StringUtils.trim(id));
        if (component == null) {
            return null;
        }
        if (!StringUtils.equalsIgnoreCase(component.getComponentType(), "sink")
                || !BooleanUtils.isTrue(component.getQueryable())) {
            return null;
        }
        return component;
    }

    @Transactional(rollbackFor = Exception.class)
    public ConfigComponent create(ConfigComponentRequest request, String userId) {
        ConfigComponent component = new ConfigComponent();
        BeanUtils.copyProperties(request, component);
        component.setCreatedBy(userId);
        if (component.getIsTemplate() == null) {
            component.setIsTemplate(false);
        }
        componentMapper.insert(component);
        log.info("创建组件成功: {}", component.getName());
        return component;
    }

    @Transactional(rollbackFor = Exception.class)
    public ConfigComponent update(String id, ConfigComponentRequest request) {
        ConfigComponent component = componentMapper.selectById(id);
        if (component == null) {
            throw new RuntimeException("组件不存在");
        }
        BeanUtils.copyProperties(request, component);
        componentMapper.updateById(component);
        log.info("更新组件成功: {}", component.getName());
        return component;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        ConfigComponent component = componentMapper.selectById(id);
        if (component == null) {
            throw new RuntimeException("组件不存在");
        }
        componentMapper.deleteById(id);
        log.info("删除组件成功: {}", component.getName());
    }

    /**
     * 获取可查询的数据源列表（queryable=true 的 Sink 组件）
     */
    public List<ConfigComponent> getQueryableDataSources() {
        return componentMapper.selectQueryableSinks();
    }

    /**
     * 获取可查询的 ClickHouse Sink。
     * Dashboard 和智能助手都应只消费这类显式可查询的数据集。
     */
    public List<ConfigComponent> getQueryableClickHouseSinks() {
        return getQueryableDataSources().stream()
                .filter(component -> StringUtils.equalsIgnoreCase(component.getVectorType(), "clickhouse"))
                .toList();
    }

    /**
     * 更新组件的可查询状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateQueryable(String id, Boolean queryable) {
        ConfigComponent component = componentMapper.selectById(id);
        if (component == null) {
            throw new RuntimeException("组件不存在");
        }
        if (!"sink".equals(component.getComponentType())) {
            throw new RuntimeException("只有 Sink 组件可以设置为数据源");
        }
        component.setQueryable(queryable);
        componentMapper.updateById(component);
        log.info("更新组件可查询状态: {} -> {}", component.getName(), queryable);
    }
}
