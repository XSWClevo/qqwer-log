package cn.mw.loganalysis.agent.vectorplan;

import cn.mw.loganalysis.agent.support.AgentToolSupport;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CreateLogParserSlotTextSupport {

    private static final Pattern FENCED_BLOCK_PATTERN = Pattern.compile("```(?:\\w+)?\\s*([\\s\\S]*?)```");
    private static final Pattern TIMESTAMP_LOG_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:[,\\.]\\d+)?[\\s\\S]*)");
    private static final Pattern JSON_LOG_PATTERN = Pattern.compile("(\\{\\s*\".+\"\\s*:[\\s\\S]*})");
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("(?:表名|tableName|table|写入表|目标表)\\s*[:：=]?\\s*([A-Za-z_][A-Za-z0-9_]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPONENT_PREFIX_PATTERN = Pattern.compile("(?:组件前缀|componentPrefix|prefix)\\s*[:：=]?\\s*([A-Za-z_][A-Za-z0-9_]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REGEX_PATTERN = Pattern.compile("(?:regexPattern|regex|正则)\\s*[:：=]\\s*([^\\n]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile("(?:文件路径|日志路径|路径|path|include)\\s*[:：=]?\\s*([^\\s，,；;]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("(?:监听地址|接收地址|地址|address|listen)\\s*[:：=]?\\s*([^\\s，,；;]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HOST_PORT_PATTERN = Pattern.compile("((?:\\d{1,3}\\.){3}\\d{1,3}|0\\.0\\.0\\.0|127\\.0\\.0\\.1|localhost|[A-Za-z0-9_.-]+):(\\d{2,5})");
    private static final Pattern PORT_PATTERN = Pattern.compile("(?:端口|port)\\s*[:：=]?\\s*(\\d{2,5})", Pattern.CASE_INSENSITIVE);
    private static final Pattern KAFKA_BOOTSTRAP_PATTERN = Pattern.compile("(?:bootstrap(?:_servers)?|bootstrap servers|brokers?|kafka地址|kafka 地址)\\s*[:：=]?\\s*([^\\s，；;]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern KAFKA_TOPIC_PATTERN = Pattern.compile("(?:topics?|主题)\\s*[:：=]?\\s*([A-Za-z0-9_.-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern KAFKA_GROUP_PATTERN = Pattern.compile("(?:group_id|group id|消费组|group)\\s*[:：=]?\\s*([A-Za-z0-9_.-]+)", Pattern.CASE_INSENSITIVE);

    /**
     * 工具类不允许实例化。
     */
    private CreateLogParserSlotTextSupport() {
    }

    /**
     * 判断文本是否包含创建日志解析任务的触发词。
     */
    static boolean containsCreateParserIntent(String message) {
        String lower = StringUtils.lowerCase(message, Locale.ROOT);
        return AgentToolSupport.containsAny(lower,
                "创建日志解析", "日志解析", "解析这个日志", "生成正则", "解析规则",
                "接入日志", "采集日志", "入库", "建表", "vector", "remap", "sink", "日志样本");
    }

    /**
     * 从用户消息中提取原始日志样本。
     */
    static String extractLogSample(String rawMessage) {
        String value = StringUtils.trimToEmpty(rawMessage);
        if (StringUtils.isBlank(value)) {
            return "";
        }

        Matcher fencedMatcher = FENCED_BLOCK_PATTERN.matcher(value);
        if (fencedMatcher.find()) {
            return StringUtils.trimToEmpty(fencedMatcher.group(1));
        }

        Matcher timestampMatcher = TIMESTAMP_LOG_PATTERN.matcher(value);
        if (timestampMatcher.find()) {
            return StringUtils.trimToEmpty(timestampMatcher.group(1));
        }

        Matcher jsonMatcher = JSON_LOG_PATTERN.matcher(value);
        if (jsonMatcher.find()) {
            return StringUtils.trimToEmpty(jsonMatcher.group(1));
        }

        Matcher labeledMatcher = Pattern.compile("(?:日志样本|logSample|sample)\\s*[:：]\\s*([\\s\\S]+)", Pattern.CASE_INSENSITIVE)
                .matcher(value);
        return labeledMatcher.find() ? StringUtils.trimToEmpty(labeledMatcher.group(1)) : "";
    }

    /**
     * 从用户消息中提取目标表名。
     */
    static String extractTableName(String message) {
        Matcher matcher = TABLE_NAME_PATTERN.matcher(StringUtils.defaultString(message));
        return matcher.find() ? sanitizeIdentifier(matcher.group(1)) : "";
    }

    /**
     * 从用户消息中提取 Vector 组件命名前缀。
     */
    static String extractComponentPrefix(String message) {
        Matcher matcher = COMPONENT_PREFIX_PATTERN.matcher(StringUtils.defaultString(message));
        return matcher.find() ? sanitizeIdentifier(matcher.group(1)) : "";
    }

    /**
     * 从用户消息中提取显式提供的正则表达式。
     */
    static String extractRegexPattern(String message) {
        Matcher matcher = REGEX_PATTERN.matcher(StringUtils.defaultString(message));
        return matcher.find() ? StringUtils.trimToEmpty(matcher.group(1)) : "";
    }

    /**
     * 从用户消息中识别日志来源类型。
     */
    static String extractSourceType(String message) {
        String lower = StringUtils.lowerCase(StringUtils.defaultString(message), Locale.ROOT);
        if (AgentToolSupport.containsAny(lower, "kafka", "bootstrap", "topic", "主题")) {
            return "kafka";
        }
        if (AgentToolSupport.containsAny(lower, "socket", "套接字")) {
            return "socket";
        }
        if (AgentToolSupport.containsAny(lower, "syslog", "514", "rfc5424")) {
            return "syslog";
        }
        if (AgentToolSupport.containsAny(lower, "文件", "file", "路径", "本地日志")) {
            return "file";
        }
        return "";
    }

    /**
     * 从用户消息中提取 file source 的文件路径。
     */
    static String extractFilePath(String message) {
        Matcher labeled = FILE_PATH_PATTERN.matcher(StringUtils.defaultString(message));
        if (labeled.find()) {
            return stripTrailingPunctuation(labeled.group(1));
        }
        Matcher absolute = Pattern.compile("(/[^\\s，,；;]+)").matcher(StringUtils.defaultString(message));
        return absolute.find() ? stripTrailingPunctuation(absolute.group(1)) : "";
    }

    /**
     * 从用户消息中提取 file source 的读取起点。
     */
    static String extractReadFrom(String message) {
        String lower = StringUtils.lowerCase(StringUtils.defaultString(message), Locale.ROOT);
        if (AgentToolSupport.containsAny(lower, "从末尾", "末尾", "end", "latest")) {
            return "end";
        }
        if (AgentToolSupport.containsAny(lower, "从头", "开头", "beginning", "earliest")) {
            return "beginning";
        }
        return "";
    }

    /**
     * 从用户消息中提取 syslog/socket 的监听协议。
     */
    static String extractProtocol(String message) {
        String lower = StringUtils.lowerCase(StringUtils.defaultString(message), Locale.ROOT);
        if (AgentToolSupport.containsAny(lower, " udp", "udp ", "udp，", "udp,") || lower.startsWith("udp")) {
            return "udp";
        }
        if (AgentToolSupport.containsAny(lower, " tcp", "tcp ", "tcp，", "tcp,") || lower.startsWith("tcp")) {
            return "tcp";
        }
        return "";
    }

    /**
     * 从用户消息中提取 syslog/socket 的监听地址。
     */
    static String extractListenAddress(String message) {
        Matcher labeled = ADDRESS_PATTERN.matcher(StringUtils.defaultString(message));
        if (labeled.find()) {
            return normalizeAddress(labeled.group(1), message);
        }
        Matcher hostPort = HOST_PORT_PATTERN.matcher(StringUtils.defaultString(message));
        if (hostPort.find()) {
            return stripTrailingPunctuation(hostPort.group(1));
        }
        Matcher port = PORT_PATTERN.matcher(StringUtils.defaultString(message));
        return port.find() ? "0.0.0.0:" + port.group(1) : "";
    }

    /**
     * 从用户消息中提取 Kafka bootstrap servers。
     */
    static String extractKafkaBootstrap(String message) {
        Matcher labeled = KAFKA_BOOTSTRAP_PATTERN.matcher(StringUtils.defaultString(message));
        if (labeled.find()) {
            return stripTrailingPunctuation(labeled.group(1));
        }
        Matcher hostPort = HOST_PORT_PATTERN.matcher(StringUtils.defaultString(message));
        return hostPort.find() ? stripTrailingPunctuation(hostPort.group(1)) : "";
    }

    /**
     * 从用户消息中提取 Kafka topic。
     */
    static String extractKafkaTopic(String message) {
        Matcher matcher = KAFKA_TOPIC_PATTERN.matcher(StringUtils.defaultString(message));
        return matcher.find() ? stripTrailingPunctuation(matcher.group(1)) : "";
    }

    /**
     * 从用户消息中提取 Kafka group id。
     */
    static String extractKafkaGroup(String message) {
        Matcher matcher = KAFKA_GROUP_PATTERN.matcher(StringUtils.defaultString(message));
        return matcher.find() ? stripTrailingPunctuation(matcher.group(1)) : "";
    }

    /**
     * 判断用户是否明确允许自动生成表名。
     */
    static boolean asksAutoTableName(String message) {
        String lower = StringUtils.lowerCase(StringUtils.defaultString(message), Locale.ROOT);
        return AgentToolSupport.containsAny(lower, "自动生成表名", "表名自动", "自动建表名", "自动生成一个表");
    }

    /**
     * 将非空槽位值写入目标配置。
     */
    static void putIfNotBlank(Map<String, Object> target, String key, Object value) {
        if (ObjectUtils.isNotEmpty(value) && StringUtils.isNotBlank(String.valueOf(value))) {
            target.put(key, value);
        }
    }

    /**
     * 补齐只有主机没有端口的监听地址。
     */
    private static String normalizeAddress(String address, String message) {
        String normalized = stripTrailingPunctuation(address);
        if (normalized.contains(":")) {
            return normalized;
        }
        Matcher port = PORT_PATTERN.matcher(StringUtils.defaultString(message));
        return port.find() ? normalized + ":" + port.group(1) : normalized;
    }

    /**
     * 将用户输入清洗成安全的标识符。
     */
    private static String sanitizeIdentifier(String value) {
        if (StringUtils.isBlank(value)) {
            return "";
        }
        String normalized = value.trim()
                .replaceAll("[^A-Za-z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (StringUtils.isBlank(normalized)) {
            return "";
        }
        if (!Character.isLetter(normalized.charAt(0)) && normalized.charAt(0) != '_') {
            normalized = "t_" + normalized;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    /**
     * 去掉槽位值末尾常见标点。
     */
    private static String stripTrailingPunctuation(String value) {
        return StringUtils.trimToEmpty(value).replaceAll("[，,；;。]+$", "");
    }
}
