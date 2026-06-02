package cn.mw.loganalysis.agent.llm;

import dev.langchain4j.service.Result;

/**
 * 基于 LangChain4j 的日志助手接口。
 *
 * 这个接口本身没有手写实现类。
 * 真正的实现对象由 LangChain4jAssistantFactory 通过 AiServices.builder(...)
 * 动态生成并交给 executor 持有。
 */
public interface LangChain4jLogAnalysisAssistant {

    Result<String> chat(String userMessage);
}
