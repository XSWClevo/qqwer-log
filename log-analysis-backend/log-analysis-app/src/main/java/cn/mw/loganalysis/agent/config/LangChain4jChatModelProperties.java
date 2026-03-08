package cn.mw.loganalysis.agent.config;

import dev.langchain4j.model.chat.Capability;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 绑定当前项目实际会用到的 OpenAI-compatible ChatModel 配置。
 *
 * 这里的 "open-ai" 前缀和 OpenAiChatModel 类名指的是“协议兼容层”，
 * 不等于项目一定在调用 OpenAI 官方服务。
 *
 * 例如当前项目接阿里云百炼（DashScope）千问时，依然会复用同一套属性：
 * - baseUrl = https://dashscope.aliyuncs.com/compatible-mode/v1
 * - modelName = qwen-plus / qwen-max 等
 *
 * LangChain4j starter 理论上也会绑定同一组配置，但当前仓库里 starter 的自动装配
 * 没有稳定产出 ChatModel Bean。这里单独声明一份可读、可控的属性类，是为了：
 * 1. 继续复用现有的 application-local.yml 配置键，不要求用户改配置结构
 * 2. 允许项目显式创建 ChatModel Bean，而不是完全依赖 starter 的条件装配
 * 3. 后续排查时可以直接看到项目真正消费了哪些 LLM 配置
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "langchain4j.open-ai.chat-model")
public class LangChain4jChatModelProperties {

    /**
     * 当前项目支持两种 OpenAI-compatible 协议：
     * 1. chat-completions: 对接标准 /v1/chat/completions
     * 2. responses: 对接只支持 /v1/responses 的网关
     *
     * 阿里云百炼千问当前走的是 chat-completions。
     * 大部分官方兼容网关默认也仍然是 chat-completions。
     * 只有当中转站明确提示 “Unsupported legacy protocol” 时，才需要切到 responses。
     */
    private String wireApi = "chat-completions";

    private String apiKey;
    private String baseUrl = "https://api.openai.com/v1";
    private String modelName;
    private Double temperature;
    private Double topP;
    private List<String> stop;
    private Integer maxTokens;
    private Integer maxCompletionTokens;
    private Double presencePenalty;
    private Double frequencyPenalty;
    private Map<String, Integer> logitBias;
    private String responseFormat;
    private Set<Capability> supportedCapabilities;
    private Boolean strictJsonSchema;
    private Integer seed;
    private String user;
    private Boolean strictTools;
    private Boolean parallelToolCalls;
    private Boolean store;
    private Map<String, String> metadata;
    private String serviceTier;
    private String reasoningEffort;
    private Boolean returnThinking;
    private Duration timeout;
    private Integer maxRetries;
    private Boolean logRequests;
    private Boolean logResponses;
    private Map<String, String> customHeaders;
    private Map<String, String> customQueryParams;
    private Map<String, Object> customParameters;
    private String organizationId;
    private String projectId;

    public boolean usesResponsesApi() {
        return "responses".equalsIgnoreCase(wireApi);
    }

    public boolean usesChatCompletionsApi() {
        return !usesResponsesApi();
    }
}
