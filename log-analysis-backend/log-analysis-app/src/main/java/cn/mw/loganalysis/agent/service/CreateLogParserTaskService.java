package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.dto.AgentChatRequest;
import cn.mw.loganalysis.agent.dto.AgentResult;
import cn.mw.loganalysis.vector.entity.ConfigComponent;
import cn.mw.loganalysis.vector.service.ConfigComponentService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * “创建日志解析/Vector 组件计划”的任务帧与槽位补齐服务。
 */
@Component
@RequiredArgsConstructor
public class CreateLogParserTaskService {

    private static final DateTimeFormatter TABLE_SUFFIX_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Pattern FENCED_BLOCK_PATTERN = Pattern.compile("```(?:\\w+)?\\s*([\\s\\S]*?)```");
    private static final Pattern TIMESTAMP_LOG_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:[,\\.]\\d+)?[\\s\\S]*)");
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("(?:表名|tableName|table|写入表|目标表)\\s*[:：=]?\\s*([A-Za-z_][A-Za-z0-9_]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPONENT_PREFIX_PATTERN = Pattern.compile("(?:组件前缀|componentPrefix|prefix)\\s*[:：=]?\\s*([A-Za-z_][A-Za-z0-9_]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REGEX_PATTERN = Pattern.compile("(?:regexPattern|regex|正则)\\s*[:：=]\\s*([^\\n]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_LOG_PATTERN = Pattern.compile("(\\{\\s*\".+\"\\s*:[\\s\\S]*})");
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile("(?:文件路径|日志路径|路径|path|include)\\s*[:：=]?\\s*([^\\s，,；;]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("(?:监听地址|接收地址|地址|address|listen)\\s*[:：=]?\\s*([^\\s，,；;]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HOST_PORT_PATTERN = Pattern.compile("((?:\\d{1,3}\\.){3}\\d{1,3}|0\\.0\\.0\\.0|127\\.0\\.0\\.1|localhost|[A-Za-z0-9_.-]+):(\\d{2,5})");
    private static final Pattern PORT_PATTERN = Pattern.compile("(?:端口|port)\\s*[:：=]?\\s*(\\d{2,5})", Pattern.CASE_INSENSITIVE);
    private static final Pattern KAFKA_BOOTSTRAP_PATTERN = Pattern.compile("(?:bootstrap(?:_servers)?|bootstrap servers|brokers?|kafka地址|kafka 地址)\\s*[:：=]?\\s*([^\\s，；;]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern KAFKA_TOPIC_PATTERN = Pattern.compile("(?:topics?|主题)\\s*[:：=]?\\s*([A-Za-z0-9_.-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern KAFKA_GROUP_PATTERN = Pattern.compile("(?:group_id|group id|消费组|group)\\s*[:：=]?\\s*([A-Za-z0-9_.-]+)", Pattern.CASE_INSENSITIVE);
    private static final String DEFAULT_PARSE_METHOD = "parse_regex";

    private final ConfigComponentService configComponentService;
    private final AgentToolFacade toolFacade;

    private final Cache<String, AgentTaskFrame> frameCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(Duration.ofMinutes(45))
            .build();

    public boolean shouldContinueSlotFilling(AgentChatRequest request, Long userId) {
        if (request == null || StringUtils.isBlank(request.getSessionId()) || StringUtils.isBlank(request.getMessage())) {
            return false;
        }
        if (!hasOpenTask(request.getSessionId(), userId)) {
            return false;
        }
        return looksLikeSlotFilling(request.getMessage());
    }

    public AgentToolPayload handle(AgentExecutionContext context,
                                   AgentChatRequest request,
                                   Long userId,
                                   String sessionId,
                                   ConfigComponent currentSink) {
        long startedAt = System.currentTimeMillis();
        AgentTaskFrame frame = loadOrCreateFrame(userId, sessionId);
        mergeSlots(frame, request.getMessage(), currentSink);
        List<String> missingSlots = resolveMissingSlots(frame);
        frame.setMissingSlots(missingSlots);
        frame.setUpdatedAt(LocalDateTime.now());

        if (CollectionUtils.isEmpty(missingSlots)) {
            frame.setStatus(AgentTaskStatus.READY_TO_PREVIEW);
            frame.setNextAction("preview_vector_components");
            frameCache.put(cacheKey(userId, sessionId), frame);
            AgentExecutionContext previewContext = resolvePreviewContext(context, frame, userId, sessionId);
            AgentExecutionContextHolder.set(previewContext);
            AgentToolPayload payload = toolFacade.previewVectorComponents(
                    frame.getLogSample(),
                    frame.getTargetDatasourceId(),
                    frame.getTableName(),
                    frame.getRegexPattern(),
                    frame.getSourceType(),
                    frame.getSourceConfig()
            );
            frame.setStatus(AgentTaskStatus.WAITING_CONFIRM);
            frame.setNextAction("wait_user_confirm");
            frameCache.put(cacheKey(userId, sessionId), frame);
            return payload;
        }

        frame.setStatus(AgentTaskStatus.SLOT_FILLING);
        frame.setNextAction("ask_user");
        frameCache.put(cacheKey(userId, sessionId), frame);
        return buildRequirementsPayload(context, frame, System.currentTimeMillis() - startedAt);
    }

    private AgentTaskFrame loadOrCreateFrame(Long userId, String sessionId) {
        String key = cacheKey(userId, sessionId);
        AgentTaskFrame existing = frameCache.getIfPresent(key);
        if (existing != null
                && AgentIntent.CREATE_LOG_PARSER.equals(existing.getIntent())
                && !AgentTaskStatus.COMMITTED.equals(existing.getStatus())) {
            return existing;
        }
        return AgentTaskFrame.builder()
                .taskId(UUID.randomUUID().toString())
                .userId(userId)
                .sessionId(sessionId)
                .intent(AgentIntent.CREATE_LOG_PARSER)
                .status(AgentTaskStatus.INTENT_DETECTED)
                .parseMethod(DEFAULT_PARSE_METHOD)
                .confirmCommit(false)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private void mergeSlots(AgentTaskFrame frame, String rawMessage, ConfigComponent currentSink) {
        String message = AgentToolSupport.normalizeText(rawMessage);
        if (StringUtils.isBlank(frame.getLogSample()) || containsCreateParserIntent(message)) {
            String logSample = extractLogSample(message);
            if (StringUtils.isNotBlank(logSample)) {
                frame.setLogSample(logSample);
            }
        }

        String tableName = extractTableName(message);
        if (StringUtils.isNotBlank(tableName)) {
            frame.setTableName(tableName);
            if (StringUtils.isBlank(frame.getComponentPrefix())) {
                frame.setComponentPrefix(tableName);
            }
        } else if (StringUtils.isBlank(frame.getTableName()) && asksAutoTableName(message)) {
            String generatedTableName = "agent_logs_" + LocalDateTime.now().format(TABLE_SUFFIX_FORMATTER);
            frame.setTableName(generatedTableName);
            frame.setComponentPrefix(generatedTableName);
        }

        String componentPrefix = extractComponentPrefix(message);
        if (StringUtils.isNotBlank(componentPrefix)) {
            frame.setComponentPrefix(componentPrefix);
        }

        String sourceType = extractSourceType(message);
        if (StringUtils.isNotBlank(sourceType)) {
            if (StringUtils.isNotBlank(frame.getSourceType()) && !StringUtils.equals(frame.getSourceType(), sourceType)) {
                frame.setSourceConfig(new LinkedHashMap<>());
            }
            frame.setSourceType(sourceType);
        }
        mergeSourceConfig(frame, message);

        String regexPattern = extractRegexPattern(message);
        if (StringUtils.isNotBlank(regexPattern)) {
            frame.setRegexPattern(regexPattern);
        }

        if (currentSink != null && StringUtils.equalsIgnoreCase(currentSink.getVectorType(), "clickhouse")) {
            frame.setTargetSinkId(currentSink.getId());
            frame.setTargetDatasourceId(currentSink.getDatasourceId());
            frame.setDatasourceName(StringUtils.defaultIfBlank(currentSink.getDisplayName(), currentSink.getName()));
        }
    }

    private List<String> resolveMissingSlots(AgentTaskFrame frame) {
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

    private AgentToolPayload buildRequirementsPayload(AgentExecutionContext context, AgentTaskFrame frame, long durationMs) {
        Map<String, Object> filledSlots = new LinkedHashMap<>();
        putIfNotBlank(filledSlots, "logSample", frame.getLogSample());
        putIfNotBlank(filledSlots, "targetSinkId", frame.getTargetSinkId());
        putIfNotBlank(filledSlots, "targetDatasourceId", frame.getTargetDatasourceId());
        putIfNotBlank(filledSlots, "tableName", frame.getTableName());
        putIfNotBlank(filledSlots, "componentPrefix", frame.getComponentPrefix());
        putIfNotBlank(filledSlots, "sourceType", frame.getSourceType());
        if (frame.getSourceConfig() != null && !frame.getSourceConfig().isEmpty()) {
            filledSlots.put("sourceConfig", frame.getSourceConfig());
        }
        putIfNotBlank(filledSlots, "regexPattern", frame.getRegexPattern());
        filledSlots.put("parseMethod", StringUtils.defaultIfBlank(frame.getParseMethod(), DEFAULT_PARSE_METHOD));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("taskId", frame.getTaskId());
        summary.put("status", frame.getStatus().name());
        summary.put("filledSlots", filledSlots);
        summary.put("missingSlots", frame.getMissingSlots());
        summary.put("sourceTypeOptions", buildSourceTypeOptions());
        summary.put("sourceFields", buildSourceFields(frame.getSourceType()));
        summary.put("sourceConfig", ObjectUtils.defaultIfNull(frame.getSourceConfig(), Map.of()));
        summary.put("examples", buildRequirementExamples(frame));
        summary.put("cardMeaning", "这张卡表示助手已经识别出创建日志解析任务，但仍在等待你补齐创建 Source、入库和部署前所需的信息；补齐前不会建表、不会写组件、不会部署。");
        summary.put("nextAction", frame.getNextAction());

        List<String> warnings = new ArrayList<>();
        if (frame.getMissingSlots().contains("targetDatasource")) {
            warnings.add("需要确认目标 ClickHouse Sink/数据源后才能生成入库计划。");
        }
        if (StringUtils.isNotBlank(frame.getTargetSinkId())) {
            warnings.add("已识别当前选中的 ClickHouse Sink，预览前仍会作为目标写入位置展示给你确认。");
        }
        if (frame.getMissingSlots().stream().anyMatch(slot -> slot.startsWith("source."))) {
            warnings.add("已选择日志来源类型，但还需要补齐该来源的连接参数，例如文件路径、监听地址端口或 Kafka topic。");
        }

        AgentResult result = AgentResult.builder()
                .type("vector_component_requirements")
                .success(true)
                .logSample(frame.getLogSample())
                .datasourceId(frame.getTargetDatasourceId())
                .datasourceName(frame.getDatasourceName())
                .tableName(frame.getTableName())
                .summary(summary)
                .warnings(warnings)
                .build();

        return AgentToolPayload.builder()
                .toolName("collect_vector_component_requirements")
                .toolLabel("补齐 Vector 创建信息")
                .intent("vector_component_requirements")
                .summary(buildRequirementAnswer(context, frame))
                .durationMs(durationMs)
                .result(result)
                .build();
    }

    private String buildRequirementAnswer(AgentExecutionContext context, AgentTaskFrame frame) {
        List<String> questions = new ArrayList<>();
        if (frame.getMissingSlots().contains("targetDatasource")) {
            questions.add("目标存到哪个 ClickHouse Sink/数据源");
        }
        if (frame.getMissingSlots().contains("tableName")) {
            questions.add("目标表名或是否允许自动生成表名");
        }
        if (frame.getMissingSlots().contains("sourceType")) {
            questions.add("日志来源是 file、syslog、socket，还是 kafka");
        }
        questions.addAll(frame.getMissingSlots().stream()
                .filter(slot -> slot.startsWith("source."))
                .map(this::formatMissingSourceSlot)
                .toList());
        if (frame.getMissingSlots().contains("logSample")) {
            questions.add("一条原始日志样本");
        }

        StringBuilder answer = new StringBuilder("我理解你要基于日志样本创建解析规则和 Vector Remap/Sink 组件计划。");
        if (StringUtils.isNotBlank(frame.getLogSample())) {
            answer.append("我已识别日志样本：").append(frame.getLogSample()).append("。");
        }
        if (StringUtils.isNotBlank(frame.getDatasourceName())) {
            answer.append("目标候选为当前选中的 ").append(frame.getDatasourceName()).append("。");
        } else if (context != null && StringUtils.isNotBlank(context.datasourceName())) {
            answer.append("当前选择的数据源不是可用的 ClickHouse Sink，需要重新确认目标。");
        }
        answer.append("还需要确认：").append(String.join("、", questions)).append("。");
        answer.append("确认后我会先生成正则、VRL、字段、DDL 和 Source/Remap/Sink 编排预览，不会直接建表、写组件或部署。");
        return answer.toString();
    }

    private AgentExecutionContext resolvePreviewContext(AgentExecutionContext context,
                                                        AgentTaskFrame frame,
                                                        Long userId,
                                                        String sessionId) {
        if (context != null && StringUtils.isNotBlank(context.datasourceId())) {
            return context;
        }
        return new AgentExecutionContext(
                frame.getTargetSinkId(),
                frame.getDatasourceName(),
                "clickhouse",
                userId,
                sessionId
        );
    }

    private boolean hasOpenTask(String sessionId, Long userId) {
        AgentTaskFrame frame = frameCache.getIfPresent(cacheKey(userId, sessionId));
        return frame != null
                && AgentIntent.CREATE_LOG_PARSER.equals(frame.getIntent())
                && (AgentTaskStatus.INTENT_DETECTED.equals(frame.getStatus())
                || AgentTaskStatus.SLOT_FILLING.equals(frame.getStatus()));
    }

    private boolean looksLikeSlotFilling(String message) {
        String lower = StringUtils.lowerCase(AgentToolSupport.normalizeText(message), Locale.ROOT);
        if (StringUtils.isBlank(lower)) {
            return false;
        }
        if (AgentToolSupport.containsAny(lower, "查询", "查看", "搜索", "最近")
                && !AgentToolSupport.containsAny(lower, "表名", "来源", "source", "自动生成", "路径", "地址", "端口", "topic", "bootstrap")) {
            return false;
        }
        return AgentToolSupport.containsAny(lower,
                "表名", "tablename", "目标表", "写入表", "来源", "source", "当前数据源",
                "clickhouse", "sink", "文件", "file", "syslog", "socket", "kafka", "topic",
                "bootstrap", "路径", "path", "地址", "address", "监听", "端口", "port",
                "已有 source", "已有source", "自动生成", "存", "写入", "入库");
    }

    private boolean containsCreateParserIntent(String message) {
        String lower = StringUtils.lowerCase(message, Locale.ROOT);
        return AgentToolSupport.containsAny(lower,
                "创建日志解析", "日志解析", "解析这个日志", "生成正则", "解析规则",
                "接入日志", "采集日志", "入库", "建表", "vector", "remap", "sink", "日志样本");
    }

    private String extractLogSample(String rawMessage) {
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
        if (labeledMatcher.find()) {
            return StringUtils.trimToEmpty(labeledMatcher.group(1));
        }
        return "";
    }

    private String extractTableName(String message) {
        Matcher matcher = TABLE_NAME_PATTERN.matcher(StringUtils.defaultString(message));
        if (!matcher.find()) {
            return "";
        }
        return sanitizeIdentifier(matcher.group(1));
    }

    private String extractComponentPrefix(String message) {
        Matcher matcher = COMPONENT_PREFIX_PATTERN.matcher(StringUtils.defaultString(message));
        if (!matcher.find()) {
            return "";
        }
        return sanitizeIdentifier(matcher.group(1));
    }

    private String extractRegexPattern(String message) {
        Matcher matcher = REGEX_PATTERN.matcher(StringUtils.defaultString(message));
        if (!matcher.find()) {
            return "";
        }
        return StringUtils.trimToEmpty(matcher.group(1));
    }

    private String extractSourceType(String message) {
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

    private boolean isSupportedSourceType(String sourceType) {
        return StringUtils.equalsAny(sourceType, "file", "syslog", "socket", "kafka");
    }

    private void mergeSourceConfig(AgentTaskFrame frame, String message) {
        if (StringUtils.isBlank(frame.getSourceType())) {
            return;
        }

        Map<String, Object> config = new LinkedHashMap<>(
                ObjectUtils.defaultIfNull(frame.getSourceConfig(), Map.of())
        );
        String sourceType = frame.getSourceType();
        if (StringUtils.equals(sourceType, "file")) {
            putIfNotBlank(config, "include", extractFilePath(message));
            String readFrom = extractReadFrom(message);
            if (StringUtils.isNotBlank(readFrom)) {
                config.put("read_from", readFrom);
            } else {
                config.putIfAbsent("read_from", "beginning");
            }
        } else if (StringUtils.equalsAny(sourceType, "syslog", "socket")) {
            putIfNotBlank(config, "syslog_mode", extractProtocol(message));
            putIfNotBlank(config, "syslog_address", extractListenAddress(message));
        } else if (StringUtils.equals(sourceType, "kafka")) {
            putIfNotBlank(config, "bootstrap_servers", extractKafkaBootstrap(message));
            putIfNotBlank(config, "topics", extractKafkaTopic(message));
            putIfNotBlank(config, "group_id", extractKafkaGroup(message));
        }
        frame.setSourceConfig(config);
    }

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

    private boolean isBlankConfigValue(Object value) {
        return ObjectUtils.isEmpty(value) || StringUtils.isBlank(String.valueOf(value));
    }

    private List<Map<String, Object>> buildSourceTypeOptions() {
        return List.of(
                sourceOption("file", "文件日志", "读取主机上的本地文件，必须提供 include 路径，例如 /var/log/app/*.log"),
                sourceOption("syslog", "Syslog", "监听 UDP/TCP syslog，必须提供协议和监听地址，例如 udp 0.0.0.0:514"),
                sourceOption("socket", "Socket", "监听普通 socket 文本流，必须提供协议和监听地址，例如 tcp 0.0.0.0:9000"),
                sourceOption("kafka", "Kafka", "从 Kafka 消费日志，必须提供 bootstrap servers 和 topic")
        );
    }

    private Map<String, Object> sourceOption(String value, String label, String description) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("value", value);
        option.put("label", label);
        option.put("description", description);
        option.put("requiredFields", buildSourceFields(value));
        return option;
    }

    private List<Map<String, Object>> buildSourceFields(String sourceType) {
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

    private Map<String, Object> sourceField(String key, String label, String help) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("key", key);
        field.put("label", label);
        field.put("help", help);
        return field;
    }

    private List<String> buildRequirementExamples(AgentTaskFrame frame) {
        String tableName = StringUtils.defaultIfBlank(frame.getTableName(), "app_text_to_sql_logs");
        return List.of(
                "存当前数据源，表名 " + tableName + "，来源 file，路径 /var/log/app/*.log",
                "存当前数据源，表名 " + tableName + "，来源 syslog，udp 0.0.0.0:514",
                "存当前数据源，表名 " + tableName + "，来源 socket，tcp 0.0.0.0:9000",
                "存当前数据源，表名 " + tableName + "，来源 kafka，bootstrap localhost:9092，topic app-logs"
        );
    }

    private String formatMissingSourceSlot(String slot) {
        return switch (slot) {
            case "source.include" -> "文件日志路径，例如 /var/log/app/*.log";
            case "source.syslog_mode" -> "监听协议 tcp 或 udp";
            case "source.syslog_address" -> "监听地址和端口，例如 0.0.0.0:514";
            case "source.bootstrap_servers" -> "Kafka bootstrap servers，例如 localhost:9092";
            case "source.topics" -> "Kafka topic，例如 app-logs";
            default -> slot;
        };
    }

    private String extractFilePath(String message) {
        Matcher labeled = FILE_PATH_PATTERN.matcher(StringUtils.defaultString(message));
        if (labeled.find()) {
            return stripTrailingPunctuation(labeled.group(1));
        }
        Matcher absolute = Pattern.compile("(/[^\\s，,；;]+)").matcher(StringUtils.defaultString(message));
        if (absolute.find()) {
            return stripTrailingPunctuation(absolute.group(1));
        }
        return "";
    }

    private String extractReadFrom(String message) {
        String lower = StringUtils.lowerCase(StringUtils.defaultString(message), Locale.ROOT);
        if (AgentToolSupport.containsAny(lower, "从末尾", "末尾", "end", "latest")) {
            return "end";
        }
        if (AgentToolSupport.containsAny(lower, "从头", "开头", "beginning", "earliest")) {
            return "beginning";
        }
        return "";
    }

    private String extractProtocol(String message) {
        String lower = StringUtils.lowerCase(StringUtils.defaultString(message), Locale.ROOT);
        if (AgentToolSupport.containsAny(lower, " udp", "udp ", "udp，", "udp,")) {
            return "udp";
        }
        if (AgentToolSupport.containsAny(lower, " tcp", "tcp ", "tcp，", "tcp,")) {
            return "tcp";
        }
        if (lower.startsWith("udp")) {
            return "udp";
        }
        if (lower.startsWith("tcp")) {
            return "tcp";
        }
        return "";
    }

    private String extractListenAddress(String message) {
        Matcher labeled = ADDRESS_PATTERN.matcher(StringUtils.defaultString(message));
        if (labeled.find()) {
            return normalizeAddress(labeled.group(1), message);
        }
        Matcher hostPort = HOST_PORT_PATTERN.matcher(StringUtils.defaultString(message));
        if (hostPort.find()) {
            return stripTrailingPunctuation(hostPort.group(1));
        }
        Matcher port = PORT_PATTERN.matcher(StringUtils.defaultString(message));
        if (port.find()) {
            return "0.0.0.0:" + port.group(1);
        }
        return "";
    }

    private String normalizeAddress(String address, String message) {
        String normalized = stripTrailingPunctuation(address);
        if (normalized.contains(":")) {
            return normalized;
        }
        Matcher port = PORT_PATTERN.matcher(StringUtils.defaultString(message));
        if (port.find()) {
            return normalized + ":" + port.group(1);
        }
        return normalized;
    }

    private String extractKafkaBootstrap(String message) {
        Matcher labeled = KAFKA_BOOTSTRAP_PATTERN.matcher(StringUtils.defaultString(message));
        if (labeled.find()) {
            return stripTrailingPunctuation(labeled.group(1));
        }
        Matcher hostPort = HOST_PORT_PATTERN.matcher(StringUtils.defaultString(message));
        if (hostPort.find()) {
            return stripTrailingPunctuation(hostPort.group(1));
        }
        return "";
    }

    private String extractKafkaTopic(String message) {
        Matcher matcher = KAFKA_TOPIC_PATTERN.matcher(StringUtils.defaultString(message));
        return matcher.find() ? stripTrailingPunctuation(matcher.group(1)) : "";
    }

    private String extractKafkaGroup(String message) {
        Matcher matcher = KAFKA_GROUP_PATTERN.matcher(StringUtils.defaultString(message));
        return matcher.find() ? stripTrailingPunctuation(matcher.group(1)) : "";
    }

    private String stripTrailingPunctuation(String value) {
        return StringUtils.trimToEmpty(value).replaceAll("[，,；;。]+$", "");
    }

    private boolean asksAutoTableName(String message) {
        String lower = StringUtils.lowerCase(StringUtils.defaultString(message), Locale.ROOT);
        return AgentToolSupport.containsAny(lower, "自动生成表名", "表名自动", "自动建表名", "自动生成一个表");
    }

    private String sanitizeIdentifier(String value) {
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

    private void putIfNotBlank(Map<String, Object> target, String key, Object value) {
        if (ObjectUtils.isNotEmpty(value) && StringUtils.isNotBlank(String.valueOf(value))) {
            target.put(key, value);
        }
    }

    private String cacheKey(Long userId, String sessionId) {
        return ObjectUtils.defaultIfNull(userId, 0L) + ":" + StringUtils.defaultString(sessionId);
    }
}
