package cn.mw.loganalysis.agent.execution;

import cn.mw.loganalysis.agent.support.AgentStreamEventEmitter;
import cn.mw.loganalysis.agent.dto.AgentChatRequest;
import cn.mw.loganalysis.agent.dto.AgentChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 规则回退执行器门面。
 *
 * 具体链路由 AgentFallbackWorkflow 编排：
 * 上下文增强 -> 意图策略匹配 -> 意图执行策略 -> 响应组装 -> 观察者记录。
 */
@Component
@RequiredArgsConstructor
public class FallbackAgentExecutor {

    private final AgentFallbackWorkflow fallbackWorkflow;

    /**
     * 判断当前请求是否应该跳过 LLM，直接走确定性工具链。
     */
    public boolean shouldHandleWithoutLlm(AgentChatRequest request, Long userId) {
        return fallbackWorkflow.shouldHandleWithoutLlm(request, userId);
    }

    /**
     * 执行规则 Agent 工作流并返回助手响应。
     */
    public AgentChatResponse execute(AgentChatRequest request,
                                     Long userId,
                                     String sessionId,
                                     AgentStreamEventEmitter emitter) throws IOException {
        return fallbackWorkflow.execute(request, userId, sessionId, emitter);
    }
}
