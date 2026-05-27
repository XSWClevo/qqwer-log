package cn.mw.loganalysis.agent.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class VectorComponentPlanToolIntentExecutor implements AgentFallbackToolExecutor {

    private final AgentToolFacade toolFacade;

    /**
     * 判断是否处理直接生成 Vector 组件预览的意图。
     */
    @Override
    public boolean supports(AgentIntent intent) {
        return AgentIntent.VECTOR_COMPONENT_PLAN.equals(intent);
    }

    /**
     * 调用 Vector 组件预览工具生成计划。
     */
    @Override
    public AgentToolPayload execute(AgentRuntimeContext context) {
        return toolFacade.previewVectorComponents(context.getEffectiveMessage(), null, null, null);
    }

    /**
     * 构造 Vector 组件预览工具调用的输入摘要。
     */
    @Override
    public Map<String, Object> buildToolInput(AgentRuntimeContext context) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("logSample", context.getEffectiveMessage());
        return input;
    }
}
