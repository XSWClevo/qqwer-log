-- 创建可信任日志源表
CREATE TABLE IF NOT EXISTS trusted_log_sources (
    id BIGSERIAL PRIMARY KEY,
    source_ip VARCHAR(45) NOT NULL UNIQUE,
    hostname VARCHAR(255),
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    first_seen_at TIMESTAMP,
    last_seen_at TIMESTAMP,
    trusted_at TIMESTAMP,
    trusted_by VARCHAR(100),
    log_count BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    remark TEXT
);

-- 创建索引
CREATE INDEX idx_trusted_log_sources_source_ip ON trusted_log_sources(source_ip);
CREATE INDEX idx_trusted_log_sources_status ON trusted_log_sources(status);
CREATE INDEX idx_trusted_log_sources_last_seen_at ON trusted_log_sources(last_seen_at DESC);

-- 添加注释
COMMENT ON TABLE trusted_log_sources IS '可信任日志源白名单表';
COMMENT ON COLUMN trusted_log_sources.source_ip IS '日志源IP地址';
COMMENT ON COLUMN trusted_log_sources.hostname IS '日志源主机名';
COMMENT ON COLUMN trusted_log_sources.description IS '描述信息';
COMMENT ON COLUMN trusted_log_sources.status IS '状态：trusted（信任）、blocked（拉黑）、pending（待审核）';
COMMENT ON COLUMN trusted_log_sources.first_seen_at IS '首次发现时间';
COMMENT ON COLUMN trusted_log_sources.last_seen_at IS '最后活跃时间';
COMMENT ON COLUMN trusted_log_sources.trusted_at IS '信任时间';
COMMENT ON COLUMN trusted_log_sources.trusted_by IS '信任操作人';
COMMENT ON COLUMN trusted_log_sources.log_count IS '日志数量统计';
COMMENT ON COLUMN trusted_log_sources.remark IS '备注';
