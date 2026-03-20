package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.dto.AgentChatMessage;
import cn.mw.loganalysis.agent.dto.AgentChatRequest;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 智能助手的轻量会话记忆。
 *
 * 这里没有把 memory 做成数据库持久化，而是先做成本进程缓存：
 * 1. 前端只要持续携带同一个 sessionId，后端就能拿到最近几轮对话
 * 2. LLM prompt 和规则回退都可以复用这段历史
 * 3. 切换数据源时会自动清掉旧上下文，避免不同数据源之间串语义
 *
 * 这版的目标是“真正有 session 语义的多轮对话”，而不是长期归档聊天记录。
 */
@Slf4j
@Service
public class AgentConversationMemoryService {

    /**
     * OpenAI-compatible 网关经常会出现厂商自定义模型名，例如 qwen-plus。
     * 这些模型名不一定能被 jtokkit 直接识别，因此 token 估算器在必要时回退到一个
     * 与 OpenAI 新模型同编码族的兼容模型名，仅用于记忆窗口裁剪，不影响真实调用模型。
     */
    private static final String DEFAULT_ESTIMATOR_MODEL = "gpt-5.2";

    private final Cache<String, ConversationSession> sessions = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(Duration.ofHours(12))
            .build();
    private final TokenCountEstimator tokenCountEstimator;
    private final int memoryMaxTokens;

    public AgentConversationMemoryService(@Value("${agent.llm.memory.max-tokens:2048}") int memoryMaxTokens,
                                          @Value("${langchain4j.open-ai.chat-model.model-name:}") String modelName) {
        this.memoryMaxTokens = Math.max(256, memoryMaxTokens);
        this.tokenCountEstimator = createTokenCountEstimator(modelName);
    }

    /**
     * 解析本次请求应该使用的 session，并把服务端已有记忆回填成有效 history。
     *
     * 如果前端已经传了 history，但当前 session 还是空的，就把这份 history 当成 bootstrap；
     * 这样旧前端或刷新后的首个请求也不会丢上下文。
     */
    public PreparedAgentChatRequest prepare(AgentChatRequest request) {
        String sessionId = StringUtils.isNotBlank(request.getSessionId())
                ? StringUtils.trim(request.getSessionId())
                : UUID.randomUUID().toString();

        ConversationSession session = sessions.get(sessionId, this::createConversationSession);
        synchronized (session) {
            if (StringUtils.isNotBlank(request.getDatasourceId())
                    && StringUtils.isNotBlank(session.datasourceId)
                    && !StringUtils.equals(request.getDatasourceId(), session.datasourceId)) {
                session.memory.clear();
            }

            if (session.memory.messages().isEmpty() && CollectionUtils.isNotEmpty(request.getHistory())) {
                List<AgentChatMessage> bootstrapHistory = new ArrayList<>(request.getHistory());
                removeDuplicatedCurrentUserMessage(bootstrapHistory, request.getMessage());
                session.memory.set(toChatMessages(bootstrapHistory));
            }

            AgentChatRequest effectiveRequest = new AgentChatRequest();
            effectiveRequest.setMessage(request.getMessage());
            effectiveRequest.setDatasourceId(request.getDatasourceId());
            effectiveRequest.setSessionId(sessionId);
            effectiveRequest.setHistory(toAgentMessages(session.memory.messages()));
            return new PreparedAgentChatRequest(sessionId, effectiveRequest);
        }
    }

    /**
     * 把本轮 user/assistant 对话写回 session。
     *
     * 后端只保存最基本的对话文本，不保存工具执行结果或大块结构化数据，
     * 这样既能让 LLM 理解上下文，又不会把 prompt 越堆越大。
     */
    public void remember(String sessionId,
                         String datasourceId,
                         String datasourceName,
                         String datasourceType,
                         String userMessage,
                         String assistantMessage) {
        if (StringUtils.isBlank(sessionId)) {
            return;
        }

        ConversationSession session = sessions.get(sessionId, this::createConversationSession);
        synchronized (session) {
            if (StringUtils.isNotBlank(datasourceId)
                    && StringUtils.isNotBlank(session.datasourceId)
                    && !StringUtils.equals(datasourceId, session.datasourceId)) {
                session.memory.clear();
            }

            session.datasourceId = datasourceId;
            session.datasourceName = datasourceName;
            session.datasourceType = datasourceType;

            if (StringUtils.isNotBlank(userMessage)) {
                session.memory.add(UserMessage.from(userMessage.trim()));
            }
            if (StringUtils.isNotBlank(assistantMessage)) {
                session.memory.add(AiMessage.from(assistantMessage.trim()));
            }
        }
    }

    /**
     * 删除指定 session 的内存上下文。
     * 历史会话被用户删除后，需要把进程内缓存也一并清掉，避免同一个 sessionId 继续被误命中。
     */
    public void forget(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return;
        }
        sessions.invalidate(StringUtils.trim(sessionId));
    }

    private boolean isUsableMessage(AgentChatMessage message) {
        return message != null
                && StringUtils.isNotBlank(message.getRole())
                && StringUtils.isNotBlank(message.getContent());
    }

    private AgentChatMessage copyMessage(String role, String content) {
        AgentChatMessage message = new AgentChatMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    /**
     * 这里显式把 Agent DTO 转成 LangChain4j 的 ChatMessage。
     *
     * 当前项目只把 user/assistant 文本写入 memory，不把 toolCalls/result 这类结构化大对象塞进
     * ChatMemory，避免 token 窗口被调试信息占满。
     */
    private List<ChatMessage> toChatMessages(List<AgentChatMessage> source) {
        List<ChatMessage> messages = new ArrayList<>();
        for (AgentChatMessage message : source) {
            if (!isUsableMessage(message)) {
                continue;
            }

            String role = StringUtils.trimToEmpty(message.getRole());
            String content = StringUtils.trimToEmpty(message.getContent());
            if (StringUtils.equalsIgnoreCase(role, "assistant")) {
                messages.add(AiMessage.from(content));
            } else if (StringUtils.equalsIgnoreCase(role, "user")) {
                messages.add(UserMessage.from(content));
            }
        }
        return messages;
    }

    private List<AgentChatMessage> toAgentMessages(List<ChatMessage> source) {
        List<AgentChatMessage> copied = new ArrayList<>();
        if (CollectionUtils.isEmpty(source)) {
            return copied;
        }

        for (ChatMessage chatMessage : source) {
            if (chatMessage instanceof UserMessage userMessage && StringUtils.isNotBlank(userMessage.singleText())) {
                copied.add(copyMessage("user", userMessage.singleText()));
            } else if (chatMessage instanceof AiMessage aiMessage && StringUtils.isNotBlank(aiMessage.text())) {
                copied.add(copyMessage("assistant", aiMessage.text()));
            }
        }
        return copied;
    }

    /**
     * 前端有时会把“当前这条 user 消息”也一并塞进 history。
     * 如果 session 初始化时不去重，当前问题会在 prompt 里重复一次。
     */
    private void removeDuplicatedCurrentUserMessage(List<AgentChatMessage> history, String currentMessage) {
        if (CollectionUtils.isEmpty(history) || StringUtils.isBlank(currentMessage)) {
            return;
        }

        AgentChatMessage last = history.getLast();
        if (last != null
                && StringUtils.equalsIgnoreCase(last.getRole(), "user")
                && StringUtils.equals(StringUtils.trim(currentMessage), StringUtils.trim(last.getContent()))) {
            history.removeLast();
        }
    }

    private ConversationSession createConversationSession(String sessionId) {
        return new ConversationSession(createChatMemory(sessionId));
    }

    /**
     * 这里使用官方 TokenWindowChatMemory，而不是继续手写“固定条数窗口”。
     *
     * 这样 LLM 上下文的裁剪维度会从“消息条数”升级成“token 总量”：
     * - 短消息会保留更多轮
     * - 长消息会更早被淘汰
     * - 窗口大小更接近模型实际输入成本
     */
    private ChatMemory createChatMemory(String sessionId) {
        return TokenWindowChatMemory.builder()
                .id(sessionId)
                .maxTokens(memoryMaxTokens, tokenCountEstimator)
                .build();
    }

    /**
     * OpenAI-compatible 平台常见自定义模型名在 jtokkit 中未必有精确映射。
     * 如果直接用未知模型名初始化估算器会抛错，所以这里先尝试真实模型名，
     * 失败后回退到同编码族的兼容模型，仅用于记忆裁剪，不影响真实推理模型。
     */
    private TokenCountEstimator createTokenCountEstimator(String modelName) {
        String resolvedModelName = StringUtils.defaultIfBlank(StringUtils.trimToNull(modelName), DEFAULT_ESTIMATOR_MODEL);
        try {
            return new OpenAiTokenCountEstimator(resolvedModelName);
        } catch (IllegalArgumentException ex) {
            log.warn("模型 {} 不被 OpenAiTokenCountEstimator 识别，记忆窗口回退到兼容估算模型 {}",
                    resolvedModelName, DEFAULT_ESTIMATOR_MODEL);
            return new OpenAiTokenCountEstimator(DEFAULT_ESTIMATOR_MODEL);
        }
    }

    public record PreparedAgentChatRequest(String sessionId, AgentChatRequest request) {
    }

    private static final class ConversationSession {
        private String datasourceId;
        private String datasourceName;
        private String datasourceType;

        /**
         * memory 只负责短期上下文窗口，不承担历史归档职责。
         * 完整历史仍然由 PostgreSQL 的 agent_conversations / agent_conversation_messages 保存。
         */
        private final ChatMemory memory;

        private ConversationSession(ChatMemory memory) {
            this.memory = memory;
        }
    }
}
