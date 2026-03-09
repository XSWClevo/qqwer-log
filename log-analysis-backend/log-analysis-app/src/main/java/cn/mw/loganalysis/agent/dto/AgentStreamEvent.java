package cn.mw.loganalysis.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 智能助手流式事件。
 *
 * 当前流式接口使用 NDJSON：每一行都是一个 AgentStreamEvent。
 * 事件尽量保持扁平，方便前端按 type 增量更新消息、工具状态和最终结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentStreamEvent {

    /**
     * 事件类型：
     * - started: 会话已建立
     * - token: 助手正文增量文本
     * - tool_started: 工具开始执行
     * - tool_finished: 工具执行完成
     * - done: 本轮对话最终完成，response 为最终完整结果
     * - error: 流式链路发生无法恢复的错误
     */
    private String type;

    private String sessionId;
    private String delta;
    private String message;
    private AgentToolCall toolCall;
    private AgentChatResponse response;

    public static AgentStreamEvent started(String sessionId) {
        return AgentStreamEvent.builder()
                .type("started")
                .sessionId(sessionId)
                .build();
    }

    public static AgentStreamEvent token(String delta) {
        return AgentStreamEvent.builder()
                .type("token")
                .delta(delta)
                .build();
    }

    public static AgentStreamEvent toolStarted(AgentToolCall toolCall) {
        return AgentStreamEvent.builder()
                .type("tool_started")
                .toolCall(toolCall)
                .build();
    }

    public static AgentStreamEvent toolFinished(AgentToolCall toolCall) {
        return AgentStreamEvent.builder()
                .type("tool_finished")
                .toolCall(toolCall)
                .build();
    }

    public static AgentStreamEvent done(AgentChatResponse response) {
        return AgentStreamEvent.builder()
                .type("done")
                .sessionId(response != null ? response.getSessionId() : null)
                .response(response)
                .build();
    }

    public static AgentStreamEvent error(String message, String sessionId) {
        return AgentStreamEvent.builder()
                .type("error")
                .message(message)
                .sessionId(sessionId)
                .build();
    }
}
