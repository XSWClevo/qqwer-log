package cn.mw.loganalysis.vector.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Vector健康检查记录实体
 */
@Data
@TableName(value = "vector_health_checks", autoResultMap = true)
public class VectorHealthCheck {

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
     * 检查类型: connectivity/config_valid/service_status/pipeline_flow
     */
    private String checkType;

    /**
     * 状态: healthy/degraded/unhealthy
     */
    private String status;

    /**
     * 检查详情
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> details;

    /**
     * 检查时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime checkedAt;
}
