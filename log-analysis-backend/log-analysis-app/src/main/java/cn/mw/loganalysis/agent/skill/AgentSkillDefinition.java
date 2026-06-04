package cn.mw.loganalysis.agent.skill;

import cn.mw.loganalysis.agent.nlu.AgentIntent;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 内置 Agent 技能定义。
 */
public record AgentSkillDefinition(
        String id,
        String displayName,
        Set<String> alternateSkillIds,
        List<String> aliases,
        List<String> positiveSignals,
        List<String> negativeSignals,
        Set<String> pageContexts,
        AgentIntent targetIntent,
        boolean deterministicToolRequest,
        String clarificationMessage
) {

    /**
     * 判断前端显式 skillId 是否指向该技能。
     */
    public boolean matchesSkillId(String skillId) {
        if (StringUtils.isBlank(skillId)) {
            return false;
        }
        String normalizedSkillId = StringUtils.lowerCase(StringUtils.trim(skillId), Locale.ROOT);
        if (StringUtils.equals(normalizedSkillId, StringUtils.lowerCase(id, Locale.ROOT))) {
            return true;
        }
        return CollectionUtils.emptyIfNull(alternateSkillIds).stream()
                .map(value -> StringUtils.lowerCase(StringUtils.trim(value), Locale.ROOT))
                .anyMatch(normalizedSkillId::equals);
    }

    /**
     * 判断页面上下文是否属于该技能支持的场景。
     */
    public boolean supportsPageContext(String pageContext) {
        if (StringUtils.isBlank(pageContext) || CollectionUtils.isEmpty(pageContexts)) {
            return false;
        }
        String normalizedPageContext = StringUtils.upperCase(StringUtils.trim(pageContext), Locale.ROOT);
        return pageContexts.stream()
                .map(value -> StringUtils.upperCase(StringUtils.trim(value), Locale.ROOT))
                .anyMatch(normalizedPageContext::equals);
    }
}
