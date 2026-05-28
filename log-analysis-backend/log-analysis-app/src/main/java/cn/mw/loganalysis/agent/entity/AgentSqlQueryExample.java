package cn.mw.loganalysis.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 智能助手成功 SQL 查询经验。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("agent_sql_query_examples")
public class AgentSqlQueryExample {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String datasourceId;

    private String datasourceType;

    private String question;

    private String normalizedQuestion;

    private String sqlTemplate;

    private String resultType;

    private Integer hitCount;

    private LocalDateTime lastUsedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
