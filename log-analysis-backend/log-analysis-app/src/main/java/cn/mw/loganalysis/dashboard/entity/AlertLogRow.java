package cn.mw.loganalysis.dashboard.entity;

import lombok.Data;

/**
 * 告警日志查询结果行
 */
@Data
public class AlertLogRow {
    private String id;
    private String timestamp;
    private String severity;
    private String hostname;
    private String appName;
    private String message;
    private String raw;
    private String sourceType;
}
