package cn.mw.loganalysis.agent.execution;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agent 链路事件发布器。
 */
@Component
@RequiredArgsConstructor
public class AgentFlowEventPublisher {

    private final List<AgentFlowObserver> observers;

    /**
     * 发布无异常的 Agent 工作流事件。
     */
    void publish(AgentFlowEventType eventType, AgentRuntimeContext context) {
        publish(eventType, context, null);
    }

    /**
     * 发布 Agent 工作流事件，并通知所有观察者。
     */
    void publish(AgentFlowEventType eventType, AgentRuntimeContext context, Throwable error) {
        for (AgentFlowObserver observer : observers) {
            observer.onEvent(eventType, context, error);
        }
    }
}
