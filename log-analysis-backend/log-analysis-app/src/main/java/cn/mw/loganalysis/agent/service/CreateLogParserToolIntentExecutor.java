package cn.mw.loganalysis.agent.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CreateLogParserToolIntentExecutor implements AgentFallbackToolExecutor {

    private final CreateLogParserTaskService createLogParserTaskService;

    /**
     * 判断是否处理创建日志解析意图。
     */
    @Override
    public boolean supports(AgentIntent intent) {
        return AgentIntent.CREATE_LOG_PARSER.equals(intent);
    }

    /**
     * 执行创建日志解析的多轮补槽或预览生成流程。
     */
    @Override
    public AgentToolPayload execute(AgentRuntimeContext context) {
        return createLogParserTaskService.handle(
                context.getExecutionContext(),
                context.getRequest(),
                context.getUserId(),
                context.getSessionId(),
                context.getDatasource()
        );
    }

    /**
     * 构造创建日志解析工具调用的输入摘要。
     */
    @Override
    public Map<String, Object> buildToolInput(AgentRuntimeContext context) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("message", context.getEffectiveMessage());
        return input;
    }
}
