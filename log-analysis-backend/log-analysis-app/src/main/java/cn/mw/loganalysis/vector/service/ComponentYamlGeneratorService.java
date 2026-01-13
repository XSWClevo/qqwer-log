package cn.mw.loganalysis.vector.service;

import cn.mw.loganalysis.vector.enums.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

import static cn.mw.loganalysis.vector.constants.VectorConfigConstants.*;

/**
 * 组件 YAML 配置生成服务（优化版）
 * 根据前端传入的 visualConfig 生成 Vector 组件配置
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComponentYamlGeneratorService {

    private final ObjectMapper objectMapper;

    /**
     * 根据 visualConfig 生成组件 YAML 配置
     *
     * @param componentType 组件类型: source, transform, sink
     * @param vectorType    Vector 类型: file, kafka, remap, clickhouse 等
     * @param visualConfig  可视化配置 JSON 字符串
     * @return YAML 配置字符串
     */
    public String generateYaml(String componentType, String vectorType, String visualConfig) {
        try {
            validateInputs(componentType, vectorType, visualConfig);

            JsonNode config = parseVisualConfig(visualConfig);
            Map<String, Object> yamlConfig = buildYamlConfig(componentType, vectorType, config);

            return toYaml(yamlConfig);
        } catch (JsonProcessingException e) {
            log.error("解析 visualConfig JSON 失败: {}", visualConfig, e);
            throw new IllegalArgumentException("配置格式错误: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            log.error("参数验证失败", e);
            throw e;
        } catch (Exception e) {
            log.error("生成 YAML 配置失败", e);
            throw new RuntimeException("生成配置失败: " + e.getMessage(), e);
        }
    }

    /**
     * 验证输入参数
     */
    private void validateInputs(String componentType, String vectorType, String visualConfig) {
        if (componentType == null || componentType.trim().isEmpty()) {
            throw new IllegalArgumentException("组件类型不能为空");
        }
        if (vectorType == null || vectorType.trim().isEmpty()) {
            throw new IllegalArgumentException("Vector 类型不能为空");
        }
        if (visualConfig == null || visualConfig.trim().isEmpty()) {
            throw new IllegalArgumentException("可视化配置不能为空");
        }
    }

    /**
     * 解析可视化配置 JSON
     */
    private JsonNode parseVisualConfig(String visualConfig) throws JsonProcessingException {
        return objectMapper.readTree(visualConfig);
    }

    /**
     * 构建 YAML 配置
     */
    private Map<String, Object> buildYamlConfig(String componentType, String vectorType, JsonNode config) {
        Map<String, Object> yamlConfig = new LinkedHashMap<>();
        yamlConfig.put(FIELD_TYPE, vectorType);

        ComponentType type = ComponentType.fromValue(componentType);
        switch (type) {
            case SOURCE:
                generateSourceConfig(yamlConfig, vectorType, config);
                break;
            case TRANSFORM:
                generateTransformConfig(yamlConfig, vectorType, config);
                break;
            case SINK:
                generateSinkConfig(yamlConfig, vectorType, config);
                break;
        }

        return yamlConfig;
    }

    // ==================== Source 配置生成 ====================

    /**
     * 生成 Source 配置
     */
    protected void generateSourceConfig(Map<String, Object> config, String vectorType, JsonNode visual) {
        SourceType sourceType = SourceType.fromValue(vectorType);

        switch (sourceType) {
            case FILE:
                generateFileSourceConfig(config, visual);
                break;
            case KAFKA:
                generateKafkaSourceConfig(config, visual);
                break;
            case HTTP_SERVER:
                generateHttpServerSourceConfig(config, visual);
                break;
            case SYSLOG:
                generateSyslogSourceConfig(config, visual);
                break;
            case DEMO_LOGS:
                generateDemoLogsSourceConfig(config, visual);
                break;
            case INTERNAL_LOGS:
                // internal_logs 不需要额外配置
                break;
        }
    }

    private void generateFileSourceConfig(Map<String, Object> config, JsonNode visual) {
        putIfNotEmpty(config, FIELD_INCLUDE, toStringList(visual.path(FIELD_INCLUDE).asText()));
        putIfNotEmpty(config, FIELD_EXCLUDE, toStringList(visual.path(FIELD_EXCLUDE).asText()));
        putIfNotEmpty(config, FIELD_READ_FROM, visual.path(FIELD_READ_FROM).asText());
    }

    private void generateKafkaSourceConfig(Map<String, Object> config, JsonNode visual) {
        putIfNotEmpty(config, FIELD_BOOTSTRAP_SERVERS, visual.path(FIELD_BOOTSTRAP_SERVERS).asText());
        putIfNotEmpty(config, FIELD_TOPICS, toStringList(visual.path(FIELD_TOPICS).asText()));
        putIfNotEmpty(config, FIELD_GROUP_ID, visual.path(FIELD_GROUP_ID).asText());
    }

    private void generateHttpServerSourceConfig(Map<String, Object> config, JsonNode visual) {
        putIfNotEmpty(config, FIELD_ADDRESS, visual.path(FIELD_ADDRESS).asText());
        putIfNotEmpty(config, FIELD_PATH, visual.path(FIELD_PATH).asText());
    }

    private void generateSyslogSourceConfig(Map<String, Object> config, JsonNode visual) {
        config.put("mode", getOrDefault(visual, FIELD_SYSLOG_MODE, DEFAULT_SYSLOG_MODE));
        config.put(FIELD_ADDRESS, getOrDefault(visual, FIELD_SYSLOG_ADDRESS, DEFAULT_SYSLOG_ADDRESS));

        List<String> receiveAddresses = jsonArrayToList(visual.path(FIELD_SYSLOG_RECEIVE_ADDRESSES));
        if (!receiveAddresses.isEmpty()) {
            config.put("receive_addresses", receiveAddresses);
        }
    }

    private void generateDemoLogsSourceConfig(Map<String, Object> config, JsonNode visual) {
        config.put("format", getOrDefault(visual, FIELD_DEMO_FORMAT, DEFAULT_SYSLOG_FORMAT));

        String interval = visual.path(FIELD_DEMO_INTERVAL).asText();
        if (!interval.isEmpty()) {
            config.put("interval", Double.parseDouble(interval));
        }

        String count = visual.path(FIELD_DEMO_COUNT).asText();
        if (!count.isEmpty()) {
            try {
                config.put("count", Integer.parseInt(count));
            } catch (NumberFormatException ignored) {
                log.warn("无效的 count 值: {}", count);
            }
        }
    }

    // ==================== Transform 配置生成 ====================

    /**
     * 生成 Transform 配置
     */
    protected void generateTransformConfig(Map<String, Object> config, String vectorType, JsonNode visual) {
        TransformType transformType = TransformType.fromValue(vectorType);

        switch (transformType) {
            case REMAP:
                String source = generateRemapSource(visual);
                if (!source.isEmpty()) {
                    config.put(FIELD_SOURCE, source);
                }
                break;
            case FILTER:
                String condition = generateFilterCondition(visual);
                if (!condition.isEmpty()) {
                    config.put(FIELD_CONDITION, condition);
                }
                break;
            case ROUTE:
                // Route 配置暂不实现
                break;
        }
    }

    /**
     * 生成 Remap VRL 脚本
     */
    protected String generateRemapSource(JsonNode visual) {
        List<String> lines = new ArrayList<>();
        String parseMethodValue = visual.path(FIELD_PARSE_METHOD).asText(ParseMethod.PARSE_JSON.getValue());
        ParseMethod parseMethod = ParseMethod.fromValue(parseMethodValue);
        JsonNode parsedFields = visual.path(FIELD_PARSED_FIELDS);

        // 优先检查是否是自定义 VRL 脚本
        if (parseMethod == ParseMethod.CUSTOM) {
            // 自定义 VRL 脚本，直接使用用户提供的脚本
            addCustomVrlScript(lines, visual);
        } else if (parsedFields.isArray() && !parsedFields.isEmpty()) {
            // 有字段配置的解析流程（非自定义 VRL）
            addSyslogPreParsing(lines);
            addMessageParsing(lines, parseMethod, visual);
            addFieldRenaming(lines, parsedFields);
            addFieldDeletion(lines, parsedFields);
        } else {
            // 默认解析方式（无字段配置）
            addDefaultParsing(lines, parseMethod, visual);
        }

        // 增强选项
        addEnhancementOptions(lines, visual);

        // 添加/删除字段
        addCustomFields(lines, visual);
        removeCustomFields(lines, visual);

        return String.join("\n", lines);
    }

    /**
     * 添加 Syslog 预解析
     */
    private void addSyslogPreParsing(List<String> lines) {
        lines.add(VRL_SYSLOG_PRE_PARSE);
        lines.add("");
    }

    /**
     * 添加消息解析
     */
    private void addMessageParsing(List<String> lines, ParseMethod parseMethod, JsonNode visual) {
        lines.add("# 解析消息内容");
        boolean mergeParsed = false;

        switch (parseMethod) {
            case PARSE_JSON:
                lines.add("parsed = parse_json(target_message) ?? {}");
                mergeParsed = true;
                break;
            case PARSE_SYSLOG:
                lines.add("# 已在预解析中处理");
                lines.add("parsed = syslog_result ?? {}");
                mergeParsed = true;
                break;
            case PARSE_REGEX:
                String regexPattern = visual.path(FIELD_REGEX_PATTERN).asText();
                if (!regexPattern.isEmpty()) {
                    lines.add(String.format("parsed = parse_regex(target_message, r'%s') ?? {}", regexPattern));
                    mergeParsed = true;
                }
                break;
            case PARSE_KEY_VALUE:
                lines.add("parsed = parse_key_value(target_message) ?? {}");
                mergeParsed = true;
                break;
            case PARSE_GROK:
                String grokPattern = visual.path(FIELD_GROK_PATTERN).asText();
                if (!grokPattern.isEmpty()) {
                    lines.add(String.format("parsed = parse_grok(target_message, \"%%{%s}\") ?? {}", grokPattern));
                    mergeParsed = true;
                }
                break;
            case CUSTOM:
                // Custom 在外层处理
                break;
        }

        if (mergeParsed) {
            lines.add(VRL_MERGE_PARSED);
            lines.add("");
        }
    }

    /**
     * 添加字段重命名
     */
    private void addFieldRenaming(List<String> lines, JsonNode parsedFields) {
        List<JsonNode> renamedFields = extractRenamedFields(parsedFields);

        if (!renamedFields.isEmpty()) {
            lines.add("# 字段重命名");
            for (JsonNode field : renamedFields) {
                String name = field.path(FIELD_NAME).asText();
                String newName = field.path(FIELD_NEW_NAME).asText();
                lines.add(String.format(".%s = del(.%s)", newName, name));
            }
            lines.add("");
        }
    }

    /**
     * 提取需要重命名的字段
     */
    private List<JsonNode> extractRenamedFields(JsonNode parsedFields) {
        List<JsonNode> renamedFields = new ArrayList<>();
        for (JsonNode field : parsedFields) {
            boolean deleted = field.path(FIELD_DELETED).asBoolean(false);
            if (!deleted) {
                String name = field.path(FIELD_NAME).asText();
                String newName = field.path(FIELD_NEW_NAME).asText();
                if (!newName.isEmpty() && !newName.equals(name)) {
                    renamedFields.add(field);
                }
            }
        }
        return renamedFields;
    }

    /**
     * 添加字段删除
     */
    private void addFieldDeletion(List<String> lines, JsonNode parsedFields) {
        List<JsonNode> deletedFields = extractDeletedFields(parsedFields);

        if (!deletedFields.isEmpty()) {
            lines.add("# 删除不需要的字段");
            for (JsonNode field : deletedFields) {
                String name = field.path(FIELD_NAME).asText();
                lines.add(String.format("del(.%s)", name));
            }
            lines.add("");
        }
    }

    /**
     * 提取需要删除的字段
     */
    private List<JsonNode> extractDeletedFields(JsonNode parsedFields) {
        List<JsonNode> deletedFields = new ArrayList<>();
        for (JsonNode field : parsedFields) {
            boolean deleted = field.path(FIELD_DELETED).asBoolean(false);
            if (deleted) {
                deletedFields.add(field);
            }
        }
        return deletedFields;
    }

    /**
     * 添加自定义 VRL 脚本
     */
    private void addCustomVrlScript(List<String> lines, JsonNode visual) {
        String vrlSource = visual.path(FIELD_VRL_SOURCE).asText();
        if (!vrlSource.isEmpty()) {
            // 处理转义字符，将字面量 \n、\t 等转换为真正的换行符、制表符
            String unescaped = unescapeString(vrlSource);
            // 去掉开头和结尾的空白字符，避免 SnakeYAML 使用双引号样式
            unescaped = unescaped.trim();
            lines.add(unescaped);
        }
    }

    /**
     * 处理字符串中的转义字符
     * 将字面量 \n、\t、\r、\\ 等转换为真正的转义字符
     *
     * @param input 包含转义字符的字符串
     * @return 处理后的字符串
     */
    private String unescapeString(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        StringBuilder result = new StringBuilder();
        int length = input.length();

        for (int i = 0; i < length; i++) {
            char c = input.charAt(i);

            if (c == '\\' && i + 1 < length) {
                char next = input.charAt(i + 1);
                switch (next) {
                    case 'n':
                        result.append('\n');
                        i++; // 跳过下一个字符
                        break;
                    case 't':
                        result.append('\t');
                        i++;
                        break;
                    case 'r':
                        result.append('\r');
                        i++;
                        break;
                    case '\\':
                        result.append('\\');
                        i++;
                        break;
                    case '"':
                        result.append('"');
                        i++;
                        break;
                    case '\'':
                        result.append('\'');
                        i++;
                        break;
                    default:
                        // 不是已知的转义字符，保留原样
                        result.append(c);
                        break;
                }
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    /**
     * 添加默认解析方式
     */
    private void addDefaultParsing(List<String> lines, ParseMethod parseMethod, JsonNode visual) {
        switch (parseMethod) {
            case PARSE_JSON:
                lines.add(". = parse_json!(.message)");
                break;
            case PARSE_SYSLOG:
                lines.add("result, err = parse_syslog(.message)");
                lines.add("if is_null(err) {");
                lines.add("  . = merge(., result)");
                lines.add("}");
                break;
            case PARSE_REGEX:
                String regexPattern = visual.path(FIELD_REGEX_PATTERN).asText();
                if (!regexPattern.isEmpty()) {
                    lines.add(String.format(". = parse_regex!(.message, r'%s')", regexPattern));
                }
                break;
            case PARSE_KEY_VALUE:
                lines.add(". = parse_key_value!(.message)");
                break;
            case PARSE_GROK:
                String grokPattern = visual.path(FIELD_GROK_PATTERN).asText();
                if (!grokPattern.isEmpty()) {
                    lines.add(String.format(". = parse_grok!(.message, \"%%{%s}\")", grokPattern));
                }
                break;
            case CUSTOM:
                // Custom 在外层处理
                break;
        }
    }

    /**
     * 添加增强选项
     */
    private void addEnhancementOptions(List<String> lines, JsonNode visual) {
        if (visual.path(FIELD_GENERATE_UUID).asBoolean(false)) {
            lines.add(".id = uuid_v7()");
        }
        if (visual.path(FIELD_KEEP_RAW).asBoolean(false)) {
            lines.add(".raw = .message");
        }
        if (visual.path(FIELD_EXTRACT_SOURCE_IP).asBoolean(false)) {
            lines.add(".source_ip = del(.host)");
        }
        if (visual.path(FIELD_CONVERT_PROCID).asBoolean(false)) {
            lines.add("if exists(.procid) {");
            lines.add("  if is_string(.procid) {");
            lines.add("    .procid = to_int!(.procid)");
            lines.add("  }");
            lines.add("}");
        }
    }

    /**
     * 添加自定义字段
     */
    private void addCustomFields(List<String> lines, JsonNode visual) {
        JsonNode addFields = visual.path(FIELD_ADD_FIELDS);
        if (addFields.isArray()) {
            for (JsonNode field : addFields) {
                String key = field.path(FIELD_KEY).asText();
                String value = field.path(FIELD_VALUE).asText();
                if (!key.isEmpty() && !value.isEmpty()) {
                    lines.add(String.format(".%s = \"%s\"", key, value));
                }
            }
        }
    }

    /**
     * 删除自定义字段
     */
    private void removeCustomFields(List<String> lines, JsonNode visual) {
        JsonNode removeFields = visual.path(FIELD_REMOVE_FIELDS);
        if (removeFields.isArray()) {
            for (JsonNode field : removeFields) {
                String fieldName = field.asText();
                if (!fieldName.isEmpty()) {
                    lines.add(String.format("del(.%s)", fieldName));
                }
            }
        }
    }

    /**
     * 生成 Filter 条件
     */
    protected String generateFilterCondition(JsonNode visual) {
        String filterTypeValue = visual.path(FIELD_FILTER_TYPE).asText(FilterType.LEVEL.getValue());
        FilterType filterType = FilterType.fromValue(filterTypeValue);

        switch (filterType) {
            case LEVEL:
                return generateLevelFilterCondition(visual);
            case FIELD:
                return generateFieldFilterCondition(visual);
            case CUSTOM:
                return visual.path(FIELD_CONDITION).asText();
            default:
                return "";
        }
    }

    private String generateLevelFilterCondition(JsonNode visual) {
        List<String> levels = jsonArrayToList(visual.path(FIELD_LEVELS));
        if (!levels.isEmpty()) {
            List<String> conditions = new ArrayList<>();
            for (String level : levels) {
                conditions.add(String.format(".level == \"%s\"", level));
            }
            return String.join(" || ", conditions);
        }
        return "";
    }

    private String generateFieldFilterCondition(JsonNode visual) {
        String fieldName = visual.path(FIELD_FIELD_NAME).asText();
        String fieldValue = visual.path(FIELD_FIELD_VALUE).asText();
        if (!fieldName.isEmpty() && !fieldValue.isEmpty()) {
            return String.format(".%s == \"%s\"", fieldName, fieldValue);
        }
        return "";
    }

    // ==================== Sink 配置生成 ====================

    /**
     * 生成 Sink 配置
     */
    protected void generateSinkConfig(Map<String, Object> config, String vectorType, JsonNode visual) {
        SinkType sinkType = SinkType.fromValue(vectorType);

        switch (sinkType) {
            case ELASTICSEARCH:
                generateElasticsearchSinkConfig(config, visual);
                break;
            case CLICKHOUSE:
                generateClickHouseSinkConfig(config, visual);
                break;
            case KAFKA:
                generateKafkaSinkConfig(config, visual);
                break;
            case CONSOLE:
                generateConsoleSinkConfig(config, visual);
                break;
        }
    }

    private void generateElasticsearchSinkConfig(Map<String, Object> config, JsonNode visual) {
        putIfNotEmpty(config, FIELD_ENDPOINTS, toStringList(visual.path(FIELD_ENDPOINTS).asText()));
        putIfNotEmpty(config, FIELD_INDEX, visual.path(FIELD_INDEX).asText());

        String esUser = visual.path(FIELD_AUTH_USER).asText();
        if (!esUser.isEmpty()) {
            Map<String, Object> auth = new LinkedHashMap<>();
            auth.put(AUTH_STRATEGY, AUTH_STRATEGY_BASIC);
            auth.put(AUTH_USER, esUser);
            auth.put(AUTH_PASSWORD, visual.path(FIELD_AUTH_PASSWORD).asText(""));
            config.put(FIELD_AUTH, auth);
        }
    }

    private void generateClickHouseSinkConfig(Map<String, Object> config, JsonNode visual) {
        // 基本配置
        putIfNotEmpty(config, FIELD_ENDPOINT, visual.path(FIELD_ENDPOINT).asText());
        putIfNotEmpty(config, FIELD_DATABASE, visual.path(FIELD_DATABASE).asText());
        putIfNotEmpty(config, FIELD_TABLE, visual.path(FIELD_TABLE).asText());

        // 数据格式配置
        addClickHouseFormatConfig(config, visual);

        // 编码配置
        addClickHouseEncodingConfig(config, visual);

        // 批处理配置
        addClickHouseBatchConfig(config, visual);

        // 缓冲配置
        addClickHouseBufferConfig(config, visual);

        // 认证配置
        addClickHouseAuthConfig(config, visual);
    }

    private void addClickHouseFormatConfig(Map<String, Object> config, JsonNode visual) {
        putIfNotEmpty(config, "format", visual.path(FIELD_CLICKHOUSE_FORMAT).asText());

        String compression = visual.path(FIELD_CLICKHOUSE_COMPRESSION).asText();
        if (!compression.isEmpty() && !COMPRESSION_NONE.equals(compression)) {
            config.put("compression", compression);
        }

        config.put("skip_unknown_fields", visual.path(FIELD_CLICKHOUSE_SKIP_UNKNOWN).asBoolean(true));
    }

    private void addClickHouseEncodingConfig(Map<String, Object> config, JsonNode visual) {
        Map<String, Object> encoding = new LinkedHashMap<>();
        encoding.put(ENCODING_TIMESTAMP_FORMAT,
            getOrDefault(visual, FIELD_CLICKHOUSE_TIMESTAMP_FORMAT, DEFAULT_CLICKHOUSE_TIMESTAMP_FORMAT));
        config.put(FIELD_ENCODING, encoding);
    }

    private void addClickHouseBatchConfig(Map<String, Object> config, JsonNode visual) {
        Map<String, Object> batch = new LinkedHashMap<>();
        batch.put(BATCH_MAX_BYTES, parseIntOrDefault(
            visual.path(FIELD_CLICKHOUSE_BATCH_MAX_BYTES).asText(), DEFAULT_CLICKHOUSE_BATCH_MAX_BYTES));
        batch.put(BATCH_TIMEOUT_SECS, parseIntOrDefault(
            visual.path(FIELD_CLICKHOUSE_BATCH_TIMEOUT).asText(), DEFAULT_CLICKHOUSE_BATCH_TIMEOUT));
        config.put(FIELD_BATCH, batch);
    }

    private void addClickHouseBufferConfig(Map<String, Object> config, JsonNode visual) {
        Map<String, Object> buffer = new LinkedHashMap<>();
        buffer.put(BUFFER_TYPE, getOrDefault(visual, FIELD_CLICKHOUSE_BUFFER_TYPE, DEFAULT_CLICKHOUSE_BUFFER_TYPE));
        buffer.put(BUFFER_MAX_EVENTS, parseIntOrDefault(
            visual.path(FIELD_CLICKHOUSE_BUFFER_MAX_EVENTS).asText(), DEFAULT_CLICKHOUSE_BUFFER_MAX_EVENTS));
        config.put(FIELD_BUFFER, buffer);
    }

    private void addClickHouseAuthConfig(Map<String, Object> config, JsonNode visual) {
        String chUser = visual.path(FIELD_CLICKHOUSE_USER).asText();
        if (!chUser.isEmpty()) {
            Map<String, Object> chAuth = new LinkedHashMap<>();
            chAuth.put(AUTH_STRATEGY, AUTH_STRATEGY_BASIC);
            chAuth.put(AUTH_USER, chUser);
            chAuth.put(AUTH_PASSWORD, visual.path(FIELD_CLICKHOUSE_PASSWORD).asText(""));
            config.put(FIELD_AUTH, chAuth);
        }
    }

    private void generateKafkaSinkConfig(Map<String, Object> config, JsonNode visual) {
        putIfNotEmpty(config, FIELD_BOOTSTRAP_SERVERS, visual.path(FIELD_BOOTSTRAP_SERVERS).asText());
        putIfNotEmpty(config, FIELD_TOPIC, visual.path(FIELD_TOPIC).asText());
    }

    private void generateConsoleSinkConfig(Map<String, Object> config, JsonNode visual) {
        config.put(FIELD_TARGET, getOrDefault(visual, FIELD_TARGET, DEFAULT_CONSOLE_TARGET));

        Map<String, Object> consoleEncoding = new LinkedHashMap<>();
        consoleEncoding.put(ENCODING_CODEC, getOrDefault(visual, FIELD_ENCODING, DEFAULT_CONSOLE_ENCODING));
        config.put(FIELD_ENCODING, consoleEncoding);
    }

    // ==================== 工具方法 ====================

    private String toYaml(Map<String, Object> config) {
        // 对于包含多行字符串的配置，手动构建 YAML 以确保正确的格式
        String type = (String) config.get("type");
        Object sourceObj = config.get("source");

        if (sourceObj instanceof String source) {
            if (source.contains("\n")) {
                // 手动构建 YAML，使用字面量块样式
                StringBuilder yaml = new StringBuilder();
                yaml.append("type: ").append(type).append("\n");
                yaml.append("source: |-\n");

                // 添加缩进的源代码
                String[] lines = source.split("\n", -1);  // -1 保留尾部空行
                for (String line : lines) {
                    yaml.append("    ").append(line).append("\n");
                }

                return yaml.toString();
            }
        }

        // 对于不包含换行符的字符串，使用标准 YAML 序列化
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(4);
        options.setIndicatorIndent(2);
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);

        Yaml yaml = new Yaml(options);
        return yaml.dump(config);
    }

    private void putIfNotEmpty(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            if (value instanceof String && !((String) value).isEmpty()) {
                map.put(key, value);
            } else if (value instanceof List && !((List<?>) value).isEmpty()) {
                map.put(key, value);
            } else if (!(value instanceof String) && !(value instanceof List)) {
                map.put(key, value);
            }
        }
    }

    private List<String> toStringList(String value) {
        if (value == null || value.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.singletonList(value);
    }

    private List<String> jsonArrayToList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode item : node) {
                String text = item.asText();
                if (!text.isEmpty()) {
                    list.add(text);
                }
            }
        }
        return list;
    }

    private String getOrDefault(JsonNode visual, String field, String defaultValue) {
        String value = visual.path(field).asText();
        return value.isEmpty() ? defaultValue : value;
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("无效的整数值: {}, 使用默认值: {}", value, defaultValue);
            return defaultValue;
        }
    }
}
