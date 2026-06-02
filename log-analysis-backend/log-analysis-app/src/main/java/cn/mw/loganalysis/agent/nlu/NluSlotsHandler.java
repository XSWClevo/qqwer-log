package cn.mw.loganalysis.agent.nlu;

import cn.mw.loganalysis.agent.execution.AgentRuntimeContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 模型 NLU 意图槽位处理器。
 *
 * 对齐参考工程的 NluSlotsHandler：模型只输出建议的 intent 和 slots，
 * 后续由 IntentDecision、SlotPolicy 和 Executor 继续校验与执行。
 */
@Slf4j
@Component
public class NluSlotsHandler {

    private static final double DEFAULT_MIN_CONFIDENCE = 0.55D;

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ObjectProvider<StreamingChatModel> streamingChatModelProvider;
    private final ObjectMapper objectMapper;

    @Value("${agent.nlu.enabled:true}")
    private boolean enabled;

    @Value("${agent.nlu.min-confidence:0.55}")
    private double minConfidence = DEFAULT_MIN_CONFIDENCE;

    @Value("${agent.nlu.timeout-ms:8000}")
    private long timeoutMs = 8000L;

    /**
     * 注入同步模型和当前协议对应的流式模型。
     */
    public NluSlotsHandler(ObjectProvider<ChatModel> chatModelProvider,
                           @Qualifier("activeStreamingChatModel")
                           ObjectProvider<StreamingChatModel> streamingChatModelProvider,
                           ObjectMapper objectMapper) {
        this.chatModelProvider = chatModelProvider;
        this.streamingChatModelProvider = streamingChatModelProvider;
        this.objectMapper = objectMapper;
    }

    /**
     * 调用模型获取意图和槽位建议。
     */
    List<IntentSlotsEntity> callNluAgent(AgentRuntimeContext context) {
        if (!enabled) {
            return List.of();
        }

        try {
            String rawResponse = callModel(buildPrompt(context));
            List<IntentSlotsEntity> intentSlots = parseIntentSlots(rawResponse);
            return intentSlots.stream()
                    .filter(this::isTrusted)
                    .toList();
        } catch (Exception ex) {
            log.warn("模型 NLU 意图槽位识别失败，回退到规则识别, message={}, reason={}",
                    context != null ? context.getEffectiveMessage() : null,
                    ex.getMessage());
            return List.of();
        }
    }

    /**
     * 根据当前可用模型协议调用 NLU 模型。
     */
    private String callModel(String prompt) throws Exception {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel != null) {
            return chatModel.chat(prompt);
        }

        StreamingChatModel streamingChatModel = streamingChatModelProvider.getIfAvailable();
        if (streamingChatModel == null) {
            return "";
        }
        return callStreamingModel(streamingChatModel, prompt);
    }

    /**
     * 聚合流式模型响应为完整文本。
     */
    private String callStreamingModel(StreamingChatModel streamingChatModel, String prompt) throws Exception {
        StringBuilder buffer = new StringBuilder();
        CompletableFuture<String> responseFuture = new CompletableFuture<>();
        streamingChatModel.chat(prompt, new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                buffer.append(StringUtils.defaultString(partialResponse));
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                if (completeResponse != null && completeResponse.aiMessage() != null
                        && StringUtils.isNotBlank(completeResponse.aiMessage().text())) {
                    responseFuture.complete(completeResponse.aiMessage().text());
                    return;
                }
                responseFuture.complete(buffer.toString());
            }

            @Override
            public void onError(Throwable error) {
                responseFuture.completeExceptionally(error);
            }
        });
        return responseFuture.get(Math.max(1000L, timeoutMs), TimeUnit.MILLISECONDS);
    }

    /**
     * 构建只要求模型返回 JSON 的 NLU prompt。
     */
    private String buildPrompt(AgentRuntimeContext context) {
        String datasourceType = context.getDatasource() != null ? context.getDatasource().getVectorType() : "";
        String datasourceName = context.getDatasource() != null ? context.getDatasource().getName() : "";
        return """
                你是日志分析平台的 NLU 意图槽位识别器，只能返回 JSON，不要返回 Markdown 或解释文字。

                可选 intent：
                - CREATE_LOG_PARSER：用户想创建日志解析、生成正则、接入日志、创建 Vector Source/Remap/Sink、入库或建表。
                - SCHEMA：用户询问字段、表结构、schema、有哪些列。
                - TIMESERIES：用户询问趋势、时序、曲线、按时间粒度统计。
                - TEXT2SQL：用户在 ClickHouse 上做统计、聚合、排行、分组、多少条等自然语言查询。
                - LOGS：用户查询日志明细、搜索关键词、最近时间范围内的日志。

                你只建议 intent 和 slots，不执行任何动作。
                创建日志解析时尽量抽取：
                logSample、tableName、componentPrefix、sourceType(file/syslog/socket/kafka)、
                sourceConfig(include/read_from/syslog_mode/syslog_address/bootstrap_servers/topics/group_id)、
                regexPattern、parseMethod。
                普通日志查询尽量抽取 keyword、severity(error/warn/info/debug)、timeRange。
                趋势查询尽量抽取 timeRange、granularity。
                Text2SQL 查询把原始问题放到 query。

                返回格式：
                {
                  "nlu_result": [
                    {
                      "intent": "CREATE_LOG_PARSER",
                      "confidence": 0.0到1.0,
                      "reason": "简短理由",
                      "slots": {}
                    }
                  ]
                }

                当前数据源类型：%s
                当前数据源名称：%s
                用户消息：%s
                """.formatted(
                StringUtils.defaultString(datasourceType),
                StringUtils.defaultString(datasourceName),
                StringUtils.defaultString(context.getEffectiveMessage())
        );
    }

    /**
     * 解析模型返回 JSON 并转换为意图槽位实体列表。
     */
    private List<IntentSlotsEntity> parseIntentSlots(String rawResponse) throws JsonProcessingException {
        String json = extractJson(rawResponse);
        if (StringUtils.isBlank(json)) {
            return List.of();
        }

        JsonNode root = objectMapper.readTree(json);
        JsonNode resultNode = root.path("nlu_result");
        if (!resultNode.isArray()) {
            return List.of();
        }

        List<IntentSlotsEntity> intentSlots = new ArrayList<>();
        for (JsonNode item : resultNode) {
            IntentSlotsEntity entity = parseIntentSlot(item);
            if (entity != null) {
                intentSlots.add(entity);
            }
        }
        return intentSlots;
    }

    /**
     * 解析单个模型意图槽位节点。
     */
    private IntentSlotsEntity parseIntentSlot(JsonNode item) throws JsonProcessingException {
        AgentIntent intent = parseIntent(item.path("intent").asText(""));
        if (intent == null) {
            return null;
        }

        AgentNluSlots slots = objectMapper.treeToValue(item.path("slots"), AgentNluSlots.class);
        IntentSlotsEntity entity = new IntentSlotsEntity();
        entity.setIntent(intent);
        entity.setSlots(slots);
        entity.setOriginalSlotResult(slots);
        entity.setConfidence(item.path("confidence").isNumber() ? item.path("confidence").asDouble() : null);
        entity.setReason(StringUtils.trimToNull(item.path("reason").asText(null)));
        return entity;
    }

    /**
     * 将模型返回的 intent 字符串转换为内部枚举。
     */
    private AgentIntent parseIntent(String value) {
        String normalized = StringUtils.upperCase(StringUtils.trimToEmpty(value), Locale.ROOT);
        if (StringUtils.isBlank(normalized)) {
            return null;
        }
        try {
            return AgentIntent.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * 校验模型建议是否达到可采信置信度。
     */
    private boolean isTrusted(IntentSlotsEntity entity) {
        if (entity == null || entity.getIntent() == null) {
            return false;
        }
        return entity.getConfidence() == null || entity.getConfidence() >= Math.max(0D, minConfidence);
    }

    /**
     * 从模型回复中截取 JSON 对象。
     */
    private String extractJson(String rawResponse) {
        String value = StringUtils.trimToEmpty(rawResponse);
        if (StringUtils.isBlank(value)) {
            return "";
        }
        if (value.startsWith("{") && value.endsWith("}")) {
            return value;
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return value.substring(start, end + 1);
        }
        return "";
    }
}
