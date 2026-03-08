package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.config.LangChain4jChatModelProperties;
import cn.mw.loganalysis.agent.dto.AgentChatRequest;
import cn.mw.loganalysis.agent.dto.AgentChatResponse;
import cn.mw.loganalysis.agent.dto.AgentChatMessage;
import cn.mw.loganalysis.agent.dto.AgentResult;
import cn.mw.loganalysis.agent.dto.AgentToolCall;
import cn.mw.loganalysis.agent.dto.AgentConversationDetail;
import cn.mw.loganalysis.agent.dto.AgentConversationSummary;
import cn.mw.loganalysis.stats.dto.LogQueryRequest;
import cn.mw.loganalysis.stats.dto.StatsQueryRequest;
import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import cn.mw.loganalysis.stats.service.query.FieldInfo;
import cn.mw.loganalysis.vector.entity.ConfigComponent;
import cn.mw.loganalysis.vector.service.ConfigComponentService;
import com.fasterxml.jackson.core.JsonParseException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesStreamingChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 第一版日志智能助手
 * 基于规则识别意图，并调度现有只读查询能力
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogAnalysisAgentService {

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern RELATIVE_RANGE_PATTERN = Pattern.compile("最近\\s*([0-9一二两三四五六七八九十半]+)\\s*(分钟|小时|天|周)");
    private static final Pattern QUOTED_KEYWORD_PATTERN = Pattern.compile("[\"“](.+?)[\"”]");
    private static final Pattern EXPLICIT_KEYWORD_PATTERN = Pattern.compile("(?:包含|搜索|查找|关键词|关键字|message包含|message含有)\\s*[:：]?\\s*([\\p{L}\\p{N}._:/-]+)");
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final DynamicLogQueryService dynamicLogQueryService;
    private final ConfigComponentService configComponentService;
    private final ApplicationContext applicationContext;
    private final AgentConversationMemoryService conversationMemoryService;
    private final AgentConversationHistoryService conversationHistoryService;
    private final LogAnalysisAgentTools logAnalysisAgentTools;
    private final LangChain4jChatModelProperties chatModelProperties;
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ObjectProvider<OpenAiResponsesStreamingChatModel> responsesStreamingChatModelProvider;
    /**
     * LLM 执行器是可选 Bean。
     * 它只有在当前所选协议对应的模型 Bean 和执行器 Bean 都满足时才会注册成功，
     * 所以这里不能直接强依赖注入，否则在未配置模型时应用会启动失败。
     */
    private final ObjectProvider<LangChain4jLogAnalysisAgentExecutor> llmExecutorProvider;
    /**
     * 避免在每次请求都重复打印“LLM 未启用”的提示日志。
     */
    private final AtomicBoolean llmUnavailableLogged = new AtomicBoolean(false);

    /**
     * 总开关。
     * 为 false 时，无论模型是否已配置，都直接走规则版解析逻辑。
     */
    @Value("${agent.llm.enabled:true}")
    private boolean llmEnabled;

    /**
     * 当 LLM 链路运行时报错时，是否自动退回规则版。
     * 这样可以保证模型暂时不可用时，智能助手至少还有基本查询能力。
     */
    @Value("${agent.llm.fallback-on-error:true}")
    private boolean fallbackOnError;

    public AgentChatResponse chat(AgentChatRequest request, Long userId) {
        AgentChatRequest hydratedRequest = conversationHistoryService.hydrateRequestHistory(request, userId);
        AgentConversationMemoryService.PreparedAgentChatRequest preparedRequest = conversationMemoryService.prepare(hydratedRequest);
        AgentChatRequest effectiveRequest = preparedRequest.request();
        String sessionId = preparedRequest.sessionId();

        if (llmEnabled) {
            /**
             * ObjectProvider#getIfAvailable() 返回 null 的含义很直接：
             * Spring 容器里当前没有 LangChain4jLogAnalysisAgentExecutor 这个 Bean。
             *
             * 常见原因只有几类：
             * 1. agent.llm.enabled=false，直接关闭了 LLM 链路
             * 2. 当前协议对应的模型 Bean 没创建成功，例如未配置 api-key
             * 3. executor 自身因为协议和模型不匹配而未注册成功
             *
             * 这里用可选获取而不是直接注入，就是为了让“未配置模型”时仍然能回退到规则版。
             */
            LangChain4jLogAnalysisAgentExecutor llmExecutor = llmExecutorProvider.getIfAvailable();
            if (llmExecutor != null) {
                try {
                    AgentChatResponse response = llmExecutor.chat(effectiveRequest);
                    return finalizeResponse(userId, sessionId, effectiveRequest.getMessage(), response);
                } catch (Exception ex) {
                    String llmFailureMessage = describeLlmFailure(ex);
                    log.error("LangChain4j 智能助手处理失败, datasourceId={}, message={}",
                            effectiveRequest.getDatasourceId(), effectiveRequest.getMessage(), ex);
                    log.warn("LangChain4j 智能助手已回退到规则版: {}", llmFailureMessage);
                    if (!fallbackOnError) {
                        return finalizeResponse(userId, sessionId, effectiveRequest.getMessage(),
                                buildErrorResponse("智能助手处理失败: " + llmFailureMessage));
                    }
                }
            } else if (llmUnavailableLogged.compareAndSet(false, true)) {
                log.info("LangChain4j 执行器未注册，当前请求回退到规则版。诊断信息: llmEnabled={}, wireApi={}, apiKeyPresent={}, chatModelPresent={}, responsesModelPresent={}, chatModelBeans={}, responsesModelBeans={}, executorBeans={}",
                        llmEnabled,
                        chatModelProperties.getWireApi(),
                        StringUtils.hasText(chatModelProperties.getApiKey()),
                        chatModelProvider.getIfAvailable() != null,
                        responsesStreamingChatModelProvider.getIfAvailable() != null,
                        Arrays.toString(applicationContext.getBeanNamesForType(ChatModel.class)),
                        Arrays.toString(applicationContext.getBeanNamesForType(OpenAiResponsesStreamingChatModel.class)),
                        Arrays.toString(applicationContext.getBeanNamesForType(LangChain4jLogAnalysisAgentExecutor.class)));
            }
        }

        String message = normalizeMessage(effectiveRequest.getMessage());
        if (!StringUtils.hasText(effectiveRequest.getDatasourceId())) {
            return finalizeResponse(userId, sessionId, effectiveRequest.getMessage(), buildErrorResponse("请选择一个可查询的数据源后再提问"));
        }

        ConfigComponent datasource = configComponentService.getById(effectiveRequest.getDatasourceId());
        if (datasource == null) {
            return finalizeResponse(userId, sessionId, effectiveRequest.getMessage(), buildErrorResponse("选中的数据源不存在"));
        }

        String effectiveMessage = enrichMessageWithHistory(message, effectiveRequest.getHistory());
        AgentIntent intent = detectIntent(effectiveMessage, datasource.getVectorType());

        AgentExecutionContextHolder.set(new AgentExecutionContext(
                effectiveRequest.getDatasourceId(),
                datasource.getName(),
                datasource.getVectorType()
        ));
        try {
            AgentChatResponse response = switch (intent) {
                case SCHEMA -> handleSchemaIntent(effectiveRequest.getDatasourceId(), datasource.getName());
                case TIMESERIES -> handleTimeseriesIntent(effectiveMessage, effectiveRequest.getDatasourceId(), datasource.getName());
                case TEXT2SQL -> handleText2SqlIntent(effectiveMessage, effectiveRequest.getDatasourceId(), datasource.getName());
                case LOGS -> handleLogsIntent(effectiveMessage, effectiveRequest.getDatasourceId(), datasource.getName());
            };
            return finalizeResponse(userId, sessionId, effectiveRequest.getMessage(), response);
        } catch (Exception ex) {
            log.error("智能助手处理失败, datasourceId={}, message={}", effectiveRequest.getDatasourceId(), effectiveRequest.getMessage(), ex);
            return finalizeResponse(userId, sessionId, effectiveRequest.getMessage(), buildErrorResponse("处理失败: " + ex.getMessage()));
        } finally {
            AgentExecutionContextHolder.clear();
        }
    }

    private AgentChatResponse handleSchemaIntent(String datasourceId, String datasourceName) {
        long startedAt = System.currentTimeMillis();
        List<FieldInfo> schema = dynamicLogQueryService.getTableSchema(datasourceId);

        List<String> timestampFields = schema.stream()
                .filter(field -> Boolean.TRUE.equals(field.getIsTimestamp()))
                .map(FieldInfo::getName)
                .toList();
        List<String> dimensions = schema.stream()
                .filter(field -> Boolean.TRUE.equals(field.getIsStatsDimension()))
                .map(FieldInfo::getName)
                .toList();
        List<String> contentFields = schema.stream()
                .filter(field -> Boolean.TRUE.equals(field.getIsContentField()))
                .map(FieldInfo::getName)
                .toList();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("fieldCount", schema.size());
        summary.put("timestampFields", timestampFields);
        summary.put("statsDimensions", dimensions);
        summary.put("contentFields", contentFields);

        String answer = String.format(
                "已读取 %s 的字段结构，共 %d 个字段。时间字段：%s；适合做统计维度的字段：%s；主要内容字段：%s。",
                datasourceName,
                schema.size(),
                joinOrFallback(timestampFields, "未识别"),
                joinOrFallback(dimensions.stream().limit(6).toList(), "暂无"),
                joinOrFallback(contentFields, "暂无")
        );

        AgentToolCall toolCall = AgentToolCall.builder()
                .toolName("get_schema")
                .toolLabel("读取字段结构")
                .status("completed")
                .input(Map.of("datasourceId", datasourceId))
                .summary("已获取字段结构，共 " + schema.size() + " 个字段")
                .durationMs(System.currentTimeMillis() - startedAt)
                .build();

        AgentResult result = AgentResult.builder()
                .type("schema")
                .schema(schema)
                .summary(summary)
                .build();

        return buildSuccessResponse("schema", answer, datasourceId, datasourceName, List.of(toolCall), result,
                List.of("最近1小时有哪些错误日志", "看最近24小时日志趋势", "搜索包含 \"timeout\" 的日志"));
    }

    private AgentChatResponse handleLogsIntent(String message, String datasourceId, String datasourceName) {
        TimeWindow timeWindow = resolveTimeWindow(message, AgentIntent.LOGS);
        long startedAt = System.currentTimeMillis();

        LogQueryRequest request = new LogQueryRequest();
        request.setDatasourceId(datasourceId);
        request.setStartTime(timeWindow.start());
        request.setEndTime(timeWindow.end());
        request.setPageNum(1);
        request.setPageSize(DEFAULT_PAGE_SIZE);

        List<LogQueryRequest.FieldFilter> fieldFilters = buildSeverityFilters(message);
        if (!fieldFilters.isEmpty()) {
            request.setFieldFilters(fieldFilters);
        }

        List<LogQueryRequest.MessageCondition> messageConditions = buildMessageConditions(message);
        if (!messageConditions.isEmpty()) {
            request.setMessageConditions(messageConditions);
        }

        Map<String, Object> queryResult = dynamicLogQueryService.queryLogs(datasourceId, request);
        List<Map<String, Object>> logs = castList(queryResult.get("data"));
        long total = toLong(queryResult.get("total"));

        Map<String, Long> severitySummary = logs.stream()
                .map(row -> stringify(row.get("severity")))
                .filter(StringUtils::hasText)
                .collect(Collectors.groupingBy(value -> value, LinkedHashMap::new, Collectors.counting()));

        String keyword = extractKeyword(message);
        String answer = buildLogsAnswer(datasourceName, timeWindow, total, logs, severitySummary, keyword);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", total);
        summary.put("returned", logs.size());
        summary.put("severities", severitySummary);
        if (StringUtils.hasText(keyword)) {
            summary.put("keyword", keyword);
        }

        Map<String, Object> toolInput = new LinkedHashMap<>();
        toolInput.put("datasourceId", datasourceId);
        toolInput.put("startTime", DATETIME_FORMATTER.format(timeWindow.start()));
        toolInput.put("endTime", DATETIME_FORMATTER.format(timeWindow.end()));
        if (!fieldFilters.isEmpty()) {
            toolInput.put("fieldFilters", fieldFilters);
        }
        if (!messageConditions.isEmpty()) {
            toolInput.put("messageConditions", messageConditions);
        }

        AgentToolCall toolCall = AgentToolCall.builder()
                .toolName("query_logs")
                .toolLabel("查询日志列表")
                .status("completed")
                .input(toolInput)
                .summary(String.format("返回 %d 条日志（共 %d 条）", logs.size(), total))
                .durationMs(System.currentTimeMillis() - startedAt)
                .build();

        AgentResult result = AgentResult.builder()
                .type("logs")
                .timeRangeLabel(timeWindow.label())
                .logs(logs)
                .total(total)
                .pageNum(1)
                .pageSize(DEFAULT_PAGE_SIZE)
                .summary(summary)
                .build();

        return buildSuccessResponse("logs", answer, datasourceId, datasourceName, List.of(toolCall), result,
                List.of("看最近24小时日志趋势", "查看这个数据源的字段结构", "再查最近15分钟的日志"));
    }

    private AgentChatResponse handleTimeseriesIntent(String message, String datasourceId, String datasourceName) {
        TimeWindow timeWindow = resolveTimeWindow(message, AgentIntent.TIMESERIES);
        String granularity = resolveGranularity(message, timeWindow);
        long startedAt = System.currentTimeMillis();

        StatsQueryRequest request = new StatsQueryRequest();
        request.setDatasourceId(datasourceId);
        request.setStartTime(timeWindow.start());
        request.setEndTime(timeWindow.end());
        request.setGranularity(granularity);

        Map<String, Object> queryResult = dynamicLogQueryService.queryTimeSeries(datasourceId, request);
        List<Map<String, Object>> series = castList(queryResult.get("series"));
        Map<String, Object> summary = buildTimeseriesSummary(series, granularity);
        String answer = buildTimeseriesAnswer(datasourceName, timeWindow, granularity, summary);

        Map<String, Object> toolInput = new LinkedHashMap<>();
        toolInput.put("datasourceId", datasourceId);
        toolInput.put("startTime", DATETIME_FORMATTER.format(timeWindow.start()));
        toolInput.put("endTime", DATETIME_FORMATTER.format(timeWindow.end()));
        toolInput.put("granularity", granularity);

        AgentToolCall toolCall = AgentToolCall.builder()
                .toolName("query_timeseries")
                .toolLabel("查询日志趋势")
                .status("completed")
                .input(toolInput)
                .summary(String.format("返回 %d 个时间点", series.size()))
                .durationMs(System.currentTimeMillis() - startedAt)
                .build();

        AgentResult result = AgentResult.builder()
                .type("timeseries")
                .timeRangeLabel(timeWindow.label())
                .granularity(granularity)
                .series(series)
                .summary(summary)
                .build();

        return buildSuccessResponse("timeseries", answer, datasourceId, datasourceName, List.of(toolCall), result,
                List.of("查看最近1小时日志", "查看字段结构", "再看最近7天的趋势"));
    }

    private AgentChatResponse handleText2SqlIntent(String message, String datasourceId, String datasourceName) {
        AgentToolPayload payload = logAnalysisAgentTools.text2SqlQuery(message);

        AgentToolCall toolCall = AgentToolCall.builder()
                .toolName(payload.getToolName())
                .toolLabel(payload.getToolLabel())
                .status("completed")
                .input(Map.of("query", message))
                .summary(payload.getSummary())
                .durationMs(payload.getDurationMs())
                .build();

        return buildSuccessResponse("text2sql", payload.getSummary(), datasourceId, datasourceName, List.of(toolCall),
                payload.getResult(), List.of("最近1天的数据有多少条", "按 severity 统计最近24小时数量", "统计最近7天每天的日志量"));
    }

    private AgentChatResponse buildSuccessResponse(String intent,
                                                   String answer,
                                                   String datasourceId,
                                                   String datasourceName,
                                                   List<AgentToolCall> toolCalls,
                                                   AgentResult result,
                                                   List<String> suggestions) {
        return AgentChatResponse.builder()
                .success(true)
                .intent(intent)
                .answer(answer)
                .datasourceId(datasourceId)
                .datasourceName(datasourceName)
                .toolCalls(toolCalls)
                .result(result)
                .suggestions(suggestions)
                .build();
    }

    private AgentChatResponse buildErrorResponse(String error) {
        return AgentChatResponse.builder()
                .success(false)
                .error(error)
                .suggestions(List.of("先选择一个可查询数据源", "查看字段结构", "查询最近1小时日志"))
                .build();
    }

    public List<AgentConversationSummary> listConversations(Long userId) {
        return conversationHistoryService.listConversations(userId);
    }

    public AgentConversationDetail getConversation(Long userId, String sessionId) {
        return conversationHistoryService.getConversation(userId, sessionId);
    }

    public void deleteConversation(Long userId, String sessionId) {
        conversationHistoryService.deleteConversation(userId, sessionId);
        conversationMemoryService.forget(sessionId);
    }

    /**
     * 所有对外返回都在这里补上 sessionId，并把本轮 user/assistant 文本写入 memory。
     *
     * 这样做的好处是：
     * 1. LLM 分支和规则分支都共用同一套 session 归档
     * 2. 前端只需要持续传 sessionId，不需要每次把完整历史回传
     * 3. 后续如果要接 summary memory 或持久化存储，这里就是统一收口点
     */
    private AgentChatResponse finalizeResponse(Long userId, String sessionId, String userMessage, AgentChatResponse response) {
        if (response == null) {
            return null;
        }

        response.setSessionId(sessionId);
        conversationMemoryService.remember(
                sessionId,
                response.getDatasourceId(),
                response.getDatasourceName(),
                null,
                userMessage,
                response.getSuccess() ? response.getAnswer() : response.getError()
        );
        conversationHistoryService.saveTurn(
                userId,
                sessionId,
                response.getDatasourceId(),
                response.getDatasourceName(),
                inferDatasourceType(response.getDatasourceId()),
                userMessage,
                response
        );
        return response;
    }

    private String inferDatasourceType(String datasourceId) {
        if (!StringUtils.hasText(datasourceId)) {
            return null;
        }
        ConfigComponent datasource = configComponentService.getById(datasourceId);
        return datasource != null ? datasource.getVectorType() : null;
    }

    private AgentIntent detectIntent(String message, String datasourceType) {
        String lower = message.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "字段", "表结构", "schema", "有哪些列", "哪些字段", "列结构")) {
            return AgentIntent.SCHEMA;
        }
        if (containsAny(lower, "趋势", "时序", "波动", "曲线", "每小时", "每分钟", "走势图")) {
            return AgentIntent.TIMESERIES;
        }
        if (supportsText2Sql(datasourceType)
                && containsAny(lower, "多少条", "多少", "统计", "总数", "数量", "汇总", "排行", "top", "分组", "占比", "平均", "avg", "sum", "max", "min")) {
            return AgentIntent.TEXT2SQL;
        }
        return AgentIntent.LOGS;
    }

    /**
     * 规则回退模式没有 LLM 自己做多轮理解，所以这里做一层很轻的上下文增强。
     *
     * 典型场景：
     * - 上一轮: 搜索包含 "timeout" 的日志
     * - 这一轮: 那最近24小时呢
     *
     * 对规则解析来说，单看“那最近24小时呢”信息不够；
     * 拼上最近一条 user 消息后，就能继续复用上一轮的关键词和意图。
     */
    private String enrichMessageWithHistory(String message, List<AgentChatMessage> history) {
        String normalized = normalizeMessage(message);
        if (!StringUtils.hasText(normalized) || !looksContextDependent(normalized)) {
            return normalized;
        }

        String lastUserMessage = latestUserMessage(history);
        if (!StringUtils.hasText(lastUserMessage)) {
            return normalized;
        }
        return lastUserMessage + " " + normalized;
    }

    private boolean looksContextDependent(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        if (message.length() <= 12) {
            return true;
        }
        return containsAny(lower, "再", "继续", "那", "这个", "这些", "呢", "同样", "也", "改成", "换成", "刚才", "上一个");
    }

    private String latestUserMessage(List<AgentChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return null;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            AgentChatMessage message = history.get(i);
            if (message != null
                    && "user".equalsIgnoreCase(message.getRole())
                    && StringUtils.hasText(message.getContent())) {
                return normalizeMessage(message.getContent());
            }
        }
        return null;
    }

    private boolean supportsText2Sql(String datasourceType) {
        return "clickhouse".equalsIgnoreCase(datasourceType);
    }

    private TimeWindow resolveTimeWindow(String message, AgentIntent intent) {
        LocalDateTime now = LocalDateTime.now();
        Matcher matcher = RELATIVE_RANGE_PATTERN.matcher(message);
        if (matcher.find()) {
            if ("半".equals(matcher.group(1)) && "小时".equals(matcher.group(2))) {
                return new TimeWindow(now.minusMinutes(30), now, "最近30分钟");
            }
            Integer amount = parseChineseNumber(matcher.group(1));
            String unit = matcher.group(2);
            if (amount != null && amount > 0) {
                if ("分钟".equals(unit)) {
                    return new TimeWindow(now.minusMinutes(amount), now, "最近" + amount + "分钟");
                }
                if ("小时".equals(unit)) {
                    return new TimeWindow(now.minusHours(amount), now, "最近" + amount + "小时");
                }
                if ("天".equals(unit)) {
                    return new TimeWindow(now.minusDays(amount), now, "最近" + amount + "天");
                }
                if ("周".equals(unit)) {
                    return new TimeWindow(now.minusWeeks(amount), now, "最近" + amount + "周");
                }
            }
        }

        if (message.contains("今天")) {
            return new TimeWindow(LocalDateTime.of(LocalDate.now(), LocalTime.MIN), now, "今天");
        }
        if (message.contains("昨天")) {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            return new TimeWindow(LocalDateTime.of(yesterday, LocalTime.MIN), LocalDateTime.of(yesterday, LocalTime.MAX), "昨天");
        }
        if (message.contains("最近一周") || message.contains("近7天")) {
            return new TimeWindow(now.minusDays(7), now, "最近7天");
        }
        if (message.contains("最近24小时")) {
            return new TimeWindow(now.minusHours(24), now, "最近24小时");
        }
        if (message.contains("最近1小时") || message.contains("近1小时")) {
            return new TimeWindow(now.minusHours(1), now, "最近1小时");
        }
        if (message.contains("最近15分钟") || message.contains("近15分钟")) {
            return new TimeWindow(now.minusMinutes(15), now, "最近15分钟");
        }

        if (intent == AgentIntent.TIMESERIES) {
            return new TimeWindow(now.minusHours(24), now, "最近24小时");
        }
        return new TimeWindow(now.minusHours(1), now, "最近1小时");
    }

    private String resolveGranularity(String message, TimeWindow timeWindow) {
        if (containsAny(message, "每分钟", "分钟趋势", "按分钟")) {
            return "1m";
        }
        if (containsAny(message, "每5分钟", "五分钟")) {
            return "5m";
        }
        if (containsAny(message, "每小时", "按小时")) {
            return "1h";
        }
        if (containsAny(message, "每天", "按天")) {
            return "1d";
        }

        long hours = java.time.Duration.between(timeWindow.start(), timeWindow.end()).toHours();
        if (hours <= 2) {
            return "1m";
        }
        if (hours <= 24) {
            return "5m";
        }
        if (hours <= 24 * 7L) {
            return "1h";
        }
        return "1d";
    }

    private List<LogQueryRequest.FieldFilter> buildSeverityFilters(String message) {
        List<String> values = new ArrayList<>();
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("error") || message.contains("错误") || message.contains("异常")) {
            values.add("error");
        }
        if (lower.contains("warn") || message.contains("告警") || message.contains("警告")) {
            values.add("warn");
        }
        if (lower.contains("info") || message.contains("信息日志")) {
            values.add("info");
        }
        if (lower.contains("debug") || message.contains("调试")) {
            values.add("debug");
        }

        if (values.isEmpty()) {
            return Collections.emptyList();
        }

        LogQueryRequest.FieldFilter filter = new LogQueryRequest.FieldFilter();
        filter.setField("levels");
        filter.setType("include");
        filter.setValues(values.stream().distinct().toList());
        return List.of(filter);
    }

    private List<LogQueryRequest.MessageCondition> buildMessageConditions(String message) {
        String keyword = extractKeyword(message);
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }

        LogQueryRequest.MessageCondition condition = new LogQueryRequest.MessageCondition();
        condition.setOperator("contains");
        condition.setValue(keyword);
        return List.of(condition);
    }

    private String extractKeyword(String message) {
        Matcher quotedMatcher = QUOTED_KEYWORD_PATTERN.matcher(message);
        if (quotedMatcher.find()) {
            return quotedMatcher.group(1).trim();
        }

        Matcher explicitMatcher = EXPLICIT_KEYWORD_PATTERN.matcher(message);
        if (explicitMatcher.find()) {
            return explicitMatcher.group(1).trim();
        }

        String normalized = message
                .replaceAll("最近\\s*[0-9一二两三四五六七八九十半]+\\s*(分钟|小时|天|周)", " ")
                .replaceAll("(今天|昨天|趋势|时序|波动|曲线|走势图|字段|表结构|schema|日志|查一下|查看|查询|搜索|帮我|帮忙|看看|一下|哪些|有没有|最近|错误|异常|告警|警告)", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (!StringUtils.hasText(normalized)) {
            return null;
        }

        return Arrays.stream(normalized.split("\\s+"))
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .filter(token -> !containsAny(token.toLowerCase(Locale.ROOT), "小时", "分钟", "天", "周"))
                .findFirst()
                .orElse(null);
    }

    private String buildLogsAnswer(String datasourceName,
                                   TimeWindow timeWindow,
                                   long total,
                                   List<Map<String, Object>> logs,
                                   Map<String, Long> severitySummary,
                                   String keyword) {
        if (total == 0) {
            return String.format("在 %s 中未查到 %s 内匹配的日志。可以换一个时间范围，或者调整关键词后再查。",
                    datasourceName, timeWindow.label());
        }

        StringBuilder answer = new StringBuilder();
        answer.append(String.format("已在 %s 中查询 %s 的日志，共 %d 条，当前展示前 %d 条。",
                datasourceName, timeWindow.label(), total, logs.size()));

        if (StringUtils.hasText(keyword)) {
            answer.append(" 关键词：").append(keyword).append("。");
        }

        if (!severitySummary.isEmpty()) {
            String severityText = severitySummary.entrySet().stream()
                    .map(entry -> entry.getKey() + " " + entry.getValue() + "条")
                    .collect(Collectors.joining("，"));
            answer.append(" 当前页级别分布：").append(severityText).append("。");
        }

        if (!logs.isEmpty()) {
            Map<String, Object> firstLog = logs.get(0);
            answer.append(" 最新一条日志时间为 ")
                    .append(stringify(firstLog.get("timestamp")))
                    .append("，摘要：")
                    .append(truncate(stringify(firstLog.get("message")), 80))
                    .append("。");
        }

        return answer.toString();
    }

    private Map<String, Object> buildTimeseriesSummary(List<Map<String, Object>> series, String granularity) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("pointCount", series.size());
        summary.put("granularity", granularity);

        long totalCount = 0L;
        long peakCount = 0L;
        String peakTimestamp = null;

        for (Map<String, Object> point : series) {
            long count = toLong(point.get("count"));
            totalCount += count;
            if (count >= peakCount) {
                peakCount = count;
                peakTimestamp = stringify(point.get("timestamp"));
            }
        }

        summary.put("totalCount", totalCount);
        summary.put("peakCount", peakCount);
        summary.put("peakTimestamp", peakTimestamp);
        return summary;
    }

    private String buildTimeseriesAnswer(String datasourceName,
                                         TimeWindow timeWindow,
                                         String granularity,
                                         Map<String, Object> summary) {
        long pointCount = toLong(summary.get("pointCount"));
        long totalCount = toLong(summary.get("totalCount"));
        long peakCount = toLong(summary.get("peakCount"));
        String peakTimestamp = stringify(summary.get("peakTimestamp"));

        if (pointCount == 0) {
            return String.format("在 %s 中没有查到 %s 的趋势数据。可以扩大时间范围后再试。",
                    datasourceName, timeWindow.label());
        }

        return String.format(
                "已生成 %s 在 %s 的日志趋势，粒度为 %s，共 %d 个时间点，总日志量 %d。峰值出现在 %s，计数 %d。",
                datasourceName,
                timeWindow.label(),
                granularity,
                pointCount,
                totalCount,
                StringUtils.hasText(peakTimestamp) ? peakTimestamp : "未知时间点",
                peakCount
        );
    }

    private String normalizeMessage(String message) {
        return message == null ? "" : message.replaceAll("\\s+", " ").trim();
    }

    /**
     * 把底层模型/网关异常转换成可操作的诊断信息。
     *
     * 当前项目同时支持两条 OpenAI 兼容链路：
     * 1. Chat Completions: /v1/chat/completions
     * 2. Responses API: /v1/responses
     *
     * 如果网关提示 legacy protocol 不支持，基本就说明当前协议选错了。
     */
    private String describeLlmFailure(Throwable throwable) {
        String jsonParseMessage = firstCauseMessage(throwable, JsonParseException.class);
        if (jsonParseMessage != null && jsonParseMessage.contains("Unexpected character ('<'")) {
            return "模型网关返回了 HTML 页面，而不是 OpenAI JSON。请确认 base-url 是否正确，以及当前 wire-api 与网关支持的协议一致。";
        }

        String httpMessage = firstCauseMessage(throwable, HttpException.class);
        if (StringUtils.hasText(httpMessage)) {
            String normalized = httpMessage.toLowerCase(Locale.ROOT);
            if (normalized.contains("insufficient_quota") || normalized.contains("quota")) {
                return "模型网关返回额度不足或计费限制，请检查 API key 对应账户的 quota 或 billing。";
            }
            if (normalized.contains("401") || normalized.contains("unauthorized")) {
                return "模型网关鉴权失败，请检查 API key 或网关鉴权方式。";
            }
            if (normalized.contains("unsupported legacy protocol") || normalized.contains("/v1/responses")) {
                return "当前网关不支持 /v1/chat/completions，请把 langchain4j.open-ai.chat-model.wire-api 配置为 responses。";
            }
            if (normalized.contains("upstream_error")) {
                return "中转站已接收到 /v1/responses 请求，但上游模型仍返回 upstream_error。当前项目已改为最小请求集；如果仍报错，基本说明该网关对 Responses 工具调用支持不完整，或者模型名在该网关不可用。";
            }
            if (normalized.contains("404")) {
                return "模型网关路径不存在，请确认 base-url 是否应包含 /v1，以及当前协议对应的路径是否被网关支持。";
            }
            return httpMessage;
        }

        Throwable root = rootCause(throwable);
        if (root != null && StringUtils.hasText(root.getMessage())) {
            return root.getMessage();
        }
        return "模型调用失败，请检查网关兼容性、鉴权和返回格式。";
    }

    private String firstCauseMessage(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return current.getMessage();
            }
            current = current.getCause();
        }
        return null;
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        Throwable last = throwable;
        while (current != null) {
            last = current;
            current = current.getCause();
        }
        return last;
    }

    private boolean containsAny(String text, String... keywords) {
        return Arrays.stream(keywords).anyMatch(text::contains);
    }

    private Integer parseChineseNumber(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        return switch (token) {
            case "半" -> 30;
            case "一" -> 1;
            case "二", "两" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> {
                try {
                    yield Integer.parseInt(token);
                } catch (NumberFormatException ex) {
                    yield null;
                }
            }
        };
    }

    private String joinOrFallback(List<String> values, String fallback) {
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        return String.join("、", values);
    }

    private String stringify(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castList(Object value) {
        if (value instanceof List<?>) {
            return ((List<?>) value).stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        return new ArrayList<>();
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private enum AgentIntent {
        SCHEMA,
        LOGS,
        TIMESERIES,
        TEXT2SQL
    }

    private record TimeWindow(LocalDateTime start, LocalDateTime end, String label) {
    }
}
