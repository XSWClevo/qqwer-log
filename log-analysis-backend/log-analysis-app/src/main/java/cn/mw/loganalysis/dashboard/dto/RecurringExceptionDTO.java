package cn.mw.loganalysis.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 重复异常 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurringExceptionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 异常列表
     */
    private List<ExceptionItem> items;

    /**
     * 总异常类型数
     */
    private Integer totalTypes;

    /**
     * 总异常次数
     */
    private Long totalOccurrences;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExceptionItem implements Serializable {
        private static final long serialVersionUID = 1L;
        
        /**
         * 异常消息摘要 (截取前200字符)
         */
        private String messageSummary;
        
        /**
         * 异常消息哈希 (用于聚合)
         */
        private String messageHash;
        
        /**
         * 异常类名 (从消息中提取)
         */
        private String exceptionClassName;
        
        /**
         * 所属服务 (应用名)
         */
        private String service;
        
        /**
         * 出现次数
         */
        private Long count;
        
        /**
         * 首次出现时间
         */
        private String firstSeen;
        
        /**
         * 最后出现时间
         */
        private String lastSeen;
        
        /**
         * 影响的主机数
         */
        private Integer affectedHosts;
        
        /**
         * 影响的应用数
         */
        private Integer affectedApps;
        
        /**
         * 日志级别
         */
        private String severity;
    }
}
