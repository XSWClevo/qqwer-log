package cn.mw.loganalysis.agent.mapper;

import cn.mw.loganalysis.agent.entity.AgentConversationMessage;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 智能助手历史消息 Mapper。
 */
@Mapper
@DS("postgres")
public interface AgentConversationMessageMapper extends BaseMapper<AgentConversationMessage> {

    void ensureTable();

    void ensureConversationTimeIndex();

    List<AgentConversationMessage> selectByConversationId(@Param("conversationId") String conversationId);
}
