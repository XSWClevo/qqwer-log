package cn.mw.loganalysis.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 智能助手聊天请求
 */
@Data
public class AgentChatRequest {

    /**
     * 当前用户问题
     */
    @NotBlank(message = "消息内容不能为空")
    private String message;

    /**
     * 选中的可查询数据源
     */
    private String datasourceId;

    /**
     * 前端显式选择的内置能力。
     * 为空时保持原有意图识别逻辑。
     */
    private String skillId;

    /**
     * 当前页面上下文，例如 COMPONENT_LIBRARY / AGENT_CHAT。
     */
    private String pageContext;

    /**
     * 当前前端路由，用于辅助判断页面语义。
     */
    private String routePath;

    /**
     * 页面可选结构化上下文。
     */
    private Map<String, Object> surfaceContext;

    /**
     * 会话ID。
     * 前端同一轮对话内保持不变，后端用它做 session/memory 归档。
     */
    private String sessionId;

    /**
     * 对话历史。
     * 现在主要作为旧前端或首次接入时的 bootstrap 数据，
     * 正常情况下后端会优先使用 sessionId 对应的服务端记忆。
     */
    private List<AgentChatMessage> history;
}
