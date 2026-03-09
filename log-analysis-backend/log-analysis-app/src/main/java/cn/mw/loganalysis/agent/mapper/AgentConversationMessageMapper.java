package cn.mw.loganalysis.agent.mapper;

import cn.mw.loganalysis.agent.entity.AgentConversationMessage;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 智能助手历史消息 Mapper。
 */
@Mapper
@DS("postgres")
public interface AgentConversationMessageMapper extends BaseMapper<AgentConversationMessage> {

    @Update("""
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
            """)
    void ensureTable();

    @Update("""
            CREATE INDEX IF NOT EXISTS idx_agent_conversation_messages_conversation_time
            ON agent_conversation_messages(conversation_id, created_at ASC, id ASC)
            """)
    void ensureConversationTimeIndex();

    @Select("""
            SELECT id,
                   conversation_id,
                   role,
                   content,
                   tool_calls_json,
                   result_json,
                   suggestions_json,
                   created_at
            FROM agent_conversation_messages
            WHERE conversation_id = #{conversationId}
            ORDER BY created_at ASC, id ASC
            """)
    List<AgentConversationMessage> selectByConversationId(String conversationId);
}
