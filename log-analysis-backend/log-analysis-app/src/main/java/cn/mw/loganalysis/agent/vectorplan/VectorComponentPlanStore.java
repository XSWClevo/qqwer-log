package cn.mw.loganalysis.agent.vectorplan;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Vector 组件预览计划的短期缓存。
 */
@Component
public class VectorComponentPlanStore {

    private final Cache<String, VectorComponentPlan> planCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    /**
     * 保存预览计划，供用户后续确认创建。
     */
    void save(VectorComponentPlan plan) {
        if (plan != null && StringUtils.isNotBlank(plan.planId())) {
            planCache.put(plan.planId(), plan);
        }
    }

    /**
     * 根据 planId 读取尚未过期的预览计划。
     */
    VectorComponentPlan get(String planId) {
        return planCache.getIfPresent(StringUtils.trim(planId));
    }

    /**
     * 确认创建完成后移除预览计划。
     */
    void invalidate(String planId) {
        if (StringUtils.isNotBlank(planId)) {
            planCache.invalidate(StringUtils.trim(planId));
        }
    }
}
