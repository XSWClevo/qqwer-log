package cn.mw.loganalysis.agent.vectorplan;

import cn.mw.loganalysis.agent.nlu.AgentNluSlots;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CREATE_LOG_PARSER 的模型槽位合并器。
 *
 * 模型 NLU 只给出建议槽位，本类只在任务帧缺字段时补齐；
 * 用户当前轮明确输入的内容仍由 SlotExtractor 在后续覆盖。
 */
@Component
public class CreateLogParserNluSlotsMerger {

    /**
     * 将模型建议槽位安全合并到任务帧。
     */
    void merge(AgentTaskFrame frame, AgentNluSlots slots) {
        if (frame == null || slots == null) {
            return;
        }
        fillIfBlank(frame::getLogSample, frame::setLogSample, slots.getLogSample());
        fillIfBlank(frame::getTargetSinkId, frame::setTargetSinkId, slots.getTargetSinkId());
        fillIfBlank(frame::getTargetDatasourceId, frame::setTargetDatasourceId, slots.getTargetDatasourceId());
        fillIfBlank(frame::getDatasourceName, frame::setDatasourceName, slots.getDatasourceName());
        fillIfBlank(frame::getTableName, frame::setTableName, slots.getTableName());
        fillIfBlank(frame::getComponentPrefix, frame::setComponentPrefix, slots.getComponentPrefix());
        fillIfBlank(frame::getSourceType, frame::setSourceType, normalizeSourceType(slots.getSourceType()));
        fillIfBlank(frame::getRegexPattern, frame::setRegexPattern, slots.getRegexPattern());
        fillIfBlank(frame::getParseMethod, frame::setParseMethod, slots.getParseMethod());
        if (frame.getConfirmCommit() == null && slots.getConfirmCommit() != null) {
            frame.setConfirmCommit(slots.getConfirmCommit());
        }
        mergeSourceConfig(frame, slots.getSourceConfig());
    }

    /**
     * 合并模型提取的 Source 配置，避免覆盖已确认参数。
     */
    private void mergeSourceConfig(AgentTaskFrame frame, Map<String, Object> nluConfig) {
        if (MapUtils.isEmpty(nluConfig)) {
            return;
        }
        Map<String, Object> merged = new LinkedHashMap<>(
                ObjectUtils.defaultIfNull(frame.getSourceConfig(), Map.of())
        );
        nluConfig.forEach((key, value) -> {
            if (StringUtils.isNotBlank(key) && ObjectUtils.isNotEmpty(value) && !merged.containsKey(key)) {
                merged.put(key, value);
            }
        });
        frame.setSourceConfig(merged);
    }

    /**
     * 只在目标字段为空时写入模型建议值。
     */
    private void fillIfBlank(ValueGetter getter, ValueSetter setter, String value) {
        if (StringUtils.isBlank(getter.get()) && StringUtils.isNotBlank(value)) {
            setter.set(StringUtils.trim(value));
        }
    }

    /**
     * 将模型返回的来源类型收敛到后端支持的枚举值。
     */
    private String normalizeSourceType(String sourceType) {
        String normalized = StringUtils.lowerCase(StringUtils.trimToEmpty(sourceType));
        if (StringUtils.equalsAny(normalized, "file", "syslog", "socket", "kafka")) {
            return normalized;
        }
        return null;
    }

    private interface ValueGetter {

        /**
         * 读取任务帧当前值。
         */
        String get();
    }

    private interface ValueSetter {

        /**
         * 写入任务帧槽位值。
         */
        void set(String value);
    }
}
