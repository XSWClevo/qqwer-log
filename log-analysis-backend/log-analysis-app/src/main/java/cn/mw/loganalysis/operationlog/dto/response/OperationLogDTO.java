package cn.mw.loganalysis.operationlog.dto.response;

import cn.mw.loganalysis.operationlog.enums.OperationType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 操作日志 DTO (返回给前端)
 *
 * @author Claude
 * @since 2026-01-07
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationLogDTO {

    /**
     * 主键
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
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
     * 资源类型
     */
    private String resourceType;

    /**
     * 资源ID
     */
    private String resourceId;

    /**
     * 具体操作
     */
    private String action;

    /**
     * HTTP 方法
     */
    private String requestMethod;

    /**
     * 请求路径
     */
    private String requestUrl;

    /**
     * 请求参数 (已脱敏)
     */
    private Object requestParams;

    /**
     * 响应状态码
     */
    private Integer responseStatus;

    /**
     * 响应消息
     */
    private String responseMessage;

    /**
     * 客户端 IP
     */
    private String ipAddress;

    /**
     * User-Agent
     */
    private String userAgent;

    /**
     * 执行耗时 (毫秒)
     */
    private Integer executionTime;

    /**
     * 是否成功
     */
    private Boolean isSuccess;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
