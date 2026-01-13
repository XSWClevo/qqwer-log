package cn.mw.loganalysis.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 系统指标 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemMetricsDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * CPU 指标
     */
    private CpuMetrics cpu;

    /**
     * 内存指标
     */
    private MemoryMetrics memory;

    /**
     * 磁盘指标
     */
    private DiskMetrics disk;

    /**
     * ClickHouse 存储指标
     */
    private StorageMetrics clickhouseStorage;

    /**
     * 采集时间戳
     */
    private Long timestamp;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CpuMetrics implements Serializable {
        private static final long serialVersionUID = 1L;
        
        /**
         * CPU 使用率 (0-100)
         */
        private Double usagePercent;
        
        /**
         * CPU 核心数
         */
        private Integer cores;
        
        /**
         * 系统负载 (1分钟平均)
         */
        private Double loadAverage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemoryMetrics implements Serializable {
        private static final long serialVersionUID = 1L;
        
        /**
         * 总内存 (bytes)
         */
        private Long total;
        
        /**
         * 已用内存 (bytes)
         */
        private Long used;
        
        /**
         * 可用内存 (bytes)
         */
        private Long available;
        
        /**
         * 使用率 (0-100)
         */
        private Double usagePercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiskMetrics implements Serializable {
        private static final long serialVersionUID = 1L;
        
        /**
         * 总容量 (bytes)
         */
        private Long total;
        
        /**
         * 已用空间 (bytes)
         */
        private Long used;
        
        /**
         * 可用空间 (bytes)
         */
        private Long available;
        
        /**
         * 使用率 (0-100)
         */
        private Double usagePercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StorageMetrics implements Serializable {
        private static final long serialVersionUID = 1L;
        
        /**
         * syslog 表占用空间 (bytes)
         */
        private Long syslogTableSize;
        
        /**
         * 总行数
         */
        private Long totalRows;
        
        /**
         * 分区数
         */
        private Integer partitionCount;
        
        /**
         * 格式化后的大小
         */
        private String formattedSize;
    }
}
