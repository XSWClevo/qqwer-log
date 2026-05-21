package cn.mw.loganalysis.vector.service;

import cn.mw.loganalysis.vector.entity.VectorLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Vector 日志 Redis 发布者
 * <p>
 * 全局单例：每秒轮询 ClickHouse 获取增量日志，publish 到 Redis channel。
 * 所有 SSE 连接通过订阅 Redis channel 获得实时日志推送，
 * 避免每个连接各自轮询数据库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorLogRedisPublisher {

    public static final String CHANNEL_VECTOR_LOGS = "vector:logs";

    private final VectorLogService vectorLogService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "vector-log-publisher")
    );

    private final AtomicReference<LocalDateTime> lastTimestamp = new AtomicReference<>();

    @PostConstruct
    public void start() {
        lastTimestamp.set(LocalDateTime.now().minusSeconds(5));
        scheduler.scheduleAtFixedRate(this::pollAndPublish, 2, 1, TimeUnit.SECONDS);
        log.info("VectorLogRedisPublisher 已启动，轮询间隔 1s");
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdownNow();
        log.info("VectorLogRedisPublisher 已停止");
    }

    private void pollAndPublish() {
        try {
            List<VectorLog> newLogs = vectorLogService.getLogsAfter(
                    lastTimestamp.get(), null, null
            );
            if (!newLogs.isEmpty()) {
                lastTimestamp.set(newLogs.getLast().getTimestamp());
                for (VectorLog logEntry : newLogs) {
                    String json = objectMapper.writeValueAsString(logEntry);
                    redisTemplate.convertAndSend(CHANNEL_VECTOR_LOGS, json);
                }
            }
        } catch (Exception e) {
            log.error("轮询 ClickHouse 并发布到 Redis 失败", e);
        }
    }
}
