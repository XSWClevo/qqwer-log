package cn.mw.loganalysis.agent.service;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CREATE_LOG_PARSER 的槽位完整性策略。
 *
 * 把“缺什么”和“每种 Source 要填什么”集中维护，方便后续扩展 source 类型。
 */
@Component
public class CreateLogParserSlotPolicy {

    /**
     * 根据任务帧内容计算还缺少哪些槽位。
     */
    List<String> resolveMissingSlots(AgentTaskFrame frame) {
        List<String> missing = new ArrayList<>();
        if (StringUtils.isBlank(frame.getTargetSinkId()) || StringUtils.isBlank(frame.getTargetDatasourceId())) {
            missing.add("targetDatasource");
        }
        if (StringUtils.isBlank(frame.getTableName())) {
            missing.add("tableName");
        }
        if (StringUtils.isBlank(frame.getSourceType()) || !isSupportedSourceType(frame.getSourceType())) {
            missing.add("sourceType");
        } else {
            missing.addAll(resolveMissingSourceConfigSlots(frame));
        }
        if (StringUtils.isBlank(frame.getLogSample())) {
            missing.add("logSample");
        }
        return missing;
    }

    /**
     * 构建前端可展示的 Source 类型选项。
     */
    List<Map<String, Object>> buildSourceTypeOptions() {
        return List.of(
                sourceOption("file", "文件日志", "读取主机上的本地文件，必须提供 include 路径，例如 /var/log/app/*.log"),
                sourceOption("syslog", "Syslog", "监听 UDP/TCP syslog，必须提供协议和监听地址，例如 udp 0.0.0.0:514"),
                sourceOption("socket", "Socket", "监听普通 socket 文本流，必须提供协议和监听地址，例如 tcp 0.0.0.0:9000"),
                sourceOption("kafka", "Kafka", "从 Kafka 消费日志，必须提供 bootstrap servers 和 topic")
        );
    }

    /**
     * 构建指定 Source 类型需要填写的配置字段说明。
     */
    List<Map<String, Object>> buildSourceFields(String sourceType) {
        if (StringUtils.equals(sourceType, "file")) {
            return List.of(
                    sourceField("include", "文件路径", "必填，例如 /var/log/app/*.log"),
                    sourceField("read_from", "读取位置", "可选，beginning 或 end，默认 beginning")
            );
        }
        if (StringUtils.equalsAny(sourceType, "syslog", "socket")) {
            return List.of(
                    sourceField("syslog_mode", "协议", "必填，tcp 或 udp"),
                    sourceField("syslog_address", "监听地址", "必填，例如 0.0.0.0:514")
            );
        }
        if (StringUtils.equals(sourceType, "kafka")) {
            return List.of(
                    sourceField("bootstrap_servers", "Bootstrap Servers", "必填，例如 localhost:9092"),
                    sourceField("topics", "Topic", "必填，例如 app-logs"),
                    sourceField("group_id", "Group ID", "可选，例如 vector-consumer")
            );
        }
        return List.of();
    }

    /**
     * 把内部缺失槽位编码转换成用户可理解的描述。
     */
    String formatMissingSourceSlot(String slot) {
        return switch (slot) {
            case "source.include" -> "文件日志路径，例如 /var/log/app/*.log";
            case "source.syslog_mode" -> "监听协议 tcp 或 udp";
            case "source.syslog_address" -> "监听地址和端口，例如 0.0.0.0:514";
            case "source.bootstrap_servers" -> "Kafka bootstrap servers，例如 localhost:9092";
            case "source.topics" -> "Kafka topic，例如 app-logs";
            default -> slot;
        };
    }

    /**
     * 判断 Source 类型是否属于当前支持范围。
     */
    private boolean isSupportedSourceType(String sourceType) {
        return StringUtils.equalsAny(sourceType, "file", "syslog", "socket", "kafka");
    }

    /**
     * 根据 Source 类型检查连接配置中还缺少哪些字段。
     */
    private List<String> resolveMissingSourceConfigSlots(AgentTaskFrame frame) {
        Map<String, Object> config = ObjectUtils.defaultIfNull(frame.getSourceConfig(), Map.of());
        List<String> missing = new ArrayList<>();
        if (StringUtils.equals(frame.getSourceType(), "file")) {
            if (isBlankConfigValue(config.get("include"))) {
                missing.add("source.include");
            }
        } else if (StringUtils.equalsAny(frame.getSourceType(), "syslog", "socket")) {
            if (isBlankConfigValue(config.get("syslog_mode"))) {
                missing.add("source.syslog_mode");
            }
            if (isBlankConfigValue(config.get("syslog_address"))) {
                missing.add("source.syslog_address");
            }
        } else if (StringUtils.equals(frame.getSourceType(), "kafka")) {
            if (isBlankConfigValue(config.get("bootstrap_servers"))) {
                missing.add("source.bootstrap_servers");
            }
            if (isBlankConfigValue(config.get("topics"))) {
                missing.add("source.topics");
            }
        }
        return missing;
    }

    /**
     * 判断配置值是否为空。
     */
    private boolean isBlankConfigValue(Object value) {
        return ObjectUtils.isEmpty(value) || StringUtils.isBlank(String.valueOf(value));
    }

    /**
     * 构建一个 Source 类型选项。
     */
    private Map<String, Object> sourceOption(String value, String label, String description) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("value", value);
        option.put("label", label);
        option.put("description", description);
        option.put("requiredFields", buildSourceFields(value));
        return option;
    }

    /**
     * 构建一个 Source 配置字段说明。
     */
    private Map<String, Object> sourceField(String key, String label, String help) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("key", key);
        field.put("label", label);
        field.put("help", help);
        return field;
    }
}
