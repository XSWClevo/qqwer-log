package cn.mw.loganalysis.vector.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Vector服务操作记录实体
 */
@Data
@TableName("vector_service_operations")
public class VectorServiceOperation {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 机器ID
     */
    private String machineId;

    /**
     * 操作类型: start/stop/restart/reload/status
     */
    private String operationType;

    /**
     * 状态: pending/running/success/failed
     */
    private String status;

    /**
     * 执行结果
     */
    private String result;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 执行人ID
     */
    private String executedBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 完成时间
     */
    private LocalDateTime completedAt;
}
