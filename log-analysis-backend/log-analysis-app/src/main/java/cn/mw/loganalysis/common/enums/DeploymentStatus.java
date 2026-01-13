package cn.mw.loganalysis.common.enums;

import lombok.Getter;

/**
 * 部署状态枚举
 */
@Getter
public enum DeploymentStatus {
    PENDING("pending", "待部署"),
    DEPLOYING("deploying", "部署中"),
    SUCCESS("success", "部署成功"),
    FAILED("failed", "部署失败");

    private final String code;
    private final String description;

    DeploymentStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static DeploymentStatus fromCode(String code) {
        for (DeploymentStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
