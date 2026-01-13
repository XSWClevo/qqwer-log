package cn.mw.loganalysis.vector.service;

import cn.mw.loganalysis.vector.dto.ComponentReferenceDTO;
import cn.mw.loganalysis.vector.dto.ComponentReferenceDTO.ConfigReference;
import cn.mw.loganalysis.vector.entity.ConfigComponent;
import cn.mw.loganalysis.vector.entity.VisualConfig;
import cn.mw.loganalysis.vector.mapper.ConfigComponentMapper;
import cn.mw.loganalysis.vector.mapper.VisualConfigMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 组件引用查询服务
 * 用于查询组件被哪些可视化配置引用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComponentReferenceService {

    private final ConfigComponentMapper componentMapper;
    private final VisualConfigMapper visualConfigMapper;
    private final ObjectMapper objectMapper;

    /**
     * 获取所有组件的引用情况
     * 
     * @return Map<componentId, ComponentReferenceDTO>
     */
    public Map<String, ComponentReferenceDTO> getAllComponentReferences() {
        Map<String, ComponentReferenceDTO> result = new HashMap<>();

        // 1. 获取所有组件
        List<ConfigComponent> components = componentMapper.selectList(null);
        for (ConfigComponent comp : components) {
            result.put(comp.getId(), ComponentReferenceDTO.builder()
                    .componentId(comp.getId())
                    .componentName(comp.getName())
                    .references(new ArrayList<>())
                    .referenceCount(0)
                    .build());
        }

        // 2. 遍历所有可视化配置，解析 graphData 找出引用的组件
        List<VisualConfig> configs = visualConfigMapper.selectList(null);
        for (VisualConfig config : configs) {
            parseConfigReferences(config, result);
        }

        // 3. 更新引用数量
        result.values().forEach(dto -> dto.setReferenceCount(dto.getReferences().size()));

        return result;
    }

    /**
     * 获取指定组件的引用情况
     * 
     * @param componentId 组件ID
     * @return 引用信息，如果组件不存在返回 null
     */
    public ComponentReferenceDTO getComponentReferences(String componentId) {
        ConfigComponent component = componentMapper.selectById(componentId);
        if (component == null) {
            return null;
        }

        ComponentReferenceDTO dto = ComponentReferenceDTO.builder()
                .componentId(componentId)
                .componentName(component.getName())
                .references(new ArrayList<>())
                .referenceCount(0)
                .build();

        // 遍历所有可视化配置查找引用
        List<VisualConfig> configs = visualConfigMapper.selectList(null);
        for (VisualConfig config : configs) {
            List<ConfigReference> refs = findReferencesInConfig(config, componentId);
            dto.getReferences().addAll(refs);
        }

        dto.setReferenceCount(dto.getReferences().size());
        return dto;
    }

    /**
     * 批量获取组件引用情况（排除指定配置）
     * 用于前端判断组件是否被其他配置引用
     * 
     * @param excludeConfigId 要排除的配置ID（当前正在编辑的配置）
     * @return Map<componentId, referenceCount>
     */
    public Map<String, Integer> getComponentReferenceCountsExcluding(String excludeConfigId) {
        Map<String, Integer> result = new HashMap<>();

        // 获取所有组件
        List<ConfigComponent> components = componentMapper.selectList(null);
        for (ConfigComponent comp : components) {
            result.put(comp.getId(), 0);
        }

        // 遍历所有可视化配置（排除指定配置）
        List<VisualConfig> configs = visualConfigMapper.selectList(null);
        for (VisualConfig config : configs) {
            if (excludeConfigId != null && excludeConfigId.equals(config.getId())) {
                continue;
            }
            
            Set<String> referencedIds = extractComponentIds(config);
            for (String compId : referencedIds) {
                result.merge(compId, 1, Integer::sum);
            }
        }

        return result;
    }

    /**
     * 解析配置中的组件引用，更新到 result map
     */
    private void parseConfigReferences(VisualConfig config, Map<String, ComponentReferenceDTO> result) {
        String graphData = config.getGraphData();
        if (graphData == null || graphData.isEmpty()) {
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(graphData);
            JsonNode cells = root.path("cells");
            
            if (!cells.isArray()) {
                return;
            }

            for (JsonNode cell : cells) {
                // 只处理节点（shape 为 rect）
                if (!"rect".equals(cell.path("shape").asText())) {
                    continue;
                }

                JsonNode data = cell.path("data");
                String componentId = data.path("componentId").asText(null);
                String nodeName = data.path("name").asText("unknown");

                if (componentId != null && result.containsKey(componentId)) {
                    ComponentReferenceDTO dto = result.get(componentId);
                    dto.getReferences().add(ConfigReference.builder()
                            .configId(config.getId())
                            .configName(config.getName())
                            .nodeName(nodeName)
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("解析配置 {} 的 graphData 失败: {}", config.getId(), e.getMessage());
        }
    }

    /**
     * 在指定配置中查找对某个组件的引用
     */
    private List<ConfigReference> findReferencesInConfig(VisualConfig config, String targetComponentId) {
        List<ConfigReference> refs = new ArrayList<>();
        String graphData = config.getGraphData();
        
        if (graphData == null || graphData.isEmpty()) {
            return refs;
        }

        try {
            JsonNode root = objectMapper.readTree(graphData);
            JsonNode cells = root.path("cells");
            
            if (!cells.isArray()) {
                return refs;
            }

            for (JsonNode cell : cells) {
                if (!"rect".equals(cell.path("shape").asText())) {
                    continue;
                }

                JsonNode data = cell.path("data");
                String componentId = data.path("componentId").asText(null);
                
                if (targetComponentId.equals(componentId)) {
                    refs.add(ConfigReference.builder()
                            .configId(config.getId())
                            .configName(config.getName())
                            .nodeName(data.path("name").asText("unknown"))
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("解析配置 {} 的 graphData 失败: {}", config.getId(), e.getMessage());
        }

        return refs;
    }

    /**
     * 提取配置中引用的所有组件ID
     */
    private Set<String> extractComponentIds(VisualConfig config) {
        Set<String> ids = new HashSet<>();
        String graphData = config.getGraphData();
        
        if (graphData == null || graphData.isEmpty()) {
            return ids;
        }

        try {
            JsonNode root = objectMapper.readTree(graphData);
            JsonNode cells = root.path("cells");
            
            if (!cells.isArray()) {
                return ids;
            }

            for (JsonNode cell : cells) {
                if (!"rect".equals(cell.path("shape").asText())) {
                    continue;
                }

                String componentId = cell.path("data").path("componentId").asText(null);
                if (componentId != null && !componentId.isEmpty()) {
                    ids.add(componentId);
                }
            }
        } catch (Exception e) {
            log.warn("解析配置 {} 的 graphData 失败: {}", config.getId(), e.getMessage());
        }

        return ids;
    }
}
