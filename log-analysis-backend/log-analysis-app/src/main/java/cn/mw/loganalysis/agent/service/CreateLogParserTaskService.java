package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.dto.AgentChatRequest;
import cn.mw.loganalysis.vector.entity.ConfigComponent;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * “创建日志解析/Vector 组件计划”的同步编排层。
 *
 * 参考 Agent Decision/Slot/Executor 的拆法：本类只负责串联任务帧、槽位补齐和预览执行，
 * 具体规则分别下沉到 FrameStore、SlotExtractor、SlotPolicy 和 Presenter。
 */
@Component
@RequiredArgsConstructor
public class CreateLogParserTaskService {

    private final AgentToolFacade toolFacade;
    private final CreateLogParserTaskFrameStore taskFrameStore;
    private final CreateLogParserSlotExtractor slotExtractor;
    private final CreateLogParserSlotPolicy slotPolicy;
    private final CreateLogParserRequirementPresenter requirementPresenter;

    /**
     * 判断用户当前消息是否应该继续已有的创建日志解析补槽任务。
     */
    public boolean shouldContinueSlotFilling(AgentChatRequest request, Long userId) {
        if (request == null || StringUtils.isBlank(request.getSessionId()) || StringUtils.isBlank(request.getMessage())) {
            return false;
        }
        if (!taskFrameStore.hasOpenTask(request.getSessionId(), userId)) {
            return false;
        }
        return slotExtractor.looksLikeSlotFilling(request.getMessage());
    }

    /**
     * 处理创建日志解析任务：先补槽，槽位齐全后生成 Vector 组件预览。
     */
    public AgentToolPayload handle(AgentExecutionContext context,
                                   AgentChatRequest request,
                                   Long userId,
                                   String sessionId,
                                   ConfigComponent currentSink) {
        long startedAt = System.currentTimeMillis();
        AgentTaskFrame frame = taskFrameStore.loadOrCreate(userId, sessionId);
        slotExtractor.mergeSlots(frame, request.getMessage(), currentSink);

        List<String> missingSlots = slotPolicy.resolveMissingSlots(frame);
        frame.setMissingSlots(missingSlots);
        frame.setUpdatedAt(LocalDateTime.now());

        if (CollectionUtils.isEmpty(missingSlots)) {
            return preview(context, frame, userId, sessionId);
        }

        frame.setStatus(AgentTaskStatus.SLOT_FILLING);
        frame.setNextAction("ask_user");
        taskFrameStore.save(frame);
        return requirementPresenter.build(context, frame, System.currentTimeMillis() - startedAt);
    }

    /**
     * 调用 Vector 组件预览工具，并把任务帧切换到等待确认状态。
     */
    private AgentToolPayload preview(AgentExecutionContext context,
                                     AgentTaskFrame frame,
                                     Long userId,
                                     String sessionId) {
        frame.setStatus(AgentTaskStatus.READY_TO_PREVIEW);
        frame.setNextAction("preview_vector_components");
        taskFrameStore.save(frame);

        AgentExecutionContext previewContext = resolvePreviewContext(context, frame, userId, sessionId);
        AgentExecutionContextHolder.set(previewContext);
        AgentToolPayload payload = toolFacade.previewVectorComponents(
                frame.getLogSample(),
                frame.getTargetDatasourceId(),
                frame.getTableName(),
                frame.getRegexPattern(),
                frame.getSourceType(),
                frame.getSourceConfig()
        );

        frame.setStatus(AgentTaskStatus.WAITING_CONFIRM);
        frame.setNextAction("wait_user_confirm");
        taskFrameStore.save(frame);
        return payload;
    }

    /**
     * 为预览工具补齐当前会话的执行上下文。
     */
    private AgentExecutionContext resolvePreviewContext(AgentExecutionContext context,
                                                        AgentTaskFrame frame,
                                                        Long userId,
                                                        String sessionId) {
        if (context != null && StringUtils.isNotBlank(context.datasourceId())) {
            return context;
        }
        return new AgentExecutionContext(
                frame.getTargetSinkId(),
                frame.getDatasourceName(),
                "clickhouse",
                userId,
                sessionId
        );
    }
}
