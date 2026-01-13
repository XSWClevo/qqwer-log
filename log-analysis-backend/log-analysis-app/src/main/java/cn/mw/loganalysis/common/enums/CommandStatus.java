package cn.mw.loganalysis.common.enums;

import lombok.Getter;

/**
 * 命令状态枚举
 */
@Getter
public enum CommandStatus {
    PENDING("pending", "待执行"),
    EXECUTING("executing", "执行中"),
    SUCCESS("success", "执行成功"),
    FAILED("failed", "执行失败");

    private final String code;
    private final String description;

    CommandStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static CommandStatus fromCode(String code) {
        for (CommandStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
