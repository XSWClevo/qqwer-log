package cn.mw.loganalysis.operationlog.annotation;

import cn.mw.loganalysis.operationlog.enums.OperationAction;
import cn.mw.loganalysis.operationlog.enums.OperationModule;
import cn.mw.loganalysis.operationlog.enums.OperationType;

import java.lang.annotation.*;

/**
 * 用户操作日志注解
 * <p>
 * 用于标记需要记录操作日志的方法
 * 通过 AOP 切面自动拦截并记录操作信息
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @PostMapping("/login")
 * @OperationLog(
 *     module = OperationModule.AUTH,
 *     operationType = OperationType.LOGIN,
 *     action = OperationAction.USER_LOGIN,
 *     resourceType = "User",
 *     sensitiveFields = {"password"}
 * )
 * public Result<LoginResponse> login(@RequestBody LoginRequest request) {
 *     return Result.success(authService.login(request));
 * }
 *
 * @PostMapping("/create")
 * @OperationLog(
 *     module = OperationModule.ALERT,
 *     operationType = OperationType.CREATE,
 *     action = OperationAction.CREATE_ALERT_RULE,
 *     resourceType = "AlertRule",
 *     resourceIdSpEL = "#result.data.id"  // SpEL 表达式获取返回的 ID
 * )
 * public Result<AlertRule> createRule(@RequestBody CreateRuleRequest request) {
 *     return Result.success(alertRuleService.createRule(request));
 * }
 * }</pre>
 *
 * @author Claude
 * @since 2026-01-07
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /**
     * 操作模块
     *
     * @return 模块枚举
     */
    OperationModule module();

    /**
     * 操作类型
     *
     * @return 操作类型枚举
     */
    OperationType operationType();

    /**
     * 具体操作
     *
     * @return 操作动作枚举
     */
    OperationAction action();

    /**
     * 资源类型
     * <p>
     * 示例: User, AlertRule, VectorConfig, Datasource
     * </p>
     *
     * @return 资源类型 (可选)
     */
    String resourceType() default "";

    /**
     * 资源 ID (支持 SpEL 表达式)
     * <p>
     * SpEL 表达式示例:
     * <ul>
     *   <li>#request.id - 获取请求参数中的 id</li>
     *   <li>#result.data.id - 获取返回结果中的 id</li>
     *   <li>#p0.id - 获取第一个参数的 id</li>
     * </ul>
     * </p>
     *
     * @return SpEL 表达式
     */
    String resourceIdSpEL() default "";

    /**
     * 是否记录请求参数
     * <p>
     * 默认记录, 可通过此参数禁用 (如查询接口参数过大)
     * </p>
     *
     * @return true 记录, false 不记录
     */
    boolean recordParams() default true;

    /**
     * 敏感字段 (需要脱敏)
     * <p>
     * 自动脱敏字段: password, passwordHash, token, secret, apiKey
     * 可通过此参数指定额外需要脱敏的字段
     * </p>
     *
     * @return 敏感字段名数组
     */
    String[] sensitiveFields() default {};

    /**
     * 采样率 (用于高频接口优化)
     * <p>
     * 取值范围: 0.0 - 1.0
     * <ul>
     *   <li>1.0: 记录所有请求 (默认)</li>
     *   <li>0.5: 记录 50% 的请求</li>
     *   <li>0.1: 记录 10% 的请求</li>
     * </ul>
     * </p>
     *
     * @return 采样率
     */
    double samplingRate() default 1.0;
}
