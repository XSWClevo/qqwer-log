package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.dto.AgentChatResponse;
import cn.mw.loganalysis.agent.dto.AgentResult;
import cn.mw.loganalysis.datasource.entity.Datasource;
import cn.mw.loganalysis.datasource.mapper.DatasourceMapper;
import cn.mw.loganalysis.vector.dto.VrlExecuteRequest;
import cn.mw.loganalysis.vector.entity.ConfigComponent;
import cn.mw.loganalysis.vector.service.ConfigComponentService;
import cn.mw.loganalysis.wizard.dto.CreateTableRequest;
import cn.mw.loganalysis.wizard.dto.CreateTableResponse;
import cn.mw.loganalysis.wizard.dto.GenerateDDLRequest;
import cn.mw.loganalysis.wizard.dto.GenerateDDLResponse;
import cn.mw.loganalysis.wizard.dto.ParseLogResponse;
import cn.mw.loganalysis.wizard.service.ClickHouseDDLGenerator;
import cn.mw.loganalysis.wizard.service.SmartWizardService;
import cn.mw.loganalysis.wizard.service.TableManagementService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能助手的 Vector 组件预览与确认工具。
 *
 * v1 只创建 Remap Transform 和 ClickHouse Sink：
 * 预览阶段生成计划并缓存，确认阶段才调用既有智能向导建表/建组件链路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorComponentPlanToolHandler {

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
    private final TableManagementService tableManagementService;
    private final ObjectMapper objectMapper;

    private final Cache<String, VectorComponentPlan> planCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    public AgentToolPayload preview(AgentExecutionContext context,
                                    String logSample,
                                    String datasourceId,
                                    String tableName,
                                    String regexPattern) {
        long startedAt = System.currentTimeMillis();
        validateContext(context);

        List<String> warnings = new ArrayList<>();
        String normalizedSample = extractLogSample(logSample);
        if (StringUtils.isBlank(normalizedSample)) {
            throw new IllegalArgumentException("请提供一条日志样本，助手才能生成正则、VRL、字段和 DDL");
        }

        Datasource clickHouseDatasource = resolveClickHouseDatasource(context, datasourceId);
        String resolvedTableName = resolveTableName(tableName, logSample, warnings);
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
                fields,
                ddlResponse.getDdl(),
                List.copyOf(warnings)
        );
        planCache.put(plan.planId(), plan);

        AgentResult result = toPlanResult(plan);
        return AgentToolPayload.builder()
                .toolName("preview_vector_components")
                .toolLabel("预览 Vector 组件计划")
                .intent("vector_component_plan")
                .summary(String.format("已生成 Vector 组件预览计划：表 %s，字段 %d 个。请确认后再创建 ClickHouse 表和 Remap/Sink 组件。",
                        plan.tableName(), plan.fields().size()))
                .durationMs(System.currentTimeMillis() - startedAt)
                .result(result)
                .build();
    }

    public AgentChatResponse commit(String planId, Long userId, String sessionId) {
        if (StringUtils.isBlank(planId)) {
            return commitError("缺少计划 ID，无法确认创建", null);
        }
        if (ObjectUtils.isEmpty(userId) || StringUtils.isBlank(sessionId)) {
            return commitError("会话信息不完整，请重新生成预览计划后再确认创建", null);
        }

        VectorComponentPlan plan = planCache.getIfPresent(StringUtils.trim(planId));
        if (plan == null) {
            return commitError("预览计划不存在或已过期，请重新让助手生成一次", null);
        }
        if (!Objects.equals(userId, plan.userId()) || !StringUtils.equals(sessionId, plan.sessionId())) {
            log.warn("拒绝提交不属于当前用户/会话的 Vector 组件计划, planId={}, userId={}, sessionId={}",
                    planId, userId, sessionId);
            return commitError("预览计划不属于当前会话，请重新生成后再确认创建", plan);
        }

        try {
            Datasource datasource = datasourceMapper.selectById(plan.datasourceId());
            validateClickHouseDatasource(datasource);
            ensureTableDoesNotExist(plan.datasourceId(), plan.tableName());
            ParseLogResponse parseResponse = parseWithRegex(plan.logSample(), plan.regexPattern());
            if (StringUtils.isBlank(plan.ddl())) {
                return commitError("预览计划缺少 DDL，请重新生成后再确认创建", plan);
            }

            CreateTableRequest request = new CreateTableRequest();
            request.setDatasourceId(plan.datasourceId());
            request.setTableName(plan.tableName());
            request.setDdl(plan.ddl());
            request.setVrlScript(StringUtils.defaultIfBlank(plan.vrlScript(), parseResponse.getVrlScript()));
            request.setParseMethod("parse_regex");
            request.setRegexPattern(plan.regexPattern());
            request.setLogSample(plan.logSample());
            request.setParsedFields(toVisualParsedFields(plan.fields()));
            request.setAutoCreateComponents(true);

            CreateTableResponse createResponse = tableManagementService.createTableWithComponents(request);
            AgentResult result = AgentResult.builder()
                    .type("vector_component_commit")
                    .success(Boolean.TRUE.equals(createResponse.getSuccess()))
                    .error(createResponse.getError())
                    .tableName(createResponse.getTableName())
                    .remapComponentId(createResponse.getRemapComponentId())
                    .sinkComponentId(createResponse.getSinkComponentId())
                    .datasourceId(plan.datasourceId())
                    .datasourceName(plan.datasourceName())
                    .warnings(plan.warnings())
                    .build();

            if (!Boolean.TRUE.equals(createResponse.getSuccess())) {
                return AgentChatResponse.builder()
                        .success(false)
                        .intent("vector_component_commit")
                        .answer("确认创建失败：" + StringUtils.defaultString(createResponse.getError(), "未知错误"))
                        .error(createResponse.getError())
                        .datasourceId(plan.queryableDatasourceId())
                        .datasourceName(plan.datasourceName())
                        .result(result)
                        .suggestions(List.of("重新生成 Vector 组件计划", "检查 ClickHouse 数据源连接", "换一个表名后重试"))
                        .build();
            }

            planCache.invalidate(plan.planId());
            return AgentChatResponse.builder()
                    .success(true)
                    .intent("vector_component_commit")
                    .answer(String.format("已创建 ClickHouse 表 %s，并创建 Remap 组件 %s、Sink 组件 %s。",
                            createResponse.getTableName(),
                            createResponse.getRemapComponentId(),
                            createResponse.getSinkComponentId()))
                    .datasourceId(createResponse.getSinkComponentId())
                    .datasourceName(plan.datasourceName())
                    .result(result)
                    .suggestions(List.of("查看字段结构", "查询最近15分钟日志", "继续生成另一个 Vector 组件"))
                    .build();
        } catch (Exception ex) {
            log.error("确认创建 Vector 组件计划失败, planId={}", planId, ex);
            return commitError("确认创建失败：" + ex.getMessage(), plan);
        }
    }

    private AgentChatResponse commitError(String message, VectorComponentPlan plan) {
        AgentResult result = AgentResult.builder()
                .type("vector_component_commit")
                .success(false)
                .error(message)
                .tableName(plan != null ? plan.tableName() : null)
                .datasourceId(plan != null ? plan.datasourceId() : null)
                .datasourceName(plan != null ? plan.datasourceName() : null)
                .warnings(plan != null ? plan.warnings() : null)
                .build();
        return AgentChatResponse.builder()
                .success(false)
                .intent("vector_component_commit")
                .answer(message)
                .error(message)
                .datasourceId(plan != null ? plan.queryableDatasourceId() : null)
                .datasourceName(plan != null ? plan.datasourceName() : null)
                .result(result)
                .suggestions(List.of("重新生成 Vector 组件计划", "检查日志样本和表名", "查看字段结构"))
                .build();
    }

    private void validateContext(AgentExecutionContext context) {
        if (context == null || ObjectUtils.isEmpty(context.userId()) || StringUtils.isBlank(context.sessionId())) {
            throw new IllegalStateException("智能助手会话信息不完整，请刷新页面后重新生成组件计划");
        }
    }

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

    private void validateClickHouseDatasource(Datasource datasource) {
        if (datasource == null) {
            throw new IllegalArgumentException("ClickHouse 数据源不存在");
        }
        if (!StringUtils.equalsIgnoreCase(datasource.getType(), "clickhouse")) {
            throw new IllegalArgumentException("目标数据源不是 ClickHouse，当前类型为 " + datasource.getType());
        }
    }

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

    private boolean looksLikeInstruction(String line) {
        String lower = StringUtils.lowerCase(line, Locale.ROOT);
        return StringUtils.isBlank(lower)
                || AgentToolSupport.containsAny(lower, "帮我", "生成", "创建", "vector", "组件", "正则", "入库", "表名", "datasource");
    }

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

    private boolean hasNamedCaptureGroup(String regexPattern) {
        String regex = StringUtils.defaultString(regexPattern);
        return regex.contains("(?P<") || regex.contains("(?<");
    }

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

    private String buildFieldComment(ParseLogResponse.ParsedFieldDTO field) {
        if (field.getSuggestion() != null && StringUtils.isNotBlank(field.getSuggestion().getReason())) {
            return field.getSuggestion().getReason();
        }
        if (StringUtils.equalsIgnoreCase(field.getName(), "raw")) {
            return "原始日志";
        }
        return "";
    }

    private String resolveFieldType(String fieldName, String inferredType) {
        if (StringUtils.equalsIgnoreCase(fieldName, "raw")) {
            return "String";
        }
        return normalizeClickHouseType(inferredType);
    }

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

    private GenerateDDLRequest.FieldDefinition toDdlField(FieldPlan field) {
        GenerateDDLRequest.FieldDefinition definition = new GenerateDDLRequest.FieldDefinition();
        definition.setName(field.name());
        definition.setType(field.type());
        definition.setComment(field.comment());
        definition.setNullable(false);
        return definition;
    }

    private List<CreateTableRequest.VisualParsedField> toVisualParsedFields(List<FieldPlan> fields) {
        return fields.stream().map(field -> {
            CreateTableRequest.VisualParsedField visualField = new CreateTableRequest.VisualParsedField();
            visualField.setName(field.name());
            visualField.setNewName(field.name());
            visualField.setDeleted(false);
            visualField.setType(field.type());
            visualField.setValue(field.sampleValue());
            visualField.setComment(field.comment());
            return visualField;
        }).toList();
    }

    private void ensureTableDoesNotExist(String datasourceId, String tableName) {
        List<Map<String, Object>> tables = tableManagementService.listTables(datasourceId);
        boolean exists = tables.stream()
                .map(table -> AgentToolSupport.stringify(table.get("name")))
                .anyMatch(name -> StringUtils.equalsIgnoreCase(name, tableName));
        if (exists) {
            throw new IllegalArgumentException("目标表已存在，请更换表名后重新生成计划：" + tableName);
        }
    }

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
                .fields(plan.fields().stream().map(this::toResultField).toList())
                .ddl(plan.ddl())
                .warnings(plan.warnings())
                .build();
    }

    private Map<String, Object> toResultField(FieldPlan field) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", field.name());
        result.put("type", field.type());
        result.put("sampleValue", field.sampleValue());
        result.put("suggestion", field.suggestion());
        result.put("comment", field.comment());
        return result;
    }

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

    private boolean containsFieldName(List<String> usedNames, String candidate) {
        return usedNames.stream().anyMatch(used -> StringUtils.equalsIgnoreCase(used, candidate));
    }

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

    private record FieldPlan(String name, String type, Object sampleValue, String suggestion, String comment) {
    }

    private record VectorComponentPlan(String planId,
                                       Long userId,
                                       String sessionId,
                                       String queryableDatasourceId,
                                       String datasourceId,
                                       String datasourceName,
                                       String tableName,
                                       String logSample,
                                       String regexPattern,
                                       String vrlScript,
                                       List<FieldPlan> fields,
                                       String ddl,
                                       List<String> warnings) {
    }
}
