package cn.mw.loganalysis.dashboard.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Dashboard 查询请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 开始时间 (ISO 8601 格式)
     */
    @NotNull(message = "开始时间不能为空")
    private String startTime;

    /**
     * 结束时间 (ISO 8601 格式)
     */
    @NotNull(message = "结束时间不能为空")
    private String endTime;

    /**
     * 时间粒度 (auto, 1m, 5m, 1h, 1d)
     * 默认 auto，根据时间范围自动选择
     */
    private String granularity;

    /**
     * 分页 - 页码 (从1开始)
     */
    @Builder.Default
    private Integer pageNum = 1;

    /**
     * 分页 - 每页大小
     */
    @Builder.Default
    private Integer pageSize = 20;

    /**
     * 实体类型 (host / app)，用于 getTopEntities
     */
    private String entityType;

    /**
     * 日志级别过滤 (用于告警日志查询)
     */
    private String[] levels;
}
