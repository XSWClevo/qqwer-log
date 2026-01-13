package cn.mw.loganalysis.datasource.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 数据源测试结果
 */
@Data
@Builder
public class DatasourceTestResult {

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 消息
     */
    private String message;

    /**
     * 响应时间（毫秒）
     */
    private Long responseTime;

    /**
     * 数据库版本
     */
    private String version;

    /**
     * 额外信息
     */
    private String details;
}
