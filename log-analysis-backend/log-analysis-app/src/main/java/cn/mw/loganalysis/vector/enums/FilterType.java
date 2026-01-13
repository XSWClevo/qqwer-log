package cn.mw.loganalysis.vector.enums;

import lombok.Getter;

/**
 * 过滤器类型枚举
 */
@Getter
public enum FilterType {
    LEVEL("level", "日志级别过滤"),
    FIELD("field", "字段过滤"),
    CUSTOM("custom", "自定义条件");

    private final String value;
    private final String description;

    FilterType(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public static FilterType fromValue(String value) {
        for (FilterType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        // 默认返回级别过滤
        return LEVEL;
    }
}
