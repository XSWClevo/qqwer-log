package cn.mw.loganalysis.vector.constants;

/**
 * Vector 配置常量
 */
public final class VectorConfigConstants {

    private VectorConfigConstants() {
        throw new UnsupportedOperationException("常量类不能实例化");
    }

    // ==================== JSON 字段名 ====================

    // 通用字段
    public static final String FIELD_TYPE = "type";
    public static final String FIELD_INCLUDE = "include";
    public static final String FIELD_EXCLUDE = "exclude";
    public static final String FIELD_TOPICS = "topics";
    public static final String FIELD_BOOTSTRAP_SERVERS = "bootstrap_servers";
    public static final String FIELD_GROUP_ID = "group_id";
    public static final String FIELD_ADDRESS = "address";
    public static final String FIELD_PATH = "path";
    public static final String FIELD_TOPIC = "topic";
    public static final String FIELD_ENDPOINTS = "endpoints";
    public static final String FIELD_INDEX = "index";
    public static final String FIELD_ENDPOINT = "endpoint";
    public static final String FIELD_DATABASE = "database";
    public static final String FIELD_TABLE = "table";
    public static final String FIELD_TARGET = "target";
    public static final String FIELD_ENCODING = "encoding";
    public static final String FIELD_AUTH = "auth";
    public static final String FIELD_BATCH = "batch";
    public static final String FIELD_BUFFER = "buffer";

    // Source 特定字段
    public static final String FIELD_READ_FROM = "read_from";
    public static final String FIELD_SYSLOG_MODE = "syslog_mode";
    public static final String FIELD_SYSLOG_ADDRESS = "syslog_address";
    public static final String FIELD_SYSLOG_RECEIVE_ADDRESSES = "syslog_receive_addresses";
    public static final String FIELD_DEMO_FORMAT = "demo_format";
    public static final String FIELD_DEMO_INTERVAL = "demo_interval";
    public static final String FIELD_DEMO_COUNT = "demo_count";

    // Transform 特定字段
    public static final String FIELD_SOURCE = "source";
    public static final String FIELD_CONDITION = "condition";
    public static final String FIELD_PARSE_METHOD = "parse_method";
    public static final String FIELD_PARSED_FIELDS = "parsed_fields";
    public static final String FIELD_REGEX_PATTERN = "regex_pattern";
    public static final String FIELD_GROK_PATTERN = "grok_pattern";
    public static final String FIELD_VRL_SOURCE = "vrl_source";
    public static final String FIELD_FILTER_TYPE = "filter_type";
    public static final String FIELD_LEVELS = "levels";
    public static final String FIELD_FIELD_NAME = "field_name";
    public static final String FIELD_FIELD_VALUE = "field_value";

    // Remap 增强选项
    public static final String FIELD_GENERATE_UUID = "generate_uuid";
    public static final String FIELD_KEEP_RAW = "keep_raw";
    public static final String FIELD_EXTRACT_SOURCE_IP = "extract_source_ip";
    public static final String FIELD_CONVERT_PROCID = "convert_procid";
    public static final String FIELD_ADD_FIELDS = "add_fields";
    public static final String FIELD_REMOVE_FIELDS = "remove_fields";

    // ClickHouse 特定字段
    public static final String FIELD_CLICKHOUSE_FORMAT = "clickhouse_format";
    public static final String FIELD_CLICKHOUSE_COMPRESSION = "clickhouse_compression";
    public static final String FIELD_CLICKHOUSE_SKIP_UNKNOWN = "clickhouse_skip_unknown";
    public static final String FIELD_CLICKHOUSE_TIMESTAMP_FORMAT = "clickhouse_timestamp_format";
    public static final String FIELD_CLICKHOUSE_BATCH_MAX_BYTES = "clickhouse_batch_max_bytes";
    public static final String FIELD_CLICKHOUSE_BATCH_TIMEOUT = "clickhouse_batch_timeout";
    public static final String FIELD_CLICKHOUSE_BUFFER_TYPE = "clickhouse_buffer_type";
    public static final String FIELD_CLICKHOUSE_BUFFER_MAX_EVENTS = "clickhouse_buffer_max_events";
    public static final String FIELD_CLICKHOUSE_USER = "clickhouse_user";
    public static final String FIELD_CLICKHOUSE_PASSWORD = "clickhouse_password";

    // Elasticsearch 特定字段
    public static final String FIELD_AUTH_USER = "auth_user";
    public static final String FIELD_AUTH_PASSWORD = "auth_password";

    // 字段属性
    public static final String FIELD_NAME = "name";
    public static final String FIELD_NEW_NAME = "newName";
    public static final String FIELD_DELETED = "deleted";
    public static final String FIELD_KEY = "key";
    public static final String FIELD_VALUE = "value";

    // ==================== 默认值 ====================

    // Syslog 默认值
    public static final String DEFAULT_SYSLOG_MODE = "tcp";
    public static final String DEFAULT_SYSLOG_ADDRESS = "0.0.0.0:514";
    public static final String DEFAULT_SYSLOG_FORMAT = "syslog";

    // Console 默认值
    public static final String DEFAULT_CONSOLE_TARGET = "stdout";
    public static final String DEFAULT_CONSOLE_ENCODING = "json";

    // ClickHouse 默认值
    public static final String DEFAULT_CLICKHOUSE_TIMESTAMP_FORMAT = "unix";
    public static final String DEFAULT_CLICKHOUSE_BUFFER_TYPE = "memory";
    public static final int DEFAULT_CLICKHOUSE_BATCH_MAX_BYTES = 10000000;
    public static final int DEFAULT_CLICKHOUSE_BATCH_TIMEOUT = 10;
    public static final int DEFAULT_CLICKHOUSE_BUFFER_MAX_EVENTS = 500000;

    // 认证策略
    public static final String AUTH_STRATEGY_BASIC = "basic";

    // 编码格式
    public static final String ENCODING_CODEC = "codec";
    public static final String ENCODING_TIMESTAMP_FORMAT = "timestamp_format";

    // 批处理配置
    public static final String BATCH_MAX_BYTES = "max_bytes";
    public static final String BATCH_TIMEOUT_SECS = "timeout_secs";

    // 缓冲配置
    public static final String BUFFER_TYPE = "type";
    public static final String BUFFER_MAX_EVENTS = "max_events";

    // 认证配置
    public static final String AUTH_STRATEGY = "strategy";
    public static final String AUTH_USER = "user";
    public static final String AUTH_PASSWORD = "password";

    // 压缩类型
    public static final String COMPRESSION_NONE = "none";

    // ==================== VRL 脚本模板 ====================

    // Syslog 预解析
    public static final String VRL_SYSLOG_PRE_PARSE =
        "# Syslog 预解析\n" +
        "raw_message = .message\n" +
        "target_message = raw_message\n" +
        "syslog_result = parse_syslog(.message) ?? null\n" +
        "if syslog_result != null {\n" +
        "  . = merge!(., syslog_result)\n" +
        "  target_message = syslog_result.message\n" +
        "}";

    // 字段合并
    public static final String VRL_MERGE_PARSED =
        "if parsed != null {\n" +
        "  . = merge!(., parsed)\n" +
        "}";
}
