package cn.mw.loganalysis.dashboard.entity;

import lombok.Data;

/**
 * 查询统计结果行
 */
@Data
public class QueryStatsRow {
    private Long queryCount;
    private Long insertCount;
}
