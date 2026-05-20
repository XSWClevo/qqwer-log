package cn.mw.loganalysis.vector.service;

import cn.mw.loganalysis.vector.dto.AgentMetricsRequest;
import cn.mw.loganalysis.vector.dto.ComponentStatusResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 组件状态服务
 * 管理各机器上 Vector 组件的运行状态
 */
@Slf4j
@Service
public class ComponentStatusService {
    
    /**
     * 机器组件状态缓存
     * key: machineId
     * value: 组件状态信息
     */
    private final Map<String, MachineComponentStatus> statusCache = new ConcurrentHashMap<>();
    
    /**
     * 状态过期时间（秒）
     * 应该比 Agent 上报间隔（60秒）稍长，避免状态闪烁
     */
    private static final int STATUS_EXPIRE_SECONDS = 90;

    /**
     * Vector 内部组件名称，不应暴露给前端可视化配置
     */
    private static final Set<String> INTERNAL_COMPONENTS = Set.of(
            "internal_metrics", "blackhole", "internal_logs"
    );
    
    /**
     * 更新机器的组件状态
     */
    public void updateComponentStatus(String machineId, AgentMetricsRequest metrics) {
        if (machineId == null || metrics == null) {
            log.warn("更新组件状态失败: machineId={}, metrics={}", machineId, metrics);
            return;
        }
        
        MachineComponentStatus status = new MachineComponentStatus();
        status.vectorRunning = metrics.getVectorRunning() != null && metrics.getVectorRunning();
        status.lastUpdated = LocalDateTime.now();
        
        if (metrics.getComponentMetrics() != null) {
            log.debug("收到组件指标: machineId={}, components={}", machineId, metrics.getComponentMetrics().keySet());
            metrics.getComponentMetrics().forEach((name, componentMetrics) -> {
                // 过滤 Vector 内部组件，只保留用户配置的业务组件
                if (INTERNAL_COMPONENTS.contains(name)) {
                    return;
                }

                String componentStatus = componentMetrics.getStatus();
                if (componentStatus == null) {
                    // 根据错误数判断状态
                    if (componentMetrics.getErrors() != null && componentMetrics.getErrors() > 0) {
                        componentStatus = "warning";
                    } else {
                        componentStatus = "normal";
                    }
                }
                status.componentStatus.put(name, componentStatus);
                log.debug("组件状态: {} -> {}", name, componentStatus);
            });
        } else {
            log.info("组件指标为空: machineId={}", machineId);
        }
        
        statusCache.put(machineId, status);
        log.debug("更新机器 {} 的组件状态: {} 个组件, vectorRunning={}",
                machineId, status.componentStatus.size(), status.vectorRunning);
    }
    
    /**
     * 获取机器的组件状态
     */
    public ComponentStatusResponse getComponentStatus(String machineId) {
        ComponentStatusResponse response = new ComponentStatusResponse();
        
        MachineComponentStatus status = statusCache.get(machineId);
        if (status == null) {
            // 没有状态数据，返回空
            response.setVectorRunning(null);
            response.setComponentStatus(new ConcurrentHashMap<>());
            response.setLastUpdated(null);
            return response;
        }
        
        // 检查状态是否过期
        if (status.lastUpdated.plusSeconds(STATUS_EXPIRE_SECONDS).isBefore(LocalDateTime.now())) {
            // 状态过期，标记所有组件为 stopped
            status.componentStatus.replaceAll((k, v) -> "stopped");
            status.vectorRunning = false;
        }
        
        response.setVectorRunning(status.vectorRunning);
        response.setComponentStatus(status.componentStatus);
        response.setLastUpdated(status.lastUpdated.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        return response;
    }
    
    /**
     * 清除机器的状态缓存
     */
    public void clearStatus(String machineId) {
        statusCache.remove(machineId);
    }
    
    /**
     * 机器组件状态内部类
     */
    private static class MachineComponentStatus {
        boolean vectorRunning;
        LocalDateTime lastUpdated;
        Map<String, String> componentStatus = new ConcurrentHashMap<>();
    }
}
