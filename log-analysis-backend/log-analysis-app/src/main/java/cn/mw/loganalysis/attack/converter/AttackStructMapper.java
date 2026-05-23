package cn.mw.loganalysis.attack.converter;

import cn.mw.loganalysis.attack.dto.AttackClassificationQueryRequest;
import cn.mw.loganalysis.attack.dto.CreateAttackDatasetRequest;
import cn.mw.loganalysis.attack.dto.CreateAttackRuleRequest;
import cn.mw.loganalysis.attack.dto.UpdateAttackDatasetRequest;
import cn.mw.loganalysis.attack.dto.UpdateAttackRuleRequest;
import cn.mw.loganalysis.attack.entity.AttackClassificationRecord;
import cn.mw.loganalysis.attack.entity.AttackDetectionRule;
import cn.mw.loganalysis.attack.entity.AttackLogDataset;
import cn.mw.loganalysis.attack.mapper.param.AttackClassificationQuerySqlParam;
import cn.mw.loganalysis.attack.model.NormalizedLogRecord;
import cn.mw.loganalysis.common.util.DateTimeUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Mapper(componentModel = "spring")
public interface AttackStructMapper {

    BigDecimal DEFAULT_CONFIDENCE = new BigDecimal("0.80");
    int DEFAULT_BATCH_SIZE = 500;

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ruleId", source = "ruleId", qualifiedByName = "trim")
    @Mapping(target = "name", source = "name", qualifiedByName = "trim")
    @Mapping(target = "description", source = "description", qualifiedByName = "trimToEmpty")
    @Mapping(target = "attackType", source = "attackType", qualifiedByName = "trim")
    @Mapping(target = "attackSubType", source = "attackSubType", qualifiedByName = "trimToEmpty")
    @Mapping(target = "severity", source = "severity", qualifiedByName = "defaultSeverity")
    @Mapping(target = "confidence", source = "confidence")
    @Mapping(target = "requiredFields", source = "requiredFields")
    @Mapping(target = "datasourceTypes", source = "datasourceTypes")
    @Mapping(target = "messagePatterns", source = "messagePatterns")
    @Mapping(target = "rawPatterns", source = "rawPatterns")
    @Mapping(target = "keywords", source = "keywords")
    @Mapping(target = "reasonTemplate", source = "reasonTemplate", qualifiedByName = "trimToEmpty")
    @Mapping(target = "mitreTactic", source = "mitreTactic", qualifiedByName = "trimToEmpty")
    @Mapping(target = "mitreTechnique", source = "mitreTechnique", qualifiedByName = "trimToEmpty")
    @Mapping(target = "enabled", source = "enabled")
    @Mapping(target = "priority", source = "priority", qualifiedByName = "identityInteger")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AttackDetectionRule toRule(CreateAttackRuleRequest request);

    @BeanMapping(ignoreByDefault = true, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ruleId", ignore = true)
    @Mapping(target = "name", source = "name", qualifiedByName = "trimToNull")
    @Mapping(target = "description", source = "description", qualifiedByName = "trimToEmpty")
    @Mapping(target = "attackType", source = "attackType", qualifiedByName = "trimToNull")
    @Mapping(target = "attackSubType", source = "attackSubType", qualifiedByName = "trimToEmpty")
    @Mapping(target = "severity", source = "severity", qualifiedByName = "trimToNull")
    @Mapping(target = "confidence", source = "confidence")
    @Mapping(target = "requiredFields", source = "requiredFields")
    @Mapping(target = "datasourceTypes", source = "datasourceTypes")
    @Mapping(target = "messagePatterns", source = "messagePatterns")
    @Mapping(target = "rawPatterns", source = "rawPatterns")
    @Mapping(target = "keywords", source = "keywords")
    @Mapping(target = "reasonTemplate", source = "reasonTemplate", qualifiedByName = "trimToEmpty")
    @Mapping(target = "mitreTactic", source = "mitreTactic", qualifiedByName = "trimToEmpty")
    @Mapping(target = "mitreTechnique", source = "mitreTechnique", qualifiedByName = "trimToEmpty")
    @Mapping(target = "enabled", source = "enabled")
    @Mapping(target = "priority", source = "priority", qualifiedByName = "identityInteger")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateRule(UpdateAttackRuleRequest request, @MappingTarget AttackDetectionRule rule);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "name", source = "name", qualifiedByName = "trim")
    @Mapping(target = "datasourceType", source = "datasourceType", qualifiedByName = "lowerTrim")
    @Mapping(target = "datasourceId", source = "datasourceId", qualifiedByName = "trimToEmpty")
    @Mapping(target = "databaseName", source = "databaseName", qualifiedByName = "defaultDatabase")
    @Mapping(target = "tableName", source = "tableName", qualifiedByName = "trimToEmpty")
    @Mapping(target = "indexName", source = "indexName", qualifiedByName = "trimToEmpty")
    @Mapping(target = "fieldMapping", source = "fieldMapping", qualifiedByName = "normalizeMapping")
    @Mapping(target = "capabilities", ignore = true)
    @Mapping(target = "enabled", source = "enabled")
    @Mapping(target = "scanCursorTimestamp", ignore = true)
    @Mapping(target = "scanCursorFingerprint", ignore = true)
    @Mapping(target = "batchSize", source = "batchSize", qualifiedByName = "normalizeBatchSize")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    AttackLogDataset toDataset(CreateAttackDatasetRequest request);

    @BeanMapping(ignoreByDefault = true, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "name", source = "name", qualifiedByName = "trimToNull")
    @Mapping(target = "datasourceType", source = "datasourceType", qualifiedByName = "lowerTrimToNull")
    @Mapping(target = "datasourceId", source = "datasourceId", qualifiedByName = "trimToEmpty")
    @Mapping(target = "databaseName", source = "databaseName", qualifiedByName = "defaultDatabase")
    @Mapping(target = "tableName", source = "tableName", qualifiedByName = "trimToEmpty")
    @Mapping(target = "indexName", source = "indexName", qualifiedByName = "trimToEmpty")
    @Mapping(target = "fieldMapping", ignore = true)
    @Mapping(target = "capabilities", ignore = true)
    @Mapping(target = "enabled", source = "enabled")
    @Mapping(target = "scanCursorTimestamp", ignore = true)
    @Mapping(target = "scanCursorFingerprint", ignore = true)
    @Mapping(target = "batchSize", source = "batchSize", qualifiedByName = "normalizeBatchSize")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateDataset(UpdateAttackDatasetRequest request, @MappingTarget AttackLogDataset dataset);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "startTime", source = "startTime", qualifiedByName = "parseTime")
    @Mapping(target = "endTime", source = "endTime", qualifiedByName = "parseTime")
    @Mapping(target = "datasourceType", source = "datasourceType", qualifiedByName = "trimToEmpty")
    @Mapping(target = "datasourceId", source = "datasourceId", qualifiedByName = "trimToEmpty")
    @Mapping(target = "databaseName", source = "databaseName", qualifiedByName = "trimToEmpty")
    @Mapping(target = "tableName", source = "tableName", qualifiedByName = "trimToEmpty")
    @Mapping(target = "indexName", source = "indexName", qualifiedByName = "trimToEmpty")
    @Mapping(target = "attackType", source = "attackType", qualifiedByName = "trimToEmpty")
    @Mapping(target = "attackSubType", source = "attackSubType", qualifiedByName = "trimToEmpty")
    @Mapping(target = "severity", source = "severity", qualifiedByName = "trimToEmpty")
    @Mapping(target = "sourceIp", source = "sourceIp", qualifiedByName = "trimToEmpty")
    @Mapping(target = "hostname", source = "hostname", qualifiedByName = "trimToEmpty")
    @Mapping(target = "keyword", source = "keyword", qualifiedByName = "trimToEmpty")
    @Mapping(target = "pageSize", expression = "java(normalizePageSize(request.getPageSize()))")
    AttackClassificationQuerySqlParam toClassificationQueryParam(AttackClassificationQueryRequest request);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "classificationKey", source = "classificationKey", qualifiedByName = "defaultString")
    @Mapping(target = "datasourceType", source = "dataset.datasourceType", qualifiedByName = "defaultString")
    @Mapping(target = "datasourceId", source = "dataset.datasourceId", qualifiedByName = "defaultString")
    @Mapping(target = "databaseName", source = "dataset.databaseName", qualifiedByName = "defaultString")
    @Mapping(target = "tableName", source = "dataset.tableName", qualifiedByName = "defaultString")
    @Mapping(target = "indexName", source = "dataset.indexName", qualifiedByName = "defaultString")
    @Mapping(target = "logFingerprint", source = "record.fingerprint", qualifiedByName = "defaultString")
    @Mapping(target = "logTimestamp", source = "record.timestamp")
    @Mapping(target = "sourceIp", source = "record.sourceIp", qualifiedByName = "defaultString")
    @Mapping(target = "hostname", source = "record.hostname", qualifiedByName = "defaultString")
    @Mapping(target = "message", source = "record.message", qualifiedByName = "defaultString")
    @Mapping(target = "raw", source = "record.raw", qualifiedByName = "defaultString")
    @Mapping(target = "attackType", source = "rule.attackType", qualifiedByName = "defaultString")
    @Mapping(target = "attackSubType", source = "rule.attackSubType", qualifiedByName = "defaultString")
    @Mapping(target = "severity", source = "rule.severity", qualifiedByName = "defaultString")
    @Mapping(target = "confidence", source = "confidence")
    @Mapping(target = "ruleId", source = "rule.ruleId", qualifiedByName = "defaultString")
    @Mapping(target = "ruleName", source = "rule.name", qualifiedByName = "defaultString")
    @Mapping(target = "reason", source = "reason", qualifiedByName = "defaultString")
    @Mapping(target = "mitreTactic", source = "rule.mitreTactic", qualifiedByName = "defaultString")
    @Mapping(target = "mitreTechnique", source = "rule.mitreTechnique", qualifiedByName = "defaultString")
    @Mapping(target = "status", expression = "java(\"unconfirmed\")")
    @Mapping(target = "classifiedAt", source = "classifiedAt")
    AttackClassificationRecord toClassificationRecord(AttackLogDataset dataset,
                                                       NormalizedLogRecord record,
                                                       AttackDetectionRule rule,
                                                       String classificationKey,
                                                       String reason,
                                                       Float confidence,
                                                       LocalDateTime classifiedAt);

    @AfterMapping
    default void afterCreateRule(CreateAttackRuleRequest request, @MappingTarget AttackDetectionRule rule) {
        rule.setConfidence(ObjectUtils.defaultIfNull(rule.getConfidence(), DEFAULT_CONFIDENCE));
        rule.setRequiredFields(defaultList(rule.getRequiredFields(), List.of("message")));
        rule.setDatasourceTypes(defaultList(rule.getDatasourceTypes(), List.of("clickhouse", "elasticsearch")));
        rule.setMessagePatterns(defaultList(rule.getMessagePatterns(), Collections.emptyList()));
        rule.setRawPatterns(defaultList(rule.getRawPatterns(), Collections.emptyList()));
        rule.setKeywords(defaultList(rule.getKeywords(), Collections.emptyList()));
        rule.setEnabled(ObjectUtils.defaultIfNull(rule.getEnabled(), true));
        rule.setPriority(ObjectUtils.defaultIfNull(rule.getPriority(), 100));
        LocalDateTime now = LocalDateTime.now();
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);
    }

    @AfterMapping
    default void afterUpdateRule(UpdateAttackRuleRequest request, @MappingTarget AttackDetectionRule rule) {
        rule.setUpdatedAt(LocalDateTime.now());
    }

    @AfterMapping
    default void afterCreateDataset(CreateAttackDatasetRequest request, @MappingTarget AttackLogDataset dataset) {
        dataset.setCapabilities(buildCapabilities(dataset.getFieldMapping(), request.getCapabilities()));
        dataset.setEnabled(ObjectUtils.defaultIfNull(dataset.getEnabled(), true));
        LocalDateTime now = LocalDateTime.now();
        dataset.setCreatedAt(now);
        dataset.setUpdatedAt(now);
    }

    @AfterMapping
    default void afterUpdateDataset(UpdateAttackDatasetRequest request, @MappingTarget AttackLogDataset dataset) {
        if (request.getFieldMapping() != null) {
            dataset.setFieldMapping(normalizeMapping(request.getFieldMapping()));
        }
        if (request.getCapabilities() != null || request.getFieldMapping() != null) {
            dataset.setCapabilities(buildCapabilities(dataset.getFieldMapping(), request.getCapabilities()));
        }
        dataset.setUpdatedAt(LocalDateTime.now());
    }

    @Named("trim")
    default String trim(String value) {
        return StringUtils.trim(value);
    }

    @Named("trimToNull")
    default String trimToNull(String value) {
        return StringUtils.trimToNull(value);
    }

    @Named("trimToEmpty")
    default String trimToEmpty(String value) {
        return StringUtils.trimToEmpty(value);
    }

    @Named("lowerTrim")
    default String lowerTrim(String value) {
        return StringUtils.lowerCase(StringUtils.trim(value));
    }

    @Named("lowerTrimToNull")
    default String lowerTrimToNull(String value) {
        String trimmed = StringUtils.trimToNull(value);
        return StringUtils.isBlank(trimmed) ? null : StringUtils.lowerCase(trimmed);
    }

    @Named("defaultString")
    default String defaultString(String value) {
        return StringUtils.defaultString(value);
    }

    @Named("defaultSeverity")
    default String defaultSeverity(String value) {
        return StringUtils.defaultIfBlank(value, "medium");
    }

    @Named("identityInteger")
    default Integer identityInteger(Integer value) {
        return value;
    }

    @Named("defaultDatabase")
    default String defaultDatabase(String value) {
        return StringUtils.defaultIfBlank(StringUtils.trim(value), "default");
    }

    @Named("normalizeMapping")
    default Map<String, String> normalizeMapping(Map<String, String> fieldMapping) {
        if (MapUtils.isEmpty(fieldMapping)) {
            return new LinkedHashMap<>();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        fieldMapping.forEach((key, value) -> {
            if (StringUtils.isNotBlank(key) && StringUtils.isNotBlank(value)) {
                normalized.put(normalizeStandardField(key), StringUtils.trim(value));
            }
        });
        return normalized;
    }

    @Named("normalizeBatchSize")
    default int normalizeBatchSize(Integer batchSize) {
        int normalized = ObjectUtils.defaultIfNull(batchSize, DEFAULT_BATCH_SIZE);
        if (normalized < 1) {
            return DEFAULT_BATCH_SIZE;
        }
        return Math.min(normalized, 5000);
    }

    @Named("parseTime")
    default LocalDateTime parseTime(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        String normalized = StringUtils.trim(text);
        try {
            return DateTimeUtils.parseDateTime(normalized, DateTimeUtils.PATTERN_DATETIME_MILLIS);
        } catch (Exception ignored) {
            return DateTimeUtils.parseDateTime(normalized, DateTimeUtils.PATTERN_DATETIME);
        }
    }

    default int normalizePageSize(Integer pageSize) {
        return Math.max(ObjectUtils.defaultIfNull(pageSize, 20), 1);
    }

    default Map<String, Object> buildCapabilities(Map<String, String> fieldMapping, Map<String, Object> provided) {
        Map<String, Object> capabilities = new LinkedHashMap<>();
        if (MapUtils.isNotEmpty(provided)) {
            capabilities.putAll(provided);
        }
        Map<String, String> mapping = ObjectUtils.defaultIfNull(fieldMapping, Collections.emptyMap());
        capabilities.put("hasSourceIp", StringUtils.isNotBlank(mapping.get("sourceIp")));
        capabilities.put("hasHostname", StringUtils.isNotBlank(mapping.get("hostname")));
        capabilities.put("hasRaw", StringUtils.isNotBlank(mapping.get("raw")));
        capabilities.put("hasSeverity", StringUtils.isNotBlank(mapping.get("severity")));
        return capabilities;
    }

    default String normalizeStandardField(String field) {
        String normalized = StringUtils.trimToEmpty(field);
        if (StringUtils.equalsAnyIgnoreCase(normalized, "source_ip", "sourceIp")) {
            return "sourceIp";
        }
        return StringUtils.uncapitalize(normalized);
    }

    default List<String> defaultList(List<String> values, List<String> defaults) {
        return CollectionUtils.isEmpty(values) ? defaults : values;
    }
}
