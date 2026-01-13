package cn.mw.loganalysis.extraction.service;

import cn.mw.loganalysis.common.exception.ResourceNotFoundException;
import cn.mw.loganalysis.common.exception.ValidationException;
import cn.mw.loganalysis.extraction.dto.CreateExtractionRuleRequest;
import cn.mw.loganalysis.extraction.dto.UpdateExtractionRuleRequest;
import cn.mw.loganalysis.extraction.entity.ExtractionRule;
import cn.mw.loganalysis.extraction.mapper.ExtractionRuleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 日志提取规则服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtractionRuleService {

    private final ExtractionRuleMapper extractionRuleMapper;

    /**
     * 创建提取规则
     */
    @Transactional
    public ExtractionRule createRule(CreateExtractionRuleRequest request, Long userId) {
        log.info("Creating extraction rule: {}", request.getName());

        // 验证规则
        validateRule(request.getRuleType(), request.getPattern());

        ExtractionRule rule = new ExtractionRule();
        rule.setName(request.getName());
        rule.setDescription(request.getDescription());
        rule.setRuleType(request.getRuleType());
        rule.setPattern(request.getPattern());
        rule.setFieldMappings(request.getFieldMappings());
        rule.setPriority(request.getPriority());
        rule.setEnabled(request.getEnabled());
        rule.setCreatedBy(userId);

        extractionRuleMapper.insert(rule);
        log.info("Extraction rule created with id: {}", rule.getId());

        return rule;
    }

    /**
     * 更新提取规则
     */
    @Transactional
    public ExtractionRule updateRule(Long id, UpdateExtractionRuleRequest request) {
        log.info("Updating extraction rule: {}", id);

        ExtractionRule rule = getById(id);

        if (request.getName() != null) {
            rule.setName(request.getName());
        }
        if (request.getDescription() != null) {
            rule.setDescription(request.getDescription());
        }
        if (request.getRuleType() != null) {
            rule.setRuleType(request.getRuleType());
        }
        if (request.getPattern() != null) {
            validateRule(request.getRuleType() != null ? request.getRuleType() : rule.getRuleType(),
                        request.getPattern());
            rule.setPattern(request.getPattern());
        }
        if (request.getFieldMappings() != null) {
            rule.setFieldMappings(request.getFieldMappings());
        }
        if (request.getPriority() != null) {
            rule.setPriority(request.getPriority());
        }
        if (request.getEnabled() != null) {
            rule.setEnabled(request.getEnabled());
        }

        extractionRuleMapper.updateById(rule);
        log.info("Extraction rule updated: {}", id);

        return rule;
    }

    /**
     * 删除提取规则
     */
    @Transactional
    public void deleteRule(Long id) {
        log.info("Deleting extraction rule: {}", id);
        ExtractionRule rule = getById(id);
        extractionRuleMapper.deleteById(id);
        log.info("Extraction rule deleted: {}", id);
    }

    /**
     * 根据ID获取规则
     */
    public ExtractionRule getById(Long id) {
        ExtractionRule rule = extractionRuleMapper.selectById(id);
        if (rule == null) {
            throw new ResourceNotFoundException("提取规则不存在: " + id);
        }
        return rule;
    }

    /**
     * 获取所有启用的规则（按优先级排序）
     */
    public List<ExtractionRule> getEnabledRules() {
        LambdaQueryWrapper<ExtractionRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExtractionRule::getEnabled, true)
               .orderByDesc(ExtractionRule::getPriority)
               .orderByAsc(ExtractionRule::getId);
        return extractionRuleMapper.selectList(wrapper);
    }

    /**
     * 分页查询规则
     */
    public Page<ExtractionRule> listRules(int pageNum, int pageSize, Boolean enabled) {
        Page<ExtractionRule> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ExtractionRule> wrapper = new LambdaQueryWrapper<>();

        if (enabled != null) {
            wrapper.eq(ExtractionRule::getEnabled, enabled);
        }

        wrapper.orderByDesc(ExtractionRule::getPriority)
               .orderByDesc(ExtractionRule::getCreatedAt);

        return extractionRuleMapper.selectPage(page, wrapper);
    }

    /**
     * 测试提取规则
     */
    public Map<String, String> testRule(String ruleType, String pattern, String testLog) {
        validateRule(ruleType, pattern);
        // TODO: 实际的提取逻辑将在extractor中实现
        log.info("Testing extraction rule - type: {}, pattern: {}", ruleType, pattern);
        return Map.of("result", "测试功能待实现");
    }

    /**
     * 验证规则
     */
    private void validateRule(String ruleType, String pattern) {
        if (!List.of("REGEX", "GROK", "JSON_PATH").contains(ruleType)) {
            throw new ValidationException("不支持的规则类型: " + ruleType);
        }

        if (pattern == null || pattern.trim().isEmpty()) {
            throw new ValidationException("匹配模式不能为空");
        }

        // TODO: 根据ruleType验证pattern格式
    }
}
