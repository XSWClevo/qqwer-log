package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.dto.AgentChatRequest;
import cn.mw.loganalysis.agent.dto.AgentChatResponse;
import cn.mw.loganalysis.agent.dto.AgentConversationDetail;
import cn.mw.loganalysis.agent.dto.AgentConversationSummary;
import cn.mw.loganalysis.vector.entity.ConfigComponent;
import cn.mw.loganalysis.vector.service.ConfigComponentService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 统一管理智能助手的 session 生命周期。
 *
 * 这里收口三类能力：
 * 1. 首次进入请求时回填持久化历史，并准备内存记忆
 * 2. 请求完成后统一补 sessionId、写 memory、落 history
 * 3. 删除会话时同时清理历史记录和内存缓存
 */
@Service
@RequiredArgsConstructor
public class AgentSessionService {

    private final AgentConversationMemoryService conversationMemoryService;
    private final AgentConversationHistoryService conversationHistoryService;
    private final ConfigComponentService configComponentService;

    public AgentConversationMemoryService.PreparedAgentChatRequest prepare(AgentChatRequest request, Long userId) {
        AgentChatRequest hydratedRequest = conversationHistoryService.hydrateRequestHistory(request, userId);
        return conversationMemoryService.prepare(hydratedRequest, userId);
    }

    public AgentChatResponse finalizeResponse(Long userId, String sessionId, String userMessage, AgentChatResponse response) {
        if (response == null) {
            return null;
        }

        response.setSessionId(sessionId);
        conversationHistoryService.saveTurn(
                userId,
                sessionId,
                response.getDatasourceId(),
                response.getDatasourceName(),
                inferDatasourceType(response.getDatasourceId()),
                userMessage,
                response
        );
        conversationMemoryService.remember(
                sessionId,
                userId,
                response.getDatasourceId(),
                response.getDatasourceName(),
                null,
                userMessage,
                Boolean.TRUE.equals(response.getSuccess()) ? response.getAnswer() : response.getError()
        );
        return response;
    }

    public List<AgentConversationSummary> listConversations(Long userId) {
        return conversationHistoryService.listConversations(userId);
    }

    public AgentConversationDetail getConversation(Long userId, String sessionId) {
        return conversationHistoryService.getConversation(userId, sessionId);
    }

    public void deleteConversation(Long userId, String sessionId) {
        conversationHistoryService.deleteConversation(userId, sessionId);
        conversationMemoryService.forget(sessionId, userId);
    }

    private String inferDatasourceType(String datasourceId) {
        if (StringUtils.isBlank(datasourceId)) {
            return null;
        }
        ConfigComponent datasource = configComponentService.getQueryableDataSourceById(datasourceId);
        return datasource != null ? datasource.getVectorType() : null;
    }
}
