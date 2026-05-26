package cn.mw.loganalysis.alert.executor;

import cn.mw.loganalysis.alert.dto.AlertAggregateDTO;
import cn.mw.loganalysis.alert.dto.AlertConditionDTO;
import cn.mw.loganalysis.alert.dto.AlertDatasetTarget;
import cn.mw.loganalysis.alert.dto.AlertMonitorOptionsDTO;
import cn.mw.loganalysis.alert.dto.AlertThresholdDTO;
import cn.mw.loganalysis.alert.dto.AlertThresholdsDTO;
import cn.mw.loganalysis.alert.dto.AlertTriggerDTO;
import cn.mw.loganalysis.alert.entity.AlertDowntime;
import cn.mw.loganalysis.alert.entity.AlertEvaluationRun;
import cn.mw.loganalysis.alert.entity.AlertEvent;
import cn.mw.loganalysis.alert.entity.AlertMonitorOptions;
import cn.mw.loganalysis.alert.entity.AlertMonitorState;
import cn.mw.loganalysis.alert.entity.AlertRule;
import cn.mw.loganalysis.alert.entity.AlertRuleCondition;
import cn.mw.loganalysis.alert.entity.AlertRuleThreshold;
import cn.mw.loganalysis.alert.mapper.AlertDowntimeMapper;
import cn.mw.loganalysis.alert.mapper.AlertEvaluationRunMapper;
import cn.mw.loganalysis.alert.mapper.AlertEventMapper;
import cn.mw.loganalysis.alert.mapper.AlertMonitorOptionsMapper;
import cn.mw.loganalysis.alert.mapper.AlertMonitorStateMapper;
import cn.mw.loganalysis.alert.mapper.AlertRuleConditionMapper;
import cn.mw.loganalysis.alert.mapper.AlertRuleThresholdMapper;
import cn.mw.loganalysis.alert.notifier.AlertNotifier;
import cn.mw.loganalysis.alert.service.AlertRuleScopeResolver;
import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于归一化 Monitor 模型的告警规则执行器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertRuleExecutor {

    private static final String STATE_OK = "OK";
    private static final String STATE_WARNING = "WARNING";
    private static final String STATE_CRITICAL = "CRITICAL";
    private static final String STATE_NO_DATA = "NO_DATA";
    private static final String STATE_RECOVERED = "RECOVERED";

    private final QueryBuilder queryBuilder;
    private final AlertEventMapper alertEventMapper;
    private final AlertNotifier alertNotifier;
    private final AlertRuleScopeResolver alertRuleScopeResolver;
    private final DynamicLogQueryService dynamicLogQueryService;
    private final AlertRuleConditionMapper alertRuleConditionMapper;
    private final AlertRuleThresholdMapper alertRuleThresholdMapper;
    private final AlertMonitorOptionsMapper alertMonitorOptionsMapper;
    private final AlertMonitorStateMapper alertMonitorStateMapper;
    private final AlertEvaluationRunMapper alertEvaluationRunMapper;
    private final AlertDowntimeMapper alertDowntimeMapper;

    @Transactional(rollbackFor = Exception.class)
    public void executeRule(AlertRule rule) {
        AlertConditionDTO condition = loadCondition(rule.getId());
        AlertThresholdsDTO thresholds = loadThresholds(rule.getId());
        AlertMonitorOptionsDTO options = loadOptions(rule.getId());
        condition.setOptions(options);
        condition.setTrigger(buildTrigger(thresholds));

        QueryBuilder.EvaluationWindow window = queryBuilder.resolveEvaluationWindow(condition, thresholds);
        AlertEvaluationRun run = startRun(rule, window);

        try {
            AlertDowntime downtime = selectActiveDowntime(rule.getId());
            if (downtime != null) {
                finishRun(run, "SKIPPED", 0, null, Map.of(
                        "reason", "downtime",
                        "downtimeId", downtime.getId(),
                        "downtimeReason", StringUtils.defaultString(downtime.getReason())
                ));
                return;
            }

            List<AlertDatasetTarget> targets = alertRuleScopeResolver.resolve(rule);
            if (CollectionUtils.isEmpty(targets)) {
                finishRun(run, "NO_TARGET", 0, "没有可评估的数据集", Map.of());
                return;
            }

            int matchedCount = 0;
            List<Map<String, Object>> details = new ArrayList<>();
            for (AlertDatasetTarget target : targets) {
                if (!supportsTarget(target)) {
                    continue;
                }
                String sql = queryBuilder.buildQuery(target, condition, thresholds);
                List<Map<String, Object>> rows = executeQuery(target, sql);
                matchedCount += evaluateRows(rule, target, condition, thresholds, options, run, rows, sql);
                details.add(Map.of(
                        "datasourceId", StringUtils.defaultString(target.getDatasourceId()),
                        "tableName", StringUtils.defaultString(target.getTableName()),
                        "rows", rows.size()
                ));
            }

            finishRun(run, "SUCCESS", matchedCount, null, Map.of("targets", details));
        } catch (Exception e) {
            log.error("Failed to execute alert rule: {} ({})", rule.getName(), rule.getId(), e);
            finishRun(run, "FAILED", 0, e.getMessage(), Map.of());
        }
    }

    private int evaluateRows(AlertRule rule,
                             AlertDatasetTarget target,
                             AlertConditionDTO condition,
                             AlertThresholdsDTO thresholds,
                             AlertMonitorOptionsDTO options,
                             AlertEvaluationRun run,
                             List<Map<String, Object>> rows,
                             String sql) {
        if (CollectionUtils.isEmpty(rows)) {
            if (ObjectUtils.defaultIfNull(options.getNotifyNoData(), Boolean.FALSE)) {
                return evaluateSingleState(rule, target, condition, thresholds, options, run,
                        Map.of(), null, STATE_NO_DATA, "no_data", "评估窗口内没有查询到数据", List.of(), sql);
            }
            markOkForSimpleNoData(rule, target, condition, thresholds, options, run, sql);
            return 0;
        }

        int matchedCount = 0;
        for (Map<String, Object> row : rows) {
            BigDecimal actualValue = convertToDecimal(row.get("value"));
            Map<String, Object> groupValues = extractGroupValues(row);
            EvaluationLevel level = resolveLevel(actualValue, thresholds);
            String nextState = level.state();
            String reason = level.reason();
            matchedCount += evaluateSingleState(rule, target, condition, thresholds, options, run,
                    groupValues, actualValue, nextState, level.thresholdLevel(), reason, List.of(new HashMap<>(row)), sql);
        }
        return matchedCount;
    }

    private int evaluateSingleState(AlertRule rule,
                                    AlertDatasetTarget target,
                                    AlertConditionDTO condition,
                                    AlertThresholdsDTO thresholds,
                                    AlertMonitorOptionsDTO options,
                                    AlertEvaluationRun run,
                                    Map<String, Object> groupValues,
                                    BigDecimal actualValue,
                                    String evaluatedState,
                                    String thresholdLevel,
                                    String reason,
                                    List<Map<String, Object>> samples,
                                    String sql) {
        String groupKey = buildGroupKey(groupValues);
        AlertMonitorState state = loadOrCreateState(rule.getId(), target, groupKey, groupValues);
        String previousState = StringUtils.defaultIfBlank(state.getState(), STATE_OK);
        StateDecision decision = decideState(previousState, evaluatedState, actualValue, thresholds);

        applyState(state, decision.nextState(), previousState, actualValue, decision.threshold(), groupValues);
        boolean stateChanged = !StringUtils.equals(previousState, decision.nextState());
        boolean shouldNotify = shouldNotify(stateChanged, decision.nextState(), state, options);

        persistState(state);
        if (!shouldNotify) {
            return STATE_OK.equals(decision.nextState()) ? 0 : 1;
        }

        AlertEvent event = createEvent(rule, target, condition, thresholds, options, run, state, previousState,
                decision, thresholdLevel, reason, samples, sql);
        alertEventMapper.insert(event);
        alertNotifier.sendNotifications(rule, event, samples);

        state.setLastNotifiedAt(LocalDateTime.now());
        if (stateChanged) {
            state.setRenotifyCount(0);
        } else {
            state.setRenotifyCount(ObjectUtils.defaultIfNull(state.getRenotifyCount(), 0) + 1);
        }
        persistState(state);
        return STATE_OK.equals(decision.nextState()) ? 0 : 1;
    }

    private void markOkForSimpleNoData(AlertRule rule,
                                       AlertDatasetTarget target,
                                       AlertConditionDTO condition,
                                       AlertThresholdsDTO thresholds,
                                       AlertMonitorOptionsDTO options,
                                       AlertEvaluationRun run,
                                       String sql) {
        evaluateSingleState(rule, target, condition, thresholds, options, run, Map.of(), null,
                STATE_OK, "ok", "评估窗口内未命中阈值", List.of(), sql);
    }

    private StateDecision decideState(String previousState,
                                      String evaluatedState,
                                      BigDecimal actualValue,
                                      AlertThresholdsDTO thresholds) {
        if (STATE_NO_DATA.equals(evaluatedState)) {
            return new StateDecision(STATE_NO_DATA, null);
        }
        if (STATE_CRITICAL.equals(evaluatedState)) {
            return new StateDecision(STATE_CRITICAL, thresholdValue(thresholds.getCritical()));
        }
        if (STATE_WARNING.equals(evaluatedState)) {
            return new StateDecision(STATE_WARNING, thresholdValue(thresholds.getWarning()));
        }

        AlertThresholdDTO recovery = thresholds.getRecovery();
        boolean wasAlerting = STATE_WARNING.equals(previousState)
                || STATE_CRITICAL.equals(previousState)
                || STATE_NO_DATA.equals(previousState);
        if (wasAlerting && recovery != null && recovery.getThreshold() != null && actualValue != null) {
            boolean recovered = compare(actualValue, recovery.getThreshold(), recovery.getOperator());
            if (recovered) {
                return new StateDecision(STATE_RECOVERED, recovery.getThreshold());
            }
            return new StateDecision(previousState, null);
        }
        return new StateDecision(STATE_OK, null);
    }

    private EvaluationLevel resolveLevel(BigDecimal actualValue, AlertThresholdsDTO thresholds) {
        AlertThresholdDTO critical = thresholds.getCritical();
        AlertThresholdDTO warning = thresholds.getWarning();
        if (actualValue != null && critical != null && compare(actualValue, critical.getThreshold(), critical.getOperator())) {
            return new EvaluationLevel(STATE_CRITICAL, "critical", "达到严重阈值");
        }
        if (actualValue != null && warning != null && compare(actualValue, warning.getThreshold(), warning.getOperator())) {
            return new EvaluationLevel(STATE_WARNING, "warning", "达到警告阈值");
        }
        return new EvaluationLevel(STATE_OK, "ok", "未命中阈值");
    }

    private boolean shouldNotify(boolean stateChanged,
                                 String nextState,
                                 AlertMonitorState state,
                                 AlertMonitorOptionsDTO options) {
        if (STATE_OK.equals(nextState)) {
            return false;
        }
        if (STATE_RECOVERED.equals(nextState)) {
            return true;
        }
        if (stateChanged) {
            return true;
        }
        Integer interval = ObjectUtils.defaultIfNull(options.getRenotifyIntervalMinutes(), 0);
        if (interval <= 0 || state.getLastNotifiedAt() == null) {
            return false;
        }
        Integer occurrences = ObjectUtils.defaultIfNull(options.getRenotifyOccurrences(), 0);
        Integer currentCount = ObjectUtils.defaultIfNull(state.getRenotifyCount(), 0);
        if (occurrences > 0 && currentCount >= occurrences) {
            return false;
        }
        return Duration.between(state.getLastNotifiedAt(), LocalDateTime.now()).toMinutes() >= interval;
    }

    private void applyState(AlertMonitorState state,
                            String nextState,
                            String previousState,
                            BigDecimal actualValue,
                            BigDecimal threshold,
                            Map<String, Object> groupValues) {
        LocalDateTime now = LocalDateTime.now();
        state.setPreviousState(previousState);
        state.setState(STATE_RECOVERED.equals(nextState) ? STATE_OK : nextState);
        state.setLastValue(actualValue);
        state.setLastThreshold(threshold);
        state.setLastEvaluatedAt(now);
        state.setUpdatedAt(now);
        state.setGroupValues(groupValues);
        if (!StringUtils.equals(previousState, nextState)) {
            state.setLastStateChangedAt(now);
            if (!STATE_NO_DATA.equals(nextState)) {
                state.setNoDataSince(null);
            }
        }
        if (STATE_NO_DATA.equals(nextState) && state.getNoDataSince() == null) {
            state.setNoDataSince(now);
        }
    }

    private AlertEvent createEvent(AlertRule rule,
                                   AlertDatasetTarget target,
                                   AlertConditionDTO condition,
                                   AlertThresholdsDTO thresholds,
                                   AlertMonitorOptionsDTO options,
                                   AlertEvaluationRun run,
                                   AlertMonitorState state,
                                   String previousState,
                                   StateDecision decision,
                                   String thresholdLevel,
                                   String reason,
                                   List<Map<String, Object>> samples,
                                   String sql) {
        AlertEvent event = new AlertEvent();
        event.setRuleId(rule.getId());
        event.setRuleName(rule.getName());
        event.setSeverity(resolveSeverity(rule, decision.nextState()));
        event.setState(decision.nextState());
        event.setPreviousState(previousState);
        event.setThresholdLevel(thresholdLevel);
        event.setMessage(buildAlertMessage(rule, target, state, decision, reason));
        event.setEvaluationRunId(run.getId());
        event.setTriggeredAt(LocalDateTime.now());
        event.setAcknowledged(false);

        Map<String, Object> logData = new LinkedHashMap<>();
        logData.put("condition", condition);
        logData.put("thresholds", thresholds);
        logData.put("options", options);
        logData.put("results", samples);
        logData.put("count", samples.size());
        logData.put("datasourceId", target.getDatasourceId());
        logData.put("tableName", target.getTableName());
        logData.put("groupValues", state.getGroupValues());
        logData.put("actualValue", state.getLastValue());
        logData.put("threshold", decision.threshold());
        logData.put("level", StringUtils.lowerCase(decision.nextState()));
        logData.put("thresholdLevel", thresholdLevel);
        logData.put("reason", reason);
        logData.put("sql", sql);
        event.setLogData(logData);
        return event;
    }

    private AlertEvaluationRun startRun(AlertRule rule, QueryBuilder.EvaluationWindow window) {
        AlertEvaluationRun run = new AlertEvaluationRun();
        run.setRuleId(rule.getId());
        run.setStartedAt(LocalDateTime.now());
        run.setWindowStart(window.startTime());
        run.setWindowEnd(window.endTime());
        run.setStatus("RUNNING");
        run.setMatchedCount(0);
        run.setDetails(Map.of());
        alertEvaluationRunMapper.insert(run);
        return run;
    }

    private void finishRun(AlertEvaluationRun run, String status, int matchedCount, String errorMessage, Map<String, Object> details) {
        run.setFinishedAt(LocalDateTime.now());
        run.setStatus(status);
        run.setMatchedCount(matchedCount);
        run.setErrorMessage(errorMessage);
        run.setDetails(details);
        alertEvaluationRunMapper.updateById(run);
    }

    private AlertConditionDTO loadCondition(Long ruleId) {
        AlertRuleCondition entity = alertRuleConditionMapper.selectOne(new LambdaQueryWrapper<AlertRuleCondition>()
                .eq(AlertRuleCondition::getRuleId, ruleId)
                .last("LIMIT 1"));
        AlertConditionDTO condition = new AlertConditionDTO();
        if (entity == null) {
            condition.setAggregate(new AlertAggregateDTO());
            condition.setFilters(Map.of());
            condition.setGroupBy(List.of());
            return condition;
        }
        AlertAggregateDTO aggregate = new AlertAggregateDTO();
        aggregate.setFunction(entity.getAggregateFunction());
        aggregate.setField(entity.getAggregateField());
        condition.setQuery(entity.getQuery());
        condition.setFilters(ObjectUtils.defaultIfNull(entity.getFilters(), Map.of()));
        condition.setAggregate(aggregate);
        condition.setGroupBy(ObjectUtils.defaultIfNull(entity.getGroupBy(), List.of()));
        return condition;
    }

    private AlertThresholdsDTO loadThresholds(Long ruleId) {
        List<AlertRuleThreshold> rows = alertRuleThresholdMapper.selectList(new LambdaQueryWrapper<AlertRuleThreshold>()
                .eq(AlertRuleThreshold::getRuleId, ruleId));
        AlertThresholdsDTO thresholds = new AlertThresholdsDTO();
        for (AlertRuleThreshold row : rows) {
            AlertThresholdDTO dto = AlertThresholdDTO.builder()
                    .level(row.getLevel())
                    .operator(row.getOperator())
                    .threshold(row.getThreshold())
                    .timeWindow(row.getTimeWindow())
                    .build();
            switch (StringUtils.lowerCase(row.getLevel())) {
                case "critical" -> thresholds.setCritical(dto);
                case "warning" -> thresholds.setWarning(dto);
                case "recovery" -> thresholds.setRecovery(dto);
                default -> log.debug("Unknown threshold level: {}", row.getLevel());
            }
        }
        return thresholds;
    }

    private AlertMonitorOptionsDTO loadOptions(Long ruleId) {
        AlertMonitorOptions entity = alertMonitorOptionsMapper.selectOne(new LambdaQueryWrapper<AlertMonitorOptions>()
                .eq(AlertMonitorOptions::getRuleId, ruleId)
                .last("LIMIT 1"));
        AlertMonitorOptionsDTO dto = new AlertMonitorOptionsDTO();
        if (entity == null) {
            dto.setNotifyNoData(false);
            dto.setIncludeTags(true);
            return dto;
        }
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

    private AlertTriggerDTO buildTrigger(AlertThresholdsDTO thresholds) {
        AlertTriggerDTO trigger = new AlertTriggerDTO();
        if (thresholds.getCritical() == null) {
            return trigger;
        }
        trigger.setOperator(thresholds.getCritical().getOperator());
        trigger.setThreshold(thresholds.getCritical().getThreshold());
        trigger.setTimeWindow(thresholds.getCritical().getTimeWindow());
        if (thresholds.getWarning() != null) {
            trigger.setWarningThreshold(thresholds.getWarning().getThreshold());
        }
        if (thresholds.getRecovery() != null) {
            trigger.setRecoveryThreshold(thresholds.getRecovery().getThreshold());
        }
        return trigger;
    }

    private AlertMonitorState loadOrCreateState(Long ruleId, AlertDatasetTarget target, String groupKey, Map<String, Object> groupValues) {
        String datasourceId = normalizeStateDatasourceId(target.getDatasourceId());
        AlertMonitorState state = alertMonitorStateMapper.selectOne(new LambdaQueryWrapper<AlertMonitorState>()
                .eq(AlertMonitorState::getRuleId, ruleId)
                .eq(AlertMonitorState::getDatasourceId, datasourceId)
                .eq(AlertMonitorState::getTableName, target.getTableName())
                .eq(AlertMonitorState::getGroupKey, groupKey)
                .last("LIMIT 1"));
        if (state != null) {
            return state;
        }
        state = new AlertMonitorState();
        state.setRuleId(ruleId);
        state.setDatasourceId(datasourceId);
        state.setTableName(target.getTableName());
        state.setGroupKey(groupKey);
        state.setGroupValues(groupValues);
        state.setState(STATE_OK);
        state.setRenotifyCount(0);
        state.setUpdatedAt(LocalDateTime.now());
        return state;
    }

    private void persistState(AlertMonitorState state) {
        state.setDatasourceId(normalizeStateDatasourceId(state.getDatasourceId()));
        alertMonitorStateMapper.upsertByTarget(state);
    }

    private String normalizeStateDatasourceId(String datasourceId) {
        return StringUtils.defaultString(StringUtils.trimToNull(datasourceId));
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

    private List<Map<String, Object>> executeQuery(AlertDatasetTarget target, String sql) {
        Object rawResult = dynamicLogQueryService.executeRawSQL(target.getDatasourceId(), sql);
        if (rawResult instanceof List<?> list) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> row) {
                    rows.add(new HashMap<>((Map<String, Object>) row));
                }
            }
            return rows;
        }
        if (rawResult instanceof Map<?, ?> map) {
            return List.of(new HashMap<>((Map<String, Object>) map));
        }
        if (rawResult != null) {
            Map<String, Object> row = new HashMap<>();
            row.put("value", rawResult);
            return List.of(row);
        }
        return List.of();
    }

    private boolean supportsTarget(AlertDatasetTarget target) {
        return StringUtils.isBlank(target.getDatasourceType()) || "clickhouse".equalsIgnoreCase(target.getDatasourceType());
    }

    private Map<String, Object> extractGroupValues(Map<String, Object> row) {
        Map<String, Object> groupValues = new LinkedHashMap<>(row);
        groupValues.remove("value");
        return groupValues;
    }

    private String buildGroupKey(Map<String, Object> groupValues) {
        if (MapUtils.isEmpty(groupValues)) {
            return "default";
        }
        return groupValues.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + "|" + right)
                .orElse("default");
    }

    private String resolveSeverity(AlertRule rule, String state) {
        return switch (state) {
            case STATE_CRITICAL -> "CRITICAL";
            case STATE_WARNING, STATE_NO_DATA -> "WARNING";
            case STATE_RECOVERED -> "INFO";
            default -> StringUtils.defaultIfBlank(rule.getSeverity(), "WARNING").toUpperCase();
        };
    }

    private String buildAlertMessage(AlertRule rule,
                                     AlertDatasetTarget target,
                                     AlertMonitorState state,
                                     StateDecision decision,
                                     String reason) {
        StringBuilder message = new StringBuilder();
        message.append("告警规则 '").append(rule.getName()).append("' ");
        if (STATE_RECOVERED.equals(decision.nextState())) {
            message.append("已恢复");
        } else if (STATE_NO_DATA.equals(decision.nextState())) {
            message.append("进入 No Data 状态");
        } else {
            message.append(reason);
        }
        if (state.getLastValue() != null) {
            message.append(": 实际值 ").append(state.getLastValue());
        }
        if (decision.threshold() != null) {
            message.append(" / 阈值 ").append(decision.threshold());
        }
        if (MapUtils.isNotEmpty(state.getGroupValues())) {
            message.append(" [").append(state.getGroupValues()).append("]");
        }
        if (StringUtils.isNotBlank(target.getTableName())) {
            message.append(" 来源表: ").append(target.getTableName());
        }
        return message.toString();
    }

    private boolean compare(BigDecimal actualValue, BigDecimal threshold, String operator) {
        if (actualValue == null || threshold == null) {
            return false;
        }
        int compareResult = actualValue.compareTo(threshold);
        return switch (StringUtils.defaultIfBlank(operator, "gt")) {
            case "gt" -> compareResult > 0;
            case "gte" -> compareResult >= 0;
            case "lt" -> compareResult < 0;
            case "lte" -> compareResult <= 0;
            case "eq" -> compareResult == 0;
            case "ne" -> compareResult != 0;
            default -> false;
        };
    }

    private BigDecimal convertToDecimal(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof BigDecimal decimal) {
            return decimal;
        }
        if (obj instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(obj));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal thresholdValue(AlertThresholdDTO threshold) {
        return threshold != null ? threshold.getThreshold() : null;
    }

    private record EvaluationLevel(String state, String thresholdLevel, String reason) {
    }

    private record StateDecision(String nextState, BigDecimal threshold) {
    }
}
