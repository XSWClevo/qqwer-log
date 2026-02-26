package cn.mw.loganalysis.logsource.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 新日志源通知 DTO
 *
 * @author Claude
 * @since 2026-01-23
 */
@Data
@Builder
public class NewLogSourceNotification {

    /**
     * IP地址
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

    /**
     * 最近一条日志内容（预览）
     */
    private String recentLogPreview;
}
