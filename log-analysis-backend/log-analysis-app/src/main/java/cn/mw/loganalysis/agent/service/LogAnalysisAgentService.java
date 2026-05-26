package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.config.LangChain4jChatModelProperties;
import cn.mw.loganalysis.agent.dto.AgentChatRequest;
import cn.mw.loganalysis.agent.dto.AgentChatResponse;
import cn.mw.loganalysis.agent.dto.AgentConversationDetail;
import cn.mw.loganalysis.agent.dto.AgentConversationSummary;
import cn.mw.loganalysis.agent.dto.AgentStreamEvent;
import com.fasterxml.jackson.core.JsonParseException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesStreamingChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 智能助手总入口。
 *
 * 重构后这里的职责只保留三层：
 * 1. session prepare
 * 2. 选择 LLM executor 或 fallback executor
 * 3. finalize memory/history，并把结果返回给控制器
 *
 * 这样规则解析、工具执行、响应组装都不再堆在同一个大类里。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogAnalysisAgentService {

    private final ApplicationContext applicationContext;
    private final AgentSessionService agentSessionService;
    private final FallbackAgentExecutor fallbackAgentExecutor;
    private final AgentResponseAssembler responseAssembler;
    private final LangChain4jChatModelProperties chatModelProperties;
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ObjectProvider<OpenAiResponsesStreamingChatModel> responsesStreamingChatModelProvider;
    /**
     * LLM 执行器是可选 Bean。
     * 当模型 Bean、协议配置或 starter 自动装配条件不满足时，系统会自动回退到规则版。
     */
    private final ObjectProvider<LangChain4jLogAnalysisAgentExecutor> llmExecutorProvider;

    private final AtomicBoolean llmUnavailableLogged = new AtomicBoolean(false);

    @Value("${agent.llm.enabled:true}")
    private boolean llmEnabled;

    @Value("${agent.llm.fallback-on-error:true}")
    private boolean fallbackOnError;

    @Value("${agent.llm.prefer-deterministic-tools:true}")
    private boolean preferDeterministicTools;

    public AgentChatResponse chat(AgentChatRequest request, Long userId) {
        AgentConversationMemoryService.PreparedAgentChatRequest preparedRequest = agentSessionService.prepare(request, userId);
        AgentChatRequest effectiveRequest = preparedRequest.request();
        String sessionId = preparedRequest.sessionId();
        AgentChatResponse response;
        try {
            response = executePreparedChat(effectiveRequest, userId, sessionId, false, null);
        } catch (IOException ex) {
            log.error("同步智能助手请求意外触发流式写出异常, datasourceId={}, message={}",
                    effectiveRequest.getDatasourceId(), effectiveRequest.getMessage(), ex);
            response = responseAssembler.error("智能助手处理失败: " + ex.getMessage());
        }
        return agentSessionService.finalizeResponse(userId, sessionId, effectiveRequest.getMessage(), response);
    }

    public void streamChat(AgentChatRequest request, Long userId, AgentStreamEventEmitter emitter) throws IOException {
        AgentConversationMemoryService.PreparedAgentChatRequest preparedRequest = agentSessionService.prepare(request, userId);
        AgentChatRequest effectiveRequest = preparedRequest.request();
        String sessionId = preparedRequest.sessionId();

        emitter.emit(AgentStreamEvent.started(sessionId));
        AgentChatResponse response = executePreparedChat(effectiveRequest, userId, sessionId, true, emitter);
        AgentChatResponse finalizedResponse = agentSessionService.finalizeResponse(userId, sessionId, effectiveRequest.getMessage(), response);
        emitter.emit(AgentStreamEvent.done(finalizedResponse));
    }

    public List<AgentConversationSummary> listConversations(Long userId) {
        return agentSessionService.listConversations(userId);
    }

    public AgentConversationDetail getConversation(Long userId, String sessionId) {
        return agentSessionService.getConversation(userId, sessionId);
    }

    public void deleteConversation(Long userId, String sessionId) {
        agentSessionService.deleteConversation(userId, sessionId);
    }

    private AgentChatResponse executePreparedChat(AgentChatRequest effectiveRequest,
                                                  Long userId,
                                                  String sessionId,
                                                  boolean streamMode,
                                                  AgentStreamEventEmitter emitter) throws IOException {
        if (preferDeterministicTools && fallbackAgentExecutor.shouldHandleWithoutLlm(effectiveRequest, userId)) {
            log.info("智能助手请求命中确定性工具链，跳过 LLM, datasourceId={}, message={}",
                    effectiveRequest.getDatasourceId(), effectiveRequest.getMessage());
            return fallbackAgentExecutor.execute(effectiveRequest, userId, sessionId, emitter);
        }

        if (llmEnabled) {
            LangChain4jLogAnalysisAgentExecutor llmExecutor = llmExecutorProvider.getIfAvailable();
            if (llmExecutor != null) {
                try {
                    return streamMode
                            ? llmExecutor.streamChat(effectiveRequest, userId, sessionId, emitter)
                            : llmExecutor.chat(effectiveRequest, userId, sessionId);
                } catch (Exception ex) {
                    String llmFailureMessage = describeLlmFailure(ex);
                    if (!fallbackOnError) {
                        log.error("LangChain4j 智能助手处理失败且未启用回退, datasourceId={}, message={}",
                                effectiveRequest.getDatasourceId(), effectiveRequest.getMessage(), ex);
                        return responseAssembler.error("智能助手处理失败: " + llmFailureMessage);
                    }
                    log.warn("LangChain4j 智能助手处理失败，已回退到规则版: datasourceId={}, reason={}",
                            effectiveRequest.getDatasourceId(), llmFailureMessage);
                    log.debug("LangChain4j 智能助手失败堆栈, datasourceId={}, message={}",
                            effectiveRequest.getDatasourceId(), effectiveRequest.getMessage(), ex);
                }
            } else {
                logLlmUnavailableOnce();
            }
        }
        return fallbackAgentExecutor.execute(effectiveRequest, userId, sessionId, emitter);
    }

    private void logLlmUnavailableOnce() {
        if (llmUnavailableLogged.compareAndSet(false, true)) {
            log.info("LangChain4j 执行器未注册，当前请求回退到规则版。诊断信息: llmEnabled={}, wireApi={}, apiKeyPresent={}, chatModelPresent={}, responsesModelPresent={}, chatModelBeans={}, responsesModelBeans={}, executorBeans={}",
                    llmEnabled,
                    chatModelProperties.getWireApi(),
                    StringUtils.isNotBlank(chatModelProperties.getApiKey()),
                    chatModelProvider.getIfAvailable() != null,
                    responsesStreamingChatModelProvider.getIfAvailable() != null,
                    Arrays.toString(applicationContext.getBeanNamesForType(ChatModel.class)),
                    Arrays.toString(applicationContext.getBeanNamesForType(OpenAiResponsesStreamingChatModel.class)),
                    Arrays.toString(applicationContext.getBeanNamesForType(LangChain4jLogAnalysisAgentExecutor.class)));
        }
    }

    /**
     * 把底层模型/网关异常转换成可操作的诊断信息。
     *
     * 当前项目同时支持两条 OpenAI-compatible 协议：
     * 1. Chat Completions
     * 2. Responses API
     *
     * 这里统一收口错误描述，避免控制器和执行器各自解释异常。
     */
    private String describeLlmFailure(Throwable throwable) {
        String jsonParseMessage = firstCauseMessage(throwable, JsonParseException.class);
        if (jsonParseMessage != null && jsonParseMessage.contains("Unexpected character ('<'")) {
            return "模型网关返回了 HTML 页面，而不是 OpenAI JSON。请确认 base-url 是否正确，以及当前 wire-api 与网关支持的协议一致。";
        }

        String httpMessage = firstCauseMessage(throwable, HttpException.class);
        if (StringUtils.isNotBlank(httpMessage)) {
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
        if (root != null && StringUtils.isNotBlank(root.getMessage())) {
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
}
