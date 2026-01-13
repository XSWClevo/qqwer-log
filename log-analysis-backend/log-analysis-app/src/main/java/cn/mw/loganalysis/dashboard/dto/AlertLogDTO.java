package cn.mw.loganalysis.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 告警日志 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertLogDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 日志列表
     */
    private List<AlertLogItem> items;

    /**
     * 总数
     */
    private Long total;

    /**
     * 当前页
     */
    private Integer pageNum;

    /**
     * 每页大小
     */
    private Integer pageSize;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlertLogItem implements Serializable {
        private static final long serialVersionUID = 1L;
        
        /**
         * 日志ID
         */
        private String id;
        
        /**
         * 时间戳
         */
        private String timestamp;
        
        /**
         * 日志级别 (ERROR / WARN)
         */
        private String severity;
        
        /**
         * 主机名
         */
        private String hostname;
        
        /**
         * 应用名
         */
        private String appName;
        
        /**
         * 日志消息
         */
        private String message;
        
        /**
         * 原始日志
         */
        private String raw;
        
        /**
         * 来源类型
         */
        private String sourceType;
    }
}
