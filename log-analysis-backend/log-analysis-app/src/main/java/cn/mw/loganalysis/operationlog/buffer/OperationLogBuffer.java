package cn.mw.loganalysis.operationlog.buffer;

import cn.mw.loganalysis.operationlog.entity.UserOperationLog;
import cn.mw.loganalysis.operationlog.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 操作日志缓冲区
 * <p>
 * 批量异步写入优化，提升高并发场景下的性能
 * </p>
 *
 * <h3>工作原理</h3>
 * <ul>
 *   <li>日志先写入内存队列 (非阻塞)</li>
 *   <li>定时任务每 5 秒批量刷入数据库</li>
 *   <li>每次最多写入 500 条</li>
 * </ul>
 *
 * <h3>性能提升</h3>
 * <ul>
 *   <li>减少数据库连接次数</li>
 *   <li>降低事务开销</li>
 *   <li>提升写入吞吐量</li>
 * </ul>
 *
 * @author Claude
 * @since 2026-01-07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationLogBuffer {

    private final OperationLogService operationLogService;

    /**
     * 日志缓冲队列 (最大容量 1000)
     */
    private final BlockingQueue<UserOperationLog> buffer = new LinkedBlockingQueue<>(1000);

    /**
     * 添加日志到缓冲区
     *
     * @param operationLog 操作日志
     * @return 是否添加成功
     */
    public boolean addLog(UserOperationLog operationLog) {
        return buffer.offer(operationLog);
    }

    /**
     * 定时刷新缓冲区 (每 5 秒执行一次)
     */
    @Scheduled(fixedDelay = 5000)
    public void flushLogs() {
        if (buffer.isEmpty()) {
            return;
        }

        List<UserOperationLog> logs = new ArrayList<>();
        buffer.drainTo(logs, 500);  // 每次最多写入 500 条

        if (!logs.isEmpty()) {
            try {
                operationLogService.batchSaveLog(logs);
                log.debug("Flushed {} operation logs to database", logs.size());
            } catch (Exception e) {
                log.error("Failed to flush operation logs", e);
                // 失败时重新放回队列 (可选)
                // buffer.addAll(logs);
            }
        }
    }

    /**
     * 应用关闭时刷新所有日志
     */
    public void flushAll() {
        log.info("Flushing all operation logs before shutdown...");
        List<UserOperationLog> logs = new ArrayList<>();
        buffer.drainTo(logs);

        if (!logs.isEmpty()) {
            try {
                operationLogService.batchSaveLog(logs);
                log.info("Successfully flushed {} operation logs", logs.size());
            } catch (Exception e) {
                log.error("Failed to flush operation logs on shutdown", e);
            }
        }
    }

    /**
     * 获取当前缓冲区大小
     */
    public int getBufferSize() {
        return buffer.size();
    }
}
