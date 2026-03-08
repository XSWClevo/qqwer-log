package cn.mw.loganalysis.agent.service;

import dev.langchain4j.service.TokenStream;

/**
 * Responses API 专用的 LangChain4j 助手接口。
 *
 * 当前项目使用的 LangChain4j 版本里，OpenAI Responses 只暴露了 StreamingChatModel，
 * 没有现成的同步 ChatModel。因此这里直接让 AiService 返回 TokenStream，
 * 再由 executor 在服务端同步等待完整结果，把它转换回前端已有的响应结构。
 */
public interface LangChain4jStreamingLogAnalysisAssistant {

    TokenStream chat(String userMessage);
}
