package cn.mw.loganalysis.stats.dto;

import cn.mw.loganalysis.common.serializer.FlexibleLocalDateTimeDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 统计查询请求DTO
 */
@Data
public class StatsQueryRequest {

    /**
     * 数据源ID（ConfigComponent 的 ID）
     * 如果不传，则使用默认的 ClickHouse syslog 表
     */
    private String datasourceId;

    /**
     * 统计维度（如: level, source, application等）
     */
    private List<String> dimensions;

    /**
     * 聚合指标（如: count, avg, sum等）
     */
    private List<String> metrics;

    /**
     * 时间范围-开始
     */
    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime startTime;

    /**
     * 时间范围-结束
     */
    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime endTime;

    /**
     * 过滤条件
     */
    private Map<String, Object> filters;

    /**
     * 聚合粒度（hour, day, week, month）
     */
    private String granularity;

    /**
     * 是否启用 MCP 查询通道（仅 ClickHouse 生效）。
     * true/空: 允许 MCP
     * false: 强制走 JDBC
     */
    private Boolean useMcp;
}
