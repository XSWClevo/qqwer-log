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
 * 智能助手历史消息持久化实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("agent_conversation_messages")
public class AgentConversationMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String conversationId;

    private String role;

    private String content;

    private String toolCallsJson;

    private String resultJson;

    private String suggestionsJson;

    private LocalDateTime createdAt;
}
