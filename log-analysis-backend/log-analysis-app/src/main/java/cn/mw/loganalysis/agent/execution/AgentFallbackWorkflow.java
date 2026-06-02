package cn.mw.loganalysis.agent.execution;

import cn.mw.loganalysis.agent.nlu.AgentIntent;
import cn.mw.loganalysis.agent.nlu.AgentIntentRecognitionService;
import cn.mw.loganalysis.agent.tool.AgentResponseAssembler;
import cn.mw.loganalysis.agent.support.AgentStreamEventEmitter;
import cn.mw.loganalysis.agent.tool.AgentToolPayload;
import cn.mw.loganalysis.agent.nlu.IntentDecision;
import cn.mw.loganalysis.agent.dto.AgentChatRequest;
import cn.mw.loganalysis.agent.dto.AgentChatResponse;
import cn.mw.loganalysis.agent.dto.AgentStreamEvent;
import cn.mw.loganalysis.agent.dto.AgentToolCall;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 本地 Agent 工作流。
 *
 * 执行阶段对齐参考工程：上下文增强 -> IntentDecision(模型 NLU + 规则兜底)
 * -> 意图执行策略 -> 响应组装 -> 观察者记录。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentFallbackWorkflow {

    private final AgentContextEnhancerChain contextEnhancerChain;
    private final AgentIntentRecognitionService intentRecognitionService;
    private final IntentDecision intentDecision;
    private final AgentFallbackToolExecutorRegistry toolExecutorRegistry;
    private final AgentResponseAssembler responseAssembler;
    private final AgentFlowEventPublisher eventPublisher;

    /**
     * 只用轻量规则判断请求是否进入本地 Agent 链路，不在预判阶段调用模型 NLU。
     */
    boolean shouldHandleWithoutLlm(AgentChatRequest request, Long userId) {
        if (request == null) {
            return false;
        }

        AgentRuntimeContext context = prepareContext(request, userId, request.getSessionId(), null, false);
        boolean createLogParserFlow = AgentIntent.CREATE_LOG_PARSER.equals(context.getIntent());
        if (createLogParserFlow) {
            return true;
        }
        if (StringUtils.isBlank(request.getDatasourceId()) || context.getDatasource() == null) {
            return false;
        }
        return context.isDeterministicToolRequest();
    }

    /**
     * 执行完整本地 Agent 链路：准备上下文、执行工具、组装响应和发布事件。
     */
    AgentChatResponse execute(AgentChatRequest request,
                              Long userId,
                              String sessionId,
                              AgentStreamEventEmitter emitter) throws IOException {
        AgentRuntimeContext context = prepareContext(request, userId, sessionId, emitter, true);
        boolean createLogParserFlow = AgentIntent.CREATE_LOG_PARSER.equals(context.getIntent());

        if (StringUtils.isBlank(request.getDatasourceId()) && !createLogParserFlow) {
            return responseAssembler.error("请选择一个可查询的数据源后再提问");
        }
        if (context.getDatasource() == null && !createLogParserFlow) {
            return responseAssembler.error("选中的数据源不存在或未标记为可查询 Sink");
        }

        AgentExecutionContextHolder.set(context.getExecutionContext());
        try {
            eventPublisher.publish(AgentFlowEventType.TOOL_EXECUTION_STARTED, context);
            AgentToolPayload payload = toolExecutorRegistry.execute(context);
            context.setToolPayload(payload);

            AgentChatResponse response = responseAssembler.fromToolPayload(
                    AgentExecutionContextHolder.require(),
                    payload,
                    toolExecutorRegistry.buildToolInput(context),
                    responseAssembler.defaultSuggestions(payload.getIntent())
            );
            context.setResponse(response);
            emitCompletedToolCalls(response, emitter);
            eventPublisher.publish(AgentFlowEventType.TOOL_EXECUTION_FINISHED, context);
            return response;
        } catch (Exception ex) {
            eventPublisher.publish(AgentFlowEventType.TOOL_EXECUTION_FAILED, context, ex);
            log.error("智能助手处理失败, datasourceId={}, message={}", request.getDatasourceId(), request.getMessage(), ex);
            return responseAssembler.error("处理失败: " + describeFailure(ex));
        } finally {
            AgentExecutionContextHolder.clear();
        }
    }

    /**
     * 构造并增强运行时上下文，同时按需要执行模型 NLU 意图理解。
     */
    private AgentRuntimeContext prepareContext(AgentChatRequest request,
                                               Long userId,
                                               String sessionId,
                                               AgentStreamEventEmitter emitter,
                                               boolean useModelNlu) {
        AgentRuntimeContext context = AgentRuntimeContext.builder()
                .request(request)
                .userId(userId)
                .sessionId(sessionId)
                .emitter(emitter)
                .build();
        contextEnhancerChain.enhance(context);
        eventPublisher.publish(AgentFlowEventType.CONTEXT_ENHANCED, context);
        if (useModelNlu) {
            intentDecision.execute(context);
        } else {
            intentRecognitionService.recognize(context);
        }
        eventPublisher.publish(AgentFlowEventType.INTENT_MATCHED, context);
        return context;
    }

    /**
     * 从异常链中提取最适合展示给用户的失败原因。
     */
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

    /**
     * 流式模式下把已完成的工具调用事件写回前端。
     */
    private void emitCompletedToolCalls(AgentChatResponse response, AgentStreamEventEmitter emitter) throws IOException {
        if (emitter == null || response == null || response.getToolCalls() == null) {
            return;
        }
        for (AgentToolCall toolCall : response.getToolCalls()) {
            emitter.emit(AgentStreamEvent.toolFinished(toolCall));
        }
    }
}
