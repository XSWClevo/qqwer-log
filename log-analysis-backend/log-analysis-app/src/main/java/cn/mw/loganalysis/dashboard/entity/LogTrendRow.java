package cn.mw.loganalysis.dashboard.entity;

import lombok.Data;

/**
 * 日志趋势查询结果行
 */
@Data
public class LogTrendRow {
    private String timeBucket;
    private String severity;
    private Long cnt;
}
