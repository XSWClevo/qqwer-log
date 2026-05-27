package cn.mw.loganalysis.agent.service;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AgentIntentTextSupport {

    private static final Pattern QUOTED_KEYWORD_PATTERN = Pattern.compile("[\"“](.+?)[\"”]");
    private static final Pattern EXPLICIT_KEYWORD_PATTERN = Pattern.compile("(?:包含|搜索|查找|关键词|关键字|message包含|message含有)\\s*[:：]?\\s*([\\p{L}\\p{N}._:/-]+)");

    /**
     * 工具类不允许实例化。
     */
    private AgentIntentTextSupport() {
    }

    /**
     * 从日志查询类文本中提取关键词条件。
     */
    static String extractKeyword(String message) {
        Matcher quotedMatcher = QUOTED_KEYWORD_PATTERN.matcher(StringUtils.defaultString(message));
        if (quotedMatcher.find()) {
            return quotedMatcher.group(1).trim();
        }

        Matcher explicitMatcher = EXPLICIT_KEYWORD_PATTERN.matcher(StringUtils.defaultString(message));
        if (explicitMatcher.find()) {
            return explicitMatcher.group(1).trim();
        }

        String normalized = StringUtils.defaultString(message)
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
                .filter(token -> !AgentToolSupport.containsAny(token.toLowerCase(Locale.ROOT), "小时", "分钟", "天", "周"))
                .findFirst()
                .orElse(null);
    }

    /**
     * 从日志查询类文本中提取 severity 条件。
     */
    static String extractSeverity(String message) {
        String lower = StringUtils.lowerCase(message, Locale.ROOT);
        if (AgentToolSupport.containsAny(lower, "error", "错误", "异常")) {
            return "error";
        }
        if (AgentToolSupport.containsAny(lower, "warn", "告警", "警告")) {
            return "warn";
        }
        if (AgentToolSupport.containsAny(lower, "info", "信息")) {
            return "info";
        }
        if (AgentToolSupport.containsAny(lower, "debug", "调试")) {
            return "debug";
        }
        return null;
    }
}
