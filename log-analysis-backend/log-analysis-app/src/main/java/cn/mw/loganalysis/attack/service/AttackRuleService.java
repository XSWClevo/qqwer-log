package cn.mw.loganalysis.attack.service;

import cn.mw.loganalysis.attack.converter.AttackStructMapper;
import cn.mw.loganalysis.attack.dto.AttackRuleQueryRequest;
import cn.mw.loganalysis.attack.dto.CreateAttackRuleRequest;
import cn.mw.loganalysis.attack.dto.UpdateAttackRuleRequest;
import cn.mw.loganalysis.attack.entity.AttackDetectionRule;
import cn.mw.loganalysis.attack.mapper.AttackDetectionRuleMapper;
import cn.mw.loganalysis.common.exception.ResourceNotFoundException;
import cn.mw.loganalysis.common.exception.ValidationException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AttackRuleService extends ServiceImpl<AttackDetectionRuleMapper, AttackDetectionRule> {

    private final AttackStructMapper attackStructMapper;

    @Transactional(rollbackFor = Exception.class)
    public AttackDetectionRule create(CreateAttackRuleRequest request) {
        if (ObjectUtils.isNotEmpty(findByRuleId(request.getRuleId()))) {
            throw new ValidationException("攻击检测规则ID已存在: " + request.getRuleId());
        }

        AttackDetectionRule rule = attackStructMapper.toRule(request);
        validateRule(rule);
        getBaseMapper().insert(rule);
        return rule;
    }

    @Transactional(rollbackFor = Exception.class)
    public AttackDetectionRule update(Long id, UpdateAttackRuleRequest request) {
        AttackDetectionRule rule = requireRule(id);
        attackStructMapper.updateRule(request, rule);
        validateRule(rule);
        getBaseMapper().updateById(rule);
        return rule;
    }

    public AttackDetectionRule get(Long id) {
        return requireRule(id);
    }

    public Page<AttackDetectionRule> list(AttackRuleQueryRequest request) {
        AttackRuleQueryRequest normalizedRequest = ObjectUtils.defaultIfNull(request, new AttackRuleQueryRequest());
        Page<AttackDetectionRule> page = new Page<>(
                Math.max(ObjectUtils.defaultIfNull(normalizedRequest.getPageNum(), 1), 1),
                Math.max(ObjectUtils.defaultIfNull(normalizedRequest.getPageSize(), 20), 1));

        LambdaQueryWrapper<AttackDetectionRule> wrapper = new LambdaQueryWrapper<>();
        String keyword = StringUtils.trimToEmpty(normalizedRequest.getKeyword());
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.and(query -> query.like(AttackDetectionRule::getName, keyword)
                    .or()
                    .like(AttackDetectionRule::getRuleId, keyword)
                    .or()
                    .like(AttackDetectionRule::getDescription, keyword));
        }
        wrapper.eq(StringUtils.isNotBlank(normalizedRequest.getAttackType()),
                AttackDetectionRule::getAttackType, StringUtils.trim(normalizedRequest.getAttackType()));
        wrapper.eq(ObjectUtils.isNotEmpty(normalizedRequest.getEnabled()),
                AttackDetectionRule::getEnabled, normalizedRequest.getEnabled());
        wrapper.orderByAsc(AttackDetectionRule::getPriority).orderByDesc(AttackDetectionRule::getUpdatedAt);
        return getBaseMapper().selectPage(page, wrapper);
    }

    public List<AttackDetectionRule> listEnabled() {
        return getBaseMapper().selectList(new LambdaQueryWrapper<AttackDetectionRule>()
                .eq(AttackDetectionRule::getEnabled, true)
                .orderByAsc(AttackDetectionRule::getPriority)
                .orderByAsc(AttackDetectionRule::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireRule(id);
        getBaseMapper().deleteById(id);
    }

    private AttackDetectionRule findByRuleId(String ruleId) {
        if (StringUtils.isBlank(ruleId)) {
            return null;
        }
        return getBaseMapper().selectOne(new LambdaQueryWrapper<AttackDetectionRule>()
                .eq(AttackDetectionRule::getRuleId, StringUtils.trim(ruleId))
                .last("LIMIT 1"));
    }

    private AttackDetectionRule requireRule(Long id) {
        AttackDetectionRule rule = getBaseMapper().selectById(id);
        if (ObjectUtils.isEmpty(rule)) {
            throw new ResourceNotFoundException("攻击检测规则不存在: " + id);
        }
        return rule;
    }

    private void validateRule(AttackDetectionRule rule) {
        if (StringUtils.isBlank(rule.getRuleId())) {
            throw new ValidationException("规则ID不能为空");
        }
        if (StringUtils.isBlank(rule.getName())) {
            throw new ValidationException("规则名称不能为空");
        }
        if (StringUtils.isBlank(rule.getAttackType())) {
            throw new ValidationException("攻击类型不能为空");
        }
        if (CollectionUtils.isEmpty(rule.getMessagePatterns())
                && CollectionUtils.isEmpty(rule.getRawPatterns())
                && CollectionUtils.isEmpty(rule.getKeywords())) {
            throw new ValidationException("规则至少需要一个 message/raw 正则或关键词");
        }
    }
}
