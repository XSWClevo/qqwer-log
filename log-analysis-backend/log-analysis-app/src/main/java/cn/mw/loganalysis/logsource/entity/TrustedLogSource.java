package cn.mw.loganalysis.logsource.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 可信任日志源实体类
 * 用于管理允许接收日志的 IP 地址白名单
 *
 * @author Claude
 * @since 2026-01-23
 */
@Data
@TableName("trusted_log_sources")
public class TrustedLogSource implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 日志源IP地址
     */
    private String sourceIp;

    /**
     * 日志源主机名（可选）
     */
    private String hostname;

    /**
     * 描述信息
     */
    private String description;

    /**
     * 状态：trusted（信任）、blocked（拉黑）、pending（待审核）
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
     * 信任时间（用户点击信任的时间）
     */
    private LocalDateTime trustedAt;

    /**
     * 信任操作人
     */
    private String trustedBy;

    /**
     * 日志数量统计
     */
    private Long logCount;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 备注
     */
    private String remark;
}
