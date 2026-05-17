package cn.mw.loganalysis.vector.enums;

import lombok.Getter;

/**
 * Vector Source 类型枚举
 */
@Getter
public enum SourceType {
    FILE("file", "文件"),
    KAFKA("kafka", "Kafka"),
    HTTP_SERVER("http_server", "HTTP 服务器"),
    SYSLOG("syslog", "Syslog"),
    SOCKET("socket", "Socket"),
    DEMO_LOGS("demo_logs", "演示日志"),
    INTERNAL_LOGS("internal_logs", "内部日志");

    private final String value;
    private final String description;

    SourceType(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public static SourceType fromValue(String value) {
        for (SourceType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("不支持的 Source 类型: " + value);
    }
}
