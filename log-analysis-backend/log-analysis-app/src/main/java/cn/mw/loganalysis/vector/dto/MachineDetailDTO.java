package cn.mw.loganalysis.vector.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 机器详情数据传输对象
 */
@Data
public class MachineDetailDTO {

    // ========== 基本信息 ==========
    
    private String id;
    private String name;
    private String hostname;
    private String ipAddress;
    private String status;
    
    // ========== 系统信息 ==========
    
    private String osType;
    
    // ========== 版本信息 ==========
    
    private String vectorVersion;
    private String agentVersion;
    
    // ========== 时间信息 ==========
    
    private LocalDateTime lastHeartbeat;
    private LocalDateTime createdAt;
    
    // ========== 最新指标 ==========
    
    private MachineMetricsDTO.MetricsPoint latestMetrics;
}
