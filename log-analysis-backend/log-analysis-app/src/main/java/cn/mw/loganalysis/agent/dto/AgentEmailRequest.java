package cn.mw.loganalysis.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 智能助手结果邮件发送请求。
 */
@Data
public class AgentEmailRequest {

    /**
     * 当前会话 ID，用于邮件标题和审计定位。
     */
    private String sessionId;

    /**
     * 会话标题。
     */
    private String conversationTitle;

    /**
     * 当前数据源显示名。
     */
    private String datasourceName;

    /**
     * 助手回复正文，通常是 Markdown 文本。
     */
    @NotBlank(message = "邮件内容不能为空")
    private String content;

    /**
     * 本轮工具执行摘要，便于邮件里补充“本次做了什么”。
     */
    private List<AgentToolCall> toolCalls;
}
