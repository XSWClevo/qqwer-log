package cn.mw.loganalysis.vector.enums;

import lombok.Getter;

/**
 * Vector Sink 类型枚举
 */
@Getter
public enum SinkType {
    CLICKHOUSE("clickhouse", "ClickHouse"),
    ELASTICSEARCH("elasticsearch", "Elasticsearch"),
    KAFKA("kafka", "Kafka"),
    CONSOLE("console", "控制台");

    private final String value;
    private final String description;

    SinkType(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public static SinkType fromValue(String value) {
        for (SinkType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("不支持的 Sink 类型: " + value);
    }
}
