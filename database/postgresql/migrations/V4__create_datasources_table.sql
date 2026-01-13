-- 数据源管理表
-- 用于统一管理所有日志数据源的连接信息

CREATE TABLE IF NOT EXISTS datasources (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    type VARCHAR(50) NOT NULL,
    host VARCHAR(255) NOT NULL,
    port INTEGER NOT NULL,
    database_name VARCHAR(100),
    username VARCHAR(100),
    password VARCHAR(255),
    ssl_enabled BOOLEAN DEFAULT FALSE,
    connection_params TEXT,
    description TEXT,
    status VARCHAR(20) DEFAULT 'active',
    last_check_time TIMESTAMP,
    last_check_status VARCHAR(20),
    last_check_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_datasources_type ON datasources(type);
CREATE INDEX IF NOT EXISTS idx_datasources_status ON datasources(status);
CREATE INDEX IF NOT EXISTS idx_datasources_created_at ON datasources(created_at);

-- 添加注释
COMMENT ON TABLE datasources IS '数据源管理表';
COMMENT ON COLUMN datasources.id IS '数据源ID（UUID）';
COMMENT ON COLUMN datasources.name IS '数据源名称（唯一）';
COMMENT ON COLUMN datasources.type IS '数据源类型: clickhouse, elasticsearch, postgresql, mysql, loki';
COMMENT ON COLUMN datasources.host IS '主机地址';
COMMENT ON COLUMN datasources.port IS '端口号';
COMMENT ON COLUMN datasources.database_name IS '数据库名称';
COMMENT ON COLUMN datasources.username IS '用户名';
COMMENT ON COLUMN datasources.password IS '密码（加密存储）';
COMMENT ON COLUMN datasources.ssl_enabled IS '是否启用 SSL';
COMMENT ON COLUMN datasources.connection_params IS '额外连接参数（JSON 格式）';
COMMENT ON COLUMN datasources.description IS '数据源描述';
COMMENT ON COLUMN datasources.status IS '状态: active-活跃, inactive-停用, error-错误';
COMMENT ON COLUMN datasources.last_check_time IS '最后健康检查时间';
COMMENT ON COLUMN datasources.last_check_status IS '最后检查状态: success-成功, failed-失败';
COMMENT ON COLUMN datasources.last_check_message IS '最后检查消息';
COMMENT ON COLUMN datasources.created_at IS '创建时间';
COMMENT ON COLUMN datasources.updated_at IS '更新时间';
COMMENT ON COLUMN datasources.created_by IS '创建人ID';
COMMENT ON COLUMN datasources.updated_by IS '更新人ID';
