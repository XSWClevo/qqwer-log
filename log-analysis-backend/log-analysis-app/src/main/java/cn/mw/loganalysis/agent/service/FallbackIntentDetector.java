package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.dto.AgentChatMessage;
import cn.mw.loganalysis.agent.dto.AgentChatRequest;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则回退模式下的轻量意图识别器。
 *
 * 这里不追求复杂 NLP，只负责把上下文依赖问题补全后，
 * 路由到和 LLM tool calling 共用的工具主线。
 */
@Component
public class FallbackIntentDetector {

    private static final Pattern QUOTED_KEYWORD_PATTERN = Pattern.compile("[\"“](.+?)[\"”]");
    private static final Pattern EXPLICIT_KEYWORD_PATTERN = Pattern.compile("(?:包含|搜索|查找|关键词|关键字|message包含|message含有)\\s*[:：]?\\s*([\\p{L}\\p{N}._:/-]+)");

    public FallbackIntentDecision detect(AgentChatRequest request, String datasourceType) {
        String normalizedMessage = AgentToolSupport.normalizeText(request.getMessage());
        String effectiveMessage = enrichMessageWithHistory(normalizedMessage, request.getHistory());
        AgentIntent intent = detectIntent(effectiveMessage, datasourceType);
        return new FallbackIntentDecision(
                intent,
                effectiveMessage,
                extractKeyword(effectiveMessage),
                extractSeverity(effectiveMessage)
        );
    }

    private AgentIntent detectIntent(String message, String datasourceType) {
        String lower = message.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "字段", "表结构", "schema", "有哪些列", "哪些字段", "列结构")) {
            return AgentIntent.SCHEMA;
        }
        if (containsAny(lower, "趋势", "时序", "波动", "曲线", "每小时", "每分钟", "走势图")) {
            return AgentIntent.TIMESERIES;
        }
        if ("clickhouse".equalsIgnoreCase(datasourceType)
                && containsAny(lower, "多少条", "多少", "统计", "总数", "数量", "汇总", "排行", "top", "分组", "占比", "平均", "avg", "sum", "max", "min")) {
            return AgentIntent.TEXT2SQL;
        }
        return AgentIntent.LOGS;
    }

    private String enrichMessageWithHistory(String message, List<AgentChatMessage> history) {
        if (StringUtils.isBlank(message) || !looksContextDependent(message)) {
            return message;
        }

        String lastUserMessage = latestUserMessage(history);
        if (StringUtils.isBlank(lastUserMessage)) {
            return message;
        }
        return lastUserMessage + " " + message;
    }

    private boolean looksContextDependent(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        if (message.length() <= 12) {
            return true;
        }
        return containsAny(lower, "再", "继续", "那", "这个", "这些", "呢", "同样", "也", "改成", "换成", "刚才", "上一个");
    }

    private String latestUserMessage(List<AgentChatMessage> history) {
        if (CollectionUtils.isEmpty(history)) {
            return null;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            AgentChatMessage message = history.get(i);
            if (message != null
                    && StringUtils.equalsIgnoreCase(message.getRole(), "user")
                    && StringUtils.isNotBlank(message.getContent())) {
                return AgentToolSupport.normalizeText(message.getContent());
            }
        }
        return null;
    }

    private String extractKeyword(String message) {
        Matcher quotedMatcher = QUOTED_KEYWORD_PATTERN.matcher(message);
        if (quotedMatcher.find()) {
            return quotedMatcher.group(1).trim();
        }

        Matcher explicitMatcher = EXPLICIT_KEYWORD_PATTERN.matcher(message);
        if (explicitMatcher.find()) {
            return explicitMatcher.group(1).trim();
        }

        String normalized = message
                .replaceAll("最近\\s*[0-9一二两三四五六七八九十半]+\\s*(分钟|小时|天|周)", " ")
                .replaceAll("(今天|昨天|趋势|时序|波动|曲线|走势图|字段|表结构|schema|日志|查一下|查看|查询|搜索|帮我|帮忙|看看|一下|哪些|有没有|最近|错误|异常|告警|警告)", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (StringUtils.isBlank(normalized)) {
            return null;
        }

        return Arrays.stream(normalized.split("\\s+"))
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .filter(token -> !containsAny(token.toLowerCase(Locale.ROOT), "小时", "分钟", "天", "周"))
                .findFirst()
                .orElse(null);
    }

    private String extractSeverity(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "error", "错误", "异常")) {
            return "error";
        }
        if (containsAny(lower, "warn", "告警", "警告")) {
            return "warn";
        }
        if (containsAny(lower, "info", "信息")) {
            return "info";
        }
        if (containsAny(lower, "debug", "调试")) {
            return "debug";
        }
        return null;
    }

    private boolean containsAny(String text, String... keywords) {
        return Arrays.stream(keywords).anyMatch(text::contains);
    }

    @Getter
    static final class FallbackIntentDecision {

        private final AgentIntent intent;
        private final String effectiveMessage;
        private final String keyword;
        private final String severity;

        private FallbackIntentDecision(AgentIntent intent, String effectiveMessage, String keyword, String severity) {
            this.intent = intent;
            this.effectiveMessage = effectiveMessage;
            this.keyword = keyword;
            this.severity = severity;
        }
    }
}
