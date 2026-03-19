package cn.mw.loganalysis.agent.mapper;

import cn.mw.loganalysis.agent.entity.AgentConversation;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 智能助手历史会话 Mapper。
 */
@Mapper
@DS("postgres")
public interface AgentConversationMapper extends BaseMapper<AgentConversation> {

    void ensureTable();

    void ensureUserLastMessageIndex();

    void ensureUserUpdatedAtIndex();

    List<AgentConversation> selectRecentByUserId(@Param("userId") Long userId);

    AgentConversation selectOwnedConversation(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    int insertIfAbsent(@Param("sessionId") String sessionId,
                       @Param("userId") Long userId,
                       @Param("title") String title,
                       @Param("datasourceId") String datasourceId,
                       @Param("datasourceName") String datasourceName,
                       @Param("datasourceType") String datasourceType);

    int updateAfterTurn(@Param("sessionId") String sessionId,
                        @Param("title") String title,
                        @Param("preview") String preview,
                        @Param("datasourceId") String datasourceId,
                        @Param("datasourceName") String datasourceName,
                        @Param("datasourceType") String datasourceType,
                        @Param("addedMessages") int addedMessages);

    int deleteOwnedConversation(@Param("userId") Long userId, @Param("sessionId") String sessionId);
}
