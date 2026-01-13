package cn.mw.loganalysis.operationlog.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 操作动作枚举
 *
 * @author Claude
 * @since 2026-01-07
 */
@Getter
@AllArgsConstructor
public enum OperationAction {

    // ==================== 认证授权相关 ====================
    /**
     * 用户登录
     */
    USER_LOGIN("user_login", "用户登录"),

    /**
     * 用户登出
     */
    USER_LOGOUT("user_logout", "用户登出"),

    // ==================== 用户管理相关 ====================
    /**
     * 创建用户
     */
    CREATE_USER("create_user", "创建用户"),

    /**
     * 更新用户
     */
    UPDATE_USER("update_user", "更新用户"),

    /**
     * 删除用户
     */
    DELETE_USER("delete_user", "删除用户"),

    /**
     * 修改密码
     */
    CHANGE_PASSWORD("change_password", "修改密码"),

    // ==================== 告警规则相关 ====================
    /**
     * 创建告警规则
     */
    CREATE_ALERT_RULE("create_alert_rule", "创建告警规则"),

    /**
     * 更新告警规则
     */
    UPDATE_ALERT_RULE("update_alert_rule", "更新告警规则"),

    /**
     * 删除告警规则
     */
    DELETE_ALERT_RULE("delete_alert_rule", "删除告警规则"),

    /**
     * 切换告警规则状态
     */
    TOGGLE_ALERT_RULE_STATUS("toggle_alert_rule_status", "切换告警规则状态"),

    /**
     * 复制告警规则
     */
    DUPLICATE_ALERT_RULE("duplicate_alert_rule", "复制告警规则"),

    /**
     * 测试告警规则
     */
    TEST_ALERT_RULE("test_alert_rule", "测试告警规则"),

    // ==================== 数据源相关 ====================
    /**
     * 创建数据源
     */
    CREATE_DATASOURCE("create_datasource", "创建数据源"),

    /**
     * 更新数据源
     */
    UPDATE_DATASOURCE("update_datasource", "更新数据源"),

    /**
     * 删除数据源
     */
    DELETE_DATASOURCE("delete_datasource", "删除数据源"),

    /**
     * 测试数据源连接
     */
    TEST_DATASOURCE_CONNECTION("test_datasource_connection", "测试数据源连接"),

    // ==================== Vector 配置相关 ====================
    /**
     * 创建 Vector 配置
     */
    CREATE_VECTOR_CONFIG("create_vector_config", "创建Vector配置"),

    /**
     * 更新 Vector 配置
     */
    UPDATE_VECTOR_CONFIG("update_vector_config", "更新Vector配置"),

    /**
     * 删除 Vector 配置
     */
    DELETE_VECTOR_CONFIG("delete_vector_config", "删除Vector配置"),

    /**
     * 复制 Vector 配置
     */
    COPY_VECTOR_CONFIG("copy_vector_config", "复制Vector配置"),

    // ==================== Vector 机器相关 ====================
    /**
     * 添加 Vector 机器
     */
    ADD_VECTOR_MACHINE("add_vector_machine", "添加Vector机器"),

    /**
     * 更新 Vector 机器
     */
    UPDATE_VECTOR_MACHINE("update_vector_machine", "更新Vector机器"),

    /**
     * 删除 Vector 机器
     */
    DELETE_VECTOR_MACHINE("delete_vector_machine", "删除Vector机器"),

    /**
     * 更新 Vector 机器状态
     */
    UPDATE_VECTOR_MACHINE_STATUS("update_vector_machine_status", "更新Vector机器状态"),

    // ==================== 日志提取规则相关 ====================
    /**
     * 创建提取规则
     */
    CREATE_EXTRACTION_RULE("create_extraction_rule", "创建提取规则"),

    /**
     * 更新提取规则
     */
    UPDATE_EXTRACTION_RULE("update_extraction_rule", "更新提取规则"),

    /**
     * 删除提取规则
     */
    DELETE_EXTRACTION_RULE("delete_extraction_rule", "删除提取规则"),

    // ==================== 系统配置相关 ====================
    /**
     * 更新系统配置
     */
    UPDATE_SYSTEM_CONFIG("update_system_config", "更新系统配置");

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
    public static OperationAction fromCode(String code) {
        for (OperationAction action : values()) {
            if (action.getCode().equals(code)) {
                return action;
            }
        }
        throw new IllegalArgumentException("Unknown operation action code: " + code);
    }
}
