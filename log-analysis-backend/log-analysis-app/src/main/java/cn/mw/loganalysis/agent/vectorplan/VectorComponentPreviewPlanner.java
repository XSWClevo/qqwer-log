package cn.mw.loganalysis.agent.vectorplan;

import cn.mw.loganalysis.agent.execution.AgentExecutionContext;
import cn.mw.loganalysis.agent.tool.AgentToolPayload;
import cn.mw.loganalysis.agent.support.AgentToolSupport;
import cn.mw.loganalysis.agent.dto.AgentResult;
import cn.mw.loganalysis.datasource.entity.Datasource;
import cn.mw.loganalysis.datasource.mapper.DatasourceMapper;
import cn.mw.loganalysis.vector.dto.VrlExecuteRequest;
import cn.mw.loganalysis.vector.entity.ConfigComponent;
import cn.mw.loganalysis.vector.service.ConfigComponentService;
import cn.mw.loganalysis.wizard.dto.GenerateDDLRequest;
import cn.mw.loganalysis.wizard.dto.GenerateDDLResponse;
import cn.mw.loganalysis.wizard.dto.ParseLogResponse;
import cn.mw.loganalysis.wizard.service.ClickHouseDDLGenerator;
import cn.mw.loganalysis.wizard.service.SmartWizardService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

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
 * 预览阶段执行器：只生成 Source/Remap/Sink 计划，不写库、不建组件。
 */
@Component
@RequiredArgsConstructor
public class VectorComponentPreviewPlanner {

    private static final DateTimeFormatter TABLE_SUFFIX_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Pattern FENCED_BLOCK_PATTERN = Pattern.compile("```(?:\\w+)?\\s*([\\s\\S]*?)```");
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("(?:表名|tableName|table|写入表|目标表)\\s*[:：=]\\s*([A-Za-z_][A-Za-z0-9_]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern KEY_VALUE_PATTERN = Pattern.compile("(?<![\\w.-])([A-Za-z_][A-Za-z0-9_.-]*)=(\"[^\"]*\"|'[^']*'|\\S+)");
    private static final Pattern PYTHON_LOG_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:[,\\.]\\d+)?\\s+-\\s+\\S+\\s+-\\s+(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL|CRITICAL)\\s+-\\s+.+", Pattern.CASE_INSENSITIVE);
    private static final Pattern ISO_LEVEL_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}.*?\\b(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL|CRITICAL)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEVEL_PATTERN = Pattern.compile("\\b(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL|CRITICAL)\\b", Pattern.CASE_INSENSITIVE);
    private static final TypeReference<LinkedHashMap<String, Object>> JSON_MAP_TYPE = new TypeReference<>() {
    };

    private final DatasourceMapper datasourceMapper;
    private final ConfigComponentService configComponentService;
    private final SmartWizardService smartWizardService;
    private final ClickHouseDDLGenerator clickHouseDDLGenerator;
    private final ObjectMapper objectMapper;
    private final VectorComponentPlanStore planStore;

    /**
     * 生成 Vector 组件预览计划，预览阶段只校验和缓存计划，不写库。
     */
    public AgentToolPayload preview(AgentExecutionContext context,
                             String logSample,
                             String datasourceId,
                             String tableName,
                             String regexPattern,
                             String sourceType,
                             Map<String, Object> sourceConfig) {
        long startedAt = System.currentTimeMillis();
        validateContext(context);

        List<String> warnings = new ArrayList<>();
        String normalizedSample = extractLogSample(logSample);
        if (StringUtils.isBlank(normalizedSample)) {
            throw new IllegalArgumentException("请提供一条日志样本，助手才能生成正则、VRL、字段和 DDL");
        }

        Datasource clickHouseDatasource = resolveClickHouseDatasource(context, datasourceId);
        String resolvedTableName = resolveTableName(tableName, logSample, warnings);
        String resolvedSourceType = normalizeSourceType(sourceType);
        Map<String, Object> resolvedSourceConfig = normalizeSourceConfig(resolvedSourceType, sourceConfig, resolvedTableName);
        validateSourceConfig(resolvedSourceType, resolvedSourceConfig);
        String resolvedRegex = StringUtils.trimToNull(regexPattern);
        if (StringUtils.isBlank(resolvedRegex)) {
            resolvedRegex = generateRegex(normalizedSample, warnings);
        }
        if (!hasNamedCaptureGroup(resolvedRegex)) {
            throw new IllegalArgumentException("正则必须包含命名捕获组，例如 (?P<level>INFO)。请调整日志样本或显式提供 regexPattern");
        }

        ParseLogResponse parseResponse = parseWithRegex(normalizedSample, resolvedRegex);
        List<FieldPlan> fields = buildFields(parseResponse, warnings);
        GenerateDDLResponse ddlResponse = generateDdl(clickHouseDatasource.getId(), resolvedTableName, fields);

        VectorComponentPlan plan = new VectorComponentPlan(
                UUID.randomUUID().toString(),
                context.userId(),
                context.sessionId(),
                context.datasourceId(),
                clickHouseDatasource.getId(),
                clickHouseDatasource.getName(),
                resolvedTableName,
                normalizedSample,
                resolvedRegex,
                parseResponse.getVrlScript(),
                resolvedSourceType,
                resolvedSourceConfig,
                fields,
                ddlResponse.getDdl(),
                List.copyOf(warnings)
        );
        planStore.save(plan);

        AgentResult result = toPlanResult(plan);
        return AgentToolPayload.builder()
                .toolName("preview_vector_components")
                .toolLabel("预览 Vector 组件计划")
                .intent("vector_component_plan")
                .summary(String.format("已生成 Vector 组件预览计划：来源 %s，表 %s，字段 %d 个。请确认后再创建 Source/Remap/Sink 和可部署配置。",
                        plan.sourceType(), plan.tableName(), plan.fields().size()))
                .durationMs(System.currentTimeMillis() - startedAt)
                .result(result)
                .build();
    }

    /**
     * 校验预览计划必须绑定到明确的用户会话。
     */
    private void validateContext(AgentExecutionContext context) {
        if (context == null || ObjectUtils.isEmpty(context.userId()) || StringUtils.isBlank(context.sessionId())) {
            throw new IllegalStateException("智能助手会话信息不完整，请刷新页面后重新生成组件计划");
        }
    }

    /**
     * 解析用户指定或当前选中的 ClickHouse 目标数据源。
     */
    private Datasource resolveClickHouseDatasource(AgentExecutionContext context, String requestedDatasourceId) {
        String normalizedDatasourceId = StringUtils.trimToNull(requestedDatasourceId);
        if (StringUtils.isNotBlank(normalizedDatasourceId)) {
            Datasource datasource = datasourceMapper.selectById(normalizedDatasourceId);
            if (datasource != null) {
                validateClickHouseDatasource(datasource);
                return datasource;
            }

            ConfigComponent component = configComponentService.getQueryableDataSourceById(normalizedDatasourceId);
            if (component != null) {
                return resolveDatasourceFromQueryableSink(component);
            }
            throw new IllegalArgumentException("目标数据源不存在，或不是可查询 ClickHouse Sink");
        }

        ConfigComponent currentSink = configComponentService.getQueryableDataSourceById(context.datasourceId());
        if (currentSink == null) {
            throw new IllegalArgumentException("当前会话没有选择可查询的数据源");
        }
        return resolveDatasourceFromQueryableSink(currentSink);
    }

    /**
     * 从可查询 Sink 组件追溯其关联的平台 ClickHouse 数据源。
     */
    private Datasource resolveDatasourceFromQueryableSink(ConfigComponent component) {
        if (!StringUtils.equalsIgnoreCase(component.getVectorType(), "clickhouse")) {
            throw new IllegalArgumentException("Vector 组件生成助手 v1 仅支持 ClickHouse Sink，当前为 " + component.getVectorType());
        }
        if (StringUtils.isBlank(component.getDatasourceId())) {
            throw new IllegalArgumentException("当前 ClickHouse Sink 未关联平台数据源，无法创建表和组件");
        }
        Datasource datasource = datasourceMapper.selectById(component.getDatasourceId());
        validateClickHouseDatasource(datasource);
        return datasource;
    }

    /**
     * 校验目标数据源必须是 ClickHouse。
     */
    private void validateClickHouseDatasource(Datasource datasource) {
        if (datasource == null) {
            throw new IllegalArgumentException("ClickHouse 数据源不存在");
        }
        if (!StringUtils.equalsIgnoreCase(datasource.getType(), "clickhouse")) {
            throw new IllegalArgumentException("目标数据源不是 ClickHouse，当前类型为 " + datasource.getType());
        }
    }

    /**
     * 规范化并校验日志来源类型。
     */
    private String normalizeSourceType(String sourceType) {
        String normalized = StringUtils.lowerCase(StringUtils.trimToEmpty(sourceType), Locale.ROOT);
        if (!StringUtils.equalsAny(normalized, "file", "syslog", "socket", "kafka")) {
            throw new IllegalArgumentException("请先确认日志来源类型，目前支持 file、syslog、socket、kafka");
        }
        return normalized;
    }

    /**
     * 规范化 Source 配置，并为可选字段补默认值。
     */
    private Map<String, Object> normalizeSourceConfig(String sourceType, Map<String, Object> sourceConfig, String tableName) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (sourceConfig != null) {
            sourceConfig.forEach((key, value) -> {
                if (StringUtils.isNotBlank(key) && ObjectUtils.isNotEmpty(value)) {
                    normalized.put(key, value);
                }
            });
        }
        if (StringUtils.equals(sourceType, "file")) {
            normalized.putIfAbsent("read_from", "beginning");
        }
        if (StringUtils.equalsAny(sourceType, "syslog", "socket")) {
            normalized.putIfAbsent("syslog_mode", "tcp");
        }
        if (StringUtils.equals(sourceType, "kafka")) {
            normalized.putIfAbsent("group_id", StringUtils.defaultIfBlank(tableName, "vector_logs") + "_consumer");
        }
        return normalized;
    }

    /**
     * 校验不同 Source 类型所需的关键配置是否齐全。
     */
    private void validateSourceConfig(String sourceType, Map<String, Object> sourceConfig) {
        if (StringUtils.equals(sourceType, "file") && isBlankConfigValue(sourceConfig.get("include"))) {
            throw new IllegalArgumentException("文件日志来源需要提供文件路径，例如 /var/log/app/*.log");
        }
        if (StringUtils.equalsAny(sourceType, "syslog", "socket")) {
            if (isBlankConfigValue(sourceConfig.get("syslog_mode"))) {
                throw new IllegalArgumentException("Syslog/Socket 来源需要提供监听协议 tcp 或 udp");
            }
            if (isBlankConfigValue(sourceConfig.get("syslog_address"))) {
                throw new IllegalArgumentException("Syslog/Socket 来源需要提供监听地址，例如 0.0.0.0:514");
            }
        }
        if (StringUtils.equals(sourceType, "kafka")) {
            if (isBlankConfigValue(sourceConfig.get("bootstrap_servers"))) {
                throw new IllegalArgumentException("Kafka 来源需要提供 bootstrap servers，例如 localhost:9092");
            }
            if (isBlankConfigValue(sourceConfig.get("topics"))) {
                throw new IllegalArgumentException("Kafka 来源需要提供 topic，例如 app-logs");
            }
        }
    }

    /**
     * 判断 Source 配置值是否为空。
     */
    private boolean isBlankConfigValue(Object value) {
        return ObjectUtils.isEmpty(value) || StringUtils.isBlank(String.valueOf(value));
    }

    /**
     * 从混合输入中挑出最像日志样本的一行。
     */
    private String extractLogSample(String rawValue) {
        String value = StringUtils.trimToEmpty(rawValue);
        if (StringUtils.isBlank(value)) {
            return "";
        }

        Matcher labeledMatcher = Pattern.compile("(?:日志样本|logSample|sample|日志)\\s*[:：]\\s*([\\s\\S]+)", Pattern.CASE_INSENSITIVE)
                .matcher(value);
        if (labeledMatcher.find()) {
            return StringUtils.trimToEmpty(labeledMatcher.group(1));
        }

        Matcher fencedMatcher = FENCED_BLOCK_PATTERN.matcher(value);
        if (fencedMatcher.find()) {
            return StringUtils.trimToEmpty(fencedMatcher.group(1));
        }

        String[] lines = value.split("\\R");
        String best = "";
        for (String line : lines) {
            String trimmed = StringUtils.trimToEmpty(line)
                    .replaceFirst("^(日志样本|sample|logSample|日志)\\s*[:：]\\s*", "");
            if (looksLikeInstruction(trimmed)) {
                continue;
            }
            if (scoreLogLine(trimmed) > scoreLogLine(best)) {
                best = trimmed;
            }
        }
        return StringUtils.isNotBlank(best) ? best : value;
    }

    /**
     * 判断文本行是否更像用户指令而不是日志内容。
     */
    private boolean looksLikeInstruction(String line) {
        String lower = StringUtils.lowerCase(line, Locale.ROOT);
        return StringUtils.isBlank(lower)
                || AgentToolSupport.containsAny(lower, "帮我", "生成", "创建", "vector", "组件", "正则", "入库", "表名", "datasource");
    }

    /**
     * 为候选日志行打分，用于从多行输入中选取样本。
     */
    private int scoreLogLine(String line) {
        if (StringUtils.isBlank(line)) {
            return 0;
        }
        int score = line.length();
        if (KEY_VALUE_PATTERN.matcher(line).find()) {
            score += 100;
        }
        if (line.startsWith("{") && line.endsWith("}")) {
            score += 80;
        }
        if (LEVEL_PATTERN.matcher(line).find()) {
            score += 30;
        }
        return score;
    }

    /**
     * 解析并规范化目标表名，缺失时生成临时表名并提示用户。
     */
    private String resolveTableName(String explicitTableName, String rawMessage, List<String> warnings) {
        String tableName = sanitizeTableName(StringUtils.trimToNull(explicitTableName));
        if (StringUtils.isBlank(tableName)) {
            Matcher matcher = TABLE_NAME_PATTERN.matcher(StringUtils.defaultString(rawMessage));
            if (matcher.find()) {
                tableName = sanitizeTableName(matcher.group(1));
            }
        }
        if (StringUtils.isBlank(tableName)) {
            tableName = "agent_logs_" + LocalDateTime.now().format(TABLE_SUFFIX_FORMATTER);
            warnings.add("未提供表名，已自动生成：" + tableName);
        }
        return tableName;
    }

    /**
     * 将用户输入清洗成 ClickHouse 可用表名。
     */
    private String sanitizeTableName(String tableName) {
        if (StringUtils.isBlank(tableName)) {
            return "";
        }
        String normalized = tableName.trim()
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
     * 根据日志样本生成命名捕获正则。
     */
    private String generateRegex(String logSample, List<String> warnings) {
        String jsonRegex = tryGenerateFlatJsonRegex(logSample);
        if (StringUtils.isNotBlank(jsonRegex)) {
            warnings.add("未显式提供正则，已根据 JSON 日志样本生成命名捕获正则。");
            return jsonRegex;
        }

        String keyValueRegex = tryGenerateKeyValueRegex(logSample);
        if (StringUtils.isNotBlank(keyValueRegex)) {
            warnings.add("未显式提供正则，已根据 key=value 日志样本生成命名捕获正则。");
            return keyValueRegex;
        }

        if (PYTHON_LOG_PATTERN.matcher(logSample).find()) {
            warnings.add("未显式提供正则，已按 timestamp + logger + level + message 模式生成命名捕获正则。");
            return "^.*?(?P<timestamp>\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:[,\\.]\\d+)?)\\s+-\\s+(?P<logger>[\\w.$-]+)\\s+-\\s+(?P<level>TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL|CRITICAL)\\s+-\\s+(?P<message>.*)$";
        }

        if (ISO_LEVEL_PATTERN.matcher(logSample).find()) {
            warnings.add("未显式提供正则，已按 timestamp + level + message 模式生成命名捕获正则。");
            return "^.*?(?P<timestamp>\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:[,\\.]\\d+)?(?:Z|[+-]\\d{2}:?\\d{2})?)\\s+(?P<level>TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL|CRITICAL)\\s+(?P<message>.*)$";
        }

        if (LEVEL_PATTERN.matcher(logSample).find()) {
            warnings.add("未显式提供正则，已按 level + message 模式生成命名捕获正则。");
            return "^.*?\\b(?P<level>TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL|CRITICAL)\\b\\s+(?P<message>.*)$";
        }

        warnings.add("未识别出稳定结构，仅生成 message 捕获组；建议确认字段是否足够。");
        return "^(?P<message>.*)$";
    }

    /**
     * 尝试为扁平 JSON 日志生成命名捕获正则。
     */
    private String tryGenerateFlatJsonRegex(String logSample) {
        try {
            LinkedHashMap<String, Object> parsed = objectMapper.readValue(logSample, JSON_MAP_TYPE);
            if (parsed.isEmpty()) {
                return "";
            }

            StringBuilder regex = new StringBuilder("^");
            List<String> usedNames = new ArrayList<>();
            for (Map.Entry<String, Object> entry : parsed.entrySet()) {
                String fieldName = uniqueFieldName(sanitizeFieldName(entry.getKey()), usedNames);
                usedNames.add(fieldName);
                regex.append(".*?\"")
                        .append(escapeRegex(entry.getKey()))
                        .append("\"\\s*:\\s*");
                Object value = entry.getValue();
                if (value instanceof Number || value instanceof Boolean) {
                    regex.append("(?P<").append(fieldName).append(">[^,}\\s]+)");
                } else {
                    regex.append("\"(?P<").append(fieldName).append(">[^\"]*)\"");
                }
            }
            regex.append(".*$");
            return regex.toString();
        } catch (JsonProcessingException ex) {
            return "";
        }
    }

    /**
     * 尝试为 key=value 日志生成命名捕获正则。
     */
    private String tryGenerateKeyValueRegex(String logSample) {
        Matcher matcher = KEY_VALUE_PATTERN.matcher(logSample);
        List<String> usedNames = new ArrayList<>();
        StringBuilder regex = new StringBuilder("^.*?");
        boolean found = false;
        while (matcher.find()) {
            String fieldName = uniqueFieldName(sanitizeFieldName(matcher.group(1)), usedNames);
            usedNames.add(fieldName);
            regex.append(escapeRegex(matcher.group(1)))
                    .append("=(?P<")
                    .append(fieldName)
                    .append(">(\"[^\"]*\"|\\S+)).*?");
            found = true;
        }
        return found ? regex.append("$").toString() : "";
    }

    /**
     * 判断正则是否包含可落字段的命名捕获组。
     */
    private boolean hasNamedCaptureGroup(String regexPattern) {
        String regex = StringUtils.defaultString(regexPattern);
        return regex.contains("(?P<") || regex.contains("(?<");
    }

    /**
     * 调用现有智能向导解析服务验证正则能解析样本。
     */
    private ParseLogResponse parseWithRegex(String logSample, String regexPattern) {
        VrlExecuteRequest request = new VrlExecuteRequest();
        request.setLogSample(logSample);
        request.setParseMethod("parse_regex");
        request.setRegexPattern(regexPattern);

        ParseLogResponse response = smartWizardService.parseLog(request);
        if (response == null || !response.isSuccess()) {
            throw new IllegalArgumentException(response != null && StringUtils.isNotBlank(response.getError())
                    ? response.getError()
                    : "正则解析失败");
        }
        if (CollectionUtils.isEmpty(response.getFields())) {
            throw new IllegalArgumentException("正则解析未提取到字段，请调整日志样本或 regexPattern");
        }
        boolean hasParseError = response.getFields().stream()
                .anyMatch(field -> StringUtils.equalsIgnoreCase(field.getName(), "parse_error"));
        if (hasParseError) {
            throw new IllegalArgumentException("正则未能匹配日志样本，请调整 regexPattern");
        }
        return response;
    }

    /**
     * 根据解析结果构建字段计划，并补齐必要系统字段。
     */
    private List<FieldPlan> buildFields(ParseLogResponse parseResponse, List<String> warnings) {
        List<FieldPlan> fields = new ArrayList<>();
        List<String> usedNames = new ArrayList<>();
        for (ParseLogResponse.ParsedFieldDTO parsedField : parseResponse.getFields()) {
            if (parsedField == null || StringUtils.isBlank(parsedField.getName())) {
                continue;
            }
            String fieldName = uniqueFieldName(sanitizeFieldName(parsedField.getName()), usedNames);
            if (StringUtils.equalsAnyIgnoreCase(fieldName, "id", "parse_error")) {
                continue;
            }
            usedNames.add(fieldName);
            fields.add(new FieldPlan(
                    fieldName,
                    resolveFieldType(fieldName, parsedField.getType()),
                    parsedField.getSampleValue(),
                    parsedField.getSuggestion() != null ? parsedField.getSuggestion().getType() : null,
                    buildFieldComment(parsedField)
            ));
        }

        boolean hasTimestamp = fields.stream().anyMatch(field -> StringUtils.equalsIgnoreCase(field.name(), "timestamp"));
        if (!hasTimestamp) {
            fields.add(0, new FieldPlan("timestamp", "DateTime", "自动填充当前时间", null, "系统默认时间戳"));
            warnings.add("日志样本未解析出 timestamp 字段，已为 ClickHouse 表补充默认时间字段。");
        }
        if (fields.stream().noneMatch(field -> !StringUtils.equalsAnyIgnoreCase(field.name(), "timestamp", "raw", "message"))) {
            warnings.add("当前只解析出系统字段或 message 字段，建议确认正则是否需要提取更多业务字段。");
        }
        if (CollectionUtils.isEmpty(fields)) {
            throw new IllegalArgumentException("未能构建有效字段，请调整日志样本或 regexPattern");
        }
        return fields;
    }

    /**
     * 生成字段备注，优先使用智能向导给出的建议原因。
     */
    private String buildFieldComment(ParseLogResponse.ParsedFieldDTO field) {
        if (field.getSuggestion() != null && StringUtils.isNotBlank(field.getSuggestion().getReason())) {
            return field.getSuggestion().getReason();
        }
        if (StringUtils.equalsIgnoreCase(field.getName(), "raw")) {
            return "原始日志";
        }
        return "";
    }

    /**
     * 解析字段最终落到 ClickHouse 的类型。
     */
    private String resolveFieldType(String fieldName, String inferredType) {
        if (StringUtils.equalsIgnoreCase(fieldName, "raw")) {
            return "String";
        }
        return normalizeClickHouseType(inferredType);
    }

    /**
     * 将解析服务的类型名称映射为 ClickHouse 类型。
     */
    private String normalizeClickHouseType(String type) {
        String normalized = StringUtils.defaultIfBlank(type, "String").trim();
        if (StringUtils.equalsIgnoreCase(normalized, "string")) {
            return "String";
        }
        if (StringUtils.equalsIgnoreCase(normalized, "integer")) {
            return "Int64";
        }
        if (StringUtils.equalsIgnoreCase(normalized, "float")) {
            return "Float64";
        }
        if (StringUtils.equalsIgnoreCase(normalized, "boolean")) {
            return "UInt8";
        }
        if (StringUtils.equalsIgnoreCase(normalized, "null")
                || StringUtils.equalsIgnoreCase(normalized, "object")
                || StringUtils.equalsIgnoreCase(normalized, "array")
                || StringUtils.equalsIgnoreCase(normalized, "unknown")) {
            return "String";
        }
        return normalized;
    }

    /**
     * 调用 DDL 生成器生成目标 ClickHouse 建表语句。
     */
    private GenerateDDLResponse generateDdl(String datasourceId, String tableName, List<FieldPlan> fields) {
        GenerateDDLRequest request = new GenerateDDLRequest();
        request.setDatasourceId(datasourceId);
        request.setTableName(tableName);
        request.setFields(fields.stream().map(this::toDdlField).toList());

        GenerateDDLResponse response = clickHouseDDLGenerator.generate(request);
        if (response == null || !response.isSuccess() || StringUtils.isBlank(response.getDdl())) {
            throw new IllegalArgumentException(response != null && StringUtils.isNotBlank(response.getError())
                    ? response.getError()
                    : "DDL 生成失败");
        }
        return response;
    }

    /**
     * 将字段计划转换成 DDL 生成器需要的字段定义。
     */
    private GenerateDDLRequest.FieldDefinition toDdlField(FieldPlan field) {
        GenerateDDLRequest.FieldDefinition definition = new GenerateDDLRequest.FieldDefinition();
        definition.setName(field.name());
        definition.setType(field.type());
        definition.setComment(field.comment());
        definition.setNullable(false);
        return definition;
    }

    /**
     * 将内部计划转换成前端预览卡片结果。
     */
    private AgentResult toPlanResult(VectorComponentPlan plan) {
        return AgentResult.builder()
                .type("vector_component_plan")
                .success(true)
                .planId(plan.planId())
                .logSample(plan.logSample())
                .datasourceId(plan.datasourceId())
                .datasourceName(plan.datasourceName())
                .tableName(plan.tableName())
                .regexPattern(plan.regexPattern())
                .vrlScript(plan.vrlScript())
                .sourceType(plan.sourceType())
                .sourceConfig(plan.sourceConfig())
                .fields(plan.fields().stream().map(this::toResultField).toList())
                .ddl(plan.ddl())
                .warnings(plan.warnings())
                .build();
    }

    /**
     * 将字段计划转换成前端字段表格行。
     */
    private Map<String, Object> toResultField(FieldPlan field) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", field.name());
        result.put("type", field.type());
        result.put("sampleValue", field.sampleValue());
        result.put("suggestion", field.suggestion());
        result.put("comment", field.comment());
        return result;
    }

    /**
     * 将解析字段名清洗成安全的 ClickHouse 字段名。
     */
    private String sanitizeFieldName(String fieldName) {
        String normalized = StringUtils.defaultString(fieldName)
                .trim()
                .replaceAll("[^A-Za-z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (StringUtils.isBlank(normalized)) {
            normalized = "field";
        }
        if (!Character.isLetter(normalized.charAt(0)) && normalized.charAt(0) != '_') {
            normalized = "field_" + normalized;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    /**
     * 为重复字段名追加序号，避免 DDL 字段冲突。
     */
    private String uniqueFieldName(String baseName, List<String> usedNames) {
        String normalized = StringUtils.defaultIfBlank(baseName, "field");
        String candidate = normalized;
        int index = 2;
        while (containsFieldName(usedNames, candidate)) {
            candidate = normalized + "_" + index;
            index++;
        }
        return candidate;
    }

    /**
     * 判断字段名集合中是否已存在候选名称。
     */
    private boolean containsFieldName(List<String> usedNames, String candidate) {
        return usedNames.stream().anyMatch(used -> StringUtils.equalsIgnoreCase(used, candidate));
    }

    /**
     * 转义日志 key，避免生成正则时把特殊字符当作元字符。
     */
    private String escapeRegex(String value) {
        StringBuilder escaped = new StringBuilder();
        for (char ch : StringUtils.defaultString(value).toCharArray()) {
            if ("\\.^$|?*+()[]{}".indexOf(ch) >= 0) {
                escaped.append('\\');
            }
            escaped.append(ch);
        }
        return escaped.toString();
    }
}
