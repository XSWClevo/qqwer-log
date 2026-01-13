package cn.mw.loganalysis.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 核心概览 DTO - 今日统计和错误率分布
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoreOverviewDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 今日总日志量
     */
    private Long todayTotalLogs;

    /**
     * 今日 INFO 级别日志数
     */
    private Long infoCount;

    /**
     * 今日 WARN 级别日志数
     */
    private Long warnCount;

    /**
     * 今日 ERROR 级别日志数
     */
    private Long errorCount;

    /**
     * 今日 FATAL 级别日志数
     */
    private Long fatalCount;

    /**
     * INFO 占比 (%)
     */
    private Double infoRate;

    /**
     * WARN 占比 (%)
     */
    private Double warnRate;

    /**
     * ERROR 占比 (%)
     */
    private Double errorRate;

    /**
     * FATAL 占比 (%)
     */
    private Double fatalRate;

    /**
     * 异常日志总数 (ERROR + FATAL)
     */
    private Long exceptionCount;

    /**
     * 异常率 (%)
     */
    private Double exceptionRate;
}
