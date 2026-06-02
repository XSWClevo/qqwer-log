package cn.mw.loganalysis.agent.nlu;

import cn.mw.loganalysis.agent.execution.AgentRuntimeContext;
import cn.mw.loganalysis.agent.execution.Decision;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 意图理解 Decision。
 *
 * 对齐参考工程：先调用模型 NLU 获取 intent + slots 建议，
 * 再交给规则识别器做兜底和安全覆盖。
 */
@Slf4j
@Component
public class IntentDecision extends Decision {

    private static final String CODE = "intent";
    private static final String DESC = "意图理解";

    private final NluSlotsHandler nluSlotsHandler;
    private final AgentIntentRecognitionService fallbackIntentRecognitionService;

    /**
     * 注入模型 NLU 处理器和规则兜底识别器。
     */
    public IntentDecision(NluSlotsHandler nluSlotsHandler,
                          AgentIntentRecognitionService fallbackIntentRecognitionService) {
        super(CODE, DESC);
        this.nluSlotsHandler = nluSlotsHandler;
        this.fallbackIntentRecognitionService = fallbackIntentRecognitionService;
    }

    /**
     * 执行意图理解，并把最终可执行意图写入上下文。
     */
    @Override
    public Object execute(AgentRuntimeContext context) {
        List<IntentSlotsEntity> intentSlots = nluSlotsHandler.callNluAgent(context);
        context.setIntentSlots(intentSlots);

        if (CollectionUtils.isNotEmpty(intentSlots)) {
            List<IntentNode> intentNodes = IntentNode.transFromIntentSlotsEntity(intentSlots);
            if (CollectionUtils.isNotEmpty(intentNodes)) {
                IntentNode intentNode = intentNodes.get(0);
                context.setIntentNode(intentNode);
                applyModelIntent(context, intentNode);
            }
        }

        AgentIntentDecision fallbackDecision = fallbackIntentRecognitionService.decide(context);
        if (shouldUseFallback(context, fallbackDecision)) {
            applyFallbackDecision(context, fallbackDecision);
        }
        return context.getIntentNode();
    }

    /**
     * 将模型建议的意图和基础槽位写入上下文。
     */
    private void applyModelIntent(AgentRuntimeContext context, IntentNode intentNode) {
        context.setIntent(intentNode.getIntent());
        context.setDeterministicToolRequest(true);
        AgentNluSlots slots = context.getNluSlots();
        if (slots == null) {
            return;
        }
        if (AgentIntent.TEXT2SQL.equals(intentNode.getIntent()) && StringUtils.isNotBlank(slots.getQuery())) {
            context.setEffectiveMessage(slots.getQuery());
        } else if (usesTimeRangeAsEffectiveMessage(intentNode.getIntent()) && StringUtils.isNotBlank(slots.getTimeRange())) {
            context.setEffectiveMessage(slots.getTimeRange());
        }
        if (StringUtils.isNotBlank(slots.getKeyword())) {
            context.setKeyword(slots.getKeyword());
        }
        if (StringUtils.isNotBlank(slots.getSeverity())) {
            context.setSeverity(slots.getSeverity());
        }
    }

    /**
     * 判断是否需要用规则兜底覆盖模型建议。
     */
    private boolean shouldUseFallback(AgentRuntimeContext context, AgentIntentDecision fallbackDecision) {
        if (context.getIntent() == null) {
            return true;
        }
        if (AgentIntent.TEXT2SQL.equals(context.getIntent())
                && (context.getDatasource() == null
                || !StringUtils.equalsIgnoreCase(context.getDatasource().getVectorType(), "clickhouse"))) {
            return true;
        }
        if (AgentIntent.CREATE_LOG_PARSER.equals(fallbackDecision.intent())) {
            return true;
        }
        return AgentIntent.LOGS.equals(context.getIntent()) && !AgentIntent.LOGS.equals(fallbackDecision.intent());
    }

    /**
     * 将规则兜底识别结果写入上下文。
     */
    private void applyFallbackDecision(AgentRuntimeContext context, AgentIntentDecision decision) {
        context.setIntent(decision.intent());
        context.setEffectiveMessage(decision.effectiveMessage());
        context.setKeyword(decision.keyword());
        context.setSeverity(decision.severity());
        context.setDeterministicToolRequest(decision.deterministicToolRequest());
        if (context.getIntentNode() == null) {
            IntentNode node = new IntentNode();
            node.setIntent(decision.intent());
            context.setIntentNode(node);
        } else {
            context.getIntentNode().setIntent(decision.intent());
        }
    }

    /**
     * 判断该意图是否可以把 timeRange 作为工具主输入。
     */
    private boolean usesTimeRangeAsEffectiveMessage(AgentIntent intent) {
        return AgentIntent.LOGS.equals(intent) || AgentIntent.TIMESERIES.equals(intent);
    }
}
