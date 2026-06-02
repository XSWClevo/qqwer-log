package cn.mw.loganalysis.agent.execution;

import cn.mw.loganalysis.agent.nlu.AgentIntent;
import cn.mw.loganalysis.agent.tool.AgentToolPayload;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 意图执行器注册表。
 */
@Component
public class AgentFallbackToolExecutorRegistry {

    private final List<AgentFallbackToolExecutor> executors;

    /**
     * 收集所有意图执行器并按 Ordered 顺序排序。
     */
    public AgentFallbackToolExecutorRegistry(List<AgentFallbackToolExecutor> executors) {
        this.executors = new ArrayList<>(executors);
        AnnotationAwareOrderComparator.sort(this.executors);
    }

    /**
     * 根据上下文意图找到执行器并执行。
     */
    AgentToolPayload execute(AgentRuntimeContext context) {
        return resolve(context.getIntent()).execute(context);
    }

    /**
     * 根据上下文意图构造工具调用输入摘要。
     */
    Map<String, Object> buildToolInput(AgentRuntimeContext context) {
        return resolve(context.getIntent()).buildToolInput(context);
    }

    /**
     * 从执行器列表中找到支持该意图的策略。
     */
    private AgentFallbackToolExecutor resolve(AgentIntent intent) {
        return executors.stream()
                .filter(executor -> executor.supports(intent))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到意图执行器: " + intent));
    }
}
