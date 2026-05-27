package cn.mw.loganalysis.agent.service;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LogsToolIntentExecutor implements AgentFallbackToolExecutor {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final AgentToolFacade toolFacade;

    /**
     * 判断是否处理普通日志查询意图。
     */
    @Override
    public boolean supports(AgentIntent intent) {
        return AgentIntent.LOGS.equals(intent);
    }

    /**
     * 调用日志查询工具返回日志列表。
     */
    @Override
    public AgentToolPayload execute(AgentRuntimeContext context) {
        return toolFacade.queryLogs(context.getEffectiveMessage(), context.getKeyword(), context.getSeverity(), DEFAULT_PAGE_SIZE);
    }

    /**
     * 构造日志查询工具调用的输入摘要。
     */
    @Override
    public Map<String, Object> buildToolInput(AgentRuntimeContext context) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("timeRange", context.getEffectiveMessage());
        if (StringUtils.isNotBlank(context.getKeyword())) {
            input.put("keyword", context.getKeyword());
        }
        if (StringUtils.isNotBlank(context.getSeverity())) {
            input.put("severity", context.getSeverity());
        }
        input.put("limit", DEFAULT_PAGE_SIZE);
        return input;
    }
}
