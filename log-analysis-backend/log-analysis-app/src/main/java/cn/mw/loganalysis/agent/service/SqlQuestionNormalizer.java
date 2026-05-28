package cn.mw.loganalysis.agent.service;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Text2SQL 问题归一化与轻量相似度计算。
 */
@Component
public class SqlQuestionNormalizer {

    /**
     * 去掉时间范围的具体数字，保留查询意图和维度词。
     */
    public String normalize(String question) {
        return StringUtils.lowerCase(AgentToolSupport.normalizeText(question), Locale.ROOT)
                .replaceAll("最近\\s*[0-9一二两三四五六七八九十半]+\\s*(分钟|小时|天|周)", "最近<range>")
                .replaceAll("近\\s*[0-9一二两三四五六七八九十半]+\\s*(分钟|小时|天|周)", "最近<range>")
                .replaceAll("\\d+", "<num>")
                .replaceAll("[，。！？、,.!?;；:：\"“”'`]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * 使用 token Jaccard 相似度做 v1 轻量匹配。
     */
    public double similarity(String left, String right) {
        Set<String> leftTokens = tokens(left);
        Set<String> rightTokens = tokens(right);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return 0D;
        }
        Set<String> intersection = new LinkedHashSet<>(leftTokens);
        intersection.retainAll(rightTokens);
        Set<String> union = new LinkedHashSet<>(leftTokens);
        union.addAll(rightTokens);
        return union.isEmpty() ? 0D : (double) intersection.size() / union.size();
    }

    private Set<String> tokens(String text) {
        return Arrays.stream(StringUtils.defaultString(text).split("\\s+"))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
