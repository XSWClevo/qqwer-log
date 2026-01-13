package cn.mw.loganalysis.dashboard.entity;

import lombok.Data;

/**
 * 摄入速率趋势查询结果行
 */
@Data
public class IngestRateTrendRow {
    private String minute;
    private Long cnt;
}
