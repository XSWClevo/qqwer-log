package cn.mw.loganalysis.agent.tool;

import cn.mw.loganalysis.agent.execution.AgentFallbackToolExecutor;
import cn.mw.loganalysis.agent.nlu.AgentIntent;
import cn.mw.loganalysis.agent.execution.AgentRuntimeContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class Text2SqlToolIntentExecutor implements AgentFallbackToolExecutor {

    private final AgentToolFacade toolFacade;

    /**
     * 判断是否处理 Text2SQL 聚合统计意图。
     */
    @Override
    public boolean supports(AgentIntent intent) {
        return AgentIntent.TEXT2SQL.equals(intent);
    }

    /**
     * 调用 Text2SQL 工具执行自然语言统计查询。
     */
    @Override
    public AgentToolPayload execute(AgentRuntimeContext context) {
        return toolFacade.text2SqlQuery(context.getEffectiveMessage());
    }

    /**
     * 构造 Text2SQL 工具调用的输入摘要。
     */
    @Override
    public Map<String, Object> buildToolInput(AgentRuntimeContext context) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", context.getEffectiveMessage());
        return input;
    }
}
