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
     * 关联的机器ID
     */
    private String machineId;

    /**
     * 日志文件名
     */
    private String fileName;

    /**
     * 日志消息
     */
    private String message;

    /**
     * 时间戳
     */
    private LocalDateTime timestamp;
}
