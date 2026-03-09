package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.dto.AgentChatMessage;
import cn.mw.loganalysis.agent.dto.AgentChatRequest;
import cn.mw.loganalysis.agent.dto.AgentChatResponse;
import cn.mw.loganalysis.agent.dto.AgentConversationDetail;
import cn.mw.loganalysis.agent.dto.AgentConversationEntry;
import cn.mw.loganalysis.agent.dto.AgentConversationSummary;
import cn.mw.loganalysis.agent.dto.AgentResult;
import cn.mw.loganalysis.agent.dto.AgentToolCall;
import cn.mw.loganalysis.agent.entity.AgentConversation;
import cn.mw.loganalysis.agent.entity.AgentConversationMessage;
import cn.mw.loganalysis.agent.mapper.AgentConversationMapper;
import cn.mw.loganalysis.agent.mapper.AgentConversationMessageMapper;
import cn.mw.loganalysis.vector.entity.ConfigComponent;
import cn.mw.loganalysis.vector.service.ConfigComponentService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 智能助手历史会话持久化服务。
 *
 * 和 Caffeine memory 的职责不同：
 * 1. memory 负责当前进程内的多轮上下文窗口
 * 2. 这里负责真正的历史记录保存、列表查询、回放和删除
 *
 * 由于项目当前没有 Flyway/Liquibase，这里会在运行期补齐所需表结构，
 * 避免“代码上线了但数据库没有执行迁移脚本”导致页面直接不可用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentConversationHistoryService {

    private static final int MAX_TITLE_LENGTH = 36;
    private static final int MAX_PREVIEW_LENGTH = 120;
    private static final TypeReference<List<AgentToolCall>> TOOL_CALL_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final AgentConversationMapper conversationMapper;
    private final AgentConversationMessageMapper messageMapper;
    private final ObjectMapper objectMapper;
    private final ConfigComponentService configComponentService;
    private final AtomicBoolean schemaReady = new AtomicBoolean(false);

    /**
     * 如果前端没传历史，且当前 session 在数据库里已经有消息，就把持久化历史回填到请求里。
     * 这样页面刷新后继续聊时，不需要前端自己回放全部历史。
     */
    public AgentChatRequest hydrateRequestHistory(AgentChatRequest request, Long userId) {
        ensureSchema();
        if (userId == null || !StringUtils.hasText(request.getSessionId())) {
            if (userId == null) {
                log.warn("跳过智能助手历史回填，因为当前请求未识别到登录用户");
            }
            return request;
        }
        if (request.getHistory() != null && !request.getHistory().isEmpty()) {
            return request;
        }

        List<AgentChatMessage> history = loadChatHistory(userId, request.getSessionId());
        if (history.isEmpty()) {
            return request;
        }

        AgentChatRequest hydrated = new AgentChatRequest();
        hydrated.setMessage(request.getMessage());
        hydrated.setDatasourceId(request.getDatasourceId());
        hydrated.setSessionId(request.getSessionId());
        hydrated.setHistory(history);
        return hydrated;
    }

    /**
     * 将本轮 user/assistant 对话持久化。
     * 这里直接复用 sessionId 作为 conversation id，避免 memory 与 history 再做映射。
     */
    public void saveTurn(Long userId,
                         String sessionId,
                         String datasourceId,
                         String datasourceName,
                         String datasourceType,
                         String userMessage,
                         AgentChatResponse response) {
        ensureSchema();
        if (userId == null || !StringUtils.hasText(sessionId)) {
            if (userId == null) {
                log.warn("跳过智能助手历史保存，因为当前请求未识别到登录用户, sessionId={}", sessionId);
            } else {
                log.warn("跳过智能助手历史保存，因为 sessionId 为空, userId={}", userId);
            }
            return;
        }

        ConversationIdentity identity = resolveConversationIdentity(sessionId, datasourceId, datasourceName, datasourceType);
        ConversationIdentity targetIdentity = fillDatasource(identity, datasourceId, datasourceName, datasourceType);
        upsertConversation(userId, targetIdentity, userMessage);

        int addedMessages = 0;
        if (StringUtils.hasText(userMessage)) {
            insertMessage(sessionId, "user", userMessage, null, null, null);
            addedMessages++;
        }

        String assistantContent = response != null
                ? (response.getSuccess() ? response.getAnswer() : response.getError())
                : null;
        if (StringUtils.hasText(assistantContent)) {
            insertMessage(
                    sessionId,
                    "assistant",
                    assistantContent,
                    response != null ? response.getToolCalls() : null,
                    response != null ? response.getResult() : null,
                    response != null ? response.getSuggestions() : null
            );
            addedMessages++;
        }

        if (addedMessages > 0) {
            String preview = StringUtils.hasText(assistantContent) ? assistantContent : userMessage;
            String titleSeed = StringUtils.hasText(userMessage) ? userMessage : preview;
            updateConversationAfterTurn(sessionId, targetIdentity, titleSeed, preview, addedMessages);
        }
    }

    public List<AgentConversationSummary> listConversations(Long userId) {
        ensureSchema();
        if (userId == null) {
            log.warn("查询智能助手历史列表时未识别到登录用户");
            return Collections.emptyList();
        }

        return conversationMapper.selectRecentByUserId(userId).stream()
                .map(this::mapConversationSummary)
                .collect(Collectors.toList());
    }

    public AgentConversationDetail getConversation(Long userId, String sessionId) {
        ensureSchema();
        if (userId == null || !StringUtils.hasText(sessionId)) {
            if (userId == null) {
                log.warn("查询智能助手历史详情时未识别到登录用户, sessionId={}", sessionId);
            }
            return null;
        }

        AgentConversation conversation = conversationMapper.selectOwnedConversation(userId, sessionId);
        if (conversation == null) {
            return null;
        }

        AgentConversationSummary summary = mapConversationSummary(conversation);
        List<AgentConversationEntry> messages = messageMapper.selectByConversationId(sessionId).stream()
                .map(this::mapConversationEntry)
                .collect(Collectors.toList());

        AgentConversationDetail detail = new AgentConversationDetail();
        detail.setSessionId(summary.getSessionId());
        detail.setTitle(summary.getTitle());
        detail.setPreview(summary.getPreview());
        detail.setDatasourceId(summary.getDatasourceId());
        detail.setDatasourceName(summary.getDatasourceName());
        detail.setDatasourceType(summary.getDatasourceType());
        detail.setMessageCount(summary.getMessageCount());
        detail.setCreatedAt(summary.getCreatedAt());
        detail.setUpdatedAt(summary.getUpdatedAt());
        detail.setLastMessageAt(summary.getLastMessageAt());
        detail.setMessages(messages);
        return detail;
    }

    public void deleteConversation(Long userId, String sessionId) {
        ensureSchema();
        if (userId == null || !StringUtils.hasText(sessionId)) {
            return;
        }

        conversationMapper.deleteOwnedConversation(userId, sessionId);
    }

    private List<AgentChatMessage> loadChatHistory(Long userId, String sessionId) {
        AgentConversation conversation = conversationMapper.selectOwnedConversation(userId, sessionId);
        if (conversation == null) {
            return Collections.emptyList();
        }
        return messageMapper.selectByConversationId(sessionId).stream()
                .map(record -> {
                    AgentChatMessage message = new AgentChatMessage();
                    message.setRole(record.getRole());
                    message.setContent(record.getContent());
                    return message;
                })
                .collect(Collectors.toList());
    }

    private void upsertConversation(Long userId, ConversationIdentity identity, String userMessage) {
        String sessionId = identity.sessionId();
        String title = abbreviate(StringUtils.hasText(userMessage) ? userMessage : "新对话", MAX_TITLE_LENGTH);

        conversationMapper.insertIfAbsent(
                sessionId,
                userId,
                title,
                identity.datasourceId(),
                identity.datasourceName(),
                identity.datasourceType()
        );
    }

    private void insertMessage(String sessionId,
                               String role,
                               String content,
                               List<AgentToolCall> toolCalls,
                               AgentResult result,
                               List<String> suggestions) {
        messageMapper.insert(AgentConversationMessage.builder()
                .conversationId(sessionId)
                .role(role)
                .content(content)
                .toolCallsJson(writeJson(toolCalls))
                .resultJson(writeJson(result))
                .suggestionsJson(writeJson(suggestions))
                .build());
    }

    private void updateConversationAfterTurn(String sessionId,
                                             ConversationIdentity identity,
                                             String titleSeed,
                                             String previewSeed,
                                             int addedMessages) {
        conversationMapper.updateAfterTurn(
                sessionId,
                abbreviate(titleSeed, MAX_TITLE_LENGTH),
                abbreviate(previewSeed, MAX_PREVIEW_LENGTH),
                identity.datasourceId(),
                identity.datasourceName(),
                identity.datasourceType(),
                addedMessages
        );
    }

    private ConversationIdentity fillDatasource(ConversationIdentity identity,
                                                String datasourceId,
                                                String datasourceName,
                                                String datasourceType) {
        if (StringUtils.hasText(datasourceId) && StringUtils.hasText(datasourceName) && StringUtils.hasText(datasourceType)) {
            return new ConversationIdentity(identity.sessionId(), datasourceId, datasourceName, datasourceType);
        }

        if (!StringUtils.hasText(datasourceId)) {
            return identity;
        }

        ConfigComponent datasource = configComponentService.getById(datasourceId);
        if (datasource == null) {
            return identity;
        }

        return new ConversationIdentity(
                identity.sessionId(),
                datasourceId,
                StringUtils.hasText(datasourceName) ? datasourceName : datasource.getName(),
                StringUtils.hasText(datasourceType) ? datasourceType : datasource.getVectorType()
        );
    }

    private ConversationIdentity resolveConversationIdentity(String sessionId,
                                                             String datasourceId,
                                                             String datasourceName,
                                                             String datasourceType) {
        return new ConversationIdentity(
                StringUtils.hasText(sessionId) ? sessionId.trim() : "agent-" + UUID.randomUUID(),
                trimToNull(datasourceId),
                trimToNull(datasourceName),
                trimToNull(datasourceType)
        );
    }

    private AgentConversationSummary mapConversationSummary(AgentConversation conversation) {
        return AgentConversationSummary.builder()
                .sessionId(conversation.getId())
                .title(conversation.getTitle())
                .preview(conversation.getPreview())
                .datasourceId(conversation.getDatasourceId())
                .datasourceName(conversation.getDatasourceName())
                .datasourceType(conversation.getDatasourceType())
                .messageCount(conversation.getMessageCount())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .lastMessageAt(conversation.getLastMessageAt())
                .build();
    }

    private AgentConversationEntry mapConversationEntry(AgentConversationMessage message) {
        return AgentConversationEntry.builder()
                .id(message.getId())
                .role(message.getRole())
                .content(message.getContent())
                .toolCalls(readToolCalls(message.getToolCallsJson()))
                .result(readResult(message.getResultJson()))
                .suggestions(readStringList(message.getSuggestionsJson()))
                .createdAt(message.getCreatedAt())
                .build();
    }

    private List<AgentToolCall> readToolCalls(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, TOOL_CALL_LIST_TYPE);
        } catch (Exception ex) {
            log.warn("解析 agent toolCalls 失败: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private AgentResult readResult(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AgentResult.class);
        } catch (Exception ex) {
            log.warn("解析 agent result 失败: {}", ex.getMessage());
            return null;
        }
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (Exception ex) {
            log.warn("解析 agent suggestions 失败: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("序列化智能助手历史记录失败: " + ex.getMessage(), ex);
        }
    }

    private String abbreviate(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private void ensureSchema() {
        if (schemaReady.compareAndSet(false, true)) {
            conversationMapper.ensureTable();
            conversationMapper.ensureUserLastMessageIndex();
            conversationMapper.ensureUserUpdatedAtIndex();
            messageMapper.ensureTable();
            messageMapper.ensureConversationTimeIndex();

            log.info("智能助手历史记录表结构已就绪");
        }
    }

    private record ConversationIdentity(String sessionId, String datasourceId, String datasourceName, String datasourceType) {
    }
}
