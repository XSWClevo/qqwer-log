package cn.mw.loganalysis.alert.service.impl;

import cn.mw.loganalysis.alert.dto.AlertAggregateDTO;
import cn.mw.loganalysis.alert.dto.AlertConditionDTO;
import cn.mw.loganalysis.alert.dto.AlertDowntimeStatusDTO;
import cn.mw.loganalysis.alert.dto.AlertEvaluationRunDTO;
import cn.mw.loganalysis.alert.dto.AlertMonitorOptionsDTO;
import cn.mw.loganalysis.alert.dto.AlertMonitorStateDTO;
import cn.mw.loganalysis.alert.dto.AlertRuleDTO;
import cn.mw.loganalysis.alert.dto.AlertRuleQueryRequest;
import cn.mw.loganalysis.alert.dto.AlertThresholdDTO;
import cn.mw.loganalysis.alert.dto.AlertThresholdsDTO;
import cn.mw.loganalysis.alert.dto.AlertTriggerDTO;
import cn.mw.loganalysis.alert.dto.CreateAlertRuleRequest;
import cn.mw.loganalysis.alert.dto.UpdateAlertRuleRequest;
import cn.mw.loganalysis.alert.entity.AlertDowntime;
import cn.mw.loganalysis.alert.entity.AlertEvaluationRun;
import cn.mw.loganalysis.alert.entity.AlertMonitorOptions;
import cn.mw.loganalysis.alert.entity.AlertMonitorState;
import cn.mw.loganalysis.alert.entity.AlertRule;
import cn.mw.loganalysis.alert.entity.AlertRuleCondition;
import cn.mw.loganalysis.alert.entity.AlertRuleThreshold;
import cn.mw.loganalysis.alert.executor.QueryBuilder;
import cn.mw.loganalysis.alert.mapper.AlertDowntimeMapper;
import cn.mw.loganalysis.alert.mapper.AlertEvaluationRunMapper;
import cn.mw.loganalysis.alert.mapper.AlertEventMapper;
import cn.mw.loganalysis.alert.mapper.AlertMonitorOptionsMapper;
import cn.mw.loganalysis.alert.mapper.AlertMonitorStateMapper;
import cn.mw.loganalysis.alert.mapper.AlertRuleConditionMapper;
import cn.mw.loganalysis.alert.mapper.AlertRuleMapper;
import cn.mw.loganalysis.alert.mapper.AlertRuleThresholdMapper;
import cn.mw.loganalysis.alert.service.AlertRuleScopeResolver;
import cn.mw.loganalysis.alert.service.AlertRuleService;
import cn.mw.loganalysis.alert.validator.QueryValidator;
import cn.mw.loganalysis.common.enums.ComparisonOperator;
import cn.mw.loganalysis.common.exception.ResourceNotFoundException;
import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 告警规则服务实现，负责归一化 Monitor 模型的持久化与组装。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRuleServiceImpl implements AlertRuleService {

    private static final List<String> DEFAULT_NOTIFICATION_CHANNELS = List.of("page");

    private final AlertRuleMapper alertRuleMapper;
    private final AlertRuleConditionMapper alertRuleConditionMapper;
    private final AlertRuleThresholdMapper alertRuleThresholdMapper;
    private final AlertMonitorOptionsMapper alertMonitorOptionsMapper;
    private final AlertMonitorStateMapper alertMonitorStateMapper;
    private final AlertEvaluationRunMapper alertEvaluationRunMapper;
    private final AlertDowntimeMapper alertDowntimeMapper;
    private final AlertEventMapper alertEventMapper;
    private final QueryValidator queryValidator;
    private final AlertRuleScopeResolver alertRuleScopeResolver;
    private final QueryBuilder queryBuilder;
    private final DynamicLogQueryService dynamicLogQueryService;

    @Override
    public IPage<AlertRuleDTO> queryRules(AlertRuleQueryRequest request) {
        Page<AlertRule> page = new Page<>(
                ObjectUtils.defaultIfNull(request.getPageNum(), 1),
                ObjectUtils.defaultIfNull(request.getPageSize(), 20)
        );

        IPage<AlertRule> rulePage = alertRuleMapper.selectPageByCondition(
                page,
                request.getKeyword(),
                request.getStatus(),
                request.getSeverity(),
                request.getType(),
                request.getChannel()
        );

        return rulePage.convert(this::convertToDTO);
    }

    @Override
    public AlertRuleDTO getRuleById(Long id) {
        return convertToDTO(requireRule(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertRuleDTO createRule(CreateAlertRuleRequest request) {
        validateMonitor(request.getCondition(), request.getThresholds());

        AlertRule rule = new AlertRule();
        applyCreateRequest(rule, request);
        rule.setCreatedBy(1L);
        alertRuleMapper.insert(rule);

        replaceCondition(rule.getId(), request.getCondition());
        replaceThresholds(rule.getId(), request.getThresholds());
        replaceMonitorOptions(rule.getId(), request.getMonitorOptions());

        return convertToDTO(rule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertRuleDTO updateRule(Long id, UpdateAlertRuleRequest request) {
        AlertRule rule = requireRule(id);

        AlertConditionDTO condition = ObjectUtils.defaultIfNull(request.getCondition(), selectConditionDTO(id));
        AlertThresholdsDTO thresholds = ObjectUtils.defaultIfNull(request.getThresholds(), selectThresholdsDTO(id));
        validateMonitor(condition, thresholds);

        applyUpdateRequest(rule, request);
        alertRuleMapper.updateById(rule);

        if (request.getCondition() != null) {
            replaceCondition(id, request.getCondition());
        }
        if (request.getThresholds() != null) {
            replaceThresholds(id, request.getThresholds());
        }
        if (request.getMonitorOptions() != null) {
            replaceMonitorOptions(id, request.getMonitorOptions());
        }

        return convertToDTO(requireRule(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(Long id) {
        requireRule(id);
        alertRuleMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertRuleDTO toggleRuleStatus(Long id, Boolean enabled) {
        AlertRule rule = requireRule(id);
        rule.setEnabled(ObjectUtils.defaultIfNull(enabled, Boolean.FALSE));
        alertRuleMapper.updateById(rule);
        return convertToDTO(rule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertRuleDTO duplicateRule(Long id) {
        AlertRule original = requireRule(id);
        AlertRule duplicate = new AlertRule();
        duplicate.setName(original.getName() + " (副本)");
        duplicate.setDescription(original.getDescription());
        duplicate.setRuleType(original.getRuleType());
        duplicate.setScopeType(original.getScopeType());
        duplicate.setCategoryCodes(original.getCategoryCodes());
        duplicate.setDatasourceIds(original.getDatasourceIds());
        duplicate.setTableNames(original.getTableNames());
        duplicate.setEvalEvery(original.getEvalEvery());
        duplicate.setConsecutiveHits(original.getConsecutiveHits());
        duplicate.setSeverity(original.getSeverity());
        duplicate.setNotificationChannels(normalizeNotificationChannels(original.getNotificationChannels()));
        duplicate.setMessageTemplate(original.getMessageTemplate());
        duplicate.setSilencePeriod(original.getSilencePeriod());
        duplicate.setEnabled(false);
        duplicate.setCreatedBy(1L);
        alertRuleMapper.insert(duplicate);

        replaceCondition(duplicate.getId(), selectConditionDTO(id));
        replaceThresholds(duplicate.getId(), selectThresholdsDTO(id));
        replaceMonitorOptions(duplicate.getId(), selectMonitorOptionsDTO(id));

        return convertToDTO(duplicate);
    }

    @Override
    public Map<String, Object> testRule(Long id) {
        AlertRule rule = requireRule(id);
        AlertConditionDTO condition = selectConditionDTO(id);
        AlertThresholdsDTO thresholds = selectThresholdsDTO(id);
        validateMonitor(condition, thresholds);

        List<Map<String, Object>> samples = new ArrayList<>();
        List<String> sqlList = new ArrayList<>();
        BigDecimal matchedValue = BigDecimal.ZERO;
        for (var target : alertRuleScopeResolver.resolve(rule)) {
            String sql = queryBuilder.buildQuery(target, condition, thresholds);
            sqlList.add(sql);
            Object rawResult = dynamicLogQueryService.executeRawSQL(target.getDatasourceId(), sql);
            List<Map<String, Object>> rows = normalizeRows(rawResult);
            matchedValue = matchedValue.add(sumResultValue(rows, rawResult));
            samples.addAll(rows);
            if (samples.size() >= 20) {
                break;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("matchedCount", matchedValue.intValue());
        result.put("sql", sqlList);
        result.put("samples", samples.size() > 20 ? samples.subList(0, 20) : samples);
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeRows(Object rawResult) {
        if (rawResult instanceof List<?> list) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> row) {
                    rows.add(new HashMap<>((Map<String, Object>) row));
                }
            }
            return rows;
        }
        if (rawResult instanceof Map<?, ?> row) {
            return List.of(new HashMap<>((Map<String, Object>) row));
        }
        if (rawResult != null) {
            return List.of(Map.of("value", rawResult));
        }
        return List.of();
    }

    private BigDecimal sumResultValue(List<Map<String, Object>> rows, Object rawResult) {
        if (CollectionUtils.isNotEmpty(rows)) {
            BigDecimal total = BigDecimal.ZERO;
            for (Map<String, Object> row : rows) {
                total = total.add(convertToDecimal(row.get("value")));
            }
            return total;
        }
        return convertToDecimal(rawResult);
    }

    private BigDecimal convertToDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private AlertRule requireRule(Long id) {
        AlertRule rule = alertRuleMapper.selectById(id);
        if (rule == null) {
            throw new ResourceNotFoundException("告警规则不存在: " + id);
        }
        return rule;
    }

    private void applyCreateRequest(AlertRule rule, CreateAlertRuleRequest request) {
        rule.setName(request.getName());
        rule.setDescription(request.getDescription());
        rule.setRuleType(StringUtils.defaultIfBlank(request.getRuleType(), "aggregation"));
        rule.setScopeType(StringUtils.defaultIfBlank(request.getScopeType(), "all"));
        rule.setCategoryCodes(request.getCategoryCodes());
        rule.setDatasourceIds(request.getDatasourceIds());
        rule.setTableNames(request.getTableNames());
        rule.setEvalEvery(StringUtils.defaultIfBlank(request.getEvalEvery(), "1m"));
        rule.setConsecutiveHits(Math.max(1, ObjectUtils.defaultIfNull(request.getConsecutiveHits(), 1)));
        rule.setSeverity(StringUtils.upperCase(StringUtils.defaultIfBlank(request.getSeverity(), "WARNING")));
        rule.setNotificationChannels(normalizeNotificationChannels(request.getNotificationChannels()));
        rule.setMessageTemplate(request.getMessageTemplate());
        rule.setSilencePeriod(Math.max(0, ObjectUtils.defaultIfNull(request.getSilencePeriod(), 300)));
        rule.setEnabled(ObjectUtils.defaultIfNull(request.getEnabled(), Boolean.TRUE));
    }

    private void applyUpdateRequest(AlertRule rule, UpdateAlertRuleRequest request) {
        if (request.getName() != null) {
            rule.setName(request.getName());
        }
        if (request.getDescription() != null) {
            rule.setDescription(request.getDescription());
        }
        if (request.getRuleType() != null) {
            rule.setRuleType(request.getRuleType());
        }
        if (request.getScopeType() != null) {
            rule.setScopeType(request.getScopeType());
        }
        if (request.getCategoryCodes() != null) {
            rule.setCategoryCodes(request.getCategoryCodes());
        }
        if (request.getDatasourceIds() != null) {
            rule.setDatasourceIds(request.getDatasourceIds());
        }
        if (request.getTableNames() != null) {
            rule.setTableNames(request.getTableNames());
        }
        if (request.getEvalEvery() != null) {
            rule.setEvalEvery(request.getEvalEvery());
        }
        if (request.getConsecutiveHits() != null) {
            rule.setConsecutiveHits(Math.max(1, request.getConsecutiveHits()));
        }
        if (request.getSeverity() != null) {
            rule.setSeverity(StringUtils.upperCase(request.getSeverity()));
        }
        if (request.getNotificationChannels() != null) {
            rule.setNotificationChannels(normalizeNotificationChannels(request.getNotificationChannels()));
        }
        if (request.getMessageTemplate() != null) {
            rule.setMessageTemplate(request.getMessageTemplate());
        }
        if (request.getSilencePeriod() != null) {
            rule.setSilencePeriod(Math.max(0, request.getSilencePeriod()));
        }
        if (request.getEnabled() != null) {
            rule.setEnabled(request.getEnabled());
        }
    }

    private void replaceCondition(Long ruleId, AlertConditionDTO dto) {
        alertRuleConditionMapper.delete(new LambdaQueryWrapper<AlertRuleCondition>()
                .eq(AlertRuleCondition::getRuleId, ruleId));

        AlertAggregateDTO aggregate = ObjectUtils.defaultIfNull(dto.getAggregate(), new AlertAggregateDTO());
        AlertRuleCondition condition = new AlertRuleCondition();
        condition.setRuleId(ruleId);
        condition.setQuery(dto.getQuery());
        condition.setFilters(ObjectUtils.defaultIfNull(dto.getFilters(), Map.of()));
        condition.setAggregateFunction(StringUtils.defaultIfBlank(aggregate.getFunction(), "count"));
        condition.setAggregateField(StringUtils.defaultIfBlank(aggregate.getField(), "*"));
        condition.setGroupBy(ObjectUtils.defaultIfNull(dto.getGroupBy(), List.of()));
        alertRuleConditionMapper.insert(condition);
    }

    private List<String> normalizeNotificationChannels(List<String> channels) {
        if (CollectionUtils.isEmpty(channels)) {
            return DEFAULT_NOTIFICATION_CHANNELS;
        }
        List<String> normalized = channels.stream()
                .filter(StringUtils::isNotBlank)
                .map(StringUtils::trim)
                .distinct()
                .toList();
        return CollectionUtils.isEmpty(normalized) ? DEFAULT_NOTIFICATION_CHANNELS : normalized;
    }

    private void replaceThresholds(Long ruleId, AlertThresholdsDTO thresholds) {
        alertRuleThresholdMapper.delete(new LambdaQueryWrapper<AlertRuleThreshold>()
                .eq(AlertRuleThreshold::getRuleId, ruleId));

        insertThreshold(ruleId, "critical", thresholds.getCritical());
        insertThreshold(ruleId, "warning", thresholds.getWarning());
        insertThreshold(ruleId, "recovery", thresholds.getRecovery());
    }

    private void insertThreshold(Long ruleId, String level, AlertThresholdDTO dto) {
        if (dto == null || dto.getThreshold() == null) {
            return;
        }

        AlertRuleThreshold threshold = new AlertRuleThreshold();
        threshold.setRuleId(ruleId);
        threshold.setLevel(level);
        threshold.setOperator(StringUtils.defaultIfBlank(dto.getOperator(), "gt"));
        threshold.setThreshold(dto.getThreshold());
        threshold.setTimeWindow(StringUtils.defaultIfBlank(dto.getTimeWindow(), "5m"));
        alertRuleThresholdMapper.insert(threshold);
    }

    private void replaceMonitorOptions(Long ruleId, AlertMonitorOptionsDTO dto) {
        alertMonitorOptionsMapper.delete(new LambdaQueryWrapper<AlertMonitorOptions>()
                .eq(AlertMonitorOptions::getRuleId, ruleId));

        AlertMonitorOptionsDTO options = ObjectUtils.defaultIfNull(dto, new AlertMonitorOptionsDTO());
        AlertMonitorOptions entity = new AlertMonitorOptions();
        entity.setRuleId(ruleId);
        entity.setNotifyNoData(ObjectUtils.defaultIfNull(options.getNotifyNoData(), Boolean.FALSE));
        entity.setNoDataTimeframe(StringUtils.defaultIfBlank(options.getNoDataTimeframe(), "5m"));
        entity.setRequireFullWindow(ObjectUtils.defaultIfNull(options.getRequireFullWindow(), Boolean.FALSE));
        entity.setEvaluationDelaySeconds(Math.max(0, ObjectUtils.defaultIfNull(options.getEvaluationDelaySeconds(), 0)));
        entity.setNewGroupDelaySeconds(Math.max(0, ObjectUtils.defaultIfNull(options.getNewGroupDelaySeconds(), 0)));
        entity.setRenotifyIntervalMinutes(Math.max(0, ObjectUtils.defaultIfNull(options.getRenotifyIntervalMinutes(), 0)));
        entity.setRenotifyOccurrences(Math.max(0, ObjectUtils.defaultIfNull(options.getRenotifyOccurrences(), 0)));
        entity.setIncludeTags(ObjectUtils.defaultIfNull(options.getIncludeTags(), Boolean.TRUE));
        entity.setPriority(options.getPriority());
        entity.setTeam(options.getTeam());
        entity.setTags(ObjectUtils.defaultIfNull(options.getTags(), List.of()));
        entity.setAlertMode(StringUtils.defaultIfBlank(options.getAlertMode(), "simple"));
        entity.setEscalationMessage(options.getEscalationMessage());
        alertMonitorOptionsMapper.insert(entity);
    }

    private void validateMonitor(AlertConditionDTO condition, AlertThresholdsDTO thresholds) {
        if (condition == null) {
            throw new IllegalArgumentException("告警查询条件不能为空");
        }
        if (thresholds == null || thresholds.getCritical() == null || thresholds.getCritical().getThreshold() == null) {
            throw new IllegalArgumentException("必须配置 critical 阈值");
        }

        if (StringUtils.isNotBlank(condition.getQuery())) {
            queryValidator.validateQuery(condition.getQuery());
        }
        validateStructuredFilters(condition.getFilters());
        if (condition.getAggregate() != null && StringUtils.isNotBlank(condition.getAggregate().getField())
                && !"*".equals(condition.getAggregate().getField())) {
            queryValidator.validateFieldName(condition.getAggregate().getField(), "aggregate");
        }
        if (CollectionUtils.isNotEmpty(condition.getGroupBy())) {
            for (String groupBy : condition.getGroupBy()) {
                queryValidator.validateGroupByField(groupBy);
            }
        }
    }

    private void validateStructuredFilters(Map<String, Object> filters) {
        if (MapUtils.isEmpty(filters)) {
            return;
        }

        Object fieldFiltersObj = filters.get("fieldFilters");
        if (fieldFiltersObj instanceof List<?> fieldFilters) {
            for (Object item : fieldFilters) {
                if (!(item instanceof Map<?, ?> filter)) {
                    continue;
                }
                String field = String.valueOf(ObjectUtils.defaultIfNull(filter.get("field"), ""));
                if (StringUtils.isNotBlank(field)) {
                    queryValidator.validateFieldName(field, "filter");
                }
            }
        }
    }

    private AlertRuleDTO convertToDTO(AlertRule rule) {
        AlertConditionDTO condition = selectConditionDTO(rule.getId());
        AlertThresholdsDTO thresholds = selectThresholdsDTO(rule.getId());
        AlertMonitorOptionsDTO monitorOptions = selectMonitorOptionsDTO(rule.getId());
        condition.setTrigger(buildTriggerDTO(thresholds));
        condition.setOptions(monitorOptions);

        LocalDateTime lastTriggered = alertEventMapper.getLastTriggeredTime(rule.getId());
        Long triggerCount = alertEventMapper.getTriggerCount(rule.getId());

        return AlertRuleDTO.builder()
                .id(rule.getId())
                .name(rule.getName())
                .description(rule.getDescription())
                .ruleType(rule.getRuleType())
                .scopeType(rule.getScopeType())
                .categoryCodes(rule.getCategoryCodes())
                .datasourceIds(rule.getDatasourceIds())
                .tableNames(rule.getTableNames())
                .condition(condition)
                .thresholds(thresholds)
                .monitorOptions(monitorOptions)
                .currentState(selectCurrentStateDTO(rule.getId()))
                .lastEvaluation(selectLastEvaluationDTO(rule.getId()))
                .downtimeStatus(selectDowntimeStatus(rule.getId()))
                .evalEvery(rule.getEvalEvery())
                .consecutiveHits(rule.getConsecutiveHits())
                .severity(rule.getSeverity())
                .notificationChannels(rule.getNotificationChannels())
                .messageTemplate(rule.getMessageTemplate())
                .silencePeriod(rule.getSilencePeriod())
                .enabled(rule.getEnabled())
                .createdBy(rule.getCreatedBy())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .lastTriggeredAt(lastTriggered)
                .triggerCount(ObjectUtils.defaultIfNull(triggerCount, 0L))
                .type(rule.getRuleType())
                .triggerConditionSummary(buildTriggerSummary(condition, thresholds))
                .build();
    }

    private AlertConditionDTO selectConditionDTO(Long ruleId) {
        AlertRuleCondition condition = alertRuleConditionMapper.selectOne(new LambdaQueryWrapper<AlertRuleCondition>()
                .eq(AlertRuleCondition::getRuleId, ruleId)
                .last("LIMIT 1"));

        AlertConditionDTO dto = new AlertConditionDTO();
        if (condition == null) {
            dto.setAggregate(new AlertAggregateDTO());
            dto.setFilters(Map.of());
            dto.setGroupBy(List.of());
            return dto;
        }

        AlertAggregateDTO aggregate = new AlertAggregateDTO();
        aggregate.setFunction(condition.getAggregateFunction());
        aggregate.setField(condition.getAggregateField());

        dto.setQuery(condition.getQuery());
        dto.setFilters(ObjectUtils.defaultIfNull(condition.getFilters(), Map.of()));
        dto.setAggregate(aggregate);
        dto.setGroupBy(ObjectUtils.defaultIfNull(condition.getGroupBy(), List.of()));
        return dto;
    }

    private AlertThresholdsDTO selectThresholdsDTO(Long ruleId) {
        List<AlertRuleThreshold> rows = alertRuleThresholdMapper.selectList(new LambdaQueryWrapper<AlertRuleThreshold>()
                .eq(AlertRuleThreshold::getRuleId, ruleId));

        AlertThresholdsDTO dto = new AlertThresholdsDTO();
        for (AlertRuleThreshold row : rows) {
            AlertThresholdDTO threshold = AlertThresholdDTO.builder()
                    .level(row.getLevel())
                    .operator(row.getOperator())
                    .threshold(row.getThreshold())
                    .timeWindow(row.getTimeWindow())
                    .build();
            switch (StringUtils.lowerCase(row.getLevel())) {
                case "critical" -> dto.setCritical(threshold);
                case "warning" -> dto.setWarning(threshold);
                case "recovery" -> dto.setRecovery(threshold);
                default -> log.debug("Ignore unknown threshold level: {}", row.getLevel());
            }
        }
        return dto;
    }

    private AlertMonitorOptionsDTO selectMonitorOptionsDTO(Long ruleId) {
        AlertMonitorOptions entity = alertMonitorOptionsMapper.selectOne(new LambdaQueryWrapper<AlertMonitorOptions>()
                .eq(AlertMonitorOptions::getRuleId, ruleId)
                .last("LIMIT 1"));
        if (entity == null) {
            return new AlertMonitorOptionsDTO();
        }

        AlertMonitorOptionsDTO dto = new AlertMonitorOptionsDTO();
        dto.setNotifyNoData(entity.getNotifyNoData());
        dto.setNoDataTimeframe(entity.getNoDataTimeframe());
        dto.setRequireFullWindow(entity.getRequireFullWindow());
        dto.setEvaluationDelaySeconds(entity.getEvaluationDelaySeconds());
        dto.setNewGroupDelaySeconds(entity.getNewGroupDelaySeconds());
        dto.setRenotifyIntervalMinutes(entity.getRenotifyIntervalMinutes());
        dto.setRenotifyOccurrences(entity.getRenotifyOccurrences());
        dto.setIncludeTags(entity.getIncludeTags());
        dto.setPriority(entity.getPriority());
        dto.setTeam(entity.getTeam());
        dto.setTags(entity.getTags());
        dto.setAlertMode(entity.getAlertMode());
        dto.setEscalationMessage(entity.getEscalationMessage());
        return dto;
    }

    private AlertTriggerDTO buildTriggerDTO(AlertThresholdsDTO thresholds) {
        AlertTriggerDTO trigger = new AlertTriggerDTO();
        if (thresholds == null || thresholds.getCritical() == null) {
            return trigger;
        }
        AlertThresholdDTO critical = thresholds.getCritical();
        trigger.setOperator(critical.getOperator());
        trigger.setThreshold(critical.getThreshold());
        trigger.setTimeWindow(critical.getTimeWindow());
        if (thresholds.getWarning() != null) {
            trigger.setWarningThreshold(thresholds.getWarning().getThreshold());
        }
        if (thresholds.getRecovery() != null) {
            trigger.setRecoveryThreshold(thresholds.getRecovery().getThreshold());
        }
        return trigger;
    }

    private AlertMonitorStateDTO selectCurrentStateDTO(Long ruleId) {
        List<AlertMonitorState> states = alertMonitorStateMapper.selectList(new LambdaQueryWrapper<AlertMonitorState>()
                .eq(AlertMonitorState::getRuleId, ruleId)
                .orderByDesc(AlertMonitorState::getLastStateChangedAt)
                .orderByDesc(AlertMonitorState::getUpdatedAt));
        if (CollectionUtils.isEmpty(states)) {
            return AlertMonitorStateDTO.builder().state("OK").build();
        }

        AlertMonitorState state = states.stream()
                .filter(item -> !"OK".equalsIgnoreCase(item.getState()))
                .findFirst()
                .orElse(states.get(0));

        return AlertMonitorStateDTO.builder()
                .state(state.getState())
                .previousState(state.getPreviousState())
                .datasourceId(state.getDatasourceId())
                .tableName(state.getTableName())
                .groupKey(state.getGroupKey())
                .groupValues(state.getGroupValues())
                .lastValue(state.getLastValue())
                .lastThreshold(state.getLastThreshold())
                .lastEvaluatedAt(state.getLastEvaluatedAt())
                .lastStateChangedAt(state.getLastStateChangedAt())
                .lastNotifiedAt(state.getLastNotifiedAt())
                .renotifyCount(state.getRenotifyCount())
                .build();
    }

    private AlertEvaluationRunDTO selectLastEvaluationDTO(Long ruleId) {
        AlertEvaluationRun run = alertEvaluationRunMapper.selectOne(new LambdaQueryWrapper<AlertEvaluationRun>()
                .eq(AlertEvaluationRun::getRuleId, ruleId)
                .orderByDesc(AlertEvaluationRun::getStartedAt)
                .last("LIMIT 1"));
        if (run == null) {
            return null;
        }
        return AlertEvaluationRunDTO.builder()
                .id(run.getId())
                .startedAt(run.getStartedAt())
                .finishedAt(run.getFinishedAt())
                .windowStart(run.getWindowStart())
                .windowEnd(run.getWindowEnd())
                .status(run.getStatus())
                .matchedCount(run.getMatchedCount())
                .errorMessage(run.getErrorMessage())
                .details(run.getDetails())
                .build();
    }

    private AlertDowntimeStatusDTO selectDowntimeStatus(Long ruleId) {
        AlertDowntime downtime = selectActiveDowntime(ruleId);
        if (downtime == null) {
            return AlertDowntimeStatusDTO.builder().active(false).build();
        }
        return AlertDowntimeStatusDTO.builder()
                .active(true)
                .downtimeId(downtime.getId())
                .reason(downtime.getReason())
                .startsAt(downtime.getStartsAt())
                .endsAt(downtime.getEndsAt())
                .build();
    }

    private AlertDowntime selectActiveDowntime(Long ruleId) {
        LocalDateTime now = LocalDateTime.now();
        return alertDowntimeMapper.selectList(new LambdaQueryWrapper<AlertDowntime>()
                        .eq(AlertDowntime::getEnabled, true)
                        .le(AlertDowntime::getStartsAt, now)
                        .ge(AlertDowntime::getEndsAt, now))
                .stream()
                .filter(item -> item.getRuleId() == null || item.getRuleId().equals(ruleId))
                .min(Comparator.comparing(AlertDowntime::getEndsAt))
                .orElse(null);
    }

    private String buildTriggerSummary(AlertConditionDTO condition, AlertThresholdsDTO thresholds) {
        if (thresholds == null || thresholds.getCritical() == null) {
            return "未配置";
        }
        AlertThresholdDTO critical = thresholds.getCritical();
        String metric = condition != null && condition.getAggregate() != null
                ? StringUtils.defaultIfBlank(condition.getAggregate().getFunction(), "count")
                : "count";
        String operator = ComparisonOperator.getSymbol(StringUtils.defaultIfBlank(critical.getOperator(), "gt"));
        List<String> parts = new ArrayList<>();
        parts.add(metric + " " + operator + " " + critical.getThreshold() + " in "
                + StringUtils.defaultIfBlank(critical.getTimeWindow(), "5m"));
        if (thresholds.getWarning() != null) {
            parts.add("warning " + operator + " " + thresholds.getWarning().getThreshold());
        }
        if (thresholds.getRecovery() != null) {
            parts.add("recovery " + thresholds.getRecovery().getThreshold());
        }
        if (condition != null && CollectionUtils.isNotEmpty(condition.getGroupBy())) {
            parts.add("group by " + String.join(", ", condition.getGroupBy()));
        }
        return String.join("，", parts);
    }
}
