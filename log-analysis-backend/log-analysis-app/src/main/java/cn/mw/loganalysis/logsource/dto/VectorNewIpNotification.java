package cn.mw.loganalysis.logsource.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Vector 发送的新日志源通知
 *
 * @author Claude
 * @since 2026-01-23
 */
@Data
public class VectorNewIpNotification {

    /**
     * 通知类型
     */
    private String notificationType;

    /**
     * 日志源 IP
     */
    private String sourceIp;

    /**
     * 主机名
     */
    private String hostname;

    /**
     * 首次发现时间
     */
    private LocalDateTime firstSeenAt;

    /**
     * 日志数量
     */
    private Long logCount;

}
