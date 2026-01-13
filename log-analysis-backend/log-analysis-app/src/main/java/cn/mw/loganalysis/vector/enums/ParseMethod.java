package cn.mw.loganalysis.vector.enums;

import lombok.Getter;

/**
 * 日志解析方法枚举
 */
@Getter
public enum ParseMethod {
    PARSE_JSON("parse_json", "JSON 解析"),
    PARSE_SYSLOG("parse_syslog", "Syslog 解析"),
    PARSE_REGEX("parse_regex", "正则表达式解析"),
    PARSE_KEY_VALUE("parse_key_value", "键值对解析"),
    PARSE_GROK("parse_grok", "Grok 解析"),
    CUSTOM("custom", "自定义脚本");

    private final String value;
    private final String description;

    ParseMethod(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public static ParseMethod fromValue(String value) {
        for (ParseMethod method : values()) {
            if (method.value.equals(value)) {
                return method;
            }
        }
        // 默认返回 JSON 解析
        return PARSE_JSON;
    }
}
