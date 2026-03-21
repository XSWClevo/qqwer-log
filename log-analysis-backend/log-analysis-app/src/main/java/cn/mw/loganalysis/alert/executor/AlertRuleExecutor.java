package cn.mw.loganalysis.alert.executor;

import cn.mw.loganalysis.alert.entity.AlertEvent;
import cn.mw.loganalysis.alert.entity.AlertRule;
import cn.mw.loganalysis.alert.mapper.AlertEventMapper;
import cn.mw.loganalysis.alert.mapper.AlertExecutionMapper;
import cn.mw.loganalysis.alert.notifier.AlertNotifier;
import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
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
    private final AlertExecutionMapper alertExecutionMapper;
    private final AlertEventMapper alertEventMapper;
    private final AlertNotifier alertNotifier;

    /**
     * 执行规则
     */
    @DS("clickhouse")
    public void executeRule(AlertRule rule) {
        log.debug("Executing alert rule: {} (ID: {})", rule.getName(), rule.getId());
        
        try {
            // 1. 检查静默期
            if (isInSilencePeriod(rule)) {
                log.debug("Rule {} is in silence period, skipping", rule.getId());
                return;
            }
            
            // 2. 构建查询
            String sql = queryBuilder.buildQuery(rule.getCondition());
            
            // 3. 执行查询 (使用 ClickHouse) - 不在事务中
            List<Map<String, Object>> results = executeClickHouseQuery(sql);
            
            // 4. 判断是否触发
            if (shouldTriggerAlert(rule, results)) {
                log.info("Alert rule {} triggered with {} results", rule.getId(), results.size());
                
                // 5. 创建告警事件 - 在单独的事务中
                createAndSaveAlertEvent(rule, results);
            } else {
                log.debug("Alert rule {} not triggered", rule.getId());
            }
            
        } catch (Exception e) {
            log.error("Failed to execute rule: {} (ID: {})", rule.getName(), rule.getId(), e);
        }
    }

    /**
     * 创建并保存告警事件（在事务中）
     */
    protected void createAndSaveAlertEvent(AlertRule rule, List<Map<String, Object>> results) {
        // 创建告警事件
        AlertEvent event = createAlertEvent(rule, results);
        alertEventMapper.insert(event);
        
        // 发送通知
        alertNotifier.sendNotifications(rule, event, results);
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
     * 执行 ClickHouse 查询
     */
    @DS("clickhouse")
    private List<Map<String, Object>> executeClickHouseQuery(String sql) {
        return alertExecutionMapper.executeDynamicQuery(sql);
    }

    /**
     * 判断是否应该触发告警
     */
    private boolean shouldTriggerAlert(AlertRule rule, List<Map<String, Object>> results) {
        if (results == null || results.isEmpty()) {
            return false;
        }
        
        Map<String, Object> condition = rule.getCondition();
        String operator = (String) condition.getOrDefault("operator", "gt");
        Object thresholdObj = condition.get("value");
        
        if (thresholdObj == null) {
            return false;
        }
        
        double threshold = convertToDouble(thresholdObj);
        
        // 获取查询结果的值
        Map<String, Object> firstResult = results.get(0);
        Object valueObj = firstResult.get("value");
        
        if (valueObj == null) {
            return false;
        }
        
        double actualValue = convertToDouble(valueObj);
        
        // 比较值
        return switch (operator) {
            case "gt" -> actualValue > threshold;
            case "gte" -> actualValue >= threshold;
            case "lt" -> actualValue < threshold;
            case "lte" -> actualValue <= threshold;
            case "eq" -> actualValue == threshold;
            default -> false;
        };
    }

    /**
     * 创建告警事件
     */
    private AlertEvent createAlertEvent(AlertRule rule, List<Map<String, Object>> results) {
        AlertEvent event = new AlertEvent();
        event.setRuleId(rule.getId());
        event.setRuleName(rule.getName());
        event.setSeverity(rule.getSeverity());
        
        // 构建告警消息
        String message = buildAlertMessage(rule, results);
        event.setMessage(message);
        
        // 保存查询结果数据
        Map<String, Object> logData = new HashMap<>();
        logData.put("results", results);
        logData.put("count", results.size());
        logData.put("condition", rule.getCondition());
        event.setLogData(logData);
        event.setTriggeredAt(LocalDateTime.now());
        
        event.setAcknowledged(false);
        
        return event;
    }

    /**
     * 构建告警消息
     */
    private String buildAlertMessage(AlertRule rule, List<Map<String, Object>> results) {
        Map<String, Object> condition = rule.getCondition();
        Map<String, Object> firstResult = results.get(0);
        
        Object valueObj = firstResult.get("value");
        double actualValue = valueObj != null ? convertToDouble(valueObj) : 0;
        
        Object thresholdObj = condition.get("value");
        double threshold = thresholdObj != null ? convertToDouble(thresholdObj) : 0;
        
        String operator = (String) condition.getOrDefault("operator", "gt");
        String operatorText = switch (operator) {
            case "gt" -> ">";
            case "gte" -> ">=";
            case "lt" -> "<";
            case "lte" -> "<=";
            case "eq" -> "=";
            default -> operator;
        };
        
        return String.format("告警规则 '%s' 被触发: 实际值 %.2f %s 阈值 %.2f", 
                rule.getName(), actualValue, operatorText, threshold);
    }

    /**
     * 转换为 double
     */
    private double convertToDouble(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        try {
            return Double.parseDouble(obj.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
