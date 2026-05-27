package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.dto.AgentChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 智能助手的 Vector 组件计划工具门面。
 *
 * 预览和确认创建分别交给独立执行器，避免一个 Tool Handler 同时承担解析、缓存、
 * 建表、组件创建、画布配置和部署提示等多个职责。
 */
@Component
@RequiredArgsConstructor
public class VectorComponentPlanToolHandler {

    private final VectorComponentPreviewPlanner previewPlanner;
    private final VectorComponentCommitService commitService;

    /**
     * 生成 Vector 组件预览计划，供智能助手卡片展示。
     */
    public AgentToolPayload preview(AgentExecutionContext context,
                                    String logSample,
                                    String datasourceId,
                                    String tableName,
                                    String regexPattern,
                                    String sourceType,
                                    Map<String, Object> sourceConfig) {
        return previewPlanner.preview(context, logSample, datasourceId, tableName, regexPattern, sourceType, sourceConfig);
    }

    /**
     * 用户确认预览计划后执行真实创建。
     */
    public AgentChatResponse commit(String planId, Long userId, String sessionId) {
        return commitService.commit(planId, userId, sessionId);
    }
}
