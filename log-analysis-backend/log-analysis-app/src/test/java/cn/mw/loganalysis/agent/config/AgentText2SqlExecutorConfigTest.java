package cn.mw.loganalysis.agent.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class AgentText2SqlExecutorConfigTest {

    @Test
    void shouldCreateConfigurableText2SqlExecutor() {
        AgentText2SqlExecutorConfig config = new AgentText2SqlExecutorConfig(5, 9, 120, 45, 15);

        Executor executor = config.agentText2SqlExecutor();

        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertThat(taskExecutor.getCorePoolSize()).isEqualTo(5);
        assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(9);
        assertThat(taskExecutor.getQueueCapacity()).isEqualTo(120);
        assertThat(taskExecutor.getKeepAliveSeconds()).isEqualTo(45);
        assertThat(taskExecutor.getThreadPoolExecutor().getRejectedExecutionHandler())
                .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);

        taskExecutor.shutdown();
    }
}
