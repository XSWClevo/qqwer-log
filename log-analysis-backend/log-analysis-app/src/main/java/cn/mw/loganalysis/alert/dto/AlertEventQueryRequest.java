package cn.mw.loganalysis.alert.dto;

import lombok.Data;

/**
 * 告警事件查询请求 DTO
 */
@Data
public class AlertEventQueryRequest {
    
    /** 时间范围: 24h, 7d, 30d, custom */
    private String timeRange = "24h";
    
    /** 自定义开始时间 */
    private String startTime;
    
    /** 自定义结束时间 */
    private String endTime;
    
    /** 搜索关键词 */
    private String keyword;
    
    /** 严重程度过滤: critical, warning */
    private String severity;
    
    /** 规则ID过滤 */
    private Long ruleId;
    
    /** 通知状态过滤: sent, failed */
    private String notificationStatus;
    
    /** 页码 */
    private Integer pageNum = 1;
    
    /** 每页大小 */
    private Integer pageSize = 20;
}
