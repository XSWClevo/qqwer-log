--liquibase formatted sql

--changeset codex:20260526-01-create-trusted-log-sources
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

CREATE INDEX IF NOT EXISTS idx_trusted_log_sources_source_ip
    ON trusted_log_sources(source_ip);
CREATE INDEX IF NOT EXISTS idx_trusted_log_sources_status
    ON trusted_log_sources(status);
CREATE INDEX IF NOT EXISTS idx_trusted_log_sources_last_seen_at
    ON trusted_log_sources(last_seen_at DESC);

--changeset codex:20260526-02-seed-attack-demo-log-sources
INSERT INTO trusted_log_sources (
    source_ip,
    hostname,
    description,
    status,
    first_seen_at,
    last_seen_at,
    trusted_at,
    trusted_by,
    log_count,
    remark
) VALUES
    ('10.77.10.11', 'attack-alias-red-01', '攻击模拟日志源 IP 别名', 'trusted', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 0, '用于攻击识别和链路分析页面展示'),
    ('10.77.10.12', 'attack-alias-red-02', '攻击模拟日志源 IP 别名', 'trusted', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 0, '用于攻击识别和链路分析页面展示'),
    ('10.77.10.13', 'attack-alias-red-03', '攻击模拟日志源 IP 别名', 'trusted', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 0, '用于攻击识别和链路分析页面展示'),
    ('10.77.10.14', 'attack-alias-red-04', '攻击模拟日志源 IP 别名', 'trusted', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 0, '用于攻击识别和链路分析页面展示'),
    ('10.77.10.15', 'attack-alias-red-05', '攻击模拟日志源 IP 别名', 'trusted', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 0, '用于攻击识别和链路分析页面展示')
ON CONFLICT (source_ip) DO UPDATE SET
    hostname = EXCLUDED.hostname,
    description = EXCLUDED.description,
    status = 'trusted',
    trusted_at = CURRENT_TIMESTAMP,
    trusted_by = 'system',
    last_seen_at = COALESCE(trusted_log_sources.last_seen_at, CURRENT_TIMESTAMP),
    updated_at = CURRENT_TIMESTAMP,
    remark = EXCLUDED.remark;
