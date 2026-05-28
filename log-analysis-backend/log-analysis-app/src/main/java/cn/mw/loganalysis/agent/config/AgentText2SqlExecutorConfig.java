package cn.mw.loganalysis.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 智能助手 Text2SQL 候选竞争线程池配置。
 */
@Configuration
public class AgentText2SqlExecutorConfig {

    private final int corePoolSize;
    private final int maxPoolSize;
    private final int queueCapacity;
    private final int keepAliveSeconds;
    private final int awaitTerminationSeconds;

    public AgentText2SqlExecutorConfig(
            @Value("${agent.text2sql.executor.core-pool-size:2}") int corePoolSize,
            @Value("${agent.text2sql.executor.max-pool-size:4}") int maxPoolSize,
            @Value("${agent.text2sql.executor.queue-capacity:20}") int queueCapacity,
            @Value("${agent.text2sql.executor.keep-alive-seconds:60}") int keepAliveSeconds,
            @Value("${agent.text2sql.executor.await-termination-seconds:30}") int awaitTerminationSeconds) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.queueCapacity = queueCapacity;
        this.keepAliveSeconds = keepAliveSeconds;
        this.awaitTerminationSeconds = awaitTerminationSeconds;
    }

    /**
     * 创建 Text2SQL 候选竞争专用线程池，避免复用公共线程池导致慢 LLM 拖住其它任务。
     */
    @Bean("agentText2SqlExecutor")
    public Executor agentText2SqlExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("agent-text2sql-");
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);
        executor.initialize();
        return executor;
    }
}
