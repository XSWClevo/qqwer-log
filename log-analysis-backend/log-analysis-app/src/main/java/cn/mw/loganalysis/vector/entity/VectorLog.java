package cn.mw.loganalysis.vector.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Vector 运行日志实体
 * 对应 ClickHouse 的 vector_logs 表
 */
@Data
@TableName("vector_logs")
public class VectorLog {

    /**
     * 日志ID
     */
    private String id;

    /**
     * 关联的机器ID
     */
    private String machineId;

    /**
     * 主机名
     */
    private String hostname;

    /**
     * IP地址
     */
    private String ipAddress;

    /**
     * 日志级别: error, warn, info, debug, trace
     */
    private String logLevel;

    /**
     * 日志消息
     */
    private String message;

    /**
     * 时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 原始日志内容
     */
    private String rawLog;

    /**
     * 额外的元数据（JSON格式）
     */
    private String metadata;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
