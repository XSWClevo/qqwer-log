package cn.mw.loganalysis.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 数据库状态 DTO - ClickHouse 集群状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseStatusDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 集群状态 (healthy / warning / error)
     */
    private String clusterStatus;

    /**
     * 存储已用空间 (GB)
     */
    private Double storageUsedGb;

    /**
     * 存储总容量 (GB)
     */
    private Double storageTotalGb;

    /**
     * 存储使用率 (%)
     */
    private Double storageUsagePercent;

    /**
     * 查询 TPS (每秒查询数)
     */
    private Double queryTps;

    /**
     * 插入 TPS (每秒插入数)
     */
    private Double insertTps;

    /**
     * 活跃连接数
     */
    private Integer activeConnections;

    /**
     * 副本数
     */
    private Integer replicaCount;

    /**
     * 分片数
     */
    private Integer shardCount;

    /**
     * 总行数
     */
    private Long totalRows;

    /**
     * 总分区数
     */
    private Integer totalPartitions;

    /**
     * 最后更新时间
     */
    private String lastUpdateTime;
}
