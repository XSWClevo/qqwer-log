package cn.mw.loganalysis.agent.skill;

import cn.mw.loganalysis.agent.execution.AgentRuntimeContext;
import cn.mw.loganalysis.agent.support.AgentToolSupport;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 基于页面上下文和用户表达匹配内置技能。
 */
@Component
@RequiredArgsConstructor
public class AgentSkillMatcher {

    private static final int MATCH_THRESHOLD = 35;
    private static final List<String> GENERIC_COMPONENT_WORDS = List.of("组件", "组件库", "配置");
    private static final List<String> LOG_SPECIFIC_WORDS = List.of(
            "日志", "log", "解析", "样本", "正则", "入库", "建表", "remap", "sink", "vector"
    );

    private final AgentSkillRegistry registry;

    /**
     * 返回技能匹配结果。未命中时返回 none，泛词高风险时返回澄清。
     */
    public AgentSkillDecision match(AgentRuntimeContext context) {
        if (context == null || context.getRequest() == null) {
            return AgentSkillDecision.none();
        }

        String message = StringUtils.defaultIfBlank(context.getEffectiveMessage(), context.getRequest().getMessage());
        String normalizedMessage = StringUtils.lowerCase(StringUtils.trimToEmpty(message), Locale.ROOT);
        String skillId = context.getSkillId();
        String pageContext = context.getPageContext();

        AgentSkillDecision explicitDecision = matchExplicitSkill(skillId, message);
        if (explicitDecision.hasIntentDecision()) {
            return explicitDecision;
        }

        AgentSkillDecision scoredDecision = registry.definitions().stream()
                .map(definition -> score(definition, normalizedMessage, pageContext, message))
                .filter(AgentSkillDecision::hasIntentDecision)
                .max(Comparator.comparingInt(AgentSkillDecision::score))
                .orElse(AgentSkillDecision.none());
        if (scoredDecision.hasIntentDecision()) {
            return scoredDecision;
        }

        if (shouldClarifyGenericComponentRequest(normalizedMessage, pageContext)) {
            return AgentSkillDecision.clarification(
                    "你说的是组件库里的日志解析组件，还是页面/前端组件配置？如果是日志解析，请补充一条日志样本或说明目标表名。",
                    List.of("粘贴一条日志样本", "在组件库创建日志解析组件", "仅查看已有组件配置")
            );
        }
        return AgentSkillDecision.none();
    }

    private AgentSkillDecision matchExplicitSkill(String skillId, String message) {
        if (StringUtils.isBlank(skillId)) {
            return AgentSkillDecision.none();
        }
        return registry.definitions().stream()
                .filter(definition -> definition.matchesSkillId(skillId))
                .findFirst()
                .map(definition -> AgentSkillDecision.intent(definition, 100, message))
                .orElse(AgentSkillDecision.none());
    }

    private AgentSkillDecision score(AgentSkillDefinition definition,
                                     String normalizedMessage,
                                     String pageContext,
                                     String originalMessage) {
        int score = 0;

        if (definition.supportsPageContext(pageContext)) {
            score += 20;
            if (mentionsAny(normalizedMessage, GENERIC_COMPONENT_WORDS)) {
                score += 20;
            }
        }

        score += Math.min(30, countMatches(normalizedMessage, definition.aliases()) * 15);
        score += Math.min(30, countMatches(normalizedMessage, definition.positiveSignals()) * 10);
        score -= countMatches(normalizedMessage, definition.negativeSignals()) * 40;

        if (score >= MATCH_THRESHOLD) {
            return AgentSkillDecision.intent(definition, score, originalMessage);
        }
        return AgentSkillDecision.none();
    }

    private boolean shouldClarifyGenericComponentRequest(String normalizedMessage, String pageContext) {
        return StringUtils.isBlank(pageContext)
                && mentionsAny(normalizedMessage, GENERIC_COMPONENT_WORDS)
                && !mentionsAny(normalizedMessage, LOG_SPECIFIC_WORDS);
    }

    private int countMatches(String normalizedMessage, List<String> words) {
        if (StringUtils.isBlank(normalizedMessage) || CollectionUtils.isEmpty(words)) {
            return 0;
        }
        int count = 0;
        for (String word : words) {
            if (StringUtils.isNotBlank(word)
                    && AgentToolSupport.containsAny(normalizedMessage, StringUtils.lowerCase(word, Locale.ROOT))) {
                count++;
            }
        }
        return count;
    }

    private boolean mentionsAny(String normalizedMessage, List<String> words) {
        return countMatches(normalizedMessage, words) > 0;
    }
}
