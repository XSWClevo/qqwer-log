package cn.mw.loganalysis.logsource.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 日志源响应 DTO
 *
 * @author Claude
 * @since 2026-01-23
 */
@Data
@Builder
public class LogSourceDTO {

    /**
     * ID
     */
    private Long id;

    /**
     * IP地址
     */
    private String sourceIp;

    /**
     * 主机名
     */
    private String hostname;

    /**
     * 描述
     */
    private String description;

    /**
     * 状态：trusted, blocked, pending
     */
    private String status;

    /**
     * 首次发现时间
     */
    private LocalDateTime firstSeenAt;

    /**
     * 最后活跃时间
     */
    private LocalDateTime lastSeenAt;

    /**
     * 信任时间
     */
    private LocalDateTime trustedAt;

    /**
     * 信任操作人
     */
    private String trustedBy;

    /**
     * 日志数量
     */
    private Long logCount;

    /**
     * 备注
     */
    private String remark;
}
