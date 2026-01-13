package cn.mw.loganalysis.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 缓存配置
 * 使用 Caffeine 作为缓存实现
 */
@Slf4j
@Configuration
@EnableCaching
@EnableScheduling
public class CacheConfig {

    /**
     * 配置缓存管理器
     * 支持多个缓存：statsCache（5分钟）、systemMetrics（30秒）等
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // 默认配置：5 分钟过期，最大 1000 条
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .recordStats());

        // 注册所有缓存名称
        cacheManager.setCacheNames(List.of(
            "statsCache",           // 统计查询缓存 (5分钟)
            "systemMetrics",        // 系统指标缓存 (30秒)
            "logTrend",             // 日志趋势缓存 (30秒)
            "topEntities",          // Top 实体缓存 (30秒)
            "recurringExceptions"   // 重复异常缓存 (30秒)
        ));

        log.info("缓存管理器初始化完成: 支持 5 个缓存，默认过期时间=5分钟");
        return cacheManager;
    }
}
