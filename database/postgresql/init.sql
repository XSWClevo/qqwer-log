-- 日志分析系统 PostgreSQL 数据库初始化脚本

-- 创建数据库
CREATE DATABASE log_analysis;

-- 连接到数据库
\c log_analysis;

-- 用户表
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    full_name VARCHAR(100),
    role VARCHAR(20) NOT NULL,
    enabled BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_role ON users(role);

-- 插入默认管理员用户 (密码: admin123, BCrypt加密)
INSERT INTO users (username, password_hash, email, full_name, role, enabled)
VALUES ('admin', '$2a$10$9Sp6shUuNi3tQ2u5TkBmPu9/EI/RBDJONSjPuKvFtcYjbP5wkoV5O', 'admin@example.com', 'System Administrator', 'ADMIN', true);

-- 提取规则表
CREATE TABLE extraction_rules (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    rule_type VARCHAR(20) NOT NULL,
    pattern TEXT NOT NULL,
    field_mappings JSONB NOT NULL,
    priority INTEGER DEFAULT 0,
    enabled BOOLEAN DEFAULT true,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_extraction_rules_created_by ON extraction_rules(created_by);
CREATE INDEX idx_extraction_rules_enabled ON extraction_rules(enabled);
CREATE INDEX idx_extraction_rules_priority ON extraction_rules(priority DESC);

-- 告警规则表
CREATE TABLE alert_rules (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    condition JSONB NOT NULL,
    severity VARCHAR(20) NOT NULL,
    notification_channels JSONB,
    silence_period INTEGER DEFAULT 300,
    enabled BOOLEAN DEFAULT true,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_alert_rules_created_by ON alert_rules(created_by);
CREATE INDEX idx_alert_rules_enabled ON alert_rules(enabled);
CREATE INDEX idx_alert_rules_severity ON alert_rules(severity);


-- 告警事件表
CREATE TABLE alert_events (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT,
    rule_name VARCHAR(100),
    severity VARCHAR(20),
    message TEXT,
    log_data JSONB,
    triggered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    acknowledged BOOLEAN DEFAULT false,
    acknowledged_by BIGINT,
    acknowledged_at TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_alert_events_triggered_at ON alert_events(triggered_at);
CREATE INDEX idx_alert_events_rule_id ON alert_events(rule_id);
CREATE INDEX idx_alert_events_acknowledged_by ON alert_events(acknowledged_by);
CREATE INDEX idx_alert_events_severity ON alert_events(severity);
CREATE INDEX idx_alert_events_acknowledged ON alert_events(acknowledged);

-- 系统配置表
CREATE TABLE system_configs (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(100) UNIQUE NOT NULL,
    config_value TEXT NOT NULL,
    description TEXT,
    is_sensitive BOOLEAN DEFAULT false,
    updated_by BIGINT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_system_configs_updated_by ON system_configs(updated_by);
CREATE INDEX idx_system_configs_key ON system_configs(config_key);

-- 配置历史表
CREATE TABLE config_history (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    changed_by BIGINT,
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_config_history_key ON config_history(config_key);
CREATE INDEX idx_config_history_changed_at ON config_history(changed_at);
CREATE INDEX idx_config_history_changed_by ON config_history(changed_by);

-- 插入默认系统配置
INSERT INTO system_configs (config_key, config_value, description, is_sensitive) VALUES
('log.retention.days', '90', '日志保留天数', false),
('alert.default.silence.period', '300', '默认告警静默期（秒）', false),
('extraction.batch.size', '1000', '日志提取批量大小', false),
('stats.cache.ttl', '300', '统计结果缓存时间（秒）', false);

-- 创建更新时间自动更新触发器
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_extraction_rules_updated_at BEFORE UPDATE ON extraction_rules
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_alert_rules_updated_at BEFORE UPDATE ON alert_rules
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_system_configs_updated_at BEFORE UPDATE ON system_configs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- 授权
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO postgres;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO postgres;
