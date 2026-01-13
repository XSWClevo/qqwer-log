package cn.mw.loganalysis.operationlog.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 操作模块枚举
 *
 * @author Claude
 * @since 2026-01-07
 */
@Getter
@AllArgsConstructor
public enum OperationModule {

    /**
     * 认证授权模块
     */
    AUTH("auth", "认证授权"),

    /**
     * 用户管理模块
     */
    USER("user", "用户管理"),

    /**
     * 告警管理模块
     */
    ALERT("alert", "告警管理"),

    /**
     * 数据源管理模块
     */
    DATASOURCE("datasource", "数据源管理"),

    /**
     * Vector 配置模块
     */
    VECTOR("vector", "Vector配置"),

    /**
     * 日志提取规则模块
     */
    EXTRACTION("extraction", "日志提取规则"),

    /**
     * 系统配置模块
     */
    CONFIG("config", "系统配置"),

    /**
     * 统计查询模块
     */
    STATS("stats", "统计查询"),

    /**
     * 仪表盘模块
     */
    DASHBOARD("dashboard", "仪表盘");

    /**
     * 存储到数据库的值
     */
    @EnumValue
    private final String code;

    /**
     * 描述信息 (用于返回给前端)
     */
    @JsonValue
    private final String description;

    /**
     * 根据 code 获取枚举
     */
    public static OperationModule fromCode(String code) {
        for (OperationModule module : values()) {
            if (module.getCode().equals(code)) {
                return module;
            }
        }
        throw new IllegalArgumentException("Unknown operation module code: " + code);
    }
}
