package cn.mw.loganalysis.agent.llm;

import cn.mw.loganalysis.agent.config.LangChain4jChatModelProperties;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 创建 LangChain4j AiServices 代理，集中处理模型协议分支和工具注册。
 */
@Component
public class LangChain4jAssistantFactory {

    private final ObjectProvider<ChatModel> chatModelProvider;
    private final ObjectProvider<StreamingChatModel> activeStreamingChatModelProvider;
    private final LogAnalysisAgentTools logAnalysisAgentTools;
    private final LangChain4jChatModelProperties chatModelProperties;
    private final LangChain4jPromptBuilder promptBuilder;

    public LangChain4jAssistantFactory(ObjectProvider<ChatModel> chatModelProvider,
                                       @Qualifier("activeStreamingChatModel")
                                       ObjectProvider<StreamingChatModel> activeStreamingChatModelProvider,
                                       LogAnalysisAgentTools logAnalysisAgentTools,
                                       LangChain4jChatModelProperties chatModelProperties,
                                       LangChain4jPromptBuilder promptBuilder) {
        this.chatModelProvider = chatModelProvider;
        this.activeStreamingChatModelProvider = activeStreamingChatModelProvider;
        this.logAnalysisAgentTools = logAnalysisAgentTools;
        this.chatModelProperties = chatModelProperties;
        this.promptBuilder = promptBuilder;
    }

    LangChain4jAssistantBundle create() {
        boolean responsesWireApi = chatModelProperties.usesResponsesApi();
        Duration llmTimeout = chatModelProperties.getTimeout() != null
                ? chatModelProperties.getTimeout()
                : Duration.ofSeconds(60);

        StreamingChatModel activeStreamingChatModel = activeStreamingChatModelProvider.getIfAvailable();
        if (activeStreamingChatModel == null) {
            throw new IllegalStateException("当前协议对应的 StreamingChatModel Bean 未注册，无法启用流式智能助手");
        }
        LangChain4jStreamingLogAnalysisAssistant streamingAssistant =
                AiServices.builder(LangChain4jStreamingLogAnalysisAssistant.class)
                        .streamingChatModel(activeStreamingChatModel)
                        .systemMessage(promptBuilder.systemPrompt())
                        .tools(logAnalysisAgentTools)
                        .maxSequentialToolsInvocations(4)
                        .build();

        if (responsesWireApi) {
            return new LangChain4jAssistantBundle(null, streamingAssistant, true, llmTimeout);
        }

        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new IllegalStateException("当前配置使用 Chat Completions，但 ChatModel Bean 未注册");
        }
        LangChain4jLogAnalysisAssistant assistant =
                AiServices.builder(LangChain4jLogAnalysisAssistant.class)
                        .chatModel(chatModel)
                        .systemMessage(promptBuilder.systemPrompt())
                        .tools(logAnalysisAgentTools)
                        .maxSequentialToolsInvocations(4)
                        .build();

        return new LangChain4jAssistantBundle(assistant, streamingAssistant, false, llmTimeout);
    }
}
