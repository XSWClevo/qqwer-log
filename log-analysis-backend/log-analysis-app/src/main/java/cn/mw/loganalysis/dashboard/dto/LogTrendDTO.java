package cn.mw.loganalysis.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 日志趋势 DTO - 适合前端堆叠柱状图
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogTrendDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 时间粒度 (1m, 5m, 1h)
     */
    private String granularity;

    /**
     * 时间轴标签列表
     */
    private List<String> timestamps;

    /**
     * 按级别分组的数据系列
     */
    private List<LevelSeries> series;

    /**
     * 总日志数
     */
    private Long totalCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LevelSeries implements Serializable {
        private static final long serialVersionUID = 1L;
        
        /**
         * 日志级别名称 (DEBUG, INFO, WARN, ERROR, FATAL)
         */
        private String severity;
        
        /**
         * 对应每个时间点的数量
         */
        private List<Long> data;
        
        /**
         * 该级别的总数
         */
        private Long total;
    }
}
