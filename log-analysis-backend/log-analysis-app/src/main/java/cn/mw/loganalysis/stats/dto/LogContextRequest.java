package cn.mw.loganalysis.stats.dto;

import cn.mw.loganalysis.common.serializer.FlexibleLocalDateTimeDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 日志上下文查询请求DTO
 */
@Data
public class LogContextRequest {

    /**
     * 数据源ID（ConfigComponent 的 ID）
     * 如果不传，则使用默认的 ClickHouse syslog 表
     */
    private String datasourceId;

    /**
     * 目标日志ID
     */
    @NotNull(message = "日志ID不能为空")
    private String logId;

    /**
     * 目标日志时间戳
     */
    @NotNull(message = "时间戳不能为空")
    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime timestamp;

    /**
     * 向前查询条数（查询比目标日志时间早的日志）
     */
    private Integer beforeCount = 50;

    /**
     * 向后查询条数（查询比目标日志时间晚的日志）
     */
    private Integer afterCount = 50;

    /**
     * 字段过滤器列表（统一的包含/排除结构）
     */
    private List<LogQueryRequest.FieldFilter> fieldFilters;

    /**
     * message字段查询条件列表
     */
    private List<LogQueryRequest.MessageCondition> messageConditions;

    /**
     * raw字段查询条件列表
     */
    private List<LogQueryRequest.MessageCondition> rawConditions;

    /**
     * 是否启用 MCP 查询通道（仅 ClickHouse 生效）。
     * true/空: 允许 MCP
     * false: 强制走 JDBC
     */
    private Boolean useMcp;
}
