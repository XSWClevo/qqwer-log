package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.dto.AgentChatMessage;
import cn.mw.loganalysis.agent.dto.AgentChatRequest;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
@Service
public class AgentConversationMemoryService {

    private static final int MAX_MESSAGES_PER_SESSION = 16;

    private final Cache<String, ConversationSession> sessions = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(Duration.ofHours(12))
            .build();

    /**
     * 解析本次请求应该使用的 session，并把服务端已有记忆回填成有效 history。
     *
     * 如果前端已经传了 history，但当前 session 还是空的，就把这份 history 当成 bootstrap；
     * 这样旧前端或刷新后的首个请求也不会丢上下文。
     */
    public PreparedAgentChatRequest prepare(AgentChatRequest request) {
        String sessionId = StringUtils.hasText(request.getSessionId())
                ? request.getSessionId().trim()
                : UUID.randomUUID().toString();

        ConversationSession session = sessions.get(sessionId, key -> new ConversationSession());
        synchronized (session) {
            if (StringUtils.hasText(request.getDatasourceId())
                    && StringUtils.hasText(session.datasourceId)
                    && !request.getDatasourceId().equals(session.datasourceId)) {
                session.messages.clear();
            }

            if (session.messages.isEmpty() && request.getHistory() != null && !request.getHistory().isEmpty()) {
                List<AgentChatMessage> bootstrapHistory = new ArrayList<>(request.getHistory());
                removeDuplicatedCurrentUserMessage(bootstrapHistory, request.getMessage());

                bootstrapHistory.stream()
                        .filter(this::isUsableMessage)
                        .forEach(message -> session.messages.add(copyMessage(message.getRole(), message.getContent())));
                trim(session.messages);
            }

            AgentChatRequest effectiveRequest = new AgentChatRequest();
            effectiveRequest.setMessage(request.getMessage());
            effectiveRequest.setDatasourceId(request.getDatasourceId());
            effectiveRequest.setSessionId(sessionId);
            effectiveRequest.setHistory(copyMessages(session.messages));
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
        if (!StringUtils.hasText(sessionId)) {
            return;
        }

        ConversationSession session = sessions.get(sessionId, key -> new ConversationSession());
        synchronized (session) {
            if (StringUtils.hasText(datasourceId)
                    && StringUtils.hasText(session.datasourceId)
                    && !datasourceId.equals(session.datasourceId)) {
                session.messages.clear();
            }

            session.datasourceId = datasourceId;
            session.datasourceName = datasourceName;
            session.datasourceType = datasourceType;

            if (StringUtils.hasText(userMessage)) {
                session.messages.add(copyMessage("user", userMessage));
            }
            if (StringUtils.hasText(assistantMessage)) {
                session.messages.add(copyMessage("assistant", assistantMessage));
            }
            trim(session.messages);
        }
    }

    /**
     * 删除指定 session 的内存上下文。
     * 历史会话被用户删除后，需要把进程内缓存也一并清掉，避免同一个 sessionId 继续被误命中。
     */
    public void forget(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        sessions.invalidate(sessionId.trim());
    }

    private boolean isUsableMessage(AgentChatMessage message) {
        return message != null
                && StringUtils.hasText(message.getRole())
                && StringUtils.hasText(message.getContent());
    }

    private AgentChatMessage copyMessage(String role, String content) {
        AgentChatMessage message = new AgentChatMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private List<AgentChatMessage> copyMessages(List<AgentChatMessage> source) {
        List<AgentChatMessage> copied = new ArrayList<>();
        for (AgentChatMessage message : source) {
            if (isUsableMessage(message)) {
                copied.add(copyMessage(message.getRole(), message.getContent()));
            }
        }
        return copied;
    }

    /**
     * 前端有时会把“当前这条 user 消息”也一并塞进 history。
     * 如果 session 初始化时不去重，当前问题会在 prompt 里重复一次。
     */
    private void removeDuplicatedCurrentUserMessage(List<AgentChatMessage> history, String currentMessage) {
        if (history == null || history.isEmpty() || !StringUtils.hasText(currentMessage)) {
            return;
        }

        AgentChatMessage last = history.getLast();
        if (last != null
                && "user".equalsIgnoreCase(last.getRole())
                && currentMessage.trim().equals(last.getContent() != null ? last.getContent().trim() : null)) {
            history.removeLast();
        }
    }

    private void trim(List<AgentChatMessage> messages) {
        int overflow = messages.size() - MAX_MESSAGES_PER_SESSION;
        if (overflow > 0) {
            messages.subList(0, overflow).clear();
        }
    }

    public record PreparedAgentChatRequest(String sessionId, AgentChatRequest request) {
    }

    private static final class ConversationSession {
        private String datasourceId;
        private String datasourceName;
        private String datasourceType;
        private final List<AgentChatMessage> messages = new ArrayList<>();
    }
}
