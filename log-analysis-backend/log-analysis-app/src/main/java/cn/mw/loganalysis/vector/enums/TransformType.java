package cn.mw.loganalysis.vector.enums;

import lombok.Getter;

/**
 * Vector Transform 类型枚举
 */
@Getter
public enum TransformType {
    REMAP("remap", "重映射"),
    FILTER("filter", "过滤器"),
    ROUTE("route", "路由");

    private final String value;
    private final String description;

    TransformType(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public static TransformType fromValue(String value) {
        for (TransformType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("不支持的 Transform 类型: " + value);
    }
}
