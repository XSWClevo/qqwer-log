package cn.mw.loganalysis.agent.repository;

import cn.mw.loganalysis.agent.entity.AgentConversationMessage;
import cn.mw.loganalysis.agent.mapper.AgentConversationMessageMapper;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

/**
 * 智能助手消息仓储。
 *
 * 这里集中封装基于 conversation_id 的简单查询和计数，
 * 避免 Service 直接依赖 Mapper 组装 wrapper。
 */
@Repository
@DS("postgres")
@RequiredArgsConstructor
public class AgentConversationMessageRepository {

    private final AgentConversationMessageMapper messageMapper;

    public void save(AgentConversationMessage message) {
        if (message == null) {
            return;
        }
        messageMapper.insert(message);
    }

    public List<AgentConversationMessage> findByConversationId(String conversationId) {
        if (StringUtils.isBlank(conversationId)) {
            return Collections.emptyList();
        }

        return messageMapper.selectList(
                Wrappers.<AgentConversationMessage>lambdaQuery()
                        .eq(AgentConversationMessage::getConversationId, StringUtils.trim(conversationId))
                        .orderByAsc(AgentConversationMessage::getCreatedAt)
                        .orderByAsc(AgentConversationMessage::getId)
        );
    }

    public int countByConversationId(String conversationId) {
        if (StringUtils.isBlank(conversationId)) {
            return 0;
        }

        Long count = messageMapper.selectCount(
                Wrappers.<AgentConversationMessage>lambdaQuery()
                        .eq(AgentConversationMessage::getConversationId, StringUtils.trim(conversationId))
        );
        return count != null ? count.intValue() : 0;
    }
}
