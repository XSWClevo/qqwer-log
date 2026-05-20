package cn.mw.loganalysis.vector.service;

import cn.mw.loganalysis.vector.entity.ConfigComponent;
import cn.mw.loganalysis.vector.mapper.ConfigComponentMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 将可视化画布 graphData 转换为 Vector YAML。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisualConfigYamlService {

    private final ObjectMapper objectMapper;
    private final ConfigComponentMapper configComponentMapper;

    public String generateContentFromGraphData(String graphData) {
        if (StringUtils.isBlank(graphData)) {
            return "";
        }

        try {
            JsonNode root = objectMapper.readTree(graphData);
            JsonNode nodes = root.path("nodes");
            JsonNode edges = root.path("edges");

            if (!nodes.isArray() || !edges.isArray()) {
                return "";
            }

            Map<String, JsonNode> nodeMap = new LinkedHashMap<>();
            for (JsonNode node : nodes) {
                String nodeId = node.path("id").asText();
                if (StringUtils.isNotBlank(nodeId)) {
                    nodeMap.put(nodeId, node);
                }
            }

            Map<String, Object> config = new LinkedHashMap<>();
            config.put("sources", new LinkedHashMap<String, Object>());
            config.put("transforms", new LinkedHashMap<String, Object>());
            config.put("sinks", new LinkedHashMap<String, Object>());

            for (JsonNode node : nodes) {
                String nodeId = node.path("id").asText();
                String nodeType = node.path("type").asText();
                JsonNode data = node.path("data");

                if (StringUtils.equals("source", nodeType)) {
                    String compType = StringUtils.removeEnd(data.path("componentType").asText(), "_source");
                    getSection(config, "sources").put(
                            nodeId,
                            buildSectionConfig("sources", compType, data.path("componentId").asText(null), data.path("config"), null)
                    );
                } else if (StringUtils.equals("processors", nodeType)) {
                    JsonNode steps = data.path("steps");
                    if (!steps.isArray()) {
                        continue;
                    }

                    for (int i = 0; i < steps.size(); i++) {
                        JsonNode step = steps.get(i);
                        String stepId = step.path("id").asText();
                        String stepType = step.path("type").asText();
                        List<String> inputs = i == 0
                                ? getIncomingInputIds(nodeId, edges, nodeMap)
                                : List.of(steps.get(i - 1).path("id").asText());
                        getSection(config, "transforms").put(
                                stepId,
                                buildSectionConfig("transforms", stepType, step.path("componentId").asText(null), step.path("config"), inputs)
                        );
                    }
                } else if (StringUtils.equals("sink", nodeType)) {
                    String compType = StringUtils.removeEnd(data.path("componentType").asText(), "_sink");
                    List<String> inputs = getIncomingInputIds(nodeId, edges, nodeMap);
                    getSection(config, "sinks").put(
                            nodeId,
                            buildSectionConfig("sinks", compType, data.path("componentId").asText(null), data.path("config"), inputs)
                    );
                }
            }

            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            options.setIndent(2);
            options.setWidth(Integer.MAX_VALUE);
            return new Yaml(options).dump(config);
        } catch (JsonProcessingException e) {
            log.warn("解析 graphData 失败: {}", e.getMessage());
            throw new RuntimeException("流程图数据解析失败");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getSection(Map<String, Object> config, String key) {
        return (Map<String, Object>) config.get(key);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildSectionConfig(String sectionName, String type, String componentId, JsonNode configNode, List<String> inputs) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("type", type);
        if (CollectionUtils.isNotEmpty(inputs)) {
            section.put("inputs", inputs);
        }

        Map<String, Object> componentConfig = loadComponentConfig(componentId);
        if (MapUtils.isNotEmpty(componentConfig)) {
            componentConfig.remove("type");
            componentConfig.remove("inputs");
            section.putAll(normalizeComponentConfig(sectionName, type, componentConfig));
        }

        if (configNode != null && !configNode.isMissingNode() && !configNode.isNull()) {
            Map<String, Object> rawConfig = objectMapper.convertValue(configNode, Map.class);
            if (rawConfig != null) {
                rawConfig.remove("type");
                rawConfig.remove("inputs");
                section.putAll(normalizeComponentConfig(sectionName, type, rawConfig));
            }
        }

        return section;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadComponentConfig(String componentId) {
        if (StringUtils.isBlank(componentId)) {
            return new LinkedHashMap<>();
        }

        ConfigComponent component = configComponentMapper.selectById(componentId);
        if (component == null || StringUtils.isBlank(component.getConfigYaml())) {
            return new LinkedHashMap<>();
        }

        try {
            Object parsed = new Yaml().load(component.getConfigYaml());
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        result.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                return result;
            }
        } catch (Exception e) {
            log.warn("解析组件 YAML 失败: componentId={}, error={}", componentId, e.getMessage());
        }

        return new LinkedHashMap<>();
    }

    private Map<String, Object> normalizeComponentConfig(String sectionName, String type, Map<String, Object> rawConfig) {
        Map<String, Object> normalized = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : rawConfig.entrySet()) {
            Object value = normalizeValue(entry.getValue());
            if (value != null) {
                normalized.put(entry.getKey(), value);
            }
        }

        if (StringUtils.equals(sectionName, "sources") && StringUtils.equals(type, "file")) {
            normalizeStringListField(normalized, "include");
            normalizeStringListField(normalized, "exclude");
        }
        if (StringUtils.equals(sectionName, "sources") && StringUtils.equals(type, "kafka")) {
            normalizeStringListField(normalized, "topics");
        }
        if (StringUtils.equals(sectionName, "sinks") && StringUtils.equals(type, "elasticsearch")) {
            normalizeStringListField(normalized, "endpoints");
        }

        return normalized;
    }

    @SuppressWarnings("unchecked")
    private Object normalizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String stringValue) {
            return StringUtils.isBlank(stringValue) ? null : stringValue;
        }
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                Object normalizedValue = normalizeValue(entry.getValue());
                if (entry.getKey() != null && normalizedValue != null) {
                    normalized.put(String.valueOf(entry.getKey()), normalizedValue);
                }
            }
            return normalized.isEmpty() ? null : normalized;
        }
        if (value instanceof List<?> listValue) {
            List<Object> normalized = listValue.stream()
                    .map(this::normalizeValue)
                    .filter(item -> item != null)
                    .collect(Collectors.toList());
            return normalized.isEmpty() ? null : normalized;
        }
        return value;
    }

    private void normalizeStringListField(Map<String, Object> config, String fieldName) {
        Object value = config.get(fieldName);
        if (value instanceof String stringValue) {
            String[] parts = StringUtils.split(stringValue, ',');
            List<String> values = parts == null ? List.of() : Stream.of(parts)
                    .map(StringUtils::trimToEmpty)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toList());
            if (CollectionUtils.isEmpty(values)) {
                config.remove(fieldName);
            } else {
                config.put(fieldName, values);
            }
        }
    }

    private List<String> getIncomingInputIds(String targetNodeId, JsonNode edges, Map<String, JsonNode> nodeMap) {
        List<String> inputs = new ArrayList<>();

        for (JsonNode edge : edges) {
            if (!StringUtils.equals(targetNodeId, edge.path("target").asText())) {
                continue;
            }

            String sourceId = edge.path("source").asText();
            JsonNode sourceNode = nodeMap.get(sourceId);
            if (sourceNode == null) {
                inputs.add(sourceId);
                continue;
            }

            inputs.addAll(getNodeOutputIds(sourceNode));
        }

        return inputs;
    }

    private List<String> getNodeOutputIds(JsonNode node) {
        String nodeType = node.path("type").asText();
        if (!StringUtils.equals("processors", nodeType)) {
            return List.of(node.path("id").asText());
        }

        JsonNode steps = node.path("data").path("steps");
        if (!steps.isArray() || steps.isEmpty()) {
            return List.of(node.path("id").asText());
        }

        JsonNode lastStep = steps.get(steps.size() - 1);
        return List.of(lastStep.path("id").asText());
    }
}
