package cn.mw.loganalysis.attack.service;

import cn.mw.loganalysis.attack.converter.AttackStructMapper;
import cn.mw.loganalysis.attack.dto.AttackClassificationQueryRequest;
import cn.mw.loganalysis.attack.dto.AttackClassificationRunRequest;
import cn.mw.loganalysis.attack.dto.AttackClassificationRunResult;
import cn.mw.loganalysis.attack.entity.AttackClassificationRecord;
import cn.mw.loganalysis.attack.entity.AttackDetectionRule;
import cn.mw.loganalysis.attack.entity.AttackLogDataset;
import cn.mw.loganalysis.attack.mapper.AttackClassificationMapper;
import cn.mw.loganalysis.attack.mapper.param.AttackClassificationQuerySqlParam;
import cn.mw.loganalysis.attack.model.NormalizedLogRecord;
import cn.mw.loganalysis.attack.reader.AttackLogDatasetReader;
import cn.mw.loganalysis.attack.reader.FingerprintUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttackClassificationService extends ServiceImpl<AttackClassificationMapper, AttackClassificationRecord> {

    private static final int DEFAULT_LIMIT = 500;

    private final AttackDatasetService attackDatasetService;
    private final AttackRuleService attackRuleService;
    private final AttackStructMapper attackStructMapper;
    private final List<AttackLogDatasetReader> readers;

    public Page<AttackClassificationRecord> query(AttackClassificationQueryRequest request) {
        AttackClassificationQueryRequest normalizedRequest =
                ObjectUtils.defaultIfNull(request, new AttackClassificationQueryRequest());
        AttackClassificationQuerySqlParam param = attackStructMapper.toClassificationQueryParam(normalizedRequest);

        Page<AttackClassificationRecord> page = new Page<>(
                Math.max(ObjectUtils.defaultIfNull(normalizedRequest.getPageNum(), 1), 1),
                param.getPageSize());
        return getBaseMapper().selectPage(page, buildClassificationQueryWrapper(param));
    }

    public AttackClassificationRunResult run(AttackClassificationRunRequest request) {
        AttackClassificationRunRequest normalizedRequest =
                ObjectUtils.defaultIfNull(request, new AttackClassificationRunRequest());
        List<AttackLogDataset> datasets = resolveDatasets(normalizedRequest);
        List<AttackDetectionRule> rules = attackRuleService.listEnabled();
        AttackClassificationRunResult result = AttackClassificationRunResult.builder()
                .datasetCount(datasets.size())
                .build();

        if (CollectionUtils.isEmpty(datasets) || CollectionUtils.isEmpty(rules)) {
            return result;
        }

        for (AttackLogDataset dataset : datasets) {
            AttackLogDatasetReader reader = findReader(dataset);
            if (ObjectUtils.isEmpty(reader)) {
                result.getSkippedDatasets().add(dataset.getName() + ": unsupported datasource type " + dataset.getDatasourceType());
                continue;
            }

            LocalDateTime startTime = resolveStartTime(dataset, normalizedRequest);
            LocalDateTime endTime = ObjectUtils.defaultIfNull(
                    attackStructMapper.parseTime(normalizedRequest.getEndTime()), LocalDateTime.now());
            int limit = resolveLimit(dataset, normalizedRequest);
            List<NormalizedLogRecord> records;
            try {
                records = reader.read(dataset, startTime, endTime, limit);
            } catch (Exception ex) {
                log.error("读取攻击分类数据集失败: id={}, name={}, error={}",
                        dataset.getId(), dataset.getName(), ex.getMessage(), ex);
                result.getSkippedDatasets().add(dataset.getName() + ": " + ex.getMessage());
                continue;
            }
            result.setScannedCount(result.getScannedCount() + records.size());

            LocalDateTime latestTimestamp = null;
            String latestFingerprint = null;
            for (NormalizedLogRecord record : records) {
                if (ObjectUtils.isNotEmpty(record.getTimestamp())
                        && (latestTimestamp == null || record.getTimestamp().isAfter(latestTimestamp))) {
                    latestTimestamp = record.getTimestamp();
                    latestFingerprint = record.getFingerprint();
                }

                for (AttackDetectionRule rule : rules) {
                    if (!canRunRule(dataset, record, rule)) {
                        continue;
                    }
                    if (!matches(rule, record)) {
                        continue;
                    }

                    result.setMatchedCount(result.getMatchedCount() + 1);
                    AttackClassificationRecord classification = buildClassification(dataset, record, rule);
                    if (classificationExists(classification.getClassificationKey())) {
                        continue;
                    }
                    getBaseMapper().insert(classification);
                    result.setInsertedCount(result.getInsertedCount() + 1);
                }
            }

            if (ObjectUtils.isNotEmpty(latestTimestamp)) {
                attackDatasetService.updateCursor(dataset, latestTimestamp, latestFingerprint);
            }
        }

        return result;
    }

    private LambdaQueryWrapper<AttackClassificationRecord> buildClassificationQueryWrapper(AttackClassificationQuerySqlParam param) {
        LambdaQueryWrapper<AttackClassificationRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(ObjectUtils.isNotEmpty(param.getStartTime()),
                AttackClassificationRecord::getClassifiedAt, param.getStartTime());
        wrapper.le(ObjectUtils.isNotEmpty(param.getEndTime()),
                AttackClassificationRecord::getClassifiedAt, param.getEndTime());
        wrapper.eq(StringUtils.isNotBlank(param.getDatasourceType()),
                AttackClassificationRecord::getDatasourceType, param.getDatasourceType());
        wrapper.eq(StringUtils.isNotBlank(param.getDatasourceId()),
                AttackClassificationRecord::getDatasourceId, param.getDatasourceId());
        wrapper.eq(StringUtils.isNotBlank(param.getDatabaseName()),
                AttackClassificationRecord::getDatabaseName, param.getDatabaseName());
        wrapper.eq(StringUtils.isNotBlank(param.getTableName()),
                AttackClassificationRecord::getTableName, param.getTableName());
        wrapper.eq(StringUtils.isNotBlank(param.getIndexName()),
                AttackClassificationRecord::getIndexName, param.getIndexName());
        wrapper.eq(StringUtils.isNotBlank(param.getAttackType()),
                AttackClassificationRecord::getAttackType, param.getAttackType());
        wrapper.eq(StringUtils.isNotBlank(param.getAttackSubType()),
                AttackClassificationRecord::getAttackSubType, param.getAttackSubType());
        wrapper.eq(StringUtils.isNotBlank(param.getSeverity()),
                AttackClassificationRecord::getSeverity, param.getSeverity());
        wrapper.eq(StringUtils.isNotBlank(param.getSourceIp()),
                AttackClassificationRecord::getSourceIp, param.getSourceIp());
        wrapper.eq(StringUtils.isNotBlank(param.getHostname()),
                AttackClassificationRecord::getHostname, param.getHostname());
        if (StringUtils.isNotBlank(param.getKeyword())) {
            wrapper.and(query -> query.like(AttackClassificationRecord::getMessage, param.getKeyword())
                    .or()
                    .like(AttackClassificationRecord::getRaw, param.getKeyword())
                    .or()
                    .like(AttackClassificationRecord::getReason, param.getKeyword()));
        }
        wrapper.orderByDesc(AttackClassificationRecord::getClassifiedAt);
        return wrapper;
    }

    private List<AttackLogDataset> resolveDatasets(AttackClassificationRunRequest request) {
        if (CollectionUtils.isNotEmpty(request.getDatasetIds())) {
            return attackDatasetService.listByIds(request.getDatasetIds());
        }
        return attackDatasetService.listEnabled();
    }

    private AttackLogDatasetReader findReader(AttackLogDataset dataset) {
        return readers.stream()
                .filter(reader -> reader.supports(dataset.getDatasourceType()))
                .findFirst()
                .orElse(null);
    }

    private LocalDateTime resolveStartTime(AttackLogDataset dataset, AttackClassificationRunRequest request) {
        LocalDateTime requested = attackStructMapper.parseTime(request.getStartTime());
        if (ObjectUtils.isNotEmpty(requested)) {
            return requested;
        }
        if (ObjectUtils.isNotEmpty(dataset.getScanCursorTimestamp())) {
            return dataset.getScanCursorTimestamp();
        }
        return LocalDateTime.now().minusHours(1);
    }

    private int resolveLimit(AttackLogDataset dataset, AttackClassificationRunRequest request) {
        int requested = ObjectUtils.defaultIfNull(request.getLimit(), 0);
        if (requested > 0) {
            return Math.min(requested, 5000);
        }
        return Math.min(Math.max(ObjectUtils.defaultIfNull(dataset.getBatchSize(), DEFAULT_LIMIT), 1), 5000);
    }

    private boolean canRunRule(AttackLogDataset dataset, NormalizedLogRecord record, AttackDetectionRule rule) {
        if (CollectionUtils.isNotEmpty(rule.getDatasourceTypes())
                && rule.getDatasourceTypes().stream().noneMatch(type -> StringUtils.equalsIgnoreCase(type, dataset.getDatasourceType()))) {
            return false;
        }

        if (CollectionUtils.isEmpty(rule.getRequiredFields())) {
            return true;
        }

        for (String requiredField : rule.getRequiredFields()) {
            if (!hasRequiredField(dataset, record, requiredField)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasRequiredField(AttackLogDataset dataset, NormalizedLogRecord record, String requiredField) {
        String normalized = attackStructMapper.normalizeStandardField(requiredField);
        Map<String, String> mapping = dataset.getFieldMapping();
        if (MapUtils.isEmpty(mapping) || StringUtils.isBlank(mapping.get(normalized))) {
            return false;
        }
        return switch (normalized) {
            case "timestamp" -> ObjectUtils.isNotEmpty(record.getTimestamp());
            case "message" -> StringUtils.isNotBlank(record.getMessage());
            case "raw" -> StringUtils.isNotBlank(record.getRaw());
            case "sourceIp" -> StringUtils.isNotBlank(record.getSourceIp());
            case "hostname" -> StringUtils.isNotBlank(record.getHostname());
            case "severity" -> StringUtils.isNotBlank(record.getSeverity());
            default -> true;
        };
    }

    private boolean matches(AttackDetectionRule rule, NormalizedLogRecord record) {
        List<Boolean> results = new ArrayList<>();
        results.add(matchesAnyPattern(rule.getMessagePatterns(), record.getMessage()));
        results.add(matchesAnyPattern(rule.getRawPatterns(), record.getRaw()));
        results.add(matchesAnyKeyword(rule.getKeywords(), record));
        return results.stream().anyMatch(Boolean.TRUE::equals);
    }

    private boolean matchesAnyPattern(List<String> patterns, String value) {
        if (CollectionUtils.isEmpty(patterns) || StringUtils.isBlank(value)) {
            return false;
        }
        for (String pattern : patterns) {
            if (StringUtils.isBlank(pattern)) {
                continue;
            }
            try {
                if (Pattern.compile(pattern).matcher(value).find()) {
                    return true;
                }
            } catch (PatternSyntaxException ex) {
                log.warn("攻击检测规则正则无效: pattern={}, error={}", pattern, ex.getMessage());
            }
        }
        return false;
    }

    private boolean matchesAnyKeyword(List<String> keywords, NormalizedLogRecord record) {
        if (CollectionUtils.isEmpty(keywords)) {
            return false;
        }
        String haystack = StringUtils.lowerCase(String.join("\n",
                StringUtils.defaultString(record.getMessage()),
                StringUtils.defaultString(record.getRaw())), Locale.ROOT);
        for (String keyword : keywords) {
            if (StringUtils.isNotBlank(keyword) && StringUtils.contains(haystack, StringUtils.lowerCase(keyword, Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private AttackClassificationRecord buildClassification(AttackLogDataset dataset,
                                                           NormalizedLogRecord record,
                                                           AttackDetectionRule rule) {
        String classificationKey = FingerprintUtils.classificationKey(dataset, record.getFingerprint(), rule.getRuleId());
        String reason = StringUtils.defaultIfBlank(rule.getReasonTemplate(), "命中攻击检测规则: " + rule.getName());
        return attackStructMapper.toClassificationRecord(
                dataset,
                record,
                rule,
                classificationKey,
                reason,
                toFloat(rule.getConfidence()),
                LocalDateTime.now());
    }

    private boolean classificationExists(String classificationKey) {
        Long count = getBaseMapper().selectCount(new LambdaQueryWrapper<AttackClassificationRecord>()
                .eq(AttackClassificationRecord::getClassificationKey, classificationKey));
        return ObjectUtils.defaultIfNull(count, 0L) > 0;
    }

    private Float toFloat(BigDecimal value) {
        return ObjectUtils.isEmpty(value) ? 0F : value.floatValue();
    }
}
