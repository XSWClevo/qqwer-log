--liquibase formatted sql

--changeset codex:20260522-01-create-attack-log-datasets
CREATE TABLE IF NOT EXISTS attack_log_datasets (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    datasource_type VARCHAR(32) NOT NULL,
    datasource_id VARCHAR(64),
    database_name VARCHAR(128),
    table_name VARCHAR(255),
    index_name VARCHAR(255),
    field_mapping JSONB NOT NULL DEFAULT '{}'::jsonb,
    capabilities JSONB NOT NULL DEFAULT '{}'::jsonb,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    scan_cursor_timestamp TIMESTAMP,
    scan_cursor_fingerprint VARCHAR(128),
    batch_size INTEGER NOT NULL DEFAULT 500,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

--changeset codex:20260522-02-create-attack-log-dataset-indexes
CREATE INDEX IF NOT EXISTS idx_attack_log_datasets_enabled
    ON attack_log_datasets(enabled);
CREATE INDEX IF NOT EXISTS idx_attack_log_datasets_datasource
    ON attack_log_datasets(datasource_type, datasource_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_attack_log_datasets_target
    ON attack_log_datasets(datasource_type, COALESCE(datasource_id, ''), COALESCE(database_name, ''), COALESCE(table_name, ''), COALESCE(index_name, ''));

--changeset codex:20260522-03-create-attack-detection-rules
CREATE TABLE IF NOT EXISTS attack_detection_rules (
    id BIGSERIAL PRIMARY KEY,
    rule_id VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    attack_type VARCHAR(64) NOT NULL,
    attack_sub_type VARCHAR(64) DEFAULT '',
    severity VARCHAR(32) NOT NULL DEFAULT 'medium',
    confidence NUMERIC(5, 2) NOT NULL DEFAULT 0.80,
    required_fields JSONB NOT NULL DEFAULT '[]'::jsonb,
    datasource_types JSONB NOT NULL DEFAULT '[]'::jsonb,
    message_patterns JSONB NOT NULL DEFAULT '[]'::jsonb,
    raw_patterns JSONB NOT NULL DEFAULT '[]'::jsonb,
    keywords JSONB NOT NULL DEFAULT '[]'::jsonb,
    reason_template TEXT,
    mitre_tactic VARCHAR(128) DEFAULT '',
    mitre_technique VARCHAR(128) DEFAULT '',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    priority INTEGER NOT NULL DEFAULT 100,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

--changeset codex:20260522-04-create-attack-detection-rule-indexes
CREATE INDEX IF NOT EXISTS idx_attack_detection_rules_enabled
    ON attack_detection_rules(enabled);
CREATE INDEX IF NOT EXISTS idx_attack_detection_rules_type
    ON attack_detection_rules(attack_type, attack_sub_type);
CREATE INDEX IF NOT EXISTS idx_attack_detection_rules_priority
    ON attack_detection_rules(priority, id);

--changeset codex:20260522-05-seed-basic-attack-rules
INSERT INTO attack_detection_rules (
    rule_id, name, description, attack_type, attack_sub_type, severity, confidence,
    required_fields, datasource_types, message_patterns, keywords, reason_template,
    mitre_tactic, mitre_technique, priority
) VALUES
(
    'auth_ssh_failed_login_001',
    'SSH 登录失败',
    '识别 SSH failed password 或 invalid user 日志，作为暴力破解的基础信号。',
    'authentication_attack',
    'ssh_failed_login',
    'medium',
    0.80,
    '["message"]'::jsonb,
    '["clickhouse", "elasticsearch"]'::jsonb,
    '["(?i)Failed password", "(?i)invalid user"]'::jsonb,
    '[]'::jsonb,
    '日志内容命中 SSH 登录失败特征',
    'Credential Access',
    'T1110 Brute Force',
    10
),
(
    'web_sqli_pattern_001',
    'Web SQL 注入特征',
    '识别常见 SQL 注入 payload。',
    'web_attack',
    'sql_injection',
    'high',
    0.88,
    '["message"]'::jsonb,
    '["clickhouse", "elasticsearch"]'::jsonb,
    '["(?i)(union\\s+select|or\\s+1\\s*=\\s*1|sleep\\s*\\(|benchmark\\s*\\(|information_schema)"]'::jsonb,
    '[]'::jsonb,
    '日志内容命中 SQL 注入特征',
    'Initial Access',
    'T1190 Exploit Public-Facing Application',
    20
),
(
    'web_path_traversal_001',
    '路径穿越特征',
    '识别 ../、/etc/passwd 等路径穿越特征。',
    'web_attack',
    'path_traversal',
    'high',
    0.86,
    '["message"]'::jsonb,
    '["clickhouse", "elasticsearch"]'::jsonb,
    '["(?i)(\\.\\./|\\.\\.\\\\|/etc/passwd|/proc/self/environ)"]'::jsonb,
    '[]'::jsonb,
    '日志内容命中路径穿越特征',
    'Initial Access',
    'T1190 Exploit Public-Facing Application',
    30
),
(
    'web_xss_pattern_001',
    'Web XSS 特征',
    '识别常见 XSS payload。',
    'web_attack',
    'xss',
    'medium',
    0.82,
    '["message"]'::jsonb,
    '["clickhouse", "elasticsearch"]'::jsonb,
    '["(?i)(<script|javascript:|onerror\\s*=|onload\\s*=)"]'::jsonb,
    '[]'::jsonb,
    '日志内容命中 XSS 特征',
    'Initial Access',
    'T1190 Exploit Public-Facing Application',
    40
),
(
    'command_execution_001',
    '可疑命令执行',
    '识别反弹 shell、下载执行等命令执行特征。',
    'command_execution',
    'suspicious_shell',
    'critical',
    0.90,
    '["message"]'::jsonb,
    '["clickhouse", "elasticsearch"]'::jsonb,
    '["(?i)(bash\\s+-i|/bin/sh|nc\\s+-e|curl\\s+.*\\|\\s*sh|wget\\s+.*\\|\\s*sh)"]'::jsonb,
    '[]'::jsonb,
    '日志内容命中可疑命令执行特征',
    'Execution',
    'T1059 Command and Scripting Interpreter',
    50
)
ON CONFLICT (rule_id) DO NOTHING;
