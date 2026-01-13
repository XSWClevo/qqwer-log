package cn.mw.loganalysis.stats.service.query;

import lombok.Builder;
import lombok.Data;

/**
 * 连接测试结果
 */
@Data
@Builder
public class ConnectionTestResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 消息
     */
    private String message;

    /**
     * 数据库版本
     */
    private String version;

    /**
     * 响应时间（毫秒）
     */
    private Long responseTimeMs;
}
