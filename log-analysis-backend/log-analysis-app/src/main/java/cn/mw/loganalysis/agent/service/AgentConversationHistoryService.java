package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.dto.AgentChatMessage;
import cn.mw.loganalysis.agent.dto.AgentChatRequest;
import cn.mw.loganalysis.agent.dto.AgentChatResponse;
import cn.mw.loganalysis.agent.dto.AgentConversationDetail;
import cn.mw.loganalysis.agent.dto.AgentConversationEntry;
import cn.mw.loganalysis.agent.dto.AgentConversationSummary;
import cn.mw.loganalysis.agent.dto.AgentResult;
import cn.mw.loganalysis.agent.dto.AgentToolCall;
import cn.mw.loganalysis.vector.entity.ConfigComponent;
import cn.mw.loganalysis.vector.service.ConfigComponentService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private final JdbcTemplate jdbcTemplate;
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

        return jdbcTemplate.query("""
                SELECT id,
                       title,
                       preview,
                       datasource_id,
                       datasource_name,
                       datasource_type,
                       message_count,
                       created_at,
                       updated_at,
                       last_message_at
                FROM agent_conversations
                WHERE user_id = ?
                ORDER BY last_message_at DESC, updated_at DESC
                LIMIT 100
                """, summaryRowMapper(), userId);
    }

    public AgentConversationDetail getConversation(Long userId, String sessionId) {
        ensureSchema();
        if (userId == null || !StringUtils.hasText(sessionId)) {
            if (userId == null) {
                log.warn("查询智能助手历史详情时未识别到登录用户, sessionId={}", sessionId);
            }
            return null;
        }

        List<AgentConversationSummary> summaries = jdbcTemplate.query("""
                SELECT id,
                       title,
                       preview,
                       datasource_id,
                       datasource_name,
                       datasource_type,
                       message_count,
                       created_at,
                       updated_at,
                       last_message_at
                FROM agent_conversations
                WHERE user_id = ? AND id = ?
                LIMIT 1
                """, summaryRowMapper(), userId, sessionId);
        if (summaries.isEmpty()) {
            return null;
        }

        AgentConversationSummary summary = summaries.get(0);
        List<AgentConversationEntry> messages = jdbcTemplate.query("""
                SELECT id,
                       role,
                       content,
                       tool_calls_json,
                       result_json,
                       suggestions_json,
                       created_at
                FROM agent_conversation_messages
                WHERE conversation_id = ?
                ORDER BY created_at ASC, id ASC
                """, this::mapConversationEntry, sessionId);

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

        jdbcTemplate.update("DELETE FROM agent_conversations WHERE user_id = ? AND id = ?", userId, sessionId);
    }

    private List<AgentChatMessage> loadChatHistory(Long userId, String sessionId) {
        return jdbcTemplate.query("""
                SELECT role, content
                FROM agent_conversation_messages m
                JOIN agent_conversations c ON c.id = m.conversation_id
                WHERE c.user_id = ? AND c.id = ?
                ORDER BY m.created_at ASC, m.id ASC
                """, (rs, rowNum) -> {
            AgentChatMessage message = new AgentChatMessage();
            message.setRole(rs.getString("role"));
            message.setContent(rs.getString("content"));
            return message;
        }, userId, sessionId);
    }

    private void upsertConversation(Long userId, ConversationIdentity identity, String userMessage) {
        String sessionId = identity.sessionId();
        String title = abbreviate(StringUtils.hasText(userMessage) ? userMessage : "新对话", MAX_TITLE_LENGTH);

        jdbcTemplate.update("""
                INSERT INTO agent_conversations (
                    id, user_id, title, preview, datasource_id, datasource_name, datasource_type,
                    message_count, created_at, updated_at, last_message_at
                )
                VALUES (?, ?, ?, '', ?, ?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (id) DO NOTHING
                """,
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
        jdbcTemplate.update("""
                INSERT INTO agent_conversation_messages (
                    conversation_id, role, content, tool_calls_json, result_json, suggestions_json, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                sessionId,
                role,
                content,
                writeJson(toolCalls),
                writeJson(result),
                writeJson(suggestions)
        );
    }

    private void updateConversationAfterTurn(String sessionId,
                                             ConversationIdentity identity,
                                             String titleSeed,
                                             String previewSeed,
                                             int addedMessages) {
        jdbcTemplate.update("""
                UPDATE agent_conversations
                SET title = CASE
                                WHEN message_count = 0 OR title IS NULL OR title = '' OR title = '新对话'
                                    THEN ?
                                ELSE title
                            END,
                    preview = ?,
                    datasource_id = COALESCE(?, datasource_id),
                    datasource_name = COALESCE(?, datasource_name),
                    datasource_type = COALESCE(?, datasource_type),
                    message_count = message_count + ?,
                    updated_at = CURRENT_TIMESTAMP,
                    last_message_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                abbreviate(titleSeed, MAX_TITLE_LENGTH),
                abbreviate(previewSeed, MAX_PREVIEW_LENGTH),
                identity.datasourceId(),
                identity.datasourceName(),
                identity.datasourceType(),
                addedMessages,
                sessionId
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

    private AgentConversationSummary mapConversationSummary(ResultSet rs, int rowNum) throws SQLException {
        return AgentConversationSummary.builder()
                .sessionId(rs.getString("id"))
                .title(rs.getString("title"))
                .preview(rs.getString("preview"))
                .datasourceId(rs.getString("datasource_id"))
                .datasourceName(rs.getString("datasource_name"))
                .datasourceType(rs.getString("datasource_type"))
                .messageCount(rs.getInt("message_count"))
                .createdAt(toLocalDateTime(rs, "created_at"))
                .updatedAt(toLocalDateTime(rs, "updated_at"))
                .lastMessageAt(toLocalDateTime(rs, "last_message_at"))
                .build();
    }

    private AgentConversationEntry mapConversationEntry(ResultSet rs, int rowNum) throws SQLException {
        return AgentConversationEntry.builder()
                .id(rs.getLong("id"))
                .role(rs.getString("role"))
                .content(rs.getString("content"))
                .toolCalls(readToolCalls(rs.getString("tool_calls_json")))
                .result(readResult(rs.getString("result_json")))
                .suggestions(readStringList(rs.getString("suggestions_json")))
                .createdAt(toLocalDateTime(rs, "created_at"))
                .build();
    }

    private RowMapper<AgentConversationSummary> summaryRowMapper() {
        return this::mapConversationSummary;
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

    private LocalDateTime toLocalDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp != null ? timestamp.toLocalDateTime() : null;
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
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS agent_conversations (
                        id VARCHAR(64) PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        title VARCHAR(255) NOT NULL,
                        preview TEXT,
                        datasource_id VARCHAR(36),
                        datasource_name VARCHAR(255),
                        datasource_type VARCHAR(50),
                        message_count INTEGER NOT NULL DEFAULT 0,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        last_message_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_agent_conversations_user_last_message ON agent_conversations(user_id, last_message_at DESC)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_agent_conversations_user_updated_at ON agent_conversations(user_id, updated_at DESC)");

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS agent_conversation_messages (
                        id BIGSERIAL PRIMARY KEY,
                        conversation_id VARCHAR(64) NOT NULL REFERENCES agent_conversations(id) ON DELETE CASCADE,
                        role VARCHAR(20) NOT NULL,
                        content TEXT,
                        tool_calls_json TEXT,
                        result_json TEXT,
                        suggestions_json TEXT,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_agent_conversation_messages_conversation_time ON agent_conversation_messages(conversation_id, created_at ASC, id ASC)");

            log.info("智能助手历史记录表结构已就绪");
        }
    }

    private record ConversationIdentity(String sessionId, String datasourceId, String datasourceName, String datasourceType) {
    }
}
