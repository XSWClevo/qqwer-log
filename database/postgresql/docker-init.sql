\set ON_ERROR_STOP on

-- PostgreSQL Docker 初始化总脚本
-- 说明：
-- 1. 本脚本假定由 postgres 容器在 POSTGRES_DB 对应的数据库中执行
-- 2. 不再在脚本内 CREATE DATABASE，避免和 docker-entrypoint 的初始化流程冲突
-- 3. 该脚本合并了当前后端实际依赖的 PostgreSQL 元数据表

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- 基础认证与配置
-- ============================================================================

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    full_name VARCHAR(100),
    role VARCHAR(20) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);

INSERT INTO users (username, password_hash, email, full_name, role, enabled)
VALUES (
    'admin',
    '$2a$10$9Sp6shUuNi3tQ2u5TkBmPu9/EI/RBDJONSjPuKvFtcYjbP5wkoV5O',
    'admin@example.com',
    'System Administrator',
    'ADMIN',
    TRUE
)
ON CONFLICT (username) DO NOTHING;

CREATE TABLE IF NOT EXISTS system_config (
    id VARCHAR(32) PRIMARY KEY,
    config_key VARCHAR(255) NOT NULL,
    config_value TEXT NOT NULL,
    config_type VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (config_key, config_type)
);

CREATE TABLE IF NOT EXISTS config_history (
    id BIGSERIAL PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    changed_by BIGINT,
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_config_history_key ON config_history(config_key);
CREATE INDEX IF NOT EXISTS idx_config_history_changed_at ON config_history(changed_at);
CREATE INDEX IF NOT EXISTS idx_config_history_changed_by ON config_history(changed_by);

INSERT INTO system_config (id, config_key, config_value, config_type, description) VALUES
('ch_engine', 'ddl.engine', 'MergeTree', 'clickhouse', '表引擎'),
('ch_partition', 'ddl.partition_by', 'toYYYYMM(timestamp)', 'clickhouse', '分区策略'),
('ch_order', 'ddl.order_by', 'timestamp,hostname', 'clickhouse', '排序键'),
('ch_ttl', 'ddl.ttl_days', '30', 'clickhouse', '数据保留期（天）'),
('ch_compression', 'ddl.compression', 'LZ4', 'clickhouse', '压缩编码'),
('ch_keep_raw', 'ddl.keep_raw', 'true', 'clickhouse', '是否保留原始日志'),
('ch_indexes', 'ddl.indexes', 'timestamp:minmax,hostname:set', 'clickhouse', '索引配置'),
('pg_pk_type', 'ddl.primary_key_type', 'UUID', 'postgresql', '主键类型'),
('pg_indexes', 'ddl.indexes', 'timestamp:btree,hostname:btree', 'postgresql', '索引配置'),
('es_shards', 'ddl.number_of_shards', '3', 'elasticsearch', '分片数'),
('es_replicas', 'ddl.number_of_replicas', '1', 'elasticsearch', '副本数'),
('es_analyzer', 'ddl.analyzer', 'standard', 'elasticsearch', '分词器'),
('es_ilm_days', 'ddl.ilm_retention_days', '30', 'elasticsearch', 'ILM保留期（天）')
ON CONFLICT (config_key, config_type) DO NOTHING;

-- ============================================================================
-- 提取、告警、字段配置
-- ============================================================================

CREATE TABLE IF NOT EXISTS extraction_rules (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    rule_type VARCHAR(20) NOT NULL,
    pattern TEXT NOT NULL,
    field_mappings JSONB NOT NULL,
    priority INTEGER DEFAULT 0,
    enabled BOOLEAN DEFAULT TRUE,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_extraction_rules_created_by ON extraction_rules(created_by);
CREATE INDEX IF NOT EXISTS idx_extraction_rules_enabled ON extraction_rules(enabled);
CREATE INDEX IF NOT EXISTS idx_extraction_rules_priority ON extraction_rules(priority DESC);

CREATE TABLE IF NOT EXISTS alert_rules (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    rule_type VARCHAR(50) DEFAULT 'aggregation',
    scope_type VARCHAR(30) DEFAULT 'all',
    category_codes JSONB DEFAULT '[]'::jsonb,
    datasource_ids JSONB DEFAULT '[]'::jsonb,
    table_names JSONB DEFAULT '[]'::jsonb,
    condition JSONB NOT NULL,
    eval_every VARCHAR(20) DEFAULT '1m',
    consecutive_hits INTEGER DEFAULT 1,
    severity VARCHAR(50) NOT NULL,
    notification_channels JSONB,
    dedup_key_fields JSONB DEFAULT '[]'::jsonb,
    message_template TEXT,
    silence_period INTEGER DEFAULT 300,
    enabled BOOLEAN DEFAULT TRUE,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_alert_rules_created_by ON alert_rules(created_by);
CREATE INDEX IF NOT EXISTS idx_alert_rules_enabled ON alert_rules(enabled);
CREATE INDEX IF NOT EXISTS idx_alert_rules_severity ON alert_rules(severity);
CREATE INDEX IF NOT EXISTS idx_alert_rules_rule_type ON alert_rules(rule_type);
CREATE INDEX IF NOT EXISTS idx_alert_rules_scope_type ON alert_rules(scope_type);

CREATE TABLE IF NOT EXISTS log_category_registry (
    id BIGSERIAL PRIMARY KEY,
    category_code VARCHAR(50) NOT NULL,
    category_name VARCHAR(100) NOT NULL,
    datasource_id VARCHAR(36),
    table_name VARCHAR(255) NOT NULL,
    database_name VARCHAR(100),
    time_field VARCHAR(50) DEFAULT 'timestamp',
    message_field VARCHAR(50) DEFAULT 'message',
    raw_field VARCHAR(50) DEFAULT 'raw',
    severity_field VARCHAR(50) DEFAULT 'severity',
    source_ip_field VARCHAR(50) DEFAULT 'source_ip',
    appname_field VARCHAR(50) DEFAULT 'appname',
    hostname_field VARCHAR(50) DEFAULT 'hostname',
    extra_mapping JSONB DEFAULT '{}'::jsonb,
    enabled BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 100,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_log_category_registry_category_code
    ON log_category_registry(category_code);
CREATE INDEX IF NOT EXISTS idx_log_category_registry_datasource_id
    ON log_category_registry(datasource_id);
CREATE INDEX IF NOT EXISTS idx_log_category_registry_enabled
    ON log_category_registry(enabled);
CREATE UNIQUE INDEX IF NOT EXISTS uk_log_category_registry_unique_target
    ON log_category_registry(category_code, datasource_id, table_name);

CREATE TABLE IF NOT EXISTS alert_events (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT,
    rule_name VARCHAR(255),
    severity VARCHAR(50),
    message TEXT,
    log_data JSONB,
    triggered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    acknowledged BOOLEAN DEFAULT FALSE,
    acknowledged_by BIGINT,
    acknowledged_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_alert_events_triggered_at ON alert_events(triggered_at);
CREATE INDEX IF NOT EXISTS idx_alert_events_rule_id ON alert_events(rule_id);
CREATE INDEX IF NOT EXISTS idx_alert_events_acknowledged_by ON alert_events(acknowledged_by);
CREATE INDEX IF NOT EXISTS idx_alert_events_severity ON alert_events(severity);
CREATE INDEX IF NOT EXISTS idx_alert_events_acknowledged ON alert_events(acknowledged);

CREATE TABLE IF NOT EXISTS alert_notifications (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL,
    channel VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    message TEXT,
    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_alert_notifications_event_id ON alert_notifications(event_id);
CREATE INDEX IF NOT EXISTS idx_alert_notifications_sent_at ON alert_notifications(sent_at DESC);

CREATE TABLE IF NOT EXISTS user_field_config (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    config_type VARCHAR(50) NOT NULL,
    selected_fields JSONB NOT NULL DEFAULT '[]'::jsonb,
    field_order JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (username, config_type)
);

CREATE INDEX IF NOT EXISTS idx_user_field_config_username ON user_field_config(username);
CREATE INDEX IF NOT EXISTS idx_user_field_config_type ON user_field_config(config_type);

-- ============================================================================
-- 数据源、可信日志源、操作审计
-- ============================================================================

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

CREATE INDEX IF NOT EXISTS idx_datasources_type ON datasources(type);
CREATE INDEX IF NOT EXISTS idx_datasources_status ON datasources(status);
CREATE INDEX IF NOT EXISTS idx_datasources_created_at ON datasources(created_at);

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

CREATE INDEX IF NOT EXISTS idx_trusted_log_sources_source_ip ON trusted_log_sources(source_ip);
CREATE INDEX IF NOT EXISTS idx_trusted_log_sources_status ON trusted_log_sources(status);
CREATE INDEX IF NOT EXISTS idx_trusted_log_sources_last_seen_at ON trusted_log_sources(last_seen_at DESC);

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
    is_success BOOLEAN DEFAULT TRUE,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

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
CREATE INDEX IF NOT EXISTS idx_user_operation_logs_alert
    ON user_operation_logs(user_id, ip_address, is_success, created_at DESC);

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
    is_success BOOLEAN DEFAULT TRUE,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE IF NOT EXISTS user_operation_logs_archive_2026
    PARTITION OF user_operation_logs_archive
    FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');

CREATE TABLE IF NOT EXISTS user_operation_logs_archive_2027
    PARTITION OF user_operation_logs_archive
    FOR VALUES FROM ('2027-01-01') TO ('2028-01-01');

CREATE TABLE IF NOT EXISTS user_operation_logs_archive_2028
    PARTITION OF user_operation_logs_archive
    FOR VALUES FROM ('2028-01-01') TO ('2029-01-01');

CREATE INDEX IF NOT EXISTS idx_user_operation_logs_archive_user_id
    ON user_operation_logs_archive(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_user_operation_logs_archive_created_at
    ON user_operation_logs_archive(created_at DESC);

-- ============================================================================
-- 智能助手会话历史
-- ============================================================================

CREATE TABLE IF NOT EXISTS agent_conversations (
    id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    preview TEXT,
    datasource_id VARCHAR(36),
    datasource_name VARCHAR(255),
    datasource_type VARCHAR(50),
    message_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_message_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_conversations_user_last_message
    ON agent_conversations(user_id, last_message_at DESC);
CREATE INDEX IF NOT EXISTS idx_agent_conversations_user_updated_at
    ON agent_conversations(user_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS agent_conversation_messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL REFERENCES agent_conversations(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    content TEXT,
    tool_calls_json TEXT,
    result_json TEXT,
    suggestions_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_conversation_messages_conversation_time
    ON agent_conversation_messages(conversation_id, created_at ASC, id ASC);

-- ============================================================================
-- Vector 元数据
-- ============================================================================

CREATE TABLE IF NOT EXISTS vector_machines (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::VARCHAR,
    name VARCHAR(100) NOT NULL,
    hostname VARCHAR(255) NOT NULL,
    ip_address VARCHAR(50) NOT NULL,
    ssh_port INTEGER DEFAULT 22,
    ssh_user VARCHAR(50) DEFAULT 'root',
    ssh_key_path VARCHAR(500),
    agent_token VARCHAR(100),
    os_type VARCHAR(50) DEFAULT 'linux',
    status VARCHAR(20) DEFAULT 'offline',
    vector_version VARCHAR(50),
    agent_version VARCHAR(50),
    vector_install_path VARCHAR(500) DEFAULT '/usr/local/bin/vector',
    vector_config_path VARCHAR(500) DEFAULT '/etc/vector/vector.yaml',
    management_method VARCHAR(20) DEFAULT 'systemctl',
    last_heartbeat TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    CONSTRAINT uk_vector_machines_hostname UNIQUE (hostname),
    CONSTRAINT uk_vector_machines_ip_address UNIQUE (ip_address)
);

CREATE INDEX IF NOT EXISTS idx_vector_machines_agent_token ON vector_machines(agent_token);
CREATE INDEX IF NOT EXISTS idx_vector_machines_status ON vector_machines(status);
CREATE INDEX IF NOT EXISTS idx_vector_machines_last_heartbeat ON vector_machines(last_heartbeat DESC);

CREATE TABLE IF NOT EXISTS vector_configs (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::VARCHAR,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    content TEXT NOT NULL,
    version INTEGER DEFAULT 1,
    is_template BOOLEAN DEFAULT FALSE,
    parent_config_id VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    CONSTRAINT uk_vector_configs_name_version UNIQUE (name, version)
);

CREATE TABLE IF NOT EXISTS vector_service_operations (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::VARCHAR,
    machine_id VARCHAR(36) NOT NULL,
    operation_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) DEFAULT 'pending',
    result TEXT,
    error_message TEXT,
    executed_by VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_vector_service_operations_machine_id
    ON vector_service_operations(machine_id);
CREATE INDEX IF NOT EXISTS idx_vector_service_operations_status
    ON vector_service_operations(status);

CREATE TABLE IF NOT EXISTS vector_pipeline_metrics (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::VARCHAR,
    machine_id VARCHAR(36) NOT NULL,
    source_name VARCHAR(100),
    transform_name VARCHAR(100),
    sink_name VARCHAR(100),
    events_in BIGINT DEFAULT 0,
    events_out BIGINT DEFAULT 0,
    bytes_in BIGINT DEFAULT 0,
    bytes_out BIGINT DEFAULT 0,
    errors INTEGER DEFAULT 0,
    latency_ms DECIMAL(10,2),
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_vpm_machine_time
    ON vector_pipeline_metrics(machine_id, recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_vpm_source
    ON vector_pipeline_metrics(source_name, recorded_at DESC);

CREATE TABLE IF NOT EXISTS vector_health_checks (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::VARCHAR,
    machine_id VARCHAR(36) NOT NULL,
    check_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) DEFAULT 'healthy',
    details JSONB,
    checked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_vhc_machine_time
    ON vector_health_checks(machine_id, checked_at DESC);
CREATE INDEX IF NOT EXISTS idx_vhc_type_status
    ON vector_health_checks(check_type, status);

CREATE TABLE IF NOT EXISTS vector_config_components (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::VARCHAR,
    name VARCHAR(100) NOT NULL,
    component_type VARCHAR(20) NOT NULL,
    vector_type VARCHAR(50) NOT NULL,
    config_yaml TEXT NOT NULL,
    visual_data TEXT,
    description TEXT,
    is_template BOOLEAN DEFAULT TRUE,
    queryable BOOLEAN DEFAULT FALSE,
    display_name VARCHAR(100),
    datasource_id VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    CONSTRAINT uk_vector_config_components_name UNIQUE (name)
);

CREATE INDEX IF NOT EXISTS idx_vector_config_components_datasource_id
    ON vector_config_components(datasource_id);
CREATE INDEX IF NOT EXISTS idx_vector_config_components_queryable
    ON vector_config_components(component_type, queryable)
    WHERE queryable = TRUE;

CREATE TABLE IF NOT EXISTS vector_visual_configs (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::VARCHAR,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    format VARCHAR(20) DEFAULT 'namespace_yaml',
    flow_data TEXT DEFAULT '{}',
    content TEXT DEFAULT '',
    node_count INTEGER DEFAULT 0,
    pipeline_name VARCHAR(100),
    deploy_mode VARCHAR(20) DEFAULT 'pipeline',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36)
);

CREATE TABLE IF NOT EXISTS vector_shared_components (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    component_type VARCHAR(20) NOT NULL,
    vector_type VARCHAR(50) NOT NULL,
    config_yaml TEXT NOT NULL,
    component_key VARCHAR(100) NOT NULL UNIQUE,
    is_active BOOLEAN DEFAULT TRUE,
    created_by VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_vector_shared_components_type
    ON vector_shared_components(component_type);
CREATE INDEX IF NOT EXISTS idx_vector_shared_components_key
    ON vector_shared_components(component_key);

CREATE TABLE IF NOT EXISTS vector_config_component_refs (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::VARCHAR,
    config_id VARCHAR(36) NOT NULL,
    shared_component_id VARCHAR(36) NOT NULL REFERENCES vector_shared_components(id) ON DELETE RESTRICT,
    ref_type VARCHAR(20) DEFAULT 'input',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_vector_config_component_refs UNIQUE (config_id, shared_component_id)
);

CREATE INDEX IF NOT EXISTS idx_vector_config_refs_config
    ON vector_config_component_refs(config_id);
CREATE INDEX IF NOT EXISTS idx_vector_config_refs_component
    ON vector_config_component_refs(shared_component_id);

CREATE TABLE IF NOT EXISTS vector_machine_configs (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::VARCHAR,
    machine_id VARCHAR(36) NOT NULL,
    config_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) DEFAULT 'pending',
    deployed_version VARCHAR(50),
    deployed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_vector_machine_configs UNIQUE (machine_id, config_id)
);

CREATE INDEX IF NOT EXISTS idx_vector_machine_configs_machine
    ON vector_machine_configs(machine_id);
CREATE INDEX IF NOT EXISTS idx_vector_machine_configs_config
    ON vector_machine_configs(config_id);

CREATE TABLE IF NOT EXISTS vector_deployments (
    id VARCHAR(36) PRIMARY KEY,
    machine_id VARCHAR(36) NOT NULL,
    config_id VARCHAR(36) NOT NULL,
    config_version VARCHAR(50),
    config_content TEXT,
    deploy_mode VARCHAR(20) DEFAULT 'restart',
    status VARCHAR(20) DEFAULT 'pending',
    error_message TEXT,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_vector_deployments_machine_id ON vector_deployments(machine_id);
CREATE INDEX IF NOT EXISTS idx_vector_deployments_config_id ON vector_deployments(config_id);
CREATE INDEX IF NOT EXISTS idx_vector_deployments_config_version ON vector_deployments(config_version);
CREATE INDEX IF NOT EXISTS idx_vector_deployments_status ON vector_deployments(status);
CREATE INDEX IF NOT EXISTS idx_vector_deployments_created_at ON vector_deployments(created_at);

CREATE TABLE IF NOT EXISTS vector_commands (
    id VARCHAR(64) PRIMARY KEY,
    machine_id VARCHAR(36) NOT NULL REFERENCES vector_machines(id) ON DELETE CASCADE,
    command_type VARCHAR(32) NOT NULL,
    target_version VARCHAR(32),
    package_id VARCHAR(64),
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    executed_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_by VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_vector_commands_machine_status
    ON vector_commands(machine_id, status);
CREATE INDEX IF NOT EXISTS idx_vector_commands_created_at
    ON vector_commands(created_at DESC);

CREATE TABLE IF NOT EXISTS vector_packages (
    id VARCHAR(64) PRIMARY KEY,
    package_type VARCHAR(32) NOT NULL,
    version VARCHAR(32) NOT NULL,
    os_type VARCHAR(16) NOT NULL,
    arch VARCHAR(16) NOT NULL DEFAULT 'amd64',
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT,
    checksum VARCHAR(64),
    download_path VARCHAR(512),
    changelog TEXT,
    is_latest BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    uploaded_by VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_vector_packages_type_os_arch
    ON vector_packages(package_type, os_type, arch);
CREATE INDEX IF NOT EXISTS idx_vector_packages_latest
    ON vector_packages(package_type, os_type, arch, is_latest)
    WHERE is_latest = TRUE;

CREATE OR REPLACE VIEW v_shared_component_usage AS
SELECT
    sc.id AS component_id,
    sc.name AS component_name,
    sc.component_key,
    sc.component_type,
    COUNT(DISTINCT cr.config_id) AS usage_count,
    ARRAY_REMOVE(ARRAY_AGG(DISTINCT vc.name), NULL) AS used_by_configs
FROM vector_shared_components sc
LEFT JOIN vector_config_component_refs cr ON sc.id = cr.shared_component_id
LEFT JOIN vector_visual_configs vc ON cr.config_id = vc.id
GROUP BY sc.id, sc.name, sc.component_key, sc.component_type;

-- ============================================================================
-- 默认种子数据
-- ============================================================================

INSERT INTO vector_config_components (
    name, component_type, vector_type, config_yaml, visual_data, description, is_template, queryable, display_name
) VALUES
(
    '文件日志源',
    'source',
    'file',
    'type: file
include:
  - /var/log/*.log
read_from: beginning',
    NULL,
    '从文件读取日志',
    TRUE,
    FALSE,
    NULL
),
(
    'Syslog源',
    'source',
    'syslog',
    'type: syslog
address: 0.0.0.0:514
mode: tcp',
    NULL,
    '接收 Syslog 日志',
    TRUE,
    FALSE,
    NULL
),
(
    'Demo日志源',
    'source',
    'demo_logs',
    'type: demo_logs
format: apache_common
interval: 1.0
count: 100',
    NULL,
    '生成模拟日志数据，支持多种格式：apache_common, apache_error, syslog, json 等',
    TRUE,
    FALSE,
    NULL
),
(
    'ClickHouse目标',
    'sink',
    'clickhouse',
    'type: clickhouse
endpoint: ${CLICKHOUSE_ENDPOINT:-http://127.0.0.1:8123}
database: ${CLICKHOUSE_DATABASE:-MWLOGDB_ANALYSIS}
table: ${CLICKHOUSE_TABLE:-syslog}
compression: gzip',
    NULL,
    '写入 ClickHouse 数据库',
    TRUE,
    FALSE,
    'ClickHouse 日志源'
),
(
    'JSON解析转换',
    'transform',
    'remap',
    'type: remap
source: |
  . = parse_json!(.message)',
    NULL,
    '解析 JSON 格式日志',
    TRUE,
    FALSE,
    NULL
)
ON CONFLICT (name) DO NOTHING;

INSERT INTO vector_shared_components (
    id, name, description, component_type, vector_type, component_key, config_yaml, is_active
) VALUES
(
    'shared-syslog-514',
    'Syslog 514端口',
    '监听 514 端口接收 Syslog 日志，可被多个配置共享',
    'source',
    'syslog',
    'shared_syslog_514',
    'type: syslog
mode: tcp
address: 0.0.0.0:514',
    TRUE
),
(
    'shared-clickhouse-sink',
    'ClickHouse 输出',
    '共享的 ClickHouse 输出配置',
    'sink',
    'clickhouse',
    'shared_clickhouse',
    'type: clickhouse
endpoint: ${CLICKHOUSE_ENDPOINT:-http://127.0.0.1:8123}
database: ${CLICKHOUSE_DATABASE:-MWLOGDB_ANALYSIS}
format: json_each_row
compression: gzip
skip_unknown_fields: true
encoding:
  timestamp_format: unix
batch:
  max_bytes: 10000000
  timeout_secs: 10
buffer:
  type: memory
  max_events: 500000
auth:
  strategy: basic
  user: ${CLICKHOUSE_USER:-default}
  password: ${CLICKHOUSE_PASSWORD:-}',
    TRUE
)
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- 更新时间触发器
-- ============================================================================

DROP TRIGGER IF EXISTS update_users_updated_at ON users;
CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_system_config_updated_at ON system_config;
CREATE TRIGGER update_system_config_updated_at
    BEFORE UPDATE ON system_config
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_extraction_rules_updated_at ON extraction_rules;
CREATE TRIGGER update_extraction_rules_updated_at
    BEFORE UPDATE ON extraction_rules
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_alert_rules_updated_at ON alert_rules;
CREATE TRIGGER update_alert_rules_updated_at
    BEFORE UPDATE ON alert_rules
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_user_field_config_updated_at ON user_field_config;
CREATE TRIGGER update_user_field_config_updated_at
    BEFORE UPDATE ON user_field_config
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_datasources_updated_at ON datasources;
CREATE TRIGGER update_datasources_updated_at
    BEFORE UPDATE ON datasources
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_trusted_log_sources_updated_at ON trusted_log_sources;
CREATE TRIGGER update_trusted_log_sources_updated_at
    BEFORE UPDATE ON trusted_log_sources
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_vector_machines_updated_at ON vector_machines;
CREATE TRIGGER update_vector_machines_updated_at
    BEFORE UPDATE ON vector_machines
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_vector_configs_updated_at ON vector_configs;
CREATE TRIGGER update_vector_configs_updated_at
    BEFORE UPDATE ON vector_configs
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_vector_config_components_updated_at ON vector_config_components;
CREATE TRIGGER update_vector_config_components_updated_at
    BEFORE UPDATE ON vector_config_components
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_vector_visual_configs_updated_at ON vector_visual_configs;
CREATE TRIGGER update_vector_visual_configs_updated_at
    BEFORE UPDATE ON vector_visual_configs
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_vector_shared_components_updated_at ON vector_shared_components;
CREATE TRIGGER update_vector_shared_components_updated_at
    BEFORE UPDATE ON vector_shared_components
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_vector_machine_configs_updated_at ON vector_machine_configs;
CREATE TRIGGER update_vector_machine_configs_updated_at
    BEFORE UPDATE ON vector_machine_configs
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
