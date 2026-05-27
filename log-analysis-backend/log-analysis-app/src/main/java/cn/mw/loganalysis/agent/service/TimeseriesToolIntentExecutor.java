package cn.mw.loganalysis.agent.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TimeseriesToolIntentExecutor implements AgentFallbackToolExecutor {

    private final AgentToolFacade toolFacade;

    /**
     * 判断是否处理趋势/时序分析意图。
     */
    @Override
    public boolean supports(AgentIntent intent) {
        return AgentIntent.TIMESERIES.equals(intent);
    }

    /**
     * 调用时序工具生成趋势数据。
     */
    @Override
    public AgentToolPayload execute(AgentRuntimeContext context) {
        return toolFacade.queryTimeseries(context.getEffectiveMessage(), null);
    }

    /**
     * 构造时序工具调用的输入摘要。
     */
    @Override
    public Map<String, Object> buildToolInput(AgentRuntimeContext context) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("timeRange", context.getEffectiveMessage());
        return input;
    }
}
