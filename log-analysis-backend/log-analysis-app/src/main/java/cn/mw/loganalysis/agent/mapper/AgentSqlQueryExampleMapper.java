package cn.mw.loganalysis.agent.mapper;

import cn.mw.loganalysis.agent.entity.AgentSqlQueryExample;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 智能助手 SQL 查询经验 Mapper。
 */
@Mapper
@DS("postgres")
public interface AgentSqlQueryExampleMapper extends BaseMapper<AgentSqlQueryExample> {

    /**
     * 原子保存成功 SQL 经验。
     */
    @Insert("""
            INSERT INTO agent_sql_query_examples (
                user_id,
                datasource_id,
                datasource_type,
                question,
                normalized_question,
                sql_template,
                result_type,
                hit_count,
                last_used_at,
                created_at,
                updated_at
            ) VALUES (
                #{userId},
                #{datasourceId},
                #{datasourceType},
                #{question},
                #{normalizedQuestion},
                #{sqlTemplate},
                #{resultType},
                0,
                #{now},
                #{now},
                #{now}
            )
            ON CONFLICT (user_id, datasource_id, normalized_question) DO UPDATE SET
                datasource_type = EXCLUDED.datasource_type,
                question = EXCLUDED.question,
                sql_template = EXCLUDED.sql_template,
                result_type = EXCLUDED.result_type,
                hit_count = agent_sql_query_examples.hit_count + 1,
                last_used_at = EXCLUDED.last_used_at,
                updated_at = EXCLUDED.updated_at
            """)
    int upsertSuccess(@Param("userId") Long userId,
                      @Param("datasourceId") String datasourceId,
                      @Param("datasourceType") String datasourceType,
                      @Param("question") String question,
                      @Param("normalizedQuestion") String normalizedQuestion,
                      @Param("sqlTemplate") String sqlTemplate,
                      @Param("resultType") String resultType,
                      @Param("now") LocalDateTime now);

    /**
     * 原子增加历史经验命中次数。
     */
    @Update("""
            UPDATE agent_sql_query_examples
            SET hit_count = hit_count + 1,
                last_used_at = #{now},
                updated_at = #{now}
            WHERE id = #{id}
            """)
    int incrementHitCount(@Param("id") Long id, @Param("now") LocalDateTime now);
}
