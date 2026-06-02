package cn.mw.loganalysis.agent.llm;

import cn.mw.loganalysis.agent.execution.AgentExecutionContext;
import cn.mw.loganalysis.agent.execution.AgentExecutionContextHolder;
import cn.mw.loganalysis.agent.tool.AgentResponseAssembler;
import cn.mw.loganalysis.agent.support.AgentStreamEventEmitter;
import cn.mw.loganalysis.agent.dto.AgentChatRequest;
import cn.mw.loganalysis.agent.dto.AgentChatResponse;
import cn.mw.loganalysis.vector.entity.ConfigComponent;
import cn.mw.loganalysis.vector.service.ConfigComponentService;
import dev.langchain4j.service.Result;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * LangChain4j 驱动的日志助手执行器。
 *
 * 这个类只保留请求入口编排：校验数据源、设置工具执行上下文、
 * 按当前协议调用助手代理，并把结果交给响应组装器。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "agent.llm", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "langchain4j.open-ai.chat-model", name = "api-key")
public class LangChain4jLogAnalysisAgentExecutor {

    private final LangChain4jAssistantBundle assistants;
    private final ConfigComponentService configComponentService;
    private final AgentResponseAssembler responseAssembler;
    private final LangChain4jPromptBuilder promptBuilder;
    private final LangChain4jStreamingResultCollector streamingResultCollector;

    public LangChain4jLogAnalysisAgentExecutor(LangChain4jAssistantFactory assistantFactory,
                                               ConfigComponentService configComponentService,
                                               AgentResponseAssembler responseAssembler,
                                               LangChain4jPromptBuilder promptBuilder,
                                               LangChain4jStreamingResultCollector streamingResultCollector) {
        this.assistants = assistantFactory.create();
        this.configComponentService = configComponentService;
        this.responseAssembler = responseAssembler;
        this.promptBuilder = promptBuilder;
        this.streamingResultCollector = streamingResultCollector;
    }

    public AgentChatResponse chat(AgentChatRequest request, Long userId, String sessionId) {
        if (StringUtils.isBlank(request.getDatasourceId())) {
            return responseAssembler.error("请选择一个可查询的数据源后再提问");
        }

        ConfigComponent datasource = configComponentService.getQueryableDataSourceById(request.getDatasourceId());
        if (datasource == null) {
            return responseAssembler.error("选中的数据源不存在或未标记为可查询 Sink");
        }

        AgentExecutionContextHolder.set(new AgentExecutionContext(
                request.getDatasourceId(),
                datasource.getName(),
                datasource.getVectorType(),
                userId,
                sessionId
        ));
        try {
            String prompt = promptBuilder.buildUserPrompt(request, datasource);
            Result<String> result = assistants.responsesWireApi()
                    ? streamingResultCollector.executeStreamingAssistant(
                            assistants.streamingAssistant(), prompt, null, assistants.llmTimeout())
                    : assistants.assistant().chat(prompt);
            return responseAssembler.fromLlmResult(request.getDatasourceId(), datasource.getName(), result);
        } finally {
            AgentExecutionContextHolder.clear();
        }
    }

    /**
     * 流式接口与同步接口共享同一套业务执行主线。
     *
     * - responses：使用真正的 token 流
     * - chat-completions：先走同步 ChatModel，完成后再把结果按流事件回放
     *
     * 这样 /chat 和 /chat/stream 在 chat-completions 下会共享同一条选工具逻辑，
     * 避免因为模型客户端不同而命中不同工具。
     */
    public AgentChatResponse streamChat(AgentChatRequest request,
                                        Long userId,
                                        String sessionId,
                                        AgentStreamEventEmitter emitter) {
        if (StringUtils.isBlank(request.getDatasourceId())) {
            return responseAssembler.error("请选择一个可查询的数据源后再提问");
        }

        ConfigComponent datasource = configComponentService.getQueryableDataSourceById(request.getDatasourceId());
        if (datasource == null) {
            return responseAssembler.error("选中的数据源不存在或未标记为可查询 Sink");
        }

        AgentExecutionContextHolder.set(new AgentExecutionContext(
                request.getDatasourceId(),
                datasource.getName(),
                datasource.getVectorType(),
                userId,
                sessionId
        ));
        try {
            String prompt = promptBuilder.buildUserPrompt(request, datasource);
            Result<String> result = assistants.responsesWireApi()
                    ? streamingResultCollector.executeStreamingAssistant(
                            assistants.streamingAssistant(), prompt, emitter, assistants.llmTimeout())
                    : streamingResultCollector.executeBufferedAssistant(assistants.assistant(), prompt, emitter);
            return responseAssembler.fromLlmResult(request.getDatasourceId(), datasource.getName(), result);
        } finally {
            AgentExecutionContextHolder.clear();
        }
    }
}
