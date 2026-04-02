package cn.mw.loganalysis.alert.executor;

import cn.mw.loganalysis.alert.dto.AlertConditionDTO;
import cn.mw.loganalysis.alert.dto.AlertDatasetTarget;
import cn.mw.loganalysis.alert.dto.AlertMatchResult;
import cn.mw.loganalysis.alert.entity.AlertEvent;
import cn.mw.loganalysis.alert.entity.AlertRule;
import cn.mw.loganalysis.alert.mapper.AlertEventMapper;
import cn.mw.loganalysis.alert.notifier.AlertNotifier;
import cn.mw.loganalysis.alert.service.AlertConditionParser;
import cn.mw.loganalysis.alert.service.AlertRuleScopeResolver;
import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 告警规则执行器
 * 执行单个告警规则，查询日志并判断是否触发
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertRuleExecutor {

    private final QueryBuilder queryBuilder;
    private final AlertEventMapper alertEventMapper;
    private final AlertNotifier alertNotifier;
    private final AlertConditionParser alertConditionParser;
    private final AlertRuleScopeResolver alertRuleScopeResolver;
    private final DynamicLogQueryService dynamicLogQueryService;

    /**
     * 执行规则
     */
    public void executeRule(AlertRule rule) {
        log.debug("Executing alert rule: {} (ID: {})", rule.getName(), rule.getId());
        
        try {
            // 1. 检查静默期
            if (isInSilencePeriod(rule)) {
                log.debug("Rule {} is in silence period, skipping", rule.getId());
                return;
            }

            AlertConditionDTO condition = alertConditionParser.parse(rule.getCondition());
            List<AlertDatasetTarget> targets = alertRuleScopeResolver.resolve(rule);
            if (CollectionUtils.isEmpty(targets)) {
                log.warn("Rule {} has no dataset targets resolved, skipping", rule.getId());
                return;
            }

            List<AlertMatchResult> matches = new ArrayList<>();
            for (AlertDatasetTarget target : targets) {
                if (!supportsTarget(target)) {
                    log.warn("Rule {} target {}:{} is not supported, skipping",
                            rule.getId(), target.getDatasourceType(), target.getTableName());
                    continue;
                }

                String sql = queryBuilder.buildQuery(target, condition);
                List<Map<String, Object>> results = executeQuery(target, sql);
                matches.addAll(filterTriggeredMatches(rule, target, condition, results));
            }

            if (CollectionUtils.isNotEmpty(matches)) {
                log.info("Alert rule {} triggered with {} matches", rule.getId(), matches.size());
                createAndSaveAlertEvents(rule, matches);
                return;
            }

            log.debug("Alert rule {} not triggered", rule.getId());
        } catch (Exception e) {
            log.error("Failed to execute rule: {} (ID: {})", rule.getName(), rule.getId(), e);
        }
    }

    /**
     * 创建并保存告警事件
     */
    protected void createAndSaveAlertEvents(AlertRule rule, List<AlertMatchResult> matches) {
        for (AlertMatchResult match : matches) {
            AlertEvent event = createAlertEvent(rule, match);
            alertEventMapper.insert(event);
            alertNotifier.sendNotifications(rule, event, match.getSamples());
        }
    }

    /**
     * 检查是否在静默期内
     */
    private boolean isInSilencePeriod(AlertRule rule) {
        LocalDateTime lastTriggered = alertEventMapper.getLastTriggeredTime(rule.getId());
        
        if (lastTriggered == null) {
            return false;
        }
        
        long secondsSinceLastTrigger = Duration.between(lastTriggered, LocalDateTime.now()).getSeconds();
        int silencePeriod = rule.getSilencePeriod() != null ? rule.getSilencePeriod() : 300;
        
        return secondsSinceLastTrigger < silencePeriod;
    }

    /**
     * 判断是否应该触发告警
     */
    private List<AlertMatchResult> filterTriggeredMatches(AlertRule rule, AlertDatasetTarget target,
                                                          AlertConditionDTO parsedCondition,
                                                          List<Map<String, Object>> results) {
        List<AlertMatchResult> matches = new ArrayList<>();
        if (CollectionUtils.isEmpty(results)) {
            return matches;
        }

        String operator = parsedCondition.getTrigger() != null
                ? parsedCondition.getTrigger().getOperator()
                : "gt";
        Object thresholdObj = parsedCondition.getTrigger() != null
                ? parsedCondition.getTrigger().getThreshold()
                : null;
        if (thresholdObj == null) {
            return matches;
        }

        BigDecimal threshold = convertToDecimal(thresholdObj);
        for (Map<String, Object> row : results) {
            Object valueObj = row.get("value");
            if (valueObj == null) {
                continue;
            }

            BigDecimal actualValue = convertToDecimal(valueObj);
            if (!compare(actualValue, threshold, operator)) {
                continue;
            }

            matches.add(AlertMatchResult.builder()
                    .datasourceId(target.getDatasourceId())
                    .tableName(target.getTableName())
                    .actualValue(actualValue)
                    .threshold(threshold)
                    .groupValues(extractGroupValues(row))
                    .samples(List.of(new HashMap<>(row)))
                    .build());
        }
        return matches;
    }

    /**
     * 创建告警事件
     */
    private AlertEvent createAlertEvent(AlertRule rule, AlertMatchResult match) {
        AlertEvent event = new AlertEvent();
        event.setRuleId(rule.getId());
        event.setRuleName(rule.getName());
        event.setSeverity(rule.getSeverity());
        
        // 构建告警消息
        String message = buildAlertMessage(rule, match);
        event.setMessage(message);
        
        // 保存查询结果数据
        Map<String, Object> logData = new HashMap<>();
        logData.put("results", match.getSamples());
        logData.put("count", CollectionUtils.size(match.getSamples()));
        logData.put("condition", rule.getCondition());
        logData.put("datasourceId", match.getDatasourceId());
        logData.put("tableName", match.getTableName());
        logData.put("groupValues", match.getGroupValues());
        logData.put("actualValue", match.getActualValue());
        logData.put("threshold", match.getThreshold());
        event.setLogData(logData);
        event.setTriggeredAt(LocalDateTime.now());
        
        event.setAcknowledged(false);
        
        return event;
    }

    /**
     * 构建告警消息
     */
    private String buildAlertMessage(AlertRule rule, AlertMatchResult match) {
        AlertConditionDTO parsedCondition = alertConditionParser.parse(rule.getCondition());
        String operator = parsedCondition.getTrigger() != null
                ? String.valueOf(ObjectUtils.defaultIfNull(parsedCondition.getTrigger().getOperator(), "gt"))
                : "gt";
        String operatorText = switch (operator) {
            case "gt" -> ">";
            case "gte" -> ">=";
            case "lt" -> "<";
            case "lte" -> "<=";
            case "eq" -> "=";
            default -> operator;
        };

        StringBuilder message = new StringBuilder();
        message.append("告警规则 '").append(rule.getName()).append("' 被触发: 实际值 ")
                .append(match.getActualValue()).append(" ")
                .append(operatorText).append(" 阈值 ")
                .append(match.getThreshold());
        if (MapUtils.isNotEmpty(match.getGroupValues())) {
            message.append(" [");
            message.append(match.getGroupValues().entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .reduce((left, right) -> left + ", " + right)
                    .orElse(""));
            message.append("]");
        }
        if (StringUtils.isNotBlank(match.getTableName())) {
            message.append(" 来源表: ").append(match.getTableName());
        }
        return message.toString();
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
        Map<String, Object> groupValues = new HashMap<>(row);
        groupValues.remove("value");
        return groupValues;
    }

    private boolean compare(BigDecimal actualValue, BigDecimal threshold, String operator) {
        int compareResult = actualValue.compareTo(threshold);
        return switch (StringUtils.defaultIfBlank(operator, "gt")) {
            case "gt" -> compareResult > 0;
            case "gte" -> compareResult >= 0;
            case "lt" -> compareResult < 0;
            case "lte" -> compareResult <= 0;
            case "eq" -> compareResult == 0;
            default -> false;
        };
    }

    private BigDecimal convertToDecimal(Object obj) {
        if (obj instanceof BigDecimal decimal) {
            return decimal;
        }
        if (obj instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(obj));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
