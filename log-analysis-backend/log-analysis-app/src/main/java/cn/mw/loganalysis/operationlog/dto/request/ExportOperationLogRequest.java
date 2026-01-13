package cn.mw.loganalysis.operationlog.dto.request;

import cn.mw.loganalysis.operationlog.enums.OperationType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导出操作日志请求
 *
 * @author Claude
 * @since 2026-01-07
 */
@Data
public class ExportOperationLogRequest {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名 (模糊查询)
     */
    private String username;

    /**
     * 操作类型
     */
    private OperationType operationType;

    /**
     * 操作模块
     */
    private String module;

    /**
     * 是否成功
     */
    private Boolean isSuccess;

    /**
     * IP 地址
     */
    private String ipAddress;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 导出格式 (csv/excel)
     */
    private String format = "csv";
}
