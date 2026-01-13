package cn.mw.loganalysis.vector.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 机器指标实体（ClickHouse）
 */
@Data
@TableName("machine_metrics")
public class MachineMetrics {

    /**
     * 机器ID
     */
    @TableField("machine_id")
    private String machineId;

    /**
     * 采集时间
     */
    @TableField("collected_at")
    private LocalDateTime collectedAt;

    /**
     * CPU使用率(%)
     */
    @TableField("cpu_usage_percent")
    private Double cpuUsagePercent;

    /**
     * 内存使用率(%)
     */
    @TableField("memory_usage_percent")
    private Double memoryUsagePercent;

    /**
     * 已用内存(MB)
     */
    @TableField("memory_used_mb")
    private Long memoryUsedMb;

    /**
     * 磁盘使用率(%)
     */
    @TableField("disk_usage_percent")
    private Double diskUsagePercent;

    /**
     * 已用磁盘(GB)
     */
    @TableField("disk_used_gb")
    private Long diskUsedGb;

    /**
     * Agent内存占用(MB)
     */
    @TableField("agent_memory_mb")
    private Integer agentMemoryMb;

    /**
     * Vector是否运行（ClickHouse 使用 UInt8: 0=false, 1=true）
     */
    @TableField("vector_running")
    private Integer vectorRunning;

    /**
     * 网卡信息（JSON 格式字符串）
     */
    @TableField("network_interfaces")
    private String networkInterfaces;

    /**
     * 记录创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 获取 Vector 运行状态（布尔值）
     */
    public boolean getVectorRunningAsBool() {
        return vectorRunning != null && vectorRunning == 1;
    }
}
