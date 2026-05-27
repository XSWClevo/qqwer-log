package cn.mw.loganalysis.agent.service;

import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 按顺序执行上下文增强。
 */
@Component
public class AgentContextEnhancerChain {

    private final List<AgentContextEnhancer> enhancers;

    /**
     * 收集所有上下文增强器并按 Ordered 顺序排序。
     */
    public AgentContextEnhancerChain(List<AgentContextEnhancer> enhancers) {
        this.enhancers = new ArrayList<>(enhancers);
        AnnotationAwareOrderComparator.sort(this.enhancers);
    }

    /**
     * 依次执行所有上下文增强器。
     */
    void enhance(AgentRuntimeContext context) {
        for (AgentContextEnhancer enhancer : enhancers) {
            enhancer.enhance(context);
        }
    }
}
