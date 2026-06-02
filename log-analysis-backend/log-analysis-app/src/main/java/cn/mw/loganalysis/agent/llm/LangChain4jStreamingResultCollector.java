package cn.mw.loganalysis.agent.llm;

import cn.mw.loganalysis.agent.dto.AgentStreamEvent;
import cn.mw.loganalysis.agent.support.AgentStreamEventEmitter;
import cn.mw.loganalysis.agent.tool.AgentResponseAssembler;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 收集 LangChain4j 流式回调，转换为统一 Result 和前端流事件。
 */
@Component
@RequiredArgsConstructor
public class LangChain4jStreamingResultCollector {

    private final AgentResponseAssembler responseAssembler;

    Result<String> executeStreamingAssistant(LangChain4jStreamingLogAnalysisAssistant streamingAssistant,
                                             String prompt,
                                             AgentStreamEventEmitter emitter,
                                             Duration llmTimeout) {
        List<ToolExecution> toolExecutions = Collections.synchronizedList(new ArrayList<>());
        CompletableFuture<ChatResponse> responseFuture = new CompletableFuture<>();

        TokenStream tokenStream = streamingAssistant.chat(prompt)
                .onPartialResponse(delta -> safeEmit(emitter, AgentStreamEvent.token(delta)))
                .beforeToolExecution(beforeToolExecution ->
                        safeEmit(emitter, AgentStreamEvent.toolStarted(responseAssembler.toRunningToolCall(beforeToolExecution))))
                .onToolExecuted(toolExecution -> {
                    toolExecutions.add(toolExecution);
                    safeEmit(emitter, AgentStreamEvent.toolFinished(responseAssembler.toFinishedToolCall(toolExecution)));
                })
                .onCompleteResponse(responseFuture::complete)
                .onError(responseFuture::completeExceptionally);
        tokenStream.start();

        ChatResponse finalResponse;
        try {
            finalResponse = responseFuture.get(llmTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 Responses API 返回结果时被中断", ex);
        } catch (TimeoutException ex) {
            throw new IllegalStateException("等待 Responses API 返回超时，请检查网关响应时间或降低问题复杂度", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Responses API 调用失败", cause);
        }

        String content = finalResponse != null && finalResponse.aiMessage() != null
                ? finalResponse.aiMessage().text()
                : "";
        return Result.<String>builder()
                .content(content)
                .tokenUsage(finalResponse != null ? finalResponse.tokenUsage() : null)
                .finishReason(finalResponse != null ? finalResponse.finishReason() : null)
                .toolExecutions(List.copyOf(toolExecutions))
                .finalResponse(finalResponse)
                .build();
    }

    Result<String> executeBufferedAssistant(LangChain4jLogAnalysisAssistant assistant,
                                            String prompt,
                                            AgentStreamEventEmitter emitter) {
        Result<String> result = assistant.chat(prompt);
        replayBufferedResult(result, emitter);
        return result;
    }

    private void replayBufferedResult(Result<String> result, AgentStreamEventEmitter emitter) {
        if (emitter == null || result == null) {
            return;
        }

        List<ToolExecution> executions = result.toolExecutions() != null ? result.toolExecutions() : List.of();
        for (ToolExecution toolExecution : executions) {
            safeEmit(emitter, AgentStreamEvent.toolFinished(responseAssembler.toFinishedToolCall(toolExecution)));
        }

        for (String chunk : chunkText(result.content(), 48)) {
            safeEmit(emitter, AgentStreamEvent.token(chunk));
        }
    }

    private List<String> chunkText(String content, int chunkSize) {
        if (StringUtils.isBlank(content)) {
            return List.of();
        }

        int size = Math.max(1, chunkSize);
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < content.length(); start += size) {
            int end = Math.min(content.length(), start + size);
            chunks.add(content.substring(start, end));
        }
        return chunks;
    }

    private void safeEmit(AgentStreamEventEmitter emitter, AgentStreamEvent event) {
        if (emitter == null || event == null) {
            return;
        }
        try {
            emitter.emit(event);
        } catch (IOException ex) {
            throw new UncheckedIOException("写出流式智能助手事件失败", ex);
        }
    }
}
