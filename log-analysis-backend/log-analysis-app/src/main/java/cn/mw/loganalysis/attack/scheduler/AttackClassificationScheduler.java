package cn.mw.loganalysis.attack.scheduler;

import cn.mw.loganalysis.attack.dto.AttackClassificationRunRequest;
import cn.mw.loganalysis.attack.dto.AttackClassificationRunResult;
import cn.mw.loganalysis.attack.service.AttackClassificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class AttackClassificationScheduler {

    private final AttackClassificationService attackClassificationService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${attack.classification.scheduler.enabled:true}")
    private boolean enabled;

    @Scheduled(
            initialDelayString = "${attack.classification.scheduler.initial-delay-ms:30000}",
            fixedDelayString = "${attack.classification.scheduler.fixed-delay-ms:60000}"
    )
    public void runEnabledDatasets() {
        if (!enabled || !running.compareAndSet(false, true)) {
            return;
        }

        try {
            AttackClassificationRunResult result = attackClassificationService.run(new AttackClassificationRunRequest());
            log.info("攻击分类调度完成: datasets={}, scanned={}, matched={}, inserted={}, skipped={}",
                    result.getDatasetCount(),
                    result.getScannedCount(),
                    result.getMatchedCount(),
                    result.getInsertedCount(),
                    result.getSkippedDatasets());
        } catch (Exception ex) {
            log.error("攻击分类调度失败: {}", ex.getMessage(), ex);
        } finally {
            running.set(false);
        }
    }
}
