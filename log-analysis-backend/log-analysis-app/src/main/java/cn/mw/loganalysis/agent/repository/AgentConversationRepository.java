package cn.mw.loganalysis.agent.repository;

import cn.mw.loganalysis.agent.entity.AgentConversation;
import cn.mw.loganalysis.agent.mapper.AgentConversationMapper;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 智能助手会话仓储。
 *
 * Repository 负责封装简单的表 CRUD 和 wrapper 组装，
 * 避免把“简单条件查询/更新”继续堆在 Mapper XML 或 Service 里。
 */
@Repository
@DS("postgres")
@RequiredArgsConstructor
public class AgentConversationRepository {

    private static final long MAX_RECENT_CONVERSATIONS = 100L;

    private final AgentConversationMapper conversationMapper;

    public List<AgentConversation> findRecentByUserId(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return Collections.emptyList();
        }

        Page<AgentConversation> page = new Page<>(1, MAX_RECENT_CONVERSATIONS, false);
        return conversationMapper.selectPage(
                page,
                Wrappers.<AgentConversation>lambdaQuery()
                        .eq(AgentConversation::getUserId, userId)
                        .orderByDesc(AgentConversation::getLastMessageAt)
                        .orderByDesc(AgentConversation::getUpdatedAt)
        ).getRecords();
    }

    public AgentConversation findOwnedConversation(Long userId, String sessionId) {
        if (ObjectUtils.isEmpty(userId) || StringUtils.isBlank(sessionId)) {
            return null;
        }

        return conversationMapper.selectOne(
                Wrappers.<AgentConversation>lambdaQuery()
                        .eq(AgentConversation::getUserId, userId)
                        .eq(AgentConversation::getId, StringUtils.trim(sessionId))
        );
    }

    public void createIfAbsent(AgentConversation conversation) {
        if (conversation == null || StringUtils.isBlank(conversation.getId())) {
            return;
        }

        if (conversationMapper.selectById(conversation.getId()) != null) {
            return;
        }

        try {
            conversationMapper.insert(conversation);
        } catch (DuplicateKeyException ignored) {
            // 并发首条消息写入时，以数据库唯一键兜底，已有记录即可视为成功。
        }
    }

    public void updateAfterTurn(String sessionId,
                                String title,
                                String preview,
                                String datasourceId,
                                String datasourceName,
                                String datasourceType,
                                int messageCount) {
        if (StringUtils.isBlank(sessionId)) {
            return;
        }

        AgentConversation existing = conversationMapper.selectById(StringUtils.trim(sessionId));
        if (existing == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        conversationMapper.update(
                null,
                Wrappers.<AgentConversation>lambdaUpdate()
                        .eq(AgentConversation::getId, existing.getId())
                        .set(shouldRefreshTitle(existing), AgentConversation::getTitle, title)
                        .set(AgentConversation::getPreview, preview)
                        .set(StringUtils.isNotBlank(datasourceId), AgentConversation::getDatasourceId, datasourceId)
                        .set(StringUtils.isNotBlank(datasourceName), AgentConversation::getDatasourceName, datasourceName)
                        .set(StringUtils.isNotBlank(datasourceType), AgentConversation::getDatasourceType, datasourceType)
                        .set(AgentConversation::getMessageCount, messageCount)
                        .set(AgentConversation::getUpdatedAt, now)
                        .set(AgentConversation::getLastMessageAt, now)
        );
    }

    public void deleteOwnedConversation(Long userId, String sessionId) {
        if (ObjectUtils.isEmpty(userId) || StringUtils.isBlank(sessionId)) {
            return;
        }

        conversationMapper.delete(
                Wrappers.<AgentConversation>lambdaQuery()
                        .eq(AgentConversation::getUserId, userId)
                        .eq(AgentConversation::getId, StringUtils.trim(sessionId))
        );
    }

    private boolean shouldRefreshTitle(AgentConversation conversation) {
        return conversation != null
                && (ObjectUtils.defaultIfNull(conversation.getMessageCount(), 0) == 0
                || StringUtils.isBlank(conversation.getTitle())
                || StringUtils.equals(conversation.getTitle(), "新对话"));
    }
}
