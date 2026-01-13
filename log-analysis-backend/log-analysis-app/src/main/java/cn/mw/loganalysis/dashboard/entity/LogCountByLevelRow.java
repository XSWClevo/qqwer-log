package cn.mw.loganalysis.dashboard.entity;

import lombok.Data;

/**
 * 按级别统计日志数查询结果行
 */
@Data
public class LogCountByLevelRow {
    private String severity;
    private Long cnt;
}
