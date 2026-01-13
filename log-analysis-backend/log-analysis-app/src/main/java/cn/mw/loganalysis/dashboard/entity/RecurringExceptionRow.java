package cn.mw.loganalysis.dashboard.entity;

import lombok.Data;

/**
 * 重复异常查询结果行
 */
@Data
public class RecurringExceptionRow {
    private String msgHash;
    private String msgSummary;
    private Long cnt;
    private String firstSeen;
    private String lastSeen;
    private Integer affectedHosts;
    private Integer affectedApps;
    private String service;
    private String severity;
}
