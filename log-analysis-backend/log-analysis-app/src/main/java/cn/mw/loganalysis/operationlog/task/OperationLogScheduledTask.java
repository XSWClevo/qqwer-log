package cn.mw.loganalysis.operationlog.task;

import cn.mw.loganalysis.operationlog.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 操作日志定时任务
 *
 * @author Claude
 * @since 2026-01-07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationLogScheduledTask {

    private final OperationLogService operationLogService;

    /**
     * 定时归档旧数据
     * <p>
     * 执行时间: 每月 1 日凌晨 2 点
     * 归档策略: 将 6 个月前的数据迁移到归档表
     * </p>
     */
    @Scheduled(cron = "0 0 2 1 * ?")
    public void archiveOldLogs() {
        log.info("Starting to archive old operation logs...");

        try {
            int archivedCount = operationLogService.archiveOldLogs();
            log.info("Successfully archived {} operation logs", archivedCount);
        } catch (Exception e) {
            log.error("Failed to archive old operation logs", e);
        }
    }
}
