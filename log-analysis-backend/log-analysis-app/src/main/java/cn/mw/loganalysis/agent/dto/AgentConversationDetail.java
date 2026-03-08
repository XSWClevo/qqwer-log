package cn.mw.loganalysis.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 智能助手会话详情。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AgentConversationDetail extends AgentConversationSummary {

    /**
     * 完整消息列表。
     */
    private List<AgentConversationEntry> messages;
}
