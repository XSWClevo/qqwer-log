package cn.mw.loganalysis.common.constants;

/**
 * 机器和部署相关常量
 */
public class MachineConstants {

    private MachineConstants() {
        throw new IllegalStateException("常量类不允许实例化");
    }

    /**
     * 默认操作系统
     */
    public static final String DEFAULT_OS = "linux";

    /**
     * 默认管理工具
     */
    public static final String DEFAULT_MANAGEMENT_TOOL = "systemctl";

    /**
     * 默认安装类型
     */
    public static final String DEFAULT_INSTALL_TYPE = "agent";

    /**
     * 默认 Vector 安装路径
     */
    public static final String DEFAULT_VECTOR_INSTALL_PATH = "/usr/local/bin/vector";

    /**
     * 默认 Vector 配置路径
     */
    public static final String DEFAULT_VECTOR_CONFIG_PATH = "/etc/vector/vector.yaml";

    /**
     * 指标缓存大小
     */
    public static final int METRICS_CACHE_SIZE = 30;

    /**
     * 离线阈值（分钟）
     */
    public static final int OFFLINE_THRESHOLD_MINUTES = 15;
}
