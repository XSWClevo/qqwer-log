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
    private final CreateLogParserTaskService createLogParserTaskService;
    private final AgentToolFacade toolFacade;
    private final AgentResponseAssembler responseAssembler;

    public boolean shouldHandleWithoutLlm(AgentChatRequest request, Long userId) {
        if (request == null) {
            return false;
        }

        if (intentDetector.isCreateLogParserIntent(request.getMessage())
                || createLogParserTaskService.shouldContinueSlotFilling(request, userId)) {
            return true;
        }

        if (StringUtils.isBlank(request.getDatasourceId())) {
            return false;
        }

        ConfigComponent datasource = configComponentService.getQueryableDataSourceById(request.getDatasourceId());
        if (datasource == null) {
            return false;
        }
        FallbackIntentDetector.FallbackIntentDecision decision = intentDetector.detect(request, datasource.getVectorType());
        return decision.isDeterministicToolRequest();
    }

    public AgentChatResponse execute(AgentChatRequest request,
                                     Long userId,
                                     String sessionId,
                                     AgentStreamEventEmitter emitter) throws IOException {
        ConfigComponent datasource = StringUtils.isNotBlank(request.getDatasourceId())
                ? configComponentService.getQueryableDataSourceById(request.getDatasourceId())
                : null;
        FallbackIntentDetector.FallbackIntentDecision decision = intentDetector.detect(
                request,
                datasource != null ? datasource.getVectorType() : null
        );
        boolean createLogParserFlow = AgentIntent.CREATE_LOG_PARSER.equals(decision.getIntent())
                || createLogParserTaskService.shouldContinueSlotFilling(request, userId);
        if (StringUtils.isBlank(request.getDatasourceId()) && !createLogParserFlow) {
            return responseAssembler.error("请选择一个可查询的数据源后再提问");
        }
        if (datasource == null && !createLogParserFlow) {
            return responseAssembler.error("选中的数据源不存在或未标记为可查询 Sink");
        }

        AgentExecutionContextHolder.set(new AgentExecutionContext(
                datasource != null ? request.getDatasourceId() : null,
                datasource != null ? datasource.getName() : null,
                datasource != null ? datasource.getVectorType() : null,
                userId,
                sessionId
        ));
        try {
            AgentIntent resolvedIntent = createLogParserFlow ? AgentIntent.CREATE_LOG_PARSER : decision.getIntent();
            AgentToolPayload payload = switch (resolvedIntent) {
                case SCHEMA -> toolFacade.getSchema();
                case TIMESERIES -> toolFacade.queryTimeseries(decision.getEffectiveMessage(), null);
                case TEXT2SQL -> toolFacade.text2SqlQuery(decision.getEffectiveMessage());
                case CREATE_LOG_PARSER -> createLogParserTaskService.handle(
                        AgentExecutionContextHolder.get(),
                        request,
                        userId,
                        sessionId,
                        datasource
                );
                case VECTOR_COMPONENT_PLAN -> toolFacade.previewVectorComponents(decision.getEffectiveMessage(), null, null, null);
                case LOGS -> toolFacade.queryLogs(decision.getEffectiveMessage(), decision.getKeyword(), decision.getSeverity(), DEFAULT_PAGE_SIZE);
                default -> toolFacade.queryLogs(decision.getEffectiveMessage(), decision.getKeyword(), decision.getSeverity(), DEFAULT_PAGE_SIZE);
            };

            AgentChatResponse response = responseAssembler.fromToolPayload(
                    AgentExecutionContextHolder.require(),
                    payload,
                    buildToolInput(decision, resolvedIntent),
                    responseAssembler.defaultSuggestions(payload.getIntent())
            );
            emitCompletedToolCalls(response, emitter);
            return response;
        } catch (Exception ex) {
            log.error("智能助手处理失败, datasourceId={}, message={}", request.getDatasourceId(), request.getMessage(), ex);
            return responseAssembler.error("处理失败: " + describeFailure(ex));
        } finally {
            AgentExecutionContextHolder.clear();
        }
    }

    private Map<String, Object> buildToolInput(FallbackIntentDetector.FallbackIntentDecision decision, AgentIntent resolvedIntent) {
        Map<String, Object> input = new LinkedHashMap<>();
        return switch (resolvedIntent) {
            case SCHEMA -> input;
            case TIMESERIES -> {
                input.put("timeRange", decision.getEffectiveMessage());
                yield input;
            }
            case TEXT2SQL -> {
                input.put("query", decision.getEffectiveMessage());
                yield input;
            }
            case CREATE_LOG_PARSER -> {
                input.put("message", decision.getEffectiveMessage());
                yield input;
            }
            case VECTOR_COMPONENT_PLAN -> {
                input.put("logSample", decision.getEffectiveMessage());
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
            default -> {
                input.put("timeRange", decision.getEffectiveMessage());
                yield input;
            }
        };
    }

    private String describeFailure(Exception ex) {
        String message = StringUtils.trimToNull(ex.getMessage());
        if (message != null) {
            return message;
        }
        Throwable cause = ex.getCause();
        while (cause != null) {
            message = StringUtils.trimToNull(cause.getMessage());
            if (message != null) {
                return message;
            }
            cause = cause.getCause();
        }
        return ex.getClass().getSimpleName();
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
