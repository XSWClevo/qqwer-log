package cn.mw.loganalysis.alert.scheduler;

import cn.mw.loganalysis.alert.entity.AlertRule;
import cn.mw.loganalysis.alert.executor.AlertRuleExecutor;
import cn.mw.loganalysis.alert.mapper.AlertRuleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 告警规则调度器
 * 定期扫描启用的告警规则并触发执行
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertRuleScheduler {

    private final AlertRuleMapper alertRuleMapper;
    private final AlertRuleExecutor alertRuleExecutor;
    
    // 线程池用于并行执行规则
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    /**
     * 每分钟执行一次规则检查
     */
    @Scheduled(cron = "0 * * * * ?")
    public void executeRules() {
        log.info("Starting scheduled alert rule execution");
        
        try {
            // 查询所有启用的规则
            List<AlertRule> enabledRules = getEnabledRules();
            log.info("Found {} enabled alert rules", enabledRules.size());
            
            // 并行执行所有规则
            for (AlertRule rule : enabledRules) {
                executorService.submit(() -> {
                    try {
                        alertRuleExecutor.executeRule(rule);
                    } catch (Exception e) {
                        log.error("Failed to execute rule: {} (ID: {})", rule.getName(), rule.getId(), e);
                    }
                });
            }
            
        } catch (Exception e) {
            log.error("Error in scheduled rule execution", e);
        }
    }

    /**
     * 获取所有启用的规则
     */
    private List<AlertRule> getEnabledRules() {
        LambdaQueryWrapper<AlertRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertRule::getEnabled, true);
        return alertRuleMapper.selectList(wrapper);
    }
}
