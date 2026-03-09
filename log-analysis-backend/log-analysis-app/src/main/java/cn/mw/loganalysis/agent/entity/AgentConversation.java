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
 * 智能助手历史会话持久化实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("agent_conversations")
public class AgentConversation {

    @TableId(type = IdType.INPUT)
    private String id;

    private Long userId;

    private String title;

    private String preview;

    private String datasourceId;

    private String datasourceName;

    private String datasourceType;

    private Integer messageCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime lastMessageAt;
}
