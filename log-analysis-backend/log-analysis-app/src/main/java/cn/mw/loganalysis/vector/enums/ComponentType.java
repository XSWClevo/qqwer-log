package cn.mw.loganalysis.vector.enums;

import lombok.Getter;

/**
 * Vector 组件类型枚举
 */
@Getter
public enum ComponentType {
    SOURCE("source", "数据源"),
    TRANSFORM("transform", "转换器"),
    SINK("sink", "输出目标");

    private final String value;
    private final String description;

    ComponentType(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public static ComponentType fromValue(String value) {
        for (ComponentType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("不支持的组件类型: " + value);
    }
}
