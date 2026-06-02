package cn.mw.loganalysis.agent.llm;

import dev.langchain4j.service.TokenStream;

/**
 * 流式 LangChain4j 助手接口。
 *
 * 这里不再只服务 Responses API。
 * 当前项目会把“当前协议对应的 StreamingChatModel”统一注入进来：
 * - chat-completions 走 OpenAiStreamingChatModel
 * - responses 走 OpenAiResponsesStreamingChatModel
 *
 * 接口本身始终返回 TokenStream，后续由 executor 决定：
 * 1. 是把 token/tool 事件继续推给前端
 * 2. 还是在服务端聚合成传统同步结果
 */
public interface LangChain4jStreamingLogAnalysisAssistant {

    TokenStream chat(String userMessage);
}
