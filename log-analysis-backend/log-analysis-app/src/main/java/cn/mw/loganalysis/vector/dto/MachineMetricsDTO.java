package cn.mw.loganalysis.vector.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 机器指标数据传输对象
 */
@Data
public class MachineMetricsDTO {

    /**
     * 机器ID
     */
    private String machineId;

    /**
     * 最新指标
     */
    private MetricsPoint latest;

    /**
     * 历史指标列表
     */
    private List<MetricsPoint> history;

    /**
     * 指标数据点
     */
    @Data
    public static class MetricsPoint {
        /**
         * 采集时间
         */
        private LocalDateTime timestamp;

        /**
         * CPU 使用率 (%)
         */
        private Double cpuUsagePercent;

        /**
         * 内存使用率 (%)
         */
        private Double memoryUsagePercent;

        /**
         * 已用内存 (MB)
         */
        private Long memoryUsedMb;

        /**
         * 磁盘使用率 (%)
         */
        private Double diskUsagePercent;

        /**
         * 已用磁盘 (GB)
         */
        private Long diskUsedGb;

        /**
         * Agent 内存占用 (MB)
         */
        private Integer agentMemoryMb;

        /**
         * Vector 是否运行
         */
        private Boolean vectorRunning;
        
        /**
         * 网卡信息列表
         */
        private List<NetworkInterfaceInfo> networkInterfaces;
    }
    
    /**
     * 网卡信息
     */
    @Data
    public static class NetworkInterfaceInfo {
        private String name;        // 网卡名称
        private Long bytesSent;     // 发送字节数
        private Long bytesRecv;     // 接收字节数
        private Long packetsSent;   // 发送包数
        private Long packetsRecv;   // 接收包数
        private Long errin;         // 接收错误数
        private Long errout;        // 发送错误数
    }
}
