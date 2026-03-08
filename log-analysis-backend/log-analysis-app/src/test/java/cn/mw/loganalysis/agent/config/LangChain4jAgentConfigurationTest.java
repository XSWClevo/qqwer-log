package cn.mw.loganalysis.agent.config;

import cn.mw.loganalysis.agent.service.LangChain4jLogAnalysisAgentExecutor;
import cn.mw.loganalysis.agent.service.LogAnalysisAgentTools;
import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import cn.mw.loganalysis.stats.service.query.LogQueryStrategy;
import cn.mw.loganalysis.vector.mapper.ConfigComponentMapper;
import cn.mw.loganalysis.vector.service.ConfigComponentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesStreamingChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LangChain4jAgentConfigurationTest {

    /**
     * 这个测试专门保护 “LLM 配置存在时，Agent 相关 Bean 必须能注册出来” 这条链路。
     * 它不加载数据库，不扫描整站，只验证 LangChain4j 相关配置本身。
     */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    LangChain4jAgentConfiguration.class,
                    LangChain4jLogAnalysisAgentExecutor.class,
                    TestBeans.class
            );

    @Test
    void shouldRegisterChatModelAndExecutorWhenChatCompletionsConfigured() {
        contextRunner
                .withPropertyValues(
                        "agent.llm.enabled=true",
                        "agent.llm.manual-chat-model=true",
                        "langchain4j.open-ai.chat-model.api-key=test-key",
                        "langchain4j.open-ai.chat-model.base-url=https://api.openai.com/v1",
                        "langchain4j.open-ai.chat-model.model-name=gpt-5.2",
                        "langchain4j.open-ai.chat-model.wire-api=chat-completions",
                        "langchain4j.open-ai.chat-model.strict-tools=true",
                        "langchain4j.open-ai.chat-model.parallel-tool-calls=false"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(ChatModel.class);
                    assertThat(context).doesNotHaveBean(OpenAiResponsesStreamingChatModel.class);
                    assertThat(context).hasSingleBean(LangChain4jLogAnalysisAgentExecutor.class);
                });
    }

    @Test
    void shouldRegisterResponsesStreamingModelAndExecutorWhenResponsesConfigured() {
        contextRunner
                .withPropertyValues(
                        "agent.llm.enabled=true",
                        "langchain4j.open-ai.chat-model.api-key=test-key",
                        "langchain4j.open-ai.chat-model.base-url=https://relay.example.com/v1",
                        "langchain4j.open-ai.chat-model.model-name=gpt-5.4",
                        "langchain4j.open-ai.chat-model.wire-api=responses",
                        "langchain4j.open-ai.chat-model.strict-tools=false",
                        "langchain4j.open-ai.chat-model.parallel-tool-calls=false"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ChatModel.class);
                    assertThat(context).hasSingleBean(OpenAiResponsesStreamingChatModel.class);
                    assertThat(context).hasSingleBean(LangChain4jLogAnalysisAgentExecutor.class);
                });
    }

    @Test
    void shouldNotRegisterAnyLlmModelWithoutApiKey() {
        contextRunner
                .withPropertyValues(
                        "agent.llm.enabled=true",
                        "agent.llm.manual-chat-model=true",
                        "langchain4j.open-ai.chat-model.wire-api=chat-completions"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ChatModel.class);
                    assertThat(context).doesNotHaveBean(OpenAiResponsesStreamingChatModel.class);
                    assertThat(context).doesNotHaveBean(LangChain4jLogAnalysisAgentExecutor.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        DynamicLogQueryService dynamicLogQueryService() {
            return new DynamicLogQueryService(configComponentService(), List.<LogQueryStrategy>of());
        }

        @Bean
        ConfigComponentService configComponentService() {
            ConfigComponentMapper mapper = (ConfigComponentMapper) Proxy.newProxyInstance(
                    ConfigComponentMapper.class.getClassLoader(),
                    new Class[]{ConfigComponentMapper.class},
                    (proxy, method, args) -> defaultValue(method.getReturnType())
            );
            return new ConfigComponentService(mapper);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        LogAnalysisAgentTools logAnalysisAgentTools(DynamicLogQueryService dynamicLogQueryService) {
            return new LogAnalysisAgentTools(dynamicLogQueryService);
        }

        private static Object defaultValue(Class<?> returnType) {
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == byte.class || returnType == short.class || returnType == int.class
                    || returnType == long.class || returnType == float.class || returnType == double.class) {
                return 0;
            }
            return null;
        }
    }
}
