package cn.mw.loganalysis.common.enums;

import lombok.Getter;

/**
 * 规则状态枚举
 */
@Getter
public enum RuleStatus {
    ENABLED("enabled", "启用"),
    DISABLED("disabled", "禁用");

    private final String code;
    private final String description;

    RuleStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static RuleStatus fromCode(String code) {
        for (RuleStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
