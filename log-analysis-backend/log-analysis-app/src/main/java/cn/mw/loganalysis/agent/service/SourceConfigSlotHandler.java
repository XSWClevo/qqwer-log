package cn.mw.loganalysis.agent.service;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SourceConfigSlotHandler implements CreateLogParserSlotHandler {

    private final List<CreateLogParserSourceConfigExtractor> extractors;

    /**
     * 收集各 Source 类型配置提取器并按 Ordered 顺序排序。
     */
    public SourceConfigSlotHandler(List<CreateLogParserSourceConfigExtractor> extractors) {
        this.extractors = new ArrayList<>(extractors);
        AnnotationAwareOrderComparator.sort(this.extractors);
    }

    /**
     * 在 Source 类型识别后提取 Source 连接配置。
     */
    @Override
    public int getOrder() {
        return 50;
    }

    /**
     * 只有已识别 Source 类型时才提取 Source 配置。
     */
    @Override
    public boolean supports(CreateLogParserSlotContext context) {
        return StringUtils.isNotBlank(context.frame().getSourceType());
    }

    /**
     * 调用匹配的 Source 配置提取器并合并配置。
     */
    @Override
    public void fill(CreateLogParserSlotContext context) {
        Map<String, Object> config = new LinkedHashMap<>(
                ObjectUtils.defaultIfNull(context.frame().getSourceConfig(), Map.of())
        );
        for (CreateLogParserSourceConfigExtractor extractor : extractors) {
            if (extractor.supports(context.frame().getSourceType())) {
                extractor.merge(context.message(), config);
            }
        }
        context.frame().setSourceConfig(config);
    }
}
