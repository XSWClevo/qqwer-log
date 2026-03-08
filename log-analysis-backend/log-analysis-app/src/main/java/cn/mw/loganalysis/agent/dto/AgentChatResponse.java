package cn.mw.loganalysis.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 智能助手聊天响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatResponse {

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 识别到的意图
     */
    private String intent;

    /**
     * 助手回复
     */
    private String answer;

    /**
     * 错误信息
     */
    private String error;

    /**
     * 数据源ID
     */
    private String datasourceId;

    /**
     * 会话ID。
     * 前端后续请求应继续携带这个值，以便后端加载同一段对话记忆。
     */
    private String sessionId;

    /**
     * 数据源名称
     */
    private String datasourceName;

    /**
     * 工具调用轨迹
     */
    private List<AgentToolCall> toolCalls;

    /**
     * 结构化结果
     */
    private AgentResult result;

    /**
     * 建议继续追问的提示
     */
    private List<String> suggestions;
}
