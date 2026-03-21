package cn.mw.loganalysis.agent.mapper;

import cn.mw.loganalysis.agent.entity.AgentConversation;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 智能助手历史会话 Mapper。
 */
@Mapper
@DS("postgres")
public interface AgentConversationMapper extends BaseMapper<AgentConversation> {
}
