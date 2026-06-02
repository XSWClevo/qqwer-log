package cn.mw.loganalysis.agent.support;

import cn.mw.loganalysis.agent.dto.AgentStreamEvent;

import java.io.IOException;

/**
 * 流式事件发射器。
 *
 * 之所以不用普通 Consumer，是因为 NDJSON 输出需要把 IOException 往上抛给
 * StreamingResponseBody 调用链处理，而不是在 lambda 里包成 RuntimeException。
 */
@FunctionalInterface
public interface AgentStreamEventEmitter {

    void emit(AgentStreamEvent event) throws IOException;
}
