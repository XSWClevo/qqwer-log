package cn.mw.loganalysis.common.constants;

/**
 * 认证相关常量
 */
public class AuthConstants {

    private AuthConstants() {
        throw new IllegalStateException("常量类不允许实例化");
    }

    /**
     * Redis Session Key 前缀
     */
    public static final String SESSION_KEY_PREFIX = "session:";

    /**
     * Session 过期时间（天）
     */
    public static final long SESSION_TTL_DAYS = 7;

    /**
     * Token 类型
     */
    public static final String TOKEN_TYPE_BEARER = "Bearer";
}
