package cn.mw.loganalysis.operationlog.entity;

import com.baomidou.mybatisplus.annotation.*;
import cn.mw.loganalysis.operationlog.enums.OperationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户操作日志实体类
 *
 * @author Claude
 * @since 2026-01-07
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_operation_logs")
public class UserOperationLog {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
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
     * 操作模块 (auth/stats/alert/vector/config/datasource/extraction/dashboard)
     */
    private String module;

    /**
     * 资源类型 (User/AlertRule/VectorConfig/Datasource 等)
     */
    private String resourceType;

    /**
     * 资源ID
     */
    private String resourceId;

    /**
     * 具体操作 (create_alert_rule/update_datasource/user_login 等)
     */
    private String action;

    /**
     * HTTP 方法 (POST/GET/PUT/DELETE)
     */
    private String requestMethod;

    /**
     * 请求路径
     */
    private String requestUrl;

    /**
     * 请求参数 (JSONB 格式, 已脱敏)
     */
    @TableField(typeHandler = cn.mw.loganalysis.operationlog.handler.PostgreSQLJSONBTypeHandler.class)
    private Object requestParams;

    /**
     * HTTP 响应状态码
     */
    private Integer responseStatus;

    /**
     * 响应消息
     */
    private String responseMessage;

    /**
     * 客户端 IP 地址
     */
    private String ipAddress;

    /**
     * User-Agent 信息
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
     * 错误信息 (失败时记录)
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
