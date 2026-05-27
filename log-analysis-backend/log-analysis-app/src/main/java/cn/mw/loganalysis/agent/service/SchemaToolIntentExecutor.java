package cn.mw.loganalysis.agent.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SchemaToolIntentExecutor implements AgentFallbackToolExecutor {

    private final AgentToolFacade toolFacade;

    /**
     * 判断是否处理字段结构查询意图。
     */
    @Override
    public boolean supports(AgentIntent intent) {
        return AgentIntent.SCHEMA.equals(intent);
    }

    /**
     * 调用 schema 工具获取当前数据源字段结构。
     */
    @Override
    public AgentToolPayload execute(AgentRuntimeContext context) {
        return toolFacade.getSchema();
    }

    /**
     * Schema 工具无需额外输入参数，返回空输入摘要。
     */
    @Override
    public Map<String, Object> buildToolInput(AgentRuntimeContext context) {
        return new LinkedHashMap<>();
    }
}
