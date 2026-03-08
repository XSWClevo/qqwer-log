package cn.mw.loganalysis.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 智能助手结果邮件发送响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentEmailResponse {

    /**
     * 实际收件人。
     */
    private String recipient;

    /**
     * 邮件主题。
     */
    private String subject;

    /**
     * 发送时间。
     */
    private LocalDateTime sentAt;
}
