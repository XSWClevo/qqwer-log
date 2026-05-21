package cn.mw.loganalysis.vector.service;

import cn.mw.loganalysis.vector.entity.VectorLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vector 日志 SSE 连接管理器
 * <p>
 * 订阅 Redis channel，将日志实时分发给所有已注册的 SSE 连接。
 * 每个连接可以设置 machineId/fileName 过滤条件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorLogSseManager implements MessageListener {

    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final ObjectMapper objectMapper;

    private final Map<String, SseConnection> connections = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        redisMessageListenerContainer.addMessageListener(
                this, new ChannelTopic(VectorLogRedisPublisher.CHANNEL_VECTOR_LOGS)
        );
        log.info("VectorLogSseManager 已订阅 Redis channel: {}", VectorLogRedisPublisher.CHANNEL_VECTOR_LOGS);
    }

    @PreDestroy
    public void destroy() {
        redisMessageListenerContainer.removeMessageListener(this);
        connections.clear();
    }

    /**
     * 注册 SSE 连接
     */
    public void register(String emitterId, SseEmitter emitter, String machineId, String fileName) {
        connections.put(emitterId, new SseConnection(emitter, machineId, fileName));

        Runnable cleanupAction = () -> unregister(emitterId);
        emitter.onCompletion(cleanupAction);
        emitter.onTimeout(cleanupAction);
        emitter.onError(ex -> cleanupAction.run());
    }

    /**
     * 注销 SSE 连接
     */
    public void unregister(String emitterId) {
        connections.remove(emitterId);
    }

    /**
     * Redis 消息回调：将日志分发给匹配的 SSE 连接
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (connections.isEmpty()) {
            return;
        }

        String json = new String(message.getBody(), StandardCharsets.UTF_8);
        VectorLog logEntry;
        try {
            logEntry = objectMapper.readValue(json, VectorLog.class);
        } catch (Exception e) {
            log.warn("反序列化 Redis 消息失败", e);
            return;
        }

        for (Map.Entry<String, SseConnection> entry : connections.entrySet()) {
            String emitterId = entry.getKey();
            SseConnection conn = entry.getValue();

            if (!conn.matches(logEntry)) {
                continue;
            }

            try {
                conn.emitter().send(SseEmitter.event().name("log").data(json));
            } catch (IOException e) {
                log.debug("SSE 推送失败，移除连接: {}", emitterId);
                connections.remove(emitterId);
            }
        }
    }

    public int getActiveConnectionCount() {
        return connections.size();
    }

    /**
     * SSE 连接包装，携带过滤条件
     */
    private record SseConnection(SseEmitter emitter, String machineId, String fileName) {

        boolean matches(VectorLog logEntry) {
            if (StringUtils.isNotBlank(machineId) && !machineId.equals(logEntry.getMachineId())) {
                return false;
            }
            return !StringUtils.isNotBlank(fileName) || fileName.equals(logEntry.getFileName());
        }
    }
}
