package cn.mw.loganalysis.operationlog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 操作日志异步任务配置
 *
 * @author Claude
 * @since 2026-01-07
 */
@Configuration
@EnableAsync
public class OperationLogAsyncConfig {

    /**
     * 操作日志异步线程池
     * <p>
     * 核心线程数: 2
     * 最大线程数: 5
     * 队列容量: 1000
     * 拒绝策略: CallerRunsPolicy (由调用线程执行)
     * </p>
     */
    @Bean("operationLogExecutor")
    public Executor operationLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数
        executor.setCorePoolSize(2);

        // 最大线程数
        executor.setMaxPoolSize(5);

        // 队列容量
        executor.setQueueCapacity(1000);

        // 线程名称前缀
        executor.setThreadNamePrefix("operation-log-");

        // 线程空闲时间 (秒)
        executor.setKeepAliveSeconds(60);

        // 拒绝策略: 由调用线程执行任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 等待所有任务完成后关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // 等待时间 (秒)
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }
}
