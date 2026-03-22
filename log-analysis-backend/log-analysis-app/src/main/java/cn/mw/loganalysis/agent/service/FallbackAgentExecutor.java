package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.dto.AgentChatRequest;
import cn.mw.loganalysis.agent.dto.AgentChatResponse;
import cn.mw.loganalysis.agent.dto.AgentToolCall;
import cn.mw.loganalysis.vector.entity.ConfigComponent;
import cn.mw.loganalysis.vector.service.ConfigComponentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 规则回退执行器。
 *
 * 它只负责：
 * 1. 根据当前消息和历史做轻量意图识别
 * 2. 路由到统一的 AgentToolFacade
 * 3. 把工具结果交给 AgentResponseAssembler 组装
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FallbackAgentExecutor {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ConfigComponentService configComponentService;
    private final FallbackIntentDetector intentDetector;
    private final AgentToolFacade toolFacade;
    private final AgentResponseAssembler responseAssembler;

    public AgentChatResponse execute(AgentChatRequest request, AgentStreamEventEmitter emitter) throws IOException {
        if (StringUtils.isBlank(request.getDatasourceId())) {
            return responseAssembler.error("请选择一个可查询的数据源后再提问");
        }

        ConfigComponent datasource = configComponentService.getById(request.getDatasourceId());
        if (datasource == null) {
            return responseAssembler.error("选中的数据源不存在");
        }

        AgentExecutionContextHolder.set(new AgentExecutionContext(
                request.getDatasourceId(),
                datasource.getName(),
                datasource.getVectorType()
        ));
        try {
            FallbackIntentDetector.FallbackIntentDecision decision = intentDetector.detect(request, datasource.getVectorType());
            AgentToolPayload payload = switch (decision.getIntent()) {
                case SCHEMA -> toolFacade.getSchema();
                case TIMESERIES -> toolFacade.queryTimeseries(decision.getEffectiveMessage(), null);
                case TEXT2SQL -> toolFacade.text2SqlQuery(decision.getEffectiveMessage());
                case LOGS -> toolFacade.queryLogs(decision.getEffectiveMessage(), decision.getKeyword(), decision.getSeverity(), DEFAULT_PAGE_SIZE);
            };

            AgentChatResponse response = responseAssembler.fromToolPayload(
                    AgentExecutionContextHolder.require(),
                    payload,
                    buildToolInput(decision),
                    responseAssembler.defaultSuggestions(payload.getIntent())
            );
            emitCompletedToolCalls(response, emitter);
            return response;
        } catch (Exception ex) {
            log.error("智能助手处理失败, datasourceId={}, message={}", request.getDatasourceId(), request.getMessage(), ex);
            return responseAssembler.error("处理失败: " + ex.getMessage());
        } finally {
            AgentExecutionContextHolder.clear();
        }
    }

    private Map<String, Object> buildToolInput(FallbackIntentDetector.FallbackIntentDecision decision) {
        Map<String, Object> input = new LinkedHashMap<>();
        return switch (decision.getIntent()) {
            case SCHEMA -> input;
            case TIMESERIES -> {
                input.put("timeRange", decision.getEffectiveMessage());
                yield input;
            }
            case TEXT2SQL -> {
                input.put("query", decision.getEffectiveMessage());
                yield input;
            }
            case LOGS -> {
                input.put("timeRange", decision.getEffectiveMessage());
                if (StringUtils.isNotBlank(decision.getKeyword())) {
                    input.put("keyword", decision.getKeyword());
                }
                if (StringUtils.isNotBlank(decision.getSeverity())) {
                    input.put("severity", decision.getSeverity());
                }
                input.put("limit", DEFAULT_PAGE_SIZE);
                yield input;
            }
        };
    }

    private void emitCompletedToolCalls(AgentChatResponse response, AgentStreamEventEmitter emitter) throws IOException {
        if (emitter == null || response == null || response.getToolCalls() == null) {
            return;
        }
        for (AgentToolCall toolCall : response.getToolCalls()) {
            emitter.emit(cn.mw.loganalysis.agent.dto.AgentStreamEvent.toolFinished(toolCall));
        }
    }
}
