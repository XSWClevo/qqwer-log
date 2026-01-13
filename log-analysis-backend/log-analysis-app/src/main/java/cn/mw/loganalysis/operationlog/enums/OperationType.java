package cn.mw.loganalysis.operationlog.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 操作类型枚举
 *
 * @author Claude
 * @since 2026-01-07
 */
@Getter
@AllArgsConstructor
public enum OperationType {

    /**
     * 创建操作
     */
    CREATE("CREATE", "创建"),

    /**
     * 更新操作
     */
    UPDATE("UPDATE", "更新"),

    /**
     * 删除操作
     */
    DELETE("DELETE", "删除"),

    /**
     * 查询操作
     */
    QUERY("QUERY", "查询"),

    /**
     * 登录操作
     */
    LOGIN("LOGIN", "登录"),

    /**
     * 登出操作
     */
    LOGOUT("LOGOUT", "登出"),

    /**
     * 导出操作
     */
    EXPORT("EXPORT", "导出"),

    /**
     * 导入操作
     */
    IMPORT("IMPORT", "导入"),

    /**
     * 执行操作
     */
    EXECUTE("EXECUTE", "执行"),

    /**
     * 配置操作
     */
    CONFIG("CONFIG", "配置");

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
    public static OperationType fromCode(String code) {
        for (OperationType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown operation type code: " + code);
    }
}
