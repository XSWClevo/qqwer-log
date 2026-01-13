-- 用户操作日志表 (V12)
-- 用于记录系统中所有用户操作，支持安全审计、行为分析和问题追溯

-- 主表: 保留最近 6 个月的操作日志
CREATE TABLE IF NOT EXISTS user_operation_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(50),
    operation_type VARCHAR(50) NOT NULL,
    module VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100),
    resource_id VARCHAR(255),
    action VARCHAR(255) NOT NULL,
    request_method VARCHAR(10),
    request_url VARCHAR(500),
    request_params JSONB,
    response_status INTEGER,
    response_message TEXT,
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    execution_time INTEGER,
    is_success BOOLEAN DEFAULT true,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 归档表: 存储历史数据 (按年分区)
-- 注意: 分区表的主键必须包含分区键 created_at
CREATE TABLE IF NOT EXISTS user_operation_logs_archive (
    id BIGSERIAL,
    user_id BIGINT,
    username VARCHAR(50),
    operation_type VARCHAR(50) NOT NULL,
    module VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100),
    resource_id VARCHAR(255),
    action VARCHAR(255) NOT NULL,
    request_method VARCHAR(10),
    request_url VARCHAR(500),
    request_params JSONB,
    response_status INTEGER,
    response_message TEXT,
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    execution_time INTEGER,
    is_success BOOLEAN DEFAULT true,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, created_at)  -- 主键必须包含分区键
) PARTITION BY RANGE (created_at);

-- 创建 2026 年分区 (后续年份通过定时任务自动创建)
CREATE TABLE IF NOT EXISTS user_operation_logs_archive_2026
    PARTITION OF user_operation_logs_archive
    FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');

-- 索引: 针对常用查询场景优化
CREATE INDEX IF NOT EXISTS idx_user_operation_logs_user_id
    ON user_operation_logs(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_operation_logs_operation_type
    ON user_operation_logs(operation_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_operation_logs_module
    ON user_operation_logs(module, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_operation_logs_ip_address
    ON user_operation_logs(ip_address);

CREATE INDEX IF NOT EXISTS idx_user_operation_logs_is_success
    ON user_operation_logs(is_success, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_operation_logs_created_at
    ON user_operation_logs(created_at DESC);

-- 联合索引: 支持告警检测 (同一用户/IP 短时间内的失败次数)
CREATE INDEX IF NOT EXISTS idx_user_operation_logs_alert
    ON user_operation_logs(user_id, ip_address, is_success, created_at DESC);

-- 归档表索引 (同主表)
CREATE INDEX IF NOT EXISTS idx_user_operation_logs_archive_user_id
    ON user_operation_logs_archive(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_operation_logs_archive_created_at
    ON user_operation_logs_archive(created_at DESC);

-- 表注释
COMMENT ON TABLE user_operation_logs IS '用户操作日志表 (主表, 保留 6 个月数据)';
COMMENT ON TABLE user_operation_logs_archive IS '用户操作日志归档表 (历史数据, 按年分区)';

-- 字段注释
COMMENT ON COLUMN user_operation_logs.id IS '主键';
COMMENT ON COLUMN user_operation_logs.user_id IS '用户ID (关联 users 表)';
COMMENT ON COLUMN user_operation_logs.username IS '用户名 (冗余存储, 便于查询)';
COMMENT ON COLUMN user_operation_logs.operation_type IS '操作类型: CREATE/UPDATE/DELETE/QUERY/LOGIN/LOGOUT/EXPORT/IMPORT/EXECUTE/CONFIG';
COMMENT ON COLUMN user_operation_logs.module IS '操作模块: auth/stats/alert/vector/config/datasource/extraction/dashboard';
COMMENT ON COLUMN user_operation_logs.resource_type IS '资源类型: User/AlertRule/VectorConfig/Datasource 等';
COMMENT ON COLUMN user_operation_logs.resource_id IS '资源ID (支持字符串类型)';
COMMENT ON COLUMN user_operation_logs.action IS '具体操作: create_alert_rule/update_datasource/user_login 等';
COMMENT ON COLUMN user_operation_logs.request_method IS 'HTTP 方法: POST/GET/PUT/DELETE';
COMMENT ON COLUMN user_operation_logs.request_url IS '请求路径';
COMMENT ON COLUMN user_operation_logs.request_params IS '请求参数 (JSONB 格式, 已脱敏)';
COMMENT ON COLUMN user_operation_logs.response_status IS 'HTTP 响应状态码';
COMMENT ON COLUMN user_operation_logs.response_message IS '响应消息';
COMMENT ON COLUMN user_operation_logs.ip_address IS '客户端 IP 地址';
COMMENT ON COLUMN user_operation_logs.user_agent IS 'User-Agent 信息';
COMMENT ON COLUMN user_operation_logs.execution_time IS '执行耗时 (毫秒)';
COMMENT ON COLUMN user_operation_logs.is_success IS '是否成功';
COMMENT ON COLUMN user_operation_logs.error_message IS '错误信息 (失败时记录)';
COMMENT ON COLUMN user_operation_logs.created_at IS '创建时间';
