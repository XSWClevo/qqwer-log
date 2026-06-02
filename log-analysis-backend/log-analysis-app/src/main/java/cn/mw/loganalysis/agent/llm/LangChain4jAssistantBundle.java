package cn.mw.loganalysis.agent.llm;

import java.time.Duration;

/**
 * LangChain4j 助手代理集合，封装当前协议分支需要的模型代理和超时配置。
 */
record LangChain4jAssistantBundle(LangChain4jLogAnalysisAssistant assistant,
                                  LangChain4jStreamingLogAnalysisAssistant streamingAssistant,
                                  boolean responsesWireApi,
                                  Duration llmTimeout) {
}
