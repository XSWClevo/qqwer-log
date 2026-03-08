package cn.mw.loganalysis.agent.dto;

import lombok.Data;

/**
 * 智能助手对话消息
 */
@Data
public class AgentChatMessage {

    /**
     * 消息角色：user / assistant
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;
}
