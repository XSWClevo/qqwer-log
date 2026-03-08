package cn.mw.loganalysis.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 智能助手历史对话中的单条消息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentConversationEntry {

    /**
     * 消息主键。
     */
    private Long id;

    /**
     * 角色：user / assistant。
     */
    private String role;

    /**
     * 文本内容。
     */
    private String content;

    /**
     * 工具调用轨迹。
     * 为了让历史对话回放时仍然能看到工具执行过程，这里会持久化助手消息对应的工具记录。
     */
    private List<AgentToolCall> toolCalls;

    /**
     * 结构化结果。
     * 这样历史会话重新打开时，前端仍然可以直接渲染 schema / logs / timeseries / text2sql 面板。
     */
    private AgentResult result;

    /**
     * 推荐追问。
     */
    private List<String> suggestions;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
}
