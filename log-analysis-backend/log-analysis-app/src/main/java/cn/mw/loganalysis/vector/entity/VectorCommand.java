package cn.mw.loganalysis.vector.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Vector 控制命令实体
 */
@Data
@TableName("vector_commands")
public class VectorCommand {
    
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    
    /**
     * 目标机器ID
     */
    private String machineId;
    
    /**
     * 命令类型: start_vector, stop_vector, restart_vector, 
     *          start_agent, stop_agent, restart_agent,
     *          upgrade_agent, upgrade_vector
     */
    private String commandType;
    
    /**
     * 目标版本（升级命令用）
     */
    private String targetVersion;
    
    /**
     * 安装包ID（升级命令用）
     */
    private String packageId;
    
    /**
     * 命令状态: pending, executing, success, failed
     */
    private String status;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    /**
     * 执行时间
     */
    private LocalDateTime executedAt;
    
    /**
     * 完成时间
     */
    private LocalDateTime completedAt;
    
    /**
     * 创建人
     */
    private String createdBy;
}
