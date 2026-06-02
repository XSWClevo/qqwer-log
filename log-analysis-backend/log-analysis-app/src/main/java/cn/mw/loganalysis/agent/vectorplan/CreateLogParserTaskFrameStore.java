package cn.mw.loganalysis.agent.vectorplan;

import cn.mw.loganalysis.agent.nlu.AgentIntent;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 会话级任务帧存储。
 *
 * 隔离缓存细节，避免补槽编排层直接依赖 Caffeine。
 */
@Component
public class CreateLogParserTaskFrameStore {

    private static final String DEFAULT_PARSE_METHOD = "parse_regex";

    private final Cache<String, AgentTaskFrame> frameCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterAccess(Duration.ofMinutes(45))
            .build();

    /**
     * 读取当前会话任务帧；不存在或已提交时创建新的任务帧。
     */
    AgentTaskFrame loadOrCreate(Long userId, String sessionId) {
        String key = cacheKey(userId, sessionId);
        AgentTaskFrame existing = frameCache.getIfPresent(key);
        if (existing != null
                && AgentIntent.CREATE_LOG_PARSER.equals(existing.getIntent())
                && !AgentTaskStatus.COMMITTED.equals(existing.getStatus())) {
            return existing;
        }

        AgentTaskFrame frame = new AgentTaskFrame();
        frame.setTaskId(UUID.randomUUID().toString());
        frame.setUserId(userId);
        frame.setSessionId(sessionId);
        frame.setIntent(AgentIntent.CREATE_LOG_PARSER);
        frame.setStatus(AgentTaskStatus.INTENT_DETECTED);
        frame.setParseMethod(DEFAULT_PARSE_METHOD);
        frame.setConfirmCommit(false);
        frame.setUpdatedAt(LocalDateTime.now());
        return frame;
    }

    /**
     * 判断当前会话是否存在尚未完成的创建日志解析任务。
     */
    boolean hasOpenTask(String sessionId, Long userId) {
        AgentTaskFrame frame = frameCache.getIfPresent(cacheKey(userId, sessionId));
        return frame != null
                && AgentIntent.CREATE_LOG_PARSER.equals(frame.getIntent())
                && (AgentTaskStatus.INTENT_DETECTED.equals(frame.getStatus())
                || AgentTaskStatus.SLOT_FILLING.equals(frame.getStatus()));
    }

    /**
     * 保存任务帧到会话级缓存。
     */
    void save(AgentTaskFrame frame) {
        if (frame == null) {
            return;
        }
        frameCache.put(cacheKey(frame.getUserId(), frame.getSessionId()), frame);
    }

    /**
     * 生成用户和会话维度的缓存 key。
     */
    private String cacheKey(Long userId, String sessionId) {
        return ObjectUtils.defaultIfNull(userId, 0L) + ":" + StringUtils.defaultString(sessionId);
    }
}
