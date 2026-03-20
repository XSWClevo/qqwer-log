package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.config.LangChain4jChatModelProperties;
import cn.mw.loganalysis.agent.dto.AgentChatMessage;
import cn.mw.loganalysis.agent.dto.AgentChatRequest;
import cn.mw.loganalysis.agent.dto.AgentChatResponse;
import cn.mw.loganalysis.agent.dto.AgentResult;
import cn.mw.loganalysis.agent.dto.AgentStreamEvent;
import cn.mw.loganalysis.agent.dto.AgentToolCall;
import cn.mw.loganalysis.vector.entity.ConfigComponent;
import cn.mw.loganalysis.vector.service.ConfigComponentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesStreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * LangChain4j 驱动的日志助手执行器。
 *
 * 这个类负责三件事：
 * 1. 把当前请求和历史对话整理成 prompt
 * 2. 调用 LangChain4j 生成的 AiService 代理
 * 3. 把 LangChain4j 的 tool execution 结果转换成前端已有的返回结构
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "agent.llm", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "langchain4j.open-ai.chat-model", name = "api-key")
public class LangChain4jLogAnalysisAgentExecutor {

    private static final String SYSTEM_PROMPT = """
            你是企业日志分析平台里的智能助手。
            你的职责只有四类：
            1. 读取当前数据源的字段结构
            2. 查询当前数据源的日志列表
            3. 查询当前数据源的日志趋势
            4. 对 ClickHouse 数据源执行自然语言 SQL 查询

            你必须遵守以下规则：
            - 所有结论都必须基于工具返回的数据，不允许编造字段、数量、时间范围或日志内容。
            - 回答字段、表结构、有哪些列、时间字段、统计维度时，先调用 get_schema。
            - 回答日志明细、错误日志、搜索关键词、最近多少时间的日志时，先调用 query_logs。
            - 回答趋势、时序、波动、按分钟/小时统计时，先调用 query_timeseries。
            - 当问题属于开放式统计、聚合、排行、按字段分组、多少条、做图、生成报表数据，并且当前数据源类型是 clickhouse 时，优先调用 text2sql_query。
            - text2sql_query 已经会把自然语言、当前表结构和数据源信息交给既有 text2sql 服务处理，你不能自行编造 SQL。
            - 如果当前数据源不是 clickhouse，不要调用 text2sql_query。
            - 默认一次问题只调用一个最合适的工具；只有确实必要时才继续调用第二个工具。
            - 回答使用简体中文，保持简洁，先给结论，再点出关键数字或时间点。
            - 如果用户的问题超出这四类能力，明确说明当前只支持字段结构、日志查询、趋势查询和 ClickHouse 自然语言统计查询。
            """;

    private final LangChain4jLogAnalysisAssistant assistant;
    private final LangChain4jStreamingLogAnalysisAssistant streamingAssistant;
    private final ConfigComponentService configComponentService;
    private final ObjectMapper objectMapper;
    private final boolean responsesWireApi;
    private final Duration llmTimeout;

    public LangChain4jLogAnalysisAgentExecutor(ObjectProvider<ChatModel> chatModelProvider,
                                               @Qualifier("activeStreamingChatModel")
                                               ObjectProvider<StreamingChatModel> activeStreamingChatModelProvider,
                                               LogAnalysisAgentTools logAnalysisAgentTools,
                                               ConfigComponentService configComponentService,
                                               ObjectMapper objectMapper,
                                               LangChain4jChatModelProperties chatModelProperties) {
        /**
         * executor 需要同时兼容两种模型协议：
         * 1. chat-completions: 直接使用同步 ChatModel
         * 2. responses: 使用 StreamingChatModel，再在服务端等待完整结果
         *
         * 之所以在这里分流，而不是再拆多个 service，是为了把前端协议和上层调用保持不变。
         */
        this.responsesWireApi = chatModelProperties.usesResponsesApi();
        this.llmTimeout = chatModelProperties.getTimeout() != null ? chatModelProperties.getTimeout() : Duration.ofSeconds(60);
        StreamingChatModel activeStreamingChatModel = activeStreamingChatModelProvider.getIfAvailable();
        if (activeStreamingChatModel == null) {
            throw new IllegalStateException("当前协议对应的 StreamingChatModel Bean 未注册，无法启用流式智能助手");
        }
        this.streamingAssistant = AiServices.builder(LangChain4jStreamingLogAnalysisAssistant.class)
                .streamingChatModel(activeStreamingChatModel)
                .systemMessage(SYSTEM_PROMPT)
                .tools(logAnalysisAgentTools)
                .maxSequentialToolsInvocations(4)
                .build();
        if (responsesWireApi) {
            this.assistant = null;
        } else {
            ChatModel chatModel = chatModelProvider.getIfAvailable();
            if (chatModel == null) {
                throw new IllegalStateException("当前配置使用 Chat Completions，但 ChatModel Bean 未注册");
            }
            this.assistant = AiServices.builder(LangChain4jLogAnalysisAssistant.class)
                    .chatModel(chatModel)
                    .systemMessage(SYSTEM_PROMPT)
                    .tools(logAnalysisAgentTools)
                    .maxSequentialToolsInvocations(4)
                    .build();
        }
        this.configComponentService = configComponentService;
        this.objectMapper = objectMapper;
    }

    public AgentChatResponse chat(AgentChatRequest request) {
        if (!StringUtils.hasText(request.getDatasourceId())) {
            return buildErrorResponse("请选择一个可查询的数据源后再提问");
        }

        ConfigComponent datasource = configComponentService.getById(request.getDatasourceId());
        if (datasource == null) {
            return buildErrorResponse("选中的数据源不存在");
        }

        /**
         * Tool 执行时需要知道当前数据源是谁，但 LangChain4j 的 @Tool 方法签名里不希望重复暴露 datasourceId。
         * 所以这里用 ThreadLocal 在一次请求线程内传递上下文。
         */
        AgentExecutionContextHolder.set(new AgentExecutionContext(
                request.getDatasourceId(),
                datasource.getName(),
                datasource.getVectorType()
        ));
        try {
            String prompt = buildPrompt(request, datasource);
            Result<String> result = responsesWireApi
                    ? executeStreamingAssistant(prompt, null)
                    : assistant.chat(prompt);
            return buildResponse(request.getDatasourceId(), datasource.getName(), result);
        } finally {
            AgentExecutionContextHolder.clear();
        }
    }

    /**
     * 流式接口和 Responses 同步兼容层都复用这条执行逻辑。
     *
     * 这里做两件事：
     * 1. 从 LangChain4j 的 TokenStream 里拿到 token/tool 事件
     * 2. 在服务端继续等待最终完整 ChatResponse，用来生成和旧接口兼容的 AgentChatResponse
     *
     * 也就是说，流式模式会“边发事件，边等待最终结果”；
     * 同步模式则只是把 emitter 传 null，当作一次普通聚合调用。
     */
    private Result<String> executeStreamingAssistant(String prompt, AgentStreamEventEmitter emitter) {
        /**
         * LangChain4j 的 token/tool 回调是异步触发的。
         * 这里仍然把完整 ToolExecution 和最终 ChatResponse 收集起来，
         * 这样无论是同步接口还是流式接口，最终都能复用同一套 buildResponse 逻辑。
         */
        List<ToolExecution> toolExecutions = Collections.synchronizedList(new ArrayList<>());
        CompletableFuture<ChatResponse> responseFuture = new CompletableFuture<>();

        TokenStream tokenStream = streamingAssistant.chat(prompt)
                .onPartialResponse(delta -> safeEmit(emitter, AgentStreamEvent.token(delta)))
                .beforeToolExecution(beforeToolExecution ->
                        safeEmit(emitter, AgentStreamEvent.toolStarted(toStreamingToolCall(beforeToolExecution))))
                .onToolExecuted(toolExecution -> {
                    toolExecutions.add(toolExecution);
                    safeEmit(emitter, AgentStreamEvent.toolFinished(toFinishedToolCall(toolExecution)));
                })
                .onCompleteResponse(responseFuture::complete)
                .onError(responseFuture::completeExceptionally);
        tokenStream.start();

        ChatResponse finalResponse;
        try {
            finalResponse = responseFuture.get(llmTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 Responses API 返回结果时被中断", ex);
        } catch (TimeoutException ex) {
            throw new IllegalStateException("等待 Responses API 返回超时，请检查网关响应时间或降低问题复杂度", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Responses API 调用失败", cause);
        }

        String content = finalResponse != null && finalResponse.aiMessage() != null
                ? finalResponse.aiMessage().text()
                : "";
        return Result.<String>builder()
                .content(content)
                .tokenUsage(finalResponse != null ? finalResponse.tokenUsage() : null)
                .finishReason(finalResponse != null ? finalResponse.finishReason() : null)
                .toolExecutions(List.copyOf(toolExecutions))
                .finalResponse(finalResponse)
                .build();
    }

    /**
     * 流式聊天接口。
     *
     * 与同步 chat() 的区别只有一层：
     * - 同步接口直接等待完整结果后返回
     * - 这里在等待完整结果的同时，把 token/tool 状态持续通过 emitter 推出去
     */
    public AgentChatResponse streamChat(AgentChatRequest request, AgentStreamEventEmitter emitter) {
        if (!StringUtils.hasText(request.getDatasourceId())) {
            return buildErrorResponse("请选择一个可查询的数据源后再提问");
        }

        ConfigComponent datasource = configComponentService.getById(request.getDatasourceId());
        if (datasource == null) {
            return buildErrorResponse("选中的数据源不存在");
        }

        AgentExecutionContextHolder.set(new AgentExecutionContext(
                request.getDatasourceId(),
                datasource.getName(),
                datasource.getVectorType()
        ));
        try {
            /**
             * 这里不能无脑让 /chat/stream 总是走 StreamingChatModel。
             *
             * 当前项目在 chat-completions 协议下同时存在两条模型链路：
             * 1. /chat    -> 同步 ChatModel
             * 2. /stream  -> StreamingChatModel
             *
             * 两条链路虽然 prompt 一样，但模型客户端实现不同，工具选择并不保证完全一致。
             * 之前线上现象就是：
             * - 同一个请求，/chat 能稳定返回 query_logs 结果
             * - /chat/stream 却更容易走到 text2sql_query，然后暴露另一条执行链上的 ClickHouse 连接问题
             *
             * 所以这里做兼容收口：
             * - responses 协议：只有 StreamingChatModel，继续走真实流式
             * - chat-completions 协议：复用与 /chat 相同的同步 ChatModel 做工具决策和执行，
             *   然后把最终答案和工具结果按流事件回放给前端
             *
             * 这样 /chat 和 /chat/stream 在 chat-completions 下会共享同一条“选工具/执行工具”路径，
             * 避免同一问题因为客户端实现不同而命中不同工具。
             */
            Result<String> result = responsesWireApi
                    ? executeStreamingAssistant(buildPrompt(request, datasource), emitter)
                    : executeBufferedAssistant(buildPrompt(request, datasource), emitter);
            return buildResponse(request.getDatasourceId(), datasource.getName(), result);
        } finally {
            AgentExecutionContextHolder.clear();
        }
    }

    /**
     * chat-completions 协议下的“兼容型流式”执行。
     *
     * 核心目标不是追求 token 级别的第一时间输出，而是保证 /chat 和 /chat/stream
     * 使用同一条同步 ChatModel 工具链，先把结果做对，再把结果按流事件发送给前端。
     */
    private Result<String> executeBufferedAssistant(String prompt, AgentStreamEventEmitter emitter) {
        Result<String> result = assistant.chat(prompt);
        replayBufferedResult(result, emitter);
        return result;
    }

    /**
     * 把同步结果回放成前端可消费的流事件。
     *
     * 这里不会伪造尚未发生的工具执行过程，只会在同步调用完成后按顺序输出：
     * 1. tool_finished
     * 2. token
     *
     * 原因是同步 ChatModel 已经把工具执行全部做完了，当前阶段只做协议兼容，不重新虚构中间态。
     */
    private void replayBufferedResult(Result<String> result, AgentStreamEventEmitter emitter) {
        if (emitter == null || result == null) {
            return;
        }

        List<ToolExecution> executions = result.toolExecutions() != null ? result.toolExecutions() : List.of();
        for (ToolExecution toolExecution : executions) {
            safeEmit(emitter, AgentStreamEvent.toolFinished(toFinishedToolCall(toolExecution)));
        }

        for (String chunk : chunkText(result.content(), 48)) {
            safeEmit(emitter, AgentStreamEvent.token(chunk));
        }
    }

    /**
     * 前端流式消息只需要“逐段追加文本”，不要求严格按 tokenizer 切分。
     * 这里按固定字符窗口切片，兼容中英文混排，避免一次性把整段答案推过去。
     */
    private List<String> chunkText(String content, int chunkSize) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }

        int size = Math.max(1, chunkSize);
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < content.length(); start += size) {
            int end = Math.min(content.length(), start + size);
            chunks.add(content.substring(start, end));
        }
        return chunks;
    }

    private AgentChatResponse buildResponse(String datasourceId, String datasourceName, Result<String> result) {
        List<AgentToolCall> toolCalls = new ArrayList<>();
        AgentToolPayload lastPayload = null;

        /**
         * LangChain4j 会把实际发生过的 tool call 放到 Result#toolExecutions 里。
         * 这里把它转换成前端已经在展示的 toolCalls 结构，避免前端协议重新设计一遍。
         */
        List<ToolExecution> executions = result.toolExecutions() != null ? result.toolExecutions() : List.of();
        for (ToolExecution toolExecution : executions) {
            AgentToolPayload payload = toolExecution.resultObject() instanceof AgentToolPayload toolPayload
                    ? toolPayload
                    : null;
            if (payload != null) {
                lastPayload = payload;
            }

            toolCalls.add(AgentToolCall.builder()
                    .toolCallId(toolExecution.request().id())
                    .toolName(toolExecution.request().name())
                    .toolLabel(payload != null ? payload.getToolLabel() : defaultToolLabel(toolExecution.request().name()))
                    .status(toolExecution.hasFailed() ? "failed" : "completed")
                    .input(parseArguments(toolExecution.request().arguments()))
                    .summary(payload != null ? payload.getSummary() : truncate(toolExecution.result(), 160))
                    .durationMs(payload != null ? payload.getDurationMs() : null)
                    .build());
        }

        AgentResult agentResult = lastPayload != null ? lastPayload.getResult() : null;
        String intent = lastPayload != null ? lastPayload.getIntent() : inferIntent(agentResult);
        if (!StringUtils.hasText(intent)) {
            intent = "logs";
        }

        return AgentChatResponse.builder()
                .success(true)
                .intent(intent)
                .answer(result.content())
                .datasourceId(datasourceId)
                .datasourceName(datasourceName)
                .toolCalls(toolCalls)
                .result(agentResult)
                .suggestions(defaultSuggestions(intent))
                .build();
    }

    private String buildPrompt(AgentChatRequest request, ConfigComponent datasource) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("当前数据源名称: ").append(datasource.getName()).append('\n');
        prompt.append("当前数据源类型: ").append(datasource.getVectorType()).append('\n');
        prompt.append("当前问题: ").append(normalizeText(request.getMessage())).append('\n');

        List<AgentChatMessage> history = request.getHistory();
        if (history != null && !history.isEmpty()) {
            /**
             * 这里不再额外按“消息条数”裁剪 history。
             *
             * 当前项目的 memory 层已经升级成官方 TokenWindowChatMemory，
             * request.history 在进入执行器前就已经按 token 窗口裁过一次。
             * 如果这里再截最近 N 条，会把已经保留下来的有效上下文再次截短，
             * 让 token window 的升级价值被抵消。
             */
            prompt.append("最近对话历史:\n");
            for (AgentChatMessage message : history) {
                if (message == null || !StringUtils.hasText(message.getContent())) {
                    continue;
                }
                prompt.append("- ")
                        .append(normalizeRole(message.getRole()))
                        .append(": ")
                        .append(normalizeText(message.getContent()))
                        .append('\n');
            }
        }

        prompt.append("请先使用合适的工具，再基于工具结果回答。");
        return prompt.toString();
    }

    private String normalizeRole(String role) {
        if ("assistant".equalsIgnoreCase(role)) {
            return "assistant";
        }
        return "user";
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private Map<String, Object> parseArguments(String arguments) {
        if (!StringUtils.hasText(arguments)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(arguments, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (JsonProcessingException ex) {
            /**
             * 某些模型返回的 tool arguments 不一定是严格 JSON。
             * 为了不因为展示参数失败而丢掉整次调用记录，这里退回原始字符串。
             */
            return Map.of("raw", arguments);
        }
    }

    private String defaultToolLabel(String toolName) {
        return switch (toolName) {
            case "get_schema" -> "读取字段结构";
            case "query_logs" -> "查询日志列表";
            case "query_timeseries" -> "查询日志趋势";
            case "text2sql_query" -> "自然语言统计查询";
            default -> toolName;
        };
    }

    private String inferIntent(AgentResult result) {
        return result != null ? result.getType() : "logs";
    }

    private List<String> defaultSuggestions(String intent) {
        if (!StringUtils.hasText(intent)) {
            intent = "logs";
        }
        return switch (intent) {
            case "schema" -> List.of("最近1小时有哪些错误日志", "看最近24小时日志趋势", "搜索包含 \"timeout\" 的日志");
            case "timeseries" -> List.of("查看最近1小时日志", "查看字段结构", "再看最近7天的趋势");
            case "text2sql" -> List.of("最近1天的数据有多少条", "按 severity 统计最近24小时数量", "统计最近7天每天的日志量");
            default -> List.of("看最近24小时日志趋势", "查看这个数据源的字段结构", "再查最近15分钟的日志");
        };
    }

    private AgentChatResponse buildErrorResponse(String error) {
        return AgentChatResponse.builder()
                .success(false)
                .error(error)
                .suggestions(List.of("先选择一个可查询数据源", "查看字段结构", "查询最近1小时日志"))
                .build();
    }

    private AgentToolCall toStreamingToolCall(BeforeToolExecution beforeToolExecution) {
        return AgentToolCall.builder()
                .toolCallId(beforeToolExecution.request().id())
                .toolName(beforeToolExecution.request().name())
                .toolLabel(defaultToolLabel(beforeToolExecution.request().name()))
                .status("running")
                .input(parseArguments(beforeToolExecution.request().arguments()))
                .summary("工具执行中")
                .build();
    }

    private AgentToolCall toFinishedToolCall(ToolExecution toolExecution) {
        AgentToolPayload payload = toolExecution.resultObject() instanceof AgentToolPayload toolPayload
                ? toolPayload
                : null;
        return AgentToolCall.builder()
                .toolCallId(toolExecution.request().id())
                .toolName(toolExecution.request().name())
                .toolLabel(payload != null ? payload.getToolLabel() : defaultToolLabel(toolExecution.request().name()))
                .status(toolExecution.hasFailed() ? "failed" : "completed")
                .input(parseArguments(toolExecution.request().arguments()))
                .summary(payload != null ? payload.getSummary() : truncate(toolExecution.result(), 160))
                .durationMs(payload != null ? payload.getDurationMs() : null)
                .build();
    }

    private void safeEmit(AgentStreamEventEmitter emitter, AgentStreamEvent event) {
        if (emitter == null || event == null) {
            return;
        }
        try {
            emitter.emit(event);
        } catch (IOException ex) {
            throw new UncheckedIOException("写出流式智能助手事件失败", ex);
        }
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
