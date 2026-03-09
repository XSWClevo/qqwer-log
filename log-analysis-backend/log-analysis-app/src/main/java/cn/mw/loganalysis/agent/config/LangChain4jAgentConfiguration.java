package cn.mw.loganalysis.agent.config;

import cn.mw.loganalysis.agent.service.LangChain4jLogAnalysisAgentExecutor;
import dev.langchain4j.http.client.spring.restclient.SpringRestClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesStreamingChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * LangChain4j 模型层配置。
 *
 * 这里现在只负责两件事：
 * 1. 在需要时创建项目自管的模型 Bean
 * 2. 启动后打印 LLM 链路自检信息
 *
 * AiService 代理不再作为独立 Spring Bean 注册，而是由 executor 在自身构造时直接创建。
 * 这样可以避开 “ChatModel 已存在，但 assistant Bean 因注册时序没有出现” 这类问题。
 */
@Configuration
@EnableConfigurationProperties(LangChain4jChatModelProperties.class)
public class LangChain4jAgentConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LangChain4jAgentConfiguration.class);


    @Bean({"responsesStreamingChatModel", "activeStreamingChatModel"})
    @ConditionalOnProperty(prefix = "agent.llm", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = "langchain4j.open-ai.chat-model", name = "api-key")
    @ConditionalOnProperty(prefix = "langchain4j.open-ai.chat-model", name = "wire-api", havingValue = "responses")
    public OpenAiResponsesStreamingChatModel responsesStreamingChatModel(LangChain4jChatModelProperties properties,
                                                                         ObjectProvider<RestClient.Builder> restClientBuilderProvider,
                                                                         ObjectProvider<ChatModelListener> chatModelListeners) {
        /**
         * 这条 Bean 专门服务只支持 /v1/responses 的 OpenAI 兼容网关。
         *
         * LangChain4j 1.12.1 里没有同步版的 Responses ChatModel，只有 StreamingChatModel。
         * 所以这里显式创建 OpenAiResponsesStreamingChatModel，后续由 executor
         * 通过 TokenStream 同步等待整次工具调用完成。
         */
        OpenAiResponsesStreamingChatModel.Builder builder = OpenAiResponsesStreamingChatModel.builder()
                .httpClientBuilder(SpringRestClient.builder()
                        .restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder)))
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModelName())
                .listeners(chatModelListeners.orderedStream().toList());

        if (StringUtils.hasText(properties.getOrganizationId())) {
            builder.organizationId(properties.getOrganizationId());
        }
        /**
         * 这里故意不把 temperature/topP/reasoning/store/strict 等可选字段继续传给 Responses 模型。
         *
         * 原因是这个分支主要服务第三方 OpenAI 兼容中转站，而这类网关最常见的问题不是路径错误，
         * 而是“/v1/responses 能通，但对部分可选字段或工具 schema 支持不完整”，然后只回一个
         * 含糊的 upstream_error。为了先把链路跑通，这里采用最小请求集策略：
         * - 只发送 baseUrl/apiKey/modelName 和工具本身
         * - 可选参数后续确认网关支持后再按需放开
         */
        if (properties.getLogRequests() != null) {
            builder.logRequests(properties.getLogRequests());
        }
        if (properties.getLogResponses() != null) {
            builder.logResponses(properties.getLogResponses());
        }

        log.info("创建 LangChain4j Responses StreamingChatModel Bean: model={}, baseUrl={}",
                properties.getModelName(), properties.getBaseUrl());
        return builder.build();
    }

    @Bean("activeStreamingChatModel")
    @ConditionalOnProperty(prefix = "agent.llm", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnProperty(prefix = "langchain4j.open-ai.chat-model", name = "api-key")
    @ConditionalOnProperty(prefix = "langchain4j.open-ai.chat-model", name = "wire-api", havingValue = "chat-completions", matchIfMissing = true)
    public OpenAiStreamingChatModel chatCompletionsStreamingChatModel(LangChain4jChatModelProperties properties,
                                                                      ObjectProvider<RestClient.Builder> restClientBuilderProvider,
                                                                      ObjectProvider<ChatModelListener> chatModelListeners) {
        /**
         * 流式聊天页不能只依赖同步 ChatModel。
         * 当前项目的同步 /api/agent/chat 仍可继续走 ChatModel，
         * 但新增的 /api/agent/chat/stream 需要一个真正的 StreamingChatModel 才能把 token 持续推给前端。
         */
        var builder = OpenAiStreamingChatModel.builder()
                .httpClientBuilder(SpringRestClient.builder()
                        .restClientBuilder(restClientBuilderProvider.getIfAvailable(RestClient::builder)))
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModelName())
                .listeners(chatModelListeners.orderedStream().toList());

        if (properties.getLogRequests() != null) {
            builder.logRequests(properties.getLogRequests());
        }
        if (properties.getLogResponses() != null) {
            builder.logResponses(properties.getLogResponses());
        }

        log.info("创建 LangChain4j Chat Completions StreamingChatModel Bean: model={}, baseUrl={}",
                properties.getModelName(), properties.getBaseUrl());
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "agent.llm", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ApplicationRunner llmStartupDiagnostics(Environment environment,
                                                   LangChain4jChatModelProperties properties,
                                                   ObjectProvider<ChatModel> chatModelProvider,
                                                   ObjectProvider<OpenAiResponsesStreamingChatModel> responsesStreamingChatModelProvider,
                                                   ObjectProvider<LangChain4jLogAnalysisAgentExecutor> executorProvider) {
        return args -> {
            /**
             * 这段自检日志只做一件事：把 LLM 链路里最关键的模型层和执行层状态打清楚。
             * 以后如果用户再看到 llmExecutorProvider 为 null，不需要再靠猜 Bean 注册顺序。
             */
            boolean apiKeyPresent = StringUtils.hasText(properties.getApiKey());
            ChatModel chatModel = chatModelProvider.getIfAvailable();
            OpenAiResponsesStreamingChatModel responsesStreamingChatModel = responsesStreamingChatModelProvider.getIfAvailable();
            boolean chatModelPresent = chatModel != null;
            boolean responsesModelPresent = responsesStreamingChatModel != null;
            boolean executorPresent = executorProvider.getIfAvailable() != null;
            boolean manualChatModelEnabled = environment.getProperty("agent.llm.manual-chat-model", Boolean.class, false);
            boolean activeModelPresent = properties.usesResponsesApi() ? responsesModelPresent : chatModelPresent;

            log.info("LLM 启动自检: activeProfiles={}, wireApi={}, apiKeyPresent={}, chatModelPresent={}, responsesModelPresent={}, executorPresent={}, manualChatModelEnabled={}, modelBeanType={}, responsesModelBeanType={}, model={}",
                    String.join(",", environment.getActiveProfiles()),
                    properties.getWireApi(),
                    apiKeyPresent,
                    chatModelPresent,
                    responsesModelPresent,
                    executorPresent,
                    manualChatModelEnabled,
                    chatModel != null ? chatModel.getClass().getName() : "none",
                    responsesStreamingChatModel != null ? responsesStreamingChatModel.getClass().getName() : "none",
                    properties.getModelName());

            if (!apiKeyPresent) {
                log.warn("LLM 启动自检未通过: 未读取到 langchain4j.open-ai.chat-model.api-key，ChatModel 不会创建。");
            } else if (!activeModelPresent) {
                log.warn("LLM 启动自检未通过: 已读取到 api-key，但当前 wireApi={} 对应的模型 Bean 仍未注册，请检查模型创建异常。", properties.getWireApi());
            } else if (!executorPresent) {
                log.warn("LLM 启动自检未通过: ChatModel 已存在，但 LangChain4j 执行器未注册，请检查 executor 条件或构造异常。");
            }
        };
    }
}
