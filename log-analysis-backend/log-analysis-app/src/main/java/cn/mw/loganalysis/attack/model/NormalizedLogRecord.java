package cn.mw.loganalysis.attack.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 不同数据源日志被读取后统一成规则引擎可识别的标准字段。
 */
@Data
@Builder
public class NormalizedLogRecord {

    private LocalDateTime timestamp;

    private String sourceIp;

    private String hostname;

    private String message;

    private String raw;

    private String severity;

    private String fingerprint;
}
