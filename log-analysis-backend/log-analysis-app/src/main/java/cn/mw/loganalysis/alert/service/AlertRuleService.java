package cn.mw.loganalysis.alert.service;

import cn.mw.loganalysis.alert.dto.*;
import cn.mw.loganalysis.alert.entity.AlertRule;
import cn.mw.loganalysis.alert.mapper.AlertRuleMapper;
import cn.mw.loganalysis.alert.mapper.AlertEventMapper;
import cn.mw.loganalysis.alert.validator.QueryValidator;
import cn.mw.loganalysis.common.enums.ComparisonOperator;
import cn.mw.loganalysis.common.exception.ResourceNotFoundException;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 告警规则服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRuleService {

    private final AlertRuleMapper alertRuleMapper;
    private final AlertEventMapper alertEventMapper;
    private final QueryValidator queryValidator;

    /**
     * 查询告警规则列表
     */
    public IPage<AlertRuleDTO> queryRules(AlertRuleQueryRequest request) {
        log.info("Querying alert rules with request: {}", request);

        Page<AlertRule> page = new Page<>(request.getPageNum(), request.getPageSize());

        // 使用 Mapper 的 default 方法
        IPage<AlertRule> rulePage = alertRuleMapper.selectPageByCondition(
                page,
                request.getKeyword(),
                request.getStatus(),
                request.getSeverity()
        );

        // 转换为 DTO
        return rulePage.convert(this::convertToDTO);
    }

    /**
     * 获取规则详情
     */
    public AlertRuleDTO getRuleById(Long id) {
        log.info("Getting alert rule by id: {}", id);

        AlertRule rule = alertRuleMapper.selectById(id);
        if (rule == null) {
            throw new ResourceNotFoundException("告警规则不存在: " + id);
        }

        return convertToDTO(rule);
    }

    /**
     * 创建告警规则
     */
    @Transactional
    public AlertRuleDTO createRule(CreateAlertRuleRequest request) {
        log.info("Creating alert rule: {}", request.getName());
        
        // 验证查询条件
        validateRuleCondition(request.getCondition());
        
        AlertRule rule = new AlertRule();
        rule.setName(request.getName());
        rule.setDescription(request.getDescription());
        rule.setCondition(request.getCondition());
        rule.setSeverity(request.getSeverity().toUpperCase());
        rule.setNotificationChannels(request.getNotificationChannels());
        rule.setSilencePeriod(request.getSilencePeriod());
        rule.setEnabled(request.getEnabled());
        rule.setCreatedBy(1L); // TODO: 从当前用户获取
        
        alertRuleMapper.insert(rule);
        
        return convertToDTO(rule);
    }

    /**
     * 更新告警规则
     */
    @Transactional
    public AlertRuleDTO updateRule(Long id, UpdateAlertRuleRequest request) {
        log.info("Updating alert rule: {}", id);

        AlertRule rule = alertRuleMapper.selectById(id);
        if (rule == null) {
            throw new ResourceNotFoundException("告警规则不存在: " + id);
        }

        // 如果更新了条件，需要验证
        if (request.getCondition() != null) {
            validateRuleCondition(request.getCondition());
        }

        if (request.getName() != null) {
            rule.setName(request.getName());
        }
        if (request.getDescription() != null) {
            rule.setDescription(request.getDescription());
        }
        if (request.getCondition() != null) {
            rule.setCondition(request.getCondition());
        }
        if (request.getSeverity() != null) {
            rule.setSeverity(request.getSeverity().toUpperCase());
        }
        if (request.getNotificationChannels() != null) {
            rule.setNotificationChannels(request.getNotificationChannels());
        }
        if (request.getSilencePeriod() != null) {
            rule.setSilencePeriod(request.getSilencePeriod());
        }
        if (request.getEnabled() != null) {
            rule.setEnabled(request.getEnabled());
        }

        alertRuleMapper.updateById(rule);

        return convertToDTO(rule);
    }

    /**
     * 删除告警规则
     */
    @Transactional
    public void deleteRule(Long id) {
        log.info("Deleting alert rule: {}", id);

        AlertRule rule = alertRuleMapper.selectById(id);
        if (rule == null) {
            throw new ResourceNotFoundException("告警规则不存在: " + id);
        }
        
        alertRuleMapper.deleteById(id);
    }

    /**
     * 切换规则状态
     */
    @Transactional
    public AlertRuleDTO toggleRuleStatus(Long id, Boolean enabled) {
        log.info("Toggling alert rule status: {} to {}", id, enabled);

        AlertRule rule = alertRuleMapper.selectById(id);
        if (rule == null) {
            throw new ResourceNotFoundException("告警规则不存在: " + id);
        }

        rule.setEnabled(enabled);
        alertRuleMapper.updateById(rule);

        return convertToDTO(rule);
    }

    /**
     * 复制规则
     */
    @Transactional
    public AlertRuleDTO duplicateRule(Long id) {
        log.info("Duplicating alert rule: {}", id);

        AlertRule original = alertRuleMapper.selectById(id);
        if (original == null) {
            throw new ResourceNotFoundException("告警规则不存在: " + id);
        }

        AlertRule duplicate = new AlertRule();
        duplicate.setName(original.getName() + " (副本)");
        duplicate.setDescription(original.getDescription());
        duplicate.setCondition(original.getCondition());
        duplicate.setSeverity(original.getSeverity());
        duplicate.setNotificationChannels(original.getNotificationChannels());
        duplicate.setSilencePeriod(original.getSilencePeriod());
        duplicate.setEnabled(false); // 默认禁用副本
        duplicate.setCreatedBy(1L); // TODO: 从当前用户获取

        alertRuleMapper.insert(duplicate);

        return convertToDTO(duplicate);
    }

    /**
     * 测试规则
     */
    public Map<String, Object> testRule(Long id) {
        log.info("Testing alert rule: {}", id);

        AlertRule rule = alertRuleMapper.selectById(id);
        if (rule == null) {
            throw new ResourceNotFoundException("告警规则不存在: " + id);
        }

        // TODO: 实现实际的规则测试逻辑
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "规则测试成功");
        result.put("matchedLogs", 0);

        return result;
    }

    /**
     * 转换为 DTO
     */
    private AlertRuleDTO convertToDTO(AlertRule rule) {
        // 获取最后触发时间和触发次数
        LocalDateTime lastTriggered = alertEventMapper.getLastTriggeredTime(rule.getId());
        Long triggerCount = alertEventMapper.getTriggerCount(rule.getId());
        
        return AlertRuleDTO.builder()
                .id(rule.getId())
                .name(rule.getName())
                .description(rule.getDescription())
                .condition(rule.getCondition())
                .severity(rule.getSeverity())
                .notificationChannels(rule.getNotificationChannels())
                .silencePeriod(rule.getSilencePeriod())
                .enabled(rule.getEnabled())
                .createdBy(rule.getCreatedBy())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .lastTriggeredAt(lastTriggered)
                .triggerCount(triggerCount != null ? triggerCount : 0L)
                .type(extractRuleType(rule.getCondition()))
                .triggerConditionSummary(buildTriggerSummary(rule.getCondition()))
                .build();
    }

    /**
     * 提取规则类型
     */
    private String extractRuleType(Map<String, Object> condition) {
        if (condition == null) {
            return "log_query";
        }
        
        String type = (String) condition.get("type");
        return type != null ? type : "log_query";
    }

    /**
     * 构建触发条件摘要
     */
    private String buildTriggerSummary(Map<String, Object> condition) {
        if (condition == null) {
            return "未配置";
        }

        String metric = (String) condition.getOrDefault("metric", "count");
        String operator = (String) condition.getOrDefault("operator", ComparisonOperator.GT.getCode());
        Object value = condition.get("value");
        String timeWindow = (String) condition.getOrDefault("timeWindow", "5m");

        // 使用枚举获取操作符符号
        String operatorText = ComparisonOperator.getSymbol(operator);

        return String.format("%s %s %s in %s", metric, operatorText, value, timeWindow);
    }

    /**
     * 验证规则条件
     * 检查查询语句和 groupBy 字段是否有效
     */
    private void validateRuleCondition(Map<String, Object> condition) {
        if (condition == null) {
            return;
        }

        // 验证查询语句
        String query = (String) condition.get("query");
        if (query != null && !query.trim().isEmpty()) {
            try {
                queryValidator.validateQuery(query);
            } catch (IllegalArgumentException e) {
                log.error("Query validation failed: {}", e.getMessage());
                throw new IllegalArgumentException("查询条件验证失败: " + e.getMessage());
            }
        }

        // 验证 groupBy 字段
        String groupBy = (String) condition.get("groupBy");
        if (groupBy != null && !groupBy.trim().isEmpty()) {
            try {
                queryValidator.validateGroupByField(groupBy);
            } catch (IllegalArgumentException e) {
                log.error("GroupBy validation failed: {}", e.getMessage());
                throw new IllegalArgumentException("分组字段验证失败: " + e.getMessage());
            }
        }
    }
}
