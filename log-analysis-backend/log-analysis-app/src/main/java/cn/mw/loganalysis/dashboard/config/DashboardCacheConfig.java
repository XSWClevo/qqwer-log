package cn.mw.loganalysis.dashboard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Dashboard 异步配置
 * 注意：缓存配置已移至 CacheConfig
 */
@Configuration
@EnableAsync
public class DashboardCacheConfig {

    /**
     * 配置异步任务执行器
     */
    @Bean(name = "dashboardTaskExecutor")
    public Executor dashboardTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("dashboard-async-");
        executor.setKeepAliveSeconds(60);
        executor.initialize();
        return executor;
    }
}
