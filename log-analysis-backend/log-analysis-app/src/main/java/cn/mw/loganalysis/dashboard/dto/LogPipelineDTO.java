package cn.mw.loganalysis.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 日志管道 DTO - 摄入速率和处理延迟
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogPipelineDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 实时摄入速率 (条/秒)
     */
    private Double ingestRatePerSecond;

    /**
     * 摄入速率趋势 (最近10分钟，每分钟采样)
     */
    private List<RateTrend> ingestRateTrends;

    /**
     * 处理延迟 (毫秒)
     */
    private Long processingDelayMs;

    /**
     * 队列堆积数
     */
    private Long queueBacklog;

    /**
     * 管道状态 (healthy / warning / error)
     */
    private String status;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RateTrend implements Serializable {
        private static final long serialVersionUID = 1L;
        
        /**
         * 时间戳
         */
        private String timestamp;
        
        /**
         * 该分钟内的日志数
         */
        private Long count;
        
        /**
         * 速率 (条/秒)
         */
        private Double rate;
    }
}
