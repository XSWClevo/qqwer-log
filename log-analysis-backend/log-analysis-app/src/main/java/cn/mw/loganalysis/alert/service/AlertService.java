package cn.mw.loganalysis.alert.service;

import cn.mw.loganalysis.alert.dto.CreateAlertRuleRequest;
import cn.mw.loganalysis.alert.entity.AlertEvent;
import cn.mw.loganalysis.alert.entity.AlertRule;
import cn.mw.loganalysis.alert.mapper.AlertEventMapper;
import cn.mw.loganalysis.alert.mapper.AlertRuleMapper;
import cn.mw.loganalysis.common.exception.ResourceNotFoundException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 告警服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRuleMapper alertRuleMapper;
    private final AlertEventMapper alertEventMapper;

    @Transactional
    public AlertRule createRule(CreateAlertRuleRequest request, Long userId) {
        log.info("Creating alert rule: {}", request.getName());

        AlertRule rule = new AlertRule();
        rule.setName(request.getName());
        rule.setDescription(request.getDescription());
        rule.setCondition(request.getCondition());
        rule.setSeverity(request.getSeverity());
        rule.setNotificationChannels(request.getNotificationChannels());
        rule.setSilencePeriod(request.getSilencePeriod());
        rule.setEnabled(request.getEnabled());
        rule.setCreatedBy(userId);

        alertRuleMapper.insert(rule);
        return rule;
    }

    public AlertRule getById(Long id) {
        AlertRule rule = alertRuleMapper.selectById(id);
        if (rule == null) {
            throw new ResourceNotFoundException("告警规则不存在: " + id);
        }
        return rule;
    }

    public Page<AlertRule> listRules(int pageNum, int pageSize) {
        return alertRuleMapper.selectPage(new Page<>(pageNum, pageSize), null);
    }

    public Page<AlertEvent> listEvents(int pageNum, int pageSize, Long ruleId) {
        Page<AlertEvent> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<>();
        if (ruleId != null) {
            wrapper.eq(AlertEvent::getRuleId, ruleId);
        }
        wrapper.orderByDesc(AlertEvent::getTriggeredAt);
        return alertEventMapper.selectPage(page, wrapper);
    }

    @Transactional
    public void deleteRule(Long id) {
        getById(id);
        alertRuleMapper.deleteById(id);
        log.info("Alert rule deleted: {}", id);
    }
}
