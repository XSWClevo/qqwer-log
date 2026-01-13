package cn.mw.loganalysis.common.enums;

import lombok.Getter;

/**
 * 机器状态枚举
 */
@Getter
public enum MachineStatus {
    ONLINE("online", "在线"),
    OFFLINE("offline", "离线"),
    UNKNOWN("unknown", "未知");

    private final String code;
    private final String description;

    MachineStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static MachineStatus fromCode(String code) {
        for (MachineStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return UNKNOWN;
    }
}
