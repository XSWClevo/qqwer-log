package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.dto.AgentChatResponse;
import cn.mw.loganalysis.agent.dto.AgentResult;
import cn.mw.loganalysis.agent.dto.AgentToolCall;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一组装智能助手响应。
 *
 * 这样规则回退和 LLM 分支都复用同一套前端响应结构，
 * 执行器只关注“如何执行”，不再负责“如何拼前端 DTO”。
 */
@Component
@RequiredArgsConstructor
public class AgentResponseAssembler {

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    /**
     * 将确定性工具执行结果组装成统一的助手响应。
     */
    public AgentChatResponse fromToolPayload(AgentExecutionContext context,
                                             AgentToolPayload payload,
                                             Map<String, Object> input,
                                             List<String> suggestions) {
        AgentToolCall toolCall = AgentToolCall.builder()
                .toolName(payload.getToolName())
                .toolLabel(payload.getToolLabel())
                .status("completed")
                .input(input)
                .summary(payload.getSummary())
                .durationMs(payload.getDurationMs())
                .build();

        return AgentChatResponse.builder()
                .success(true)
                .intent(payload.getIntent())
                .answer(payload.getSummary())
                .datasourceId(context.datasourceId())
                .datasourceName(context.datasourceName())
                .toolCalls(List.of(toolCall))
                .result(payload.getResult())
                .suggestions(suggestions)
                .build();
    }

    /**
     * 将 LLM 调用结果和工具调用轨迹组装成统一的助手响应。
     */
    public AgentChatResponse fromLlmResult(String datasourceId, String datasourceName, Result<String> result) {
        List<AgentToolCall> toolCalls = new ArrayList<>();
        AgentToolPayload lastPayload = null;

        List<ToolExecution> executions = result.toolExecutions() != null ? result.toolExecutions() : List.of();
        for (ToolExecution toolExecution : executions) {
            AgentToolPayload payload = toolExecution.resultObject() instanceof AgentToolPayload toolPayload
                    ? toolPayload
                    : null;
            if (payload != null) {
                lastPayload = payload;
            }
            toolCalls.add(toFinishedToolCall(toolExecution));
        }

        AgentResult agentResult = lastPayload != null ? lastPayload.getResult() : null;
        String intent = lastPayload != null ? lastPayload.getIntent() : inferIntent(agentResult);
        if (StringUtils.isBlank(intent)) {
            intent = "logs";
        }

        return AgentChatResponse.builder()
                .success(true)
                .intent(intent)
                .answer(result.content())
                .datasourceId(datasourceId)
                .datasourceName(datasourceName)
                .toolCalls(toolCalls)
                .result(agentResult)
                .suggestions(defaultSuggestions(intent))
                .build();
    }

    /**
     * 构建助手统一错误响应。
     */
    public AgentChatResponse error(String error) {
        return AgentChatResponse.builder()
                .success(false)
                .error(error)
                .suggestions(List.of("先选择一个可查询数据源", "查看字段结构", "查询最近1小时日志"))
                .build();
    }

    /**
     * 将 LangChain4j 工具执行前事件转换成前端 running 状态。
     */
    public AgentToolCall toRunningToolCall(BeforeToolExecution beforeToolExecution) {
        return AgentToolCall.builder()
                .toolCallId(beforeToolExecution.request().id())
                .toolName(beforeToolExecution.request().name())
                .toolLabel(defaultToolLabel(beforeToolExecution.request().name()))
                .status("running")
                .input(parseArguments(beforeToolExecution.request().arguments()))
                .summary("工具执行中")
                .build();
    }

    /**
     * 将 LangChain4j 工具执行结果转换成前端 completed/failed 状态。
     */
    public AgentToolCall toFinishedToolCall(ToolExecution toolExecution) {
        AgentToolPayload payload = toolExecution.resultObject() instanceof AgentToolPayload toolPayload
                ? toolPayload
                : null;
        return AgentToolCall.builder()
                .toolCallId(toolExecution.request().id())
                .toolName(toolExecution.request().name())
                .toolLabel(payload != null ? payload.getToolLabel() : defaultToolLabel(toolExecution.request().name()))
                .status(toolExecution.hasFailed() ? "failed" : "completed")
                .input(parseArguments(toolExecution.request().arguments()))
                .summary(payload != null ? payload.getSummary() : AgentToolSupport.truncate(toolExecution.result(), 160))
                .durationMs(payload != null ? payload.getDurationMs() : null)
                .build();
    }

    /**
     * 根据意图提供下一步快捷建议。
     */
    public List<String> defaultSuggestions(String intent) {
        String resolvedIntent = StringUtils.defaultIfBlank(intent, "logs");
        return switch (resolvedIntent) {
            case "schema" -> List.of("最近1小时有哪些错误日志", "看最近24小时日志趋势", "搜索包含 \"timeout\" 的日志");
            case "timeseries" -> List.of("查看最近1小时日志", "查看字段结构", "再看最近7天的趋势");
            case "text2sql" -> List.of("最近1天的数据有多少条", "按 severity 统计最近24小时数量", "统计最近7天每天的日志量");
            case "vector_component_requirements" -> List.of("存当前数据源，表名 app_text_to_sql_logs，来源 file，路径 /var/log/app/*.log", "存当前数据源，表名 app_text_to_sql_logs，来源 socket，tcp 0.0.0.0:9000", "存当前数据源，表名 app_text_to_sql_logs，来源 kafka，bootstrap localhost:9092，topic app-logs");
            case "vector_component_plan" -> List.of("确认创建前帮我检查字段", "换一个表名重新生成", "再生成一个 Vector 组件计划");
            case "vector_component_commit" -> List.of("查看字段结构", "查询最近15分钟日志", "继续生成另一个 Vector 组件");
            default -> List.of("看最近24小时日志趋势", "查看这个数据源的字段结构", "再查最近15分钟的日志");
        };
    }

    /**
     * 将工具方法名转换成前端展示名称。
     */
    public String defaultToolLabel(String toolName) {
        return switch (toolName) {
            case "get_schema" -> "读取字段结构";
            case "query_logs" -> "查询日志列表";
            case "query_timeseries" -> "查询日志趋势";
            case "text2sql_query" -> "自然语言统计查询";
            case "collect_vector_component_requirements" -> "补齐 Vector 创建信息";
            case "preview_vector_components" -> "预览 Vector 组件计划";
            default -> toolName;
        };
    }

    /**
     * 从结构化结果中推断响应意图。
     */
    private String inferIntent(AgentResult result) {
        return result != null ? result.getType() : "logs";
    }

    /**
     * 将工具调用参数 JSON 解析成前端可展示的 Map。
     */
    private Map<String, Object> parseArguments(String arguments) {
        if (StringUtils.isBlank(arguments)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(arguments, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            return Map.of("raw", arguments);
        }
    }
}
