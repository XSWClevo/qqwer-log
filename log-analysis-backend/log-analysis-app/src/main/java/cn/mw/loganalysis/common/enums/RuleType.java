package cn.mw.loganalysis.common.enums;

import lombok.Getter;

/**
 * 规则类型枚举
 */
@Getter
public enum RuleType {
    REGEX("REGEX", "正则表达式"),
    GROK("GROK", "Grok 模式"),
    JSON_PATH("JSON_PATH", "JSON 路径");

    private final String code;
    private final String description;

    RuleType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static RuleType fromCode(String code) {
        for (RuleType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }

    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }
}
