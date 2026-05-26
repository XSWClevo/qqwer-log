package cn.mw.loganalysis.alert.service;

import cn.mw.loganalysis.alert.dto.AlertDatasetTarget;
import cn.mw.loganalysis.alert.entity.AlertRule;
import cn.mw.loganalysis.logcategory.entity.LogCategoryRegistry;
import cn.mw.loganalysis.logcategory.service.LogCategoryRegistryService;
import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import cn.mw.loganalysis.stats.service.query.DatasourceConnectionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 告警规则作用范围解析器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertRuleScopeResolver {

    private final DynamicLogQueryService dynamicLogQueryService;
    private final LogCategoryRegistryService logCategoryRegistryService;

    public List<AlertDatasetTarget> resolve(AlertRule rule) {
        String scopeType = StringUtils.defaultIfBlank(rule.getScopeType(), "all");
        return switch (scopeType) {
            case "category" -> resolveByCategories(rule.getCategoryCodes());
            case "datasource" -> resolveByDatasources(rule.getDatasourceIds());
            case "table" -> resolveByTables(rule.getDatasourceIds(), rule.getTableNames());
            default -> resolveAll();
        };
    }

    private List<AlertDatasetTarget> resolveAll() {
        List<LogCategoryRegistry> registries = logCategoryRegistryService.listEnabled();
        if (CollectionUtils.isNotEmpty(registries)) {
            return registries.stream().map(this::fromRegistry).toList();
        }
        return List.of(defaultTarget());
    }

    private List<AlertDatasetTarget> resolveByCategories(List<String> categoryCodes) {
        List<LogCategoryRegistry> registries = logCategoryRegistryService.listEnabledByCategoryCodes(categoryCodes);
        return registries.stream().map(this::fromRegistry).toList();
    }

    private List<AlertDatasetTarget> resolveByDatasources(List<String> datasourceIds) {
        List<AlertDatasetTarget> targets = new ArrayList<>();
        if (CollectionUtils.isEmpty(datasourceIds)) {
            return targets;
        }

        for (String datasourceId : datasourceIds) {
            DatasourceConnectionConfig config = dynamicLogQueryService.getDatasourceConfigPublic(datasourceId);
            targets.add(AlertDatasetTarget.builder()
                    .datasourceId(datasourceId)
                    .datasourceType(config.getType())
                    .databaseName(config.getDatabase())
                    .tableName(config.getTable())
                    .timeField("timestamp")
                    .messageField("message")
                    .rawField("raw")
                    .severityField("severity")
                    .fieldMapping(defaultFieldMapping())
                    .build());
        }
        return targets;
    }

    private List<AlertDatasetTarget> resolveByTables(List<String> datasourceIds, List<String> tableNames) {
        List<AlertDatasetTarget> targets = new ArrayList<>();
        if (CollectionUtils.isEmpty(tableNames)) {
            return targets;
        }

        if (CollectionUtils.isEmpty(datasourceIds)) {
            for (String tableName : tableNames) {
                targets.add(AlertDatasetTarget.builder()
                        .datasourceType("clickhouse")
                        .tableName(tableName)
                        .timeField("timestamp")
                        .messageField("message")
                        .rawField("raw")
                        .severityField("severity")
                        .fieldMapping(defaultFieldMapping())
                        .build());
            }
            return targets;
        }

        for (String datasourceId : datasourceIds) {
            DatasourceConnectionConfig config = dynamicLogQueryService.getDatasourceConfigPublic(datasourceId);
            for (String tableName : tableNames) {
                LogCategoryRegistry registry = logCategoryRegistryService.getEnabledByDatasourceAndTable(datasourceId, tableName);
                if (registry != null) {
                    targets.add(fromRegistry(registry));
                    continue;
                }

                targets.add(AlertDatasetTarget.builder()
                        .datasourceId(datasourceId)
                        .datasourceType(config.getType())
                        .databaseName(config.getDatabase())
                        .tableName(tableName)
                        .timeField("timestamp")
                        .messageField("message")
                        .rawField("raw")
                        .severityField("severity")
                        .fieldMapping(defaultFieldMapping())
                        .build());
            }
        }
        return targets;
    }

    private AlertDatasetTarget fromRegistry(LogCategoryRegistry registry) {
        DatasourceConnectionConfig config = null;
        if (StringUtils.isNotBlank(registry.getDatasourceId())) {
            config = dynamicLogQueryService.getDatasourceConfigPublic(registry.getDatasourceId());
        }

        Map<String, String> fieldMapping = new LinkedHashMap<>(defaultFieldMapping());
        fieldMapping.put("timestamp", StringUtils.defaultIfBlank(registry.getTimeField(), "timestamp"));
        fieldMapping.put("message", StringUtils.defaultIfBlank(registry.getMessageField(), "message"));
        fieldMapping.put("raw", StringUtils.defaultIfBlank(registry.getRawField(), "raw"));
        fieldMapping.put("severity", StringUtils.defaultIfBlank(registry.getSeverityField(), "severity"));
        fieldMapping.put("source_ip", StringUtils.defaultIfBlank(registry.getSourceIpField(), "source_ip"));
        fieldMapping.put("appname", StringUtils.defaultIfBlank(registry.getAppnameField(), "appname"));
        fieldMapping.put("hostname", StringUtils.defaultIfBlank(registry.getHostnameField(), "hostname"));
        if (MapUtils.isNotEmpty(registry.getExtraMapping())) {
            fieldMapping.putAll(registry.getExtraMapping());
        }

        return AlertDatasetTarget.builder()
                .datasourceId(registry.getDatasourceId())
                .datasourceType(config != null ? config.getType() : "clickhouse")
                .databaseName(StringUtils.defaultIfBlank(registry.getDatabaseName(), config != null ? config.getDatabase() : null))
                .tableName(registry.getTableName())
                .timeField(fieldMapping.get("timestamp"))
                .messageField(fieldMapping.get("message"))
                .rawField(fieldMapping.get("raw"))
                .severityField(fieldMapping.get("severity"))
                .fieldMapping(fieldMapping)
                .build();
    }

    private AlertDatasetTarget defaultTarget() {
        return AlertDatasetTarget.builder()
                .datasourceType("clickhouse")
                .tableName("syslog_logs")
                .timeField("timestamp")
                .messageField("message")
                .rawField("raw")
                .severityField("severity")
                .fieldMapping(defaultFieldMapping())
                .build();
    }

    private Map<String, String> defaultFieldMapping() {
        Map<String, String> fieldMapping = new LinkedHashMap<>();
        fieldMapping.put("id", "id");
        fieldMapping.put("timestamp", "timestamp");
        fieldMapping.put("severity", "severity");
        fieldMapping.put("hostname", "hostname");
        fieldMapping.put("appname", "appname");
        fieldMapping.put("source_type", "source_type");
        fieldMapping.put("message", "message");
        fieldMapping.put("facility", "facility");
        fieldMapping.put("procid", "procid");
        fieldMapping.put("source_ip", "source_ip");
        fieldMapping.put("raw", "raw");
        return fieldMapping;
    }
}
