package cn.mw.loganalysis.vector.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Vector管道性能指标实体
 */
@Data
@TableName("vector_pipeline_metrics")
public class VectorPipelineMetric {

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
     * Source组件名
     */
    private String sourceName;

    /**
     * Transform组件名
     */
    private String transformName;

    /**
     * Sink组件名
     */
    private String sinkName;

    /**
     * 输入事件数
     */
    private Long eventsIn;

    /**
     * 输出事件数
     */
    private Long eventsOut;

    /**
     * 输入字节数
     */
    private Long bytesIn;

    /**
     * 输出字节数
     */
    private Long bytesOut;

    /**
     * 错误数
     */
    private Integer errors;

    /**
     * 延迟(毫秒)
     */
    private BigDecimal latencyMs;

    /**
     * 记录时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime recordedAt;
}
