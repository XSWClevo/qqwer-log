package cn.mw.loganalysis.agent.support;

import cn.mw.loganalysis.agent.dto.AgentStreamEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * NDJSON 流写出器。
 *
 * 每次 emit 都写一行 JSON 并 flush，这样前端 fetch reader 可以尽快拿到增量事件。
 */
public class AgentStreamWriter implements AgentStreamEventEmitter {

    private final ObjectMapper objectMapper;
    private final OutputStream outputStream;

    public AgentStreamWriter(ObjectMapper objectMapper, OutputStream outputStream) {
        this.objectMapper = objectMapper;
        this.outputStream = outputStream;
    }

    @Override
    public synchronized void emit(AgentStreamEvent event) throws IOException {
        outputStream.write(objectMapper.writeValueAsString(event).getBytes(StandardCharsets.UTF_8));
        outputStream.write('\n');
        outputStream.flush();
    }
}
