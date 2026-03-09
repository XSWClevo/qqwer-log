package cn.mw.loganalysis.agent.mapper;

import cn.mw.loganalysis.agent.entity.AgentConversation;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 智能助手历史会话 Mapper。
 */
@Mapper
@DS("postgres")
public interface AgentConversationMapper extends BaseMapper<AgentConversation> {

    @Update("""
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
            """)
    void ensureTable();

    @Update("""
            CREATE INDEX IF NOT EXISTS idx_agent_conversations_user_last_message
            ON agent_conversations(user_id, last_message_at DESC)
            """)
    void ensureUserLastMessageIndex();

    @Update("""
            CREATE INDEX IF NOT EXISTS idx_agent_conversations_user_updated_at
            ON agent_conversations(user_id, updated_at DESC)
            """)
    void ensureUserUpdatedAtIndex();

    @Select("""
            SELECT id,
                   user_id,
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
            WHERE user_id = #{userId}
            ORDER BY last_message_at DESC, updated_at DESC
            LIMIT 100
            """)
    List<AgentConversation> selectRecentByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT id,
                   user_id,
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
            WHERE user_id = #{userId}
              AND id = #{sessionId}
            LIMIT 1
            """)
    AgentConversation selectOwnedConversation(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    @Insert("""
            INSERT INTO agent_conversations (
                id, user_id, title, preview, datasource_id, datasource_name, datasource_type,
                message_count, created_at, updated_at, last_message_at
            )
            VALUES (
                #{sessionId}, #{userId}, #{title}, '', #{datasourceId}, #{datasourceName}, #{datasourceType},
                0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            )
            ON CONFLICT (id) DO NOTHING
            """)
    int insertIfAbsent(@Param("sessionId") String sessionId,
                       @Param("userId") Long userId,
                       @Param("title") String title,
                       @Param("datasourceId") String datasourceId,
                       @Param("datasourceName") String datasourceName,
                       @Param("datasourceType") String datasourceType);

    @Update("""
            UPDATE agent_conversations
            SET title = CASE
                            WHEN message_count = 0 OR title IS NULL OR title = '' OR title = '新对话'
                                THEN #{title}
                            ELSE title
                        END,
                preview = #{preview},
                datasource_id = COALESCE(#{datasourceId}, datasource_id),
                datasource_name = COALESCE(#{datasourceName}, datasource_name),
                datasource_type = COALESCE(#{datasourceType}, datasource_type),
                message_count = message_count + #{addedMessages},
                updated_at = CURRENT_TIMESTAMP,
                last_message_at = CURRENT_TIMESTAMP
            WHERE id = #{sessionId}
            """)
    int updateAfterTurn(@Param("sessionId") String sessionId,
                        @Param("title") String title,
                        @Param("preview") String preview,
                        @Param("datasourceId") String datasourceId,
                        @Param("datasourceName") String datasourceName,
                        @Param("datasourceType") String datasourceType,
                        @Param("addedMessages") int addedMessages);

    @Delete("""
            DELETE FROM agent_conversations
            WHERE user_id = #{userId}
              AND id = #{sessionId}
            """)
    int deleteOwnedConversation(@Param("userId") Long userId, @Param("sessionId") String sessionId);
}
