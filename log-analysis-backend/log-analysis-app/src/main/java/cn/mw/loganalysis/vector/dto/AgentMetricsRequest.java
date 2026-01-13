package cn.mw.loganalysis.vector.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Agent 指标上报请求
 */
@Data
public class AgentMetricsRequest {
    private LocalDateTime collectedAt;
    private Double cpuUsagePercent;
    private Double memoryUsagePercent;
    private Long memoryUsedMb;
    private Double diskUsagePercent;
    private Long diskUsedGb;
    private Long agentUptimeSeconds;
    private Integer agentMemoryMb;
    private Boolean vectorRunning;
    private Long vectorUptimeSeconds;

    public Double  getCpuUsagePercent() {
        return safeRound(cpuUsagePercent);
    }
    public Double  getMemoryUsagePercent() {
        return safeRound(memoryUsagePercent);
    }
    public Double  getDiskUsagePercent() {
        return safeRound(diskUsagePercent);
    }

    private double safeRound(Double value) {
        if (value == null) {
            return 0.0;
        }

        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return value;  // 保持 NaN 或 Infinity
        }

        double factor = Math.pow(10, 2);
        return Math.round(value * factor) / factor;
    }
    
    /**
     * 网卡信息列表
     */
    private List<NetworkInterface> networkInterfaces;
    
    /**
     * 组件级别指标
     * key: 组件名称
     * value: 组件指标
     */
    private Map<String, ComponentMetrics> componentMetrics;
    
    /**
     * 网卡信息
     */
    @Data
    public static class NetworkInterface {
        private String name;        // 网卡名称
        private Long bytesSent;     // 发送字节数
        private Long bytesRecv;     // 接收字节数
        private Long packetsSent;   // 发送包数
        private Long packetsRecv;   // 接收包数
        private Long errin;         // 接收错误数
        private Long errout;        // 发送错误数
    }
    
    /**
     * 组件指标
     */
    @Data
    public static class ComponentMetrics {
        /**
         * 组件状态: normal, warning, error, stopped
         */
        private String status;
        
        /**
         * 处理的事件数
         */
        private Long eventsProcessed;
        
        /**
         * 处理的字节数
         */
        private Long bytesProcessed;
        
        /**
         * 错误数
         */
        private Long errors;
        
        /**
         * 最后活跃时间
         */
        private LocalDateTime lastActive;
    }
}

