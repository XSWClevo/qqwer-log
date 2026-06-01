package cn.mw.loganalysis.dashboard.service;

import cn.mw.loganalysis.dashboard.dto.DashboardDatasetCandidateDTO;
import cn.mw.loganalysis.logcategory.entity.LogCategoryRegistry;
import cn.mw.loganalysis.logcategory.mapper.LogCategoryRegistryMapper;
import cn.mw.loganalysis.vector.entity.ConfigComponent;
import cn.mw.loganalysis.vector.service.ConfigComponentService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 发现可用于首页展示的日志数据集候选。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardDatasetDiscoveryService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ConfigComponentService configComponentService;
    private final LogCategoryRegistryMapper logCategoryRegistryMapper;
    private final ObjectMapper objectMapper;

    /**
     * 优先从 queryable ClickHouse sink 发现候选，若没有则回退注册表。
     */
    public List<DashboardDatasetCandidateDTO> discoverCandidates() {
        List<DashboardDatasetCandidateDTO> sinkCandidates = fromQueryableSinks();
        if (CollectionUtils.isNotEmpty(sinkCandidates)) {
            return sinkCandidates;
        }
        return fromRegistry();
    }

    private List<DashboardDatasetCandidateDTO> fromQueryableSinks() {
        return configComponentService.getQueryableClickHouseSinks().stream()
                .filter(component -> !Boolean.TRUE.equals(component.getIsTemplate()))
                .filter(component -> !StringUtils.startsWithIgnoreCase(component.getId(), "template-"))
                .map(this::mapFromSink)
                .filter(candidate -> StringUtils.isNotBlank(candidate.getTableName()))
                .collect(Collectors.toList());
    }

    private DashboardDatasetCandidateDTO mapFromSink(ConfigComponent component) {
        Map<String, Object> visualData = parseVisualData(component.getVisualData());
        Map<String, Object> yamlData = parseYamlData(component.getConfigYaml());
        String database = firstNonBlank(
                asString(visualData.get("database")),
                asString(yamlData.get("database")),
                extractNestedString(visualData, "connection", "database"),
                "default"
        );
        String table = firstNonBlank(
                asString(visualData.get("table")),
                asString(visualData.get("tableName")),
                extractNestedString(visualData, "connection", "table"),
                extractNestedString(visualData, "connection", "tableName"),
                asString(yamlData.get("table")),
                asString(yamlData.get("tableName")),
                extractNestedString(visualData, "connection", "table")
        );
        Map<String, String> fieldMapping = buildFieldMapping(visualData, yamlData);
        return DashboardDatasetCandidateDTO.builder()
                .source("queryable_sink")
                .datasourceId(component.getId())
                .datasourceName(firstNonBlank(component.getDisplayName(), component.getName()))
                .databaseName(database)
                .tableName(table)
                .componentType(component.getVectorType())
                .queryable(component.getQueryable())
                .fieldMapping(fieldMapping)
                .build();
    }

    private List<DashboardDatasetCandidateDTO> fromRegistry() {
        return logCategoryRegistryMapper.selectEnabled().stream()
                .map(this::mapFromRegistry)
                .filter(candidate -> StringUtils.isNotBlank(candidate.getTableName()))
                .collect(Collectors.toList());
    }

    private DashboardDatasetCandidateDTO mapFromRegistry(LogCategoryRegistry registry) {
        Map<String, String> fieldMapping = new HashMap<>();
        fieldMapping.put("timestamp", firstNonBlank(registry.getTimeField(), "timestamp"));
        fieldMapping.put("severity", firstNonBlank(registry.getSeverityField(), "severity"));
        fieldMapping.put("message", firstNonBlank(registry.getMessageField(), "message"));
        fieldMapping.put("raw", firstNonBlank(registry.getRawField(), "raw"));
        fieldMapping.put("hostname", firstNonBlank(registry.getHostnameField(), "hostname"));
        fieldMapping.put("appname", firstNonBlank(registry.getAppnameField(), "appname"));
        if (registry.getExtraMapping() != null) {
            fieldMapping.putAll(registry.getExtraMapping());
        }

        return DashboardDatasetCandidateDTO.builder()
                .source("registry")
                .datasourceId(registry.getDatasourceId())
                .datasourceName(firstNonBlank(registry.getCategoryName(), registry.getCategoryCode(), registry.getTableName()))
                .databaseName(firstNonBlank(registry.getDatabaseName(), "default"))
                .tableName(registry.getTableName())
                .componentType("clickhouse")
                .queryable(Boolean.TRUE)
                .fieldMapping(fieldMapping)
                .build();
    }

    private Map<String, String> buildFieldMapping(Map<String, Object> visualData, Map<String, Object> yamlData) {
        Map<String, String> fieldMapping = new HashMap<>();
        fieldMapping.put("timestamp", resolveFieldAlias(visualData, yamlData, "timestamp", "time", "@timestamp"));
        fieldMapping.put("severity", resolveFieldAlias(visualData, yamlData, "severity", "level", "log_level"));
        fieldMapping.put("message", resolveFieldAlias(visualData, yamlData, "message", "msg", "content"));
        fieldMapping.put("raw", resolveFieldAlias(visualData, yamlData, "raw", "original", "log"));
        fieldMapping.put("hostname", resolveFieldAlias(visualData, yamlData, "hostname", "host", "host_name"));
        fieldMapping.put("appname", resolveFieldAlias(visualData, yamlData, "appname", "app_name", "service"));
        return fieldMapping;
    }

    private String resolveFieldAlias(Map<String, Object> visualData,
                                     Map<String, Object> yamlData,
                                     String defaultField,
                                     String... aliases) {
        String configured = firstNonBlank(
                findExistingField(visualData, aliases),
                findExistingField(yamlData, aliases)
        );
        return StringUtils.defaultIfBlank(configured, defaultField);
    }

    private String findExistingField(Map<String, Object> source, String... candidates) {
        if (source == null || source.isEmpty() || candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (source.containsKey(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private Map<String, Object> parseVisualData(String visualData) {
        if (StringUtils.isBlank(visualData)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(visualData, MAP_TYPE);
        } catch (Exception ex) {
            log.warn("解析 queryable sink visualData 失败: {}", ex.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseYamlData(String configYaml) {
        if (StringUtils.isBlank(configYaml)) {
            return Map.of();
        }
        try {
            Object yamlObject = new Yaml().load(configYaml);
            if (yamlObject instanceof Map<?, ?> yamlMap) {
                return (Map<String, Object>) yamlMap;
            }
        } catch (Exception ex) {
            log.warn("解析 queryable sink configYaml 失败: {}", ex.getMessage());
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private String extractNestedString(Map<String, Object> source, String key, String nestedKey) {
        Object nested = source.get(key);
        if (nested instanceof Map<?, ?> nestedMap) {
            return asString(((Map<String, Object>) nestedMap).get(nestedKey));
        }
        return null;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }
}
