package cn.mw.loganalysis.stats.dto;

import cn.mw.loganalysis.common.serializer.FlexibleLocalDateTimeDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 日志查询请求DTO - 优化版
 */
@Data
public class LogQueryRequest {

    /**
     * 数据源ID（ConfigComponent 的 ID）
     * 如果不传，则使用默认的 ClickHouse syslog 表
     */
    private String datasourceId;

    /**
     * 时间范围-开始
     */
    @NotNull(message = "开始时间不能为空")
    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime startTime;

    /**
     * 时间范围-结束
     */
    @NotNull(message = "结束时间不能为空")
    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime endTime;

    /**
     * 字段过滤器列表（统一的包含/排除结构）
     */
    private List<FieldFilter> fieldFilters;

    /**
     * message字段查询条件列表
     */
    private List<MessageCondition> messageConditions;

    /**
     * raw字段查询条件列表
     */
    private List<MessageCondition> rawConditions;

    /**
     * 页码
     */
    private Integer pageNum = 1;

    /**
     * 每页大小
     */
    private Integer pageSize = 100;

    /**
     * 字段过滤器
     */
    @Data
    public static class FieldFilter {
        /**
         * 字段名：level, source, host, service, facility, procid, sourceIp
         */
        private String field;

        /**
         * 过滤类型：include(包含), exclude(排除)
         */
        private String type;

        /**
         * 过滤值列表
         */
        private List<String> values;
    }

    /**
     * Message/Raw字段查询条件
     */
    @Data
    public static class MessageCondition {
        /**
         * 操作类型：contains(包含), notContains(不包含), equals(等于), notEquals(不等于)
         */
        private String operator;

        /**
         * 查询值
         */
        private String value;
    }
}
