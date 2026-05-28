package cn.mw.loganalysis.agent.mapper;

import cn.mw.loganalysis.agent.entity.AgentSqlQueryExample;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 智能助手 SQL 查询经验 Mapper。
 */
@Mapper
@DS("postgres")
public interface AgentSqlQueryExampleMapper extends BaseMapper<AgentSqlQueryExample> {
}
