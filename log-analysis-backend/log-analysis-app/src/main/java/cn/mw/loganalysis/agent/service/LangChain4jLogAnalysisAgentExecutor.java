package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.config.LangChain4jChatModelProperties;
import cn.mw.loganalysis.agent.dto.AgentChatMessage;
import cn.mw.loganalysis.agent.dto.AgentChatRequest;
import cn.mw.loganalysis.agent.dto.AgentChatResponse;
import cn.mw.loganalysis.agent.dto.AgentStreamEvent;
import cn.mw.loganalysis.vector.entity.ConfigComponent;
import cn.mw.loganalysis.vector.service.ConfigComponentService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * LangChain4j 驱动的日志助手执行器。
 *
 * 职责收口后，这个类只保留三件事：
 * 1. 构造 prompt
 * 2. 调用 LangChain4j AiService 代理
 * 3. 在流式模式下把 token/tool 事件透传给上层
 *
 * 前端响应组装统一交给 AgentResponseAssembler，避免执行器再承担 DTO 拼装职责。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "agent.llm", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "langchain4j.open-ai.chat-model", name = "api-key")
public class LangChain4jLogAnalysisAgentExecutor {

    private static final String SYSTEM_PROMPT = """
            你是企业日志分析平台里的智能助手。
            你的职责只有五类：
            1. 读取当前数据源的字段结构
            2. 查询当前数据源的日志列表
            3. 查询当前数据源的日志趋势
            4. 对 ClickHouse 数据源执行自然语言 SQL 查询
            5. 根据用户提供的日志样本预览生成 Vector Remap/Sink 组件创建计划

            你必须遵守以下规则：
            - 所有结论都必须基于工具返回的数据，不允许编造字段、数量、时间范围或日志内容。
            - 回答字段、表结构、有哪些列、时间字段、统计维度时，先调用 get_schema。
            - 回答日志明细、错误日志、搜索关键词、最近多少时间的日志时，先调用 query_logs。
            - 回答趋势、时序、波动、按分钟/小时统计时，先调用 query_timeseries。
            - 当问题属于开放式统计、聚合、排行、按字段分组、多少条、做图、生成报表数据，并且当前数据源类型是 clickhouse 时，优先调用 text2sql_query。
            - text2sql_query 已经会把自然语言、当前表结构和数据源信息交给既有 text2sql 服务处理，你不能自行编造 SQL。
            - 如果当前数据源不是 clickhouse，不要调用 text2sql_query。
            - 当用户要求根据日志样本创建、生成、配置 Vector 组件、Remap/Sink、正则或入库表时，先调用 preview_vector_components。
            - preview_vector_components 只生成预览计划，不会建表或写入组件；必须提醒用户检查后点击“确认创建”，不要声称已经创建成功。
            - 调用 preview_vector_components 时，尽量提供命名捕获正则 regexPattern，例如 (?P<field>...)；如果无法可靠生成，可以留空让后端启发式生成并校验。
            - 默认一次问题只调用一个最合适的工具；只有确实必要时才继续调用第二个工具。
            - 回答使用简体中文，保持简洁，先给结论，再点出关键数字或时间点。
            - 如果用户的问题超出这五类能力，明确说明当前只支持字段结构、日志查询、趋势查询、ClickHouse 自然语言统计查询和 Vector 组件预览生成。
            """;

    private final LangChain4jLogAnalysisAssistant assistant;
    private final LangChain4jStreamingLogAnalysisAssistant streamingAssistant;
    private final ConfigComponentService configComponentService;
    private final AgentResponseAssembler responseAssembler;
    private final boolean responsesWireApi;
    private final Duration llmTimeout;

    public LangChain4jLogAnalysisAgentExecutor(ObjectProvider<ChatModel> chatModelProvider,
                                               @Qualifier("activeStreamingChatModel")
                                               ObjectProvider<StreamingChatModel> activeStreamingChatModelProvider,
                                               LogAnalysisAgentTools logAnalysisAgentTools,
                                               ConfigComponentService configComponentService,
                                               AgentResponseAssembler responseAssembler,
                                               LangChain4jChatModelProperties chatModelProperties) {
        /**
         * executor 需要同时兼容两种模型协议：
         * 1. chat-completions：同步 ChatModel
         * 2. responses：StreamingChatModel + TokenStream
         *
         * 这里不引入额外编排框架，只在执行器内部做协议分流。
         */
        this.responsesWireApi = chatModelProperties.usesResponsesApi();
        this.llmTimeout = chatModelProperties.getTimeout() != null ? chatModelProperties.getTimeout() : Duration.ofSeconds(60);
        this.configComponentService = configComponentService;
        this.responseAssembler = responseAssembler;

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
    }

    public AgentChatResponse chat(AgentChatRequest request, Long userId, String sessionId) {
        if (StringUtils.isBlank(request.getDatasourceId())) {
            return responseAssembler.error("请选择一个可查询的数据源后再提问");
        }

        ConfigComponent datasource = configComponentService.getQueryableDataSourceById(request.getDatasourceId());
        if (datasource == null) {
            return responseAssembler.error("选中的数据源不存在或未标记为可查询 Sink");
        }

        AgentExecutionContextHolder.set(new AgentExecutionContext(
                request.getDatasourceId(),
                datasource.getName(),
                datasource.getVectorType(),
                userId,
                sessionId
        ));
        try {
            String prompt = buildPrompt(request, datasource);
            Result<String> result = responsesWireApi
                    ? executeStreamingAssistant(prompt, null)
                    : assistant.chat(prompt);
            return responseAssembler.fromLlmResult(request.getDatasourceId(), datasource.getName(), result);
        } finally {
            AgentExecutionContextHolder.clear();
        }
    }

    /**
     * 流式接口与同步接口共享同一套业务执行主线。
     *
     * - responses：使用真正的 token 流
     * - chat-completions：先走同步 ChatModel，完成后再把结果按流事件回放
     *
     * 这样 /chat 和 /chat/stream 在 chat-completions 下会共享同一条选工具逻辑，
     * 避免因为模型客户端不同而命中不同工具。
     */
    public AgentChatResponse streamChat(AgentChatRequest request,
                                        Long userId,
                                        String sessionId,
                                        AgentStreamEventEmitter emitter) {
        if (StringUtils.isBlank(request.getDatasourceId())) {
            return responseAssembler.error("请选择一个可查询的数据源后再提问");
        }

        ConfigComponent datasource = configComponentService.getQueryableDataSourceById(request.getDatasourceId());
        if (datasource == null) {
            return responseAssembler.error("选中的数据源不存在或未标记为可查询 Sink");
        }

        AgentExecutionContextHolder.set(new AgentExecutionContext(
                request.getDatasourceId(),
                datasource.getName(),
                datasource.getVectorType(),
                userId,
                sessionId
        ));
        try {
            Result<String> result = responsesWireApi
                    ? executeStreamingAssistant(buildPrompt(request, datasource), emitter)
                    : executeBufferedAssistant(buildPrompt(request, datasource), emitter);
            return responseAssembler.fromLlmResult(request.getDatasourceId(), datasource.getName(), result);
        } finally {
            AgentExecutionContextHolder.clear();
        }
    }

    private Result<String> executeStreamingAssistant(String prompt, AgentStreamEventEmitter emitter) {
        List<ToolExecution> toolExecutions = Collections.synchronizedList(new ArrayList<>());
        CompletableFuture<ChatResponse> responseFuture = new CompletableFuture<>();

        // tokenStream 作用，当有新的响应，模型结束，错误时 对应回调 api 被调用
        TokenStream tokenStream = streamingAssistant.chat(prompt)
                .onPartialResponse(delta -> safeEmit(emitter, AgentStreamEvent.token(delta)))
                .beforeToolExecution(beforeToolExecution ->
                        safeEmit(emitter, AgentStreamEvent.toolStarted(responseAssembler.toRunningToolCall(beforeToolExecution))))
                .onToolExecuted(toolExecution -> {
                    toolExecutions.add(toolExecution);
                    safeEmit(emitter, AgentStreamEvent.toolFinished(responseAssembler.toFinishedToolCall(toolExecution)));
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

    private Result<String> executeBufferedAssistant(String prompt, AgentStreamEventEmitter emitter) {
        Result<String> result = assistant.chat(prompt);
        replayBufferedResult(result, emitter);
        return result;
    }

    private void replayBufferedResult(Result<String> result, AgentStreamEventEmitter emitter) {
        if (emitter == null || result == null) {
            return;
        }

        List<ToolExecution> executions = result.toolExecutions() != null ? result.toolExecutions() : List.of();
        for (ToolExecution toolExecution : executions) {
            safeEmit(emitter, AgentStreamEvent.toolFinished(responseAssembler.toFinishedToolCall(toolExecution)));
        }

        for (String chunk : chunkText(result.content(), 48)) {
            safeEmit(emitter, AgentStreamEvent.token(chunk));
        }
    }

    private List<String> chunkText(String content, int chunkSize) {
        if (StringUtils.isBlank(content)) {
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

    private String buildPrompt(AgentChatRequest request, ConfigComponent datasource) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("当前数据源名称: ").append(datasource.getName()).append('\n');
        prompt.append("当前数据源类型: ").append(datasource.getVectorType()).append('\n');
        prompt.append("当前问题: ").append(normalizeText(request.getMessage())).append('\n');

        List<AgentChatMessage> history = request.getHistory();
        if (history != null && !history.isEmpty()) {
            prompt.append("最近对话历史:\n");
            for (AgentChatMessage message : history) {
                if (message == null || StringUtils.isBlank(message.getContent())) {
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
        return "assistant".equalsIgnoreCase(role) ? "assistant" : "user";
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
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
}
