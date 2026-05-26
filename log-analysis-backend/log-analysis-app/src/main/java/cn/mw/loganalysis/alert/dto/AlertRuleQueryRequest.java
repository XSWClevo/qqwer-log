package cn.mw.loganalysis.alert.dto;

import lombok.Data;

/**
 * 告警规则查询请求 DTO
 */
@Data
public class AlertRuleQueryRequest {
    
    /** 搜索关键词 */
    private String keyword;
    
    /** 状态过滤: enabled, disabled */
    private String status;
    
    /** 严重程度过滤: critical, warning, info */
    private String severity;
    
    /** 类型过滤: log_query, metric_threshold, anomaly */
    private String type;
    
    /** 通知渠道过滤: page, slack, email, webhook */
    private String channel;
    
    /** 页码 */
    private Integer pageNum = 1;
    
    /** 每页大小 */
    private Integer pageSize = 20;
}
