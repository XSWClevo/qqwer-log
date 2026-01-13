package cn.mw.loganalysis.common.constants;

/**
 * 告警相关常量
 */
public class AlertConstants {

    private AlertConstants() {
        throw new IllegalStateException("常量类不允许实例化");
    }

    /**
     * 默认静默期（秒）
     */
    public static final int DEFAULT_SILENCE_SECONDS = 300;

    /**
     * 告警执行器线程池大小
     */
    public static final int EXECUTOR_POOL_SIZE = 10;

    /**
     * 默认角色
     */
    public static final String DEFAULT_ROLE = "VIEWER";

    /**
     * 角色常量
     */
    public static final class Role {
        public static final String ADMIN = "ADMIN";
        public static final String ANALYST = "ANALYST";
        public static final String VIEWER = "VIEWER";
        public static final String READ = "READ";

        private Role() {}
    }
}
