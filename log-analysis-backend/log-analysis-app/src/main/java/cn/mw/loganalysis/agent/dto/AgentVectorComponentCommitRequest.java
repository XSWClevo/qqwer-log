package cn.mw.loganalysis.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 智能助手 Vector 组件计划确认请求。
 */
@Data
public class AgentVectorComponentCommitRequest {

    /**
     * 当前智能助手会话 ID，用于校验 planId 是否属于当前会话。
     */
    @NotBlank(message = "会话ID不能为空")
    private String sessionId;
}
