package cn.mw.loganalysis.vector.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将可视化画布 graphData 转换为 Vector YAML。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisualConfigYamlService {

    private final ObjectMapper objectMapper;

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
                    getSection(config, "sources").put(nodeId, buildSectionConfig(compType, data.path("config"), null));
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
                                buildSectionConfig(stepType, step.path("config"), inputs)
                        );
                    }
                } else if (StringUtils.equals("sink", nodeType)) {
                    String compType = StringUtils.removeEnd(data.path("componentType").asText(), "_sink");
                    List<String> inputs = getIncomingInputIds(nodeId, edges, nodeMap);
                    getSection(config, "sinks").put(nodeId, buildSectionConfig(compType, data.path("config"), inputs));
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
    private Map<String, Object> buildSectionConfig(String type, JsonNode configNode, List<String> inputs) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("type", type);
        if (CollectionUtils.isNotEmpty(inputs)) {
            section.put("inputs", inputs);
        }

        if (configNode != null && !configNode.isMissingNode() && !configNode.isNull()) {
            Map<String, Object> rawConfig = objectMapper.convertValue(configNode, Map.class);
            if (rawConfig != null) {
                rawConfig.remove("type");
                rawConfig.remove("inputs");
                section.putAll(rawConfig);
            }
        }

        return section;
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
