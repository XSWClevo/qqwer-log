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
import cn.mw.loganalysis.agent.repository.AgentConversationMessageRepository;
import cn.mw.loganalysis.agent.repository.AgentConversationRepository;
import cn.mw.loganalysis.vector.entity.ConfigComponent;
import cn.mw.loganalysis.vector.service.ConfigComponentService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
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

    private final AgentConversationRepository conversationRepository;
    private final AgentConversationMessageRepository messageRepository;
    private final ObjectMapper objectMapper;
    private final ConfigComponentService configComponentService;

    /**
     * 补充 request 历史
     * 如果前端没传历史，且当前 session 在数据库里已经有消息，就把持久化历史回填到请求里。
     * 这样页面刷新后继续聊时，不需要前端自己回放全部历史。
     */
    public AgentChatRequest hydrateRequestHistory(AgentChatRequest request, Long userId) {
        if (ObjectUtils.isEmpty(userId) || StringUtils.isBlank(request.getSessionId())) {
            if (ObjectUtils.isEmpty(userId)) {
                log.warn("跳过智能助手历史回填，因为当前请求未识别到登录用户");
            }
            return request;
        }
        if (CollectionUtils.isNotEmpty(request.getHistory())) {
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
        if (ObjectUtils.isEmpty(userId) || StringUtils.isBlank(sessionId)) {
            if (ObjectUtils.isEmpty(userId)) {
                log.warn("跳过智能助手历史保存，因为当前请求未识别到登录用户, sessionId={}", sessionId);
            } else {
                log.warn("跳过智能助手历史保存，因为 sessionId 为空, userId={}", userId);
            }
            return;
        }

        ConversationIdentity identity = resolveConversationIdentity(sessionId, datasourceId, datasourceName, datasourceType);
        ConversationIdentity targetIdentity = fillDatasource(identity, datasourceId, datasourceName, datasourceType);
        upsertConversation(userId, targetIdentity, userMessage);

        if (StringUtils.isNotBlank(userMessage)) {
            insertMessage(sessionId, "user", userMessage, null, null, null);
        }

        String assistantContent = response != null
                ? (response.getSuccess() ? response.getAnswer() : response.getError())
                : null;
        if (StringUtils.isNotBlank(assistantContent)) {
            insertMessage(
                    sessionId,
                    "assistant",
                    assistantContent,
                    response.getToolCalls(),
                    response.getResult(),
                    response.getSuggestions()
            );
        }

        int messageCount = messageRepository.countByConversationId(sessionId);
        if (messageCount > 0) {
            String preview = StringUtils.isNotBlank(assistantContent) ? assistantContent : userMessage;
            String titleSeed = StringUtils.isNotBlank(userMessage) ? userMessage : preview;
            updateConversationAfterTurn(sessionId, targetIdentity, titleSeed, preview, messageCount);
        }
    }

    public List<AgentConversationSummary> listConversations(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            log.warn("查询智能助手历史列表时未识别到登录用户");
            return Collections.emptyList();
        }

        return conversationRepository.findRecentByUserId(userId).stream()
                .map(this::mapConversationSummary)
                .collect(Collectors.toList());
    }

    public AgentConversationDetail getConversation(Long userId, String sessionId) {
        if (ObjectUtils.isEmpty(userId) || StringUtils.isBlank(sessionId)) {
            if (ObjectUtils.isEmpty(userId)) {
                log.warn("查询智能助手历史详情时未识别到登录用户, sessionId={}", sessionId);
            }
            return null;
        }

        AgentConversation conversation = conversationRepository.findOwnedConversation(userId, sessionId);
        if (conversation == null) {
            return null;
        }

        AgentConversationSummary summary = mapConversationSummary(conversation);
        List<AgentConversationEntry> messages = messageRepository.findByConversationId(sessionId).stream()
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
        if (ObjectUtils.isEmpty(userId) || StringUtils.isBlank(sessionId)) {
            return;
        }

        conversationRepository.deleteOwnedConversation(userId, sessionId);
    }

    private List<AgentChatMessage> loadChatHistory(Long userId, String sessionId) {
        AgentConversation conversation = conversationRepository.findOwnedConversation(userId, sessionId);
        if (conversation == null) {
            return Collections.emptyList();
        }
        return messageRepository.findByConversationId(sessionId).stream()
                .map(record -> {
                    AgentChatMessage message = new AgentChatMessage();
                    message.setRole(record.getRole());
                    message.setContent(record.getContent());
                    return message;
                })
                .toList();
    }

    private void upsertConversation(Long userId, ConversationIdentity identity, String userMessage) {
        String sessionId = identity.sessionId();
        String title = abbreviate(StringUtils.isNotBlank(userMessage) ? userMessage : "新对话", MAX_TITLE_LENGTH);

        conversationRepository.createIfAbsent(AgentConversation.builder()
                .id(sessionId)
                .userId(userId)
                .title(title)
                .preview("")
                .datasourceId(identity.datasourceId())
                .datasourceName(identity.datasourceName())
                .datasourceType(identity.datasourceType())
                .messageCount(0)
                .build());
    }

    private void insertMessage(String sessionId,
                               String role,
                               String content,
                               List<AgentToolCall> toolCalls,
                               AgentResult result,
                               List<String> suggestions) {
        messageRepository.save(AgentConversationMessage.builder()
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
                                             int messageCount) {
        conversationRepository.updateAfterTurn(
                sessionId,
                abbreviate(titleSeed, MAX_TITLE_LENGTH),
                abbreviate(previewSeed, MAX_PREVIEW_LENGTH),
                identity.datasourceId(),
                identity.datasourceName(),
                identity.datasourceType(),
                messageCount
        );
    }

    private ConversationIdentity fillDatasource(ConversationIdentity identity,
                                                String datasourceId,
                                                String datasourceName,
                                                String datasourceType) {
        if (StringUtils.isNotBlank(datasourceId) && StringUtils.isNotBlank(datasourceName) && StringUtils.isNotBlank(datasourceType)) {
            return new ConversationIdentity(identity.sessionId(), datasourceId, datasourceName, datasourceType);
        }

        if (StringUtils.isBlank(datasourceId)) {
            return identity;
        }

        ConfigComponent datasource = configComponentService.getById(datasourceId);
        if (datasource == null) {
            return identity;
        }

        return new ConversationIdentity(
                identity.sessionId(),
                datasourceId,
                StringUtils.isNotBlank(datasourceName) ? datasourceName : datasource.getName(),
                StringUtils.isNotBlank(datasourceType) ? datasourceType : datasource.getVectorType()
        );
    }

    private ConversationIdentity resolveConversationIdentity(String sessionId,
                                                             String datasourceId,
                                                             String datasourceName,
                                                             String datasourceType) {
        return new ConversationIdentity(
                StringUtils.isNotBlank(sessionId) ? sessionId.trim() : "agent-" + UUID.randomUUID(),
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
        if (StringUtils.isBlank(json)) {
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
        if (StringUtils.isBlank(json)) {
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
        if (StringUtils.isBlank(json)) {
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
        if (StringUtils.isBlank(text)) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private String trimToNull(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return value.trim();
    }

    private record ConversationIdentity(String sessionId, String datasourceId, String datasourceName, String datasourceType) {
    }
}
