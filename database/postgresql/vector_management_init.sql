-- =====================================================
-- Vector 日志收集管理器 - 数据库初始化脚本
-- 数据库: PostgreSQL 15+
-- 创建时间: 2025-12-26
-- =====================================================

-- 1. Agent机器管理表
CREATE TABLE IF NOT EXISTS vector_hosts (
    id BIGSERIAL PRIMARY KEY,
    hostname VARCHAR(255) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    agent_token VARCHAR(255) UNIQUE NOT NULL,
    agent_version VARCHAR(50),
    vector_version VARCHAR(50),

    -- 状态字段
    status VARCHAR(20) NOT NULL DEFAULT 'offline',
    last_heartbeat TIMESTAMP,

    -- 标签和分组
    tags JSONB DEFAULT '[]',
    environment VARCHAR(50) DEFAULT 'production',

    -- 配置版本
    current_config_version VARCHAR(50),
    target_config_version VARCHAR(50),

    -- 系统信息
    os_type VARCHAR(50),
    os_version VARCHAR(100),
    cpu_cores INTEGER,
    total_memory_mb BIGINT,

    -- 审计字段
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_vector_hosts_ip UNIQUE (ip_address)
);

CREATE INDEX idx_vector_hosts_status ON vector_hosts(status);
CREATE INDEX idx_vector_hosts_tags ON vector_hosts USING GIN(tags);
CREATE INDEX idx_vector_hosts_heartbeat ON vector_hosts(last_heartbeat);

COMMENT ON TABLE vector_hosts IS 'Vector Agent机器清单表';
COMMENT ON COLUMN vector_hosts.agent_token IS 'Agent认证Token';
COMMENT ON COLUMN vector_hosts.status IS 'online, offline, error';
COMMENT ON COLUMN vector_hosts.tags IS '机器标签，用于分组';
COMMENT ON COLUMN vector_hosts.environment IS '环境：production, staging, test';
COMMENT ON COLUMN vector_hosts.current_config_version IS '当前使用的配置版本';
COMMENT ON COLUMN vector_hosts.target_config_version IS '目标配置版本（用于灰度发布）';

-- 2. 配置模板表
CREATE TABLE IF NOT EXISTS vector_config_templates (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    category VARCHAR(50),

    -- 配置内容
    yaml_content TEXT NOT NULL,
    config_json JSONB,

    -- 变量定义
    variables JSONB DEFAULT '[]',

    -- 属性
    is_public BOOLEAN DEFAULT true,
    is_builtin BOOLEAN DEFAULT false,

    -- 审计
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_vector_config_templates_name UNIQUE (name)
);

CREATE INDEX idx_vector_config_templates_category ON vector_config_templates(category);

COMMENT ON TABLE vector_config_templates IS 'Vector配置模板库';
COMMENT ON COLUMN vector_config_templates.category IS 'source, transform, sink, full';
COMMENT ON COLUMN vector_config_templates.yaml_content IS 'YAML配置内容';
COMMENT ON COLUMN vector_config_templates.config_json IS '可视化配置JSON（用于流程图）';
COMMENT ON COLUMN vector_config_templates.variables IS '配置变量列表 [{"name", "type", "default", "description"}]';
COMMENT ON COLUMN vector_config_templates.is_public IS '是否公开模板';
COMMENT ON COLUMN vector_config_templates.is_builtin IS '是否内置模板';

-- 3. 配置版本表
CREATE TABLE IF NOT EXISTS vector_configs (
    id BIGSERIAL PRIMARY KEY,
    version VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,

    -- 配置内容
    yaml_content TEXT NOT NULL,
    config_json JSONB,

    -- 校验信息
    is_validated BOOLEAN DEFAULT false,
    validation_error TEXT,

    -- 发布状态
    status VARCHAR(20) DEFAULT 'draft',
    released_at TIMESTAMP,

    -- 适用范围（标签匹配）
    target_tags JSONB DEFAULT '[]',
    target_environment VARCHAR(50),

    -- 审计
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_vector_configs_version ON vector_configs(version);
CREATE INDEX idx_vector_configs_status ON vector_configs(status);
CREATE INDEX idx_vector_configs_tags ON vector_configs USING GIN(target_tags);

COMMENT ON TABLE vector_configs IS 'Vector配置版本管理表';
COMMENT ON COLUMN vector_configs.version IS '配置版本号，如 v1.0.0';
COMMENT ON COLUMN vector_configs.name IS '配置名称';
COMMENT ON COLUMN vector_configs.yaml_content IS 'YAML配置内容';
COMMENT ON COLUMN vector_configs.config_json IS '可视化配置JSON';
COMMENT ON COLUMN vector_configs.is_validated IS '是否已校验';
COMMENT ON COLUMN vector_configs.validation_error IS '校验错误信息';
COMMENT ON COLUMN vector_configs.status IS 'draft, testing, released, deprecated';
COMMENT ON COLUMN vector_configs.released_at IS '发布时间';
COMMENT ON COLUMN vector_configs.target_tags IS '目标机器标签';
COMMENT ON COLUMN vector_configs.target_environment IS '目标环境';

-- 4. 配置部署历史表
CREATE TABLE IF NOT EXISTS vector_config_deployments (
    id BIGSERIAL PRIMARY KEY,
    host_id BIGINT NOT NULL REFERENCES vector_hosts(id) ON DELETE CASCADE,
    config_id BIGINT NOT NULL REFERENCES vector_configs(id),
    config_version VARCHAR(50) NOT NULL,

    -- 部署信息
    deployment_type VARCHAR(20) DEFAULT 'manual',
    status VARCHAR(20) DEFAULT 'pending',

    -- 执行结果
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT,

    -- 回滚信息
    previous_config_version VARCHAR(50),
    is_rollback BOOLEAN DEFAULT false,

    -- 审计
    deployed_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_vector_config_deployments_host ON vector_config_deployments(host_id);
CREATE INDEX idx_vector_config_deployments_config ON vector_config_deployments(config_id);
CREATE INDEX idx_vector_config_deployments_status ON vector_config_deployments(status);
CREATE INDEX idx_vector_config_deployments_time ON vector_config_deployments(created_at DESC);

COMMENT ON TABLE vector_config_deployments IS 'Vector配置部署历史表';
COMMENT ON COLUMN vector_config_deployments.deployment_type IS 'manual, auto, rollback, canary';
COMMENT ON COLUMN vector_config_deployments.status IS 'pending, deploying, success, failed, rollback';
COMMENT ON COLUMN vector_config_deployments.previous_config_version IS '回滚前的版本';

-- 5. Agent心跳和指标表
CREATE TABLE IF NOT EXISTS vector_agent_metrics (
    id BIGSERIAL PRIMARY KEY,
    host_id BIGINT NOT NULL REFERENCES vector_hosts(id) ON DELETE CASCADE,

    -- 时间戳
    collected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 系统指标
    cpu_usage_percent DECIMAL(5,2),
    memory_usage_percent DECIMAL(5,2),
    memory_used_mb BIGINT,
    disk_usage_percent DECIMAL(5,2),
    disk_used_gb BIGINT,

    -- Agent状态
    agent_uptime_seconds BIGINT,
    agent_memory_mb INTEGER,

    -- Vector状态
    vector_running BOOLEAN,
    vector_uptime_seconds BIGINT,
    vector_config_reload_count INTEGER,
    vector_error_count INTEGER,

    -- Vector吞吐量（如果有）
    events_in_total BIGINT,
    events_out_total BIGINT,
    events_in_rate DECIMAL(10,2),
    events_out_rate DECIMAL(10,2),

    -- 额外指标（JSON格式，灵活扩展）
    extra_metrics JSONB
);

CREATE INDEX idx_vector_agent_metrics_host ON vector_agent_metrics(host_id);
CREATE INDEX idx_vector_agent_metrics_time ON vector_agent_metrics(collected_at DESC);

COMMENT ON TABLE vector_agent_metrics IS 'Agent和Vector指标数据表';
COMMENT ON COLUMN vector_agent_metrics.vector_config_reload_count IS '配置重载次数';
COMMENT ON COLUMN vector_agent_metrics.vector_error_count IS '错误计数';
COMMENT ON COLUMN vector_agent_metrics.events_in_total IS '接收事件总数';
COMMENT ON COLUMN vector_agent_metrics.events_out_total IS '发送事件总数';
COMMENT ON COLUMN vector_agent_metrics.events_in_rate IS '接收速率（events/s）';
COMMENT ON COLUMN vector_agent_metrics.events_out_rate IS '发送速率（events/s）';

-- 6. Agent日志表
CREATE TABLE IF NOT EXISTS vector_agent_logs (
    id BIGSERIAL PRIMARY KEY,
    host_id BIGINT NOT NULL REFERENCES vector_hosts(id) ON DELETE CASCADE,

    -- 日志信息
    log_level VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,

    -- 日志来源
    source VARCHAR(50),

    -- 时间戳
    logged_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 额外上下文
    context JSONB
);

CREATE INDEX idx_vector_agent_logs_host ON vector_agent_logs(host_id);
CREATE INDEX idx_vector_agent_logs_level ON vector_agent_logs(log_level);
CREATE INDEX idx_vector_agent_logs_time ON vector_agent_logs(logged_at DESC);
CREATE INDEX idx_vector_agent_logs_source ON vector_agent_logs(source);

COMMENT ON TABLE vector_agent_logs IS 'Agent日志表';
COMMENT ON COLUMN vector_agent_logs.log_level IS 'DEBUG, INFO, WARN, ERROR, FATAL';
COMMENT ON COLUMN vector_agent_logs.source IS 'agent, vector, system';
COMMENT ON COLUMN vector_agent_logs.context IS '日志上下文信息';

-- 7. 操作审计日志表
CREATE TABLE IF NOT EXISTS vector_operation_logs (
    id BIGSERIAL PRIMARY KEY,

    -- 操作对象
    host_id BIGINT REFERENCES vector_hosts(id) ON DELETE SET NULL,
    config_id BIGINT REFERENCES vector_configs(id) ON DELETE SET NULL,

    -- 操作信息
    operation VARCHAR(50) NOT NULL,
    operation_detail TEXT,

    -- 执行结果
    status VARCHAR(20) DEFAULT 'pending',
    error_message TEXT,

    -- 操作时间
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    duration_ms INTEGER,

    -- 审计
    executed_by BIGINT NOT NULL,
    ip_address VARCHAR(45)
);

CREATE INDEX idx_vector_operation_logs_host ON vector_operation_logs(host_id);
CREATE INDEX idx_vector_operation_logs_operation ON vector_operation_logs(operation);
CREATE INDEX idx_vector_operation_logs_status ON vector_operation_logs(status);
CREATE INDEX idx_vector_operation_logs_time ON vector_operation_logs(started_at DESC);
CREATE INDEX idx_vector_operation_logs_user ON vector_operation_logs(executed_by);

COMMENT ON TABLE vector_operation_logs IS 'Vector操作审计日志表';
COMMENT ON COLUMN vector_operation_logs.operation IS 'deploy, rollback, restart, start, stop, reload';
COMMENT ON COLUMN vector_operation_logs.operation_detail IS '操作详细说明';
COMMENT ON COLUMN vector_operation_logs.status IS 'pending, success, failed';
COMMENT ON COLUMN vector_operation_logs.duration_ms IS '执行耗时（毫秒）';
COMMENT ON COLUMN vector_operation_logs.executed_by IS '执行人用户ID';
COMMENT ON COLUMN vector_operation_logs.ip_address IS '操作来源IP';

-- 8. 批量任务表
CREATE TABLE IF NOT EXISTS vector_batch_tasks (
    id BIGSERIAL PRIMARY KEY,
    task_name VARCHAR(200) NOT NULL,
    task_type VARCHAR(50) NOT NULL,

    -- 任务目标
    target_host_ids BIGINT[],
    target_tags JSONB,

    -- 任务参数
    task_params JSONB,

    -- 执行策略
    execution_mode VARCHAR(20) DEFAULT 'parallel',
    batch_size INTEGER DEFAULT 10,
    canary_percent INTEGER,

    -- 任务状态
    status VARCHAR(20) DEFAULT 'pending',
    progress INTEGER DEFAULT 0,

    -- 统计
    total_hosts INTEGER,
    success_count INTEGER DEFAULT 0,
    failed_count INTEGER DEFAULT 0,

    -- 时间
    scheduled_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,

    -- 审计
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_vector_batch_tasks_status ON vector_batch_tasks(status);
CREATE INDEX idx_vector_batch_tasks_type ON vector_batch_tasks(task_type);
CREATE INDEX idx_vector_batch_tasks_time ON vector_batch_tasks(created_at DESC);

COMMENT ON TABLE vector_batch_tasks IS 'Vector批量任务表';
COMMENT ON COLUMN vector_batch_tasks.task_type IS 'deploy, restart, upgrade';
COMMENT ON COLUMN vector_batch_tasks.target_host_ids IS '目标机器ID数组';
COMMENT ON COLUMN vector_batch_tasks.target_tags IS '目标标签（动态匹配）';
COMMENT ON COLUMN vector_batch_tasks.task_params IS '任务参数（如配置版本号）';
COMMENT ON COLUMN vector_batch_tasks.execution_mode IS 'parallel, sequential, canary';
COMMENT ON COLUMN vector_batch_tasks.batch_size IS '批量大小（每批执行多少台）';
COMMENT ON COLUMN vector_batch_tasks.canary_percent IS '金丝雀百分比（1-100）';
COMMENT ON COLUMN vector_batch_tasks.status IS 'pending, running, paused, completed, failed';
COMMENT ON COLUMN vector_batch_tasks.progress IS '进度百分比';

-- 9. 配置变量表（用于配置中的变量替换）
CREATE TABLE IF NOT EXISTS vector_config_variables (
    id BIGSERIAL PRIMARY KEY,
    var_name VARCHAR(100) NOT NULL UNIQUE,
    var_value TEXT NOT NULL,
    is_encrypted BOOLEAN DEFAULT false,
    description TEXT,

    -- 作用域
    scope VARCHAR(20) DEFAULT 'global',
    environment VARCHAR(50),

    -- 审计
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_vector_config_variables_scope ON vector_config_variables(scope, environment);

COMMENT ON TABLE vector_config_variables IS '配置变量表';
COMMENT ON COLUMN vector_config_variables.var_name IS '变量名，如 CLICKHOUSE_HOST';
COMMENT ON COLUMN vector_config_variables.var_value IS '变量值';
COMMENT ON COLUMN vector_config_variables.is_encrypted IS '是否加密存储';
COMMENT ON COLUMN vector_config_variables.scope IS 'global, environment';
COMMENT ON COLUMN vector_config_variables.environment IS '环境（仅当scope=environment时）';

-- =====================================================
-- 初始化数据
-- =====================================================

-- 插入内置配置模板
INSERT INTO vector_config_templates (name, description, category, yaml_content, is_builtin, variables) VALUES
('基础Syslog收集', '从514端口收集Syslog并写入ClickHouse', 'full',
'sources:
  syslog_source:
    type: syslog
    address: 0.0.0.0:514
    mode: tcp

sinks:
  clickhouse_sink:
    type: clickhouse
    inputs:
      - syslog_source
    endpoint: "{{CLICKHOUSE_ENDPOINT}}"
    database: "{{CLICKHOUSE_DATABASE}}"
    table: syslog
    auth:
      strategy: basic
      user: "{{CLICKHOUSE_USER}}"
      password: "{{CLICKHOUSE_PASSWORD}}"
',
true,
'[
  {"name": "CLICKHOUSE_ENDPOINT", "type": "string", "default": "http://localhost:8123", "description": "ClickHouse HTTP端点"},
  {"name": "CLICKHOUSE_DATABASE", "type": "string", "default": "MWLOGDB_ANALYSIS", "description": "数据库名"},
  {"name": "CLICKHOUSE_USER", "type": "string", "default": "default", "description": "用户名"},
  {"name": "CLICKHOUSE_PASSWORD", "type": "password", "default": "", "description": "密码"}
]'::jsonb
),
('文件日志收集', '从指定文件收集日志', 'full',
'sources:
  file_source:
    type: file
    include:
      - /var/log/app/*.log
    read_from: end

transforms:
  parse_json:
    type: remap
    inputs:
      - file_source
    source: |
      . = parse_json!(.message)

sinks:
  clickhouse_sink:
    type: clickhouse
    inputs:
      - parse_json
    endpoint: "{{CLICKHOUSE_ENDPOINT}}"
    database: "{{CLICKHOUSE_DATABASE}}"
    table: log_entries
',
true,
'[
  {"name": "CLICKHOUSE_ENDPOINT", "type": "string", "default": "http://localhost:8123", "description": "ClickHouse HTTP端点"},
  {"name": "CLICKHOUSE_DATABASE", "type": "string", "default": "MWLOGDB_ANALYSIS", "description": "数据库名"}
]'::jsonb
),
('过滤ERROR日志', '只保留ERROR级别的日志', 'transform',
'transforms:
  filter_error:
    type: filter
    inputs:
      - "{{INPUT}}"
    condition: |
      .severity == "error" || .level == "ERROR"
',
true,
'[
  {"name": "INPUT", "type": "string", "default": "source", "description": "输入源名称"}
]'::jsonb
);

-- 插入默认配置变量
INSERT INTO vector_config_variables (var_name, var_value, is_encrypted, description, scope) VALUES
('CLICKHOUSE_ENDPOINT', 'http://10.180.5.72:8123', false, 'ClickHouse HTTP端点', 'global'),
('CLICKHOUSE_DATABASE', 'MWLOGDB_ANALYSIS', false, 'ClickHouse数据库名', 'global'),
('CLICKHOUSE_USER', 'default', false, 'ClickHouse用户名', 'global'),
('CLICKHOUSE_PASSWORD', 'mwclickhouse@2024', true, 'ClickHouse密码（已加密）', 'global');

-- =====================================================
-- 定期清理历史数据的函数（可选）
-- =====================================================
CREATE OR REPLACE FUNCTION cleanup_old_metrics() RETURNS void AS $$
BEGIN
    -- 删除30天前的指标数据
    DELETE FROM vector_agent_metrics WHERE collected_at < NOW() - INTERVAL '30 days';

    -- 删除90天前的日志数据
    DELETE FROM vector_agent_logs WHERE logged_at < NOW() - INTERVAL '90 days';

    -- 删除180天前的操作日志
    DELETE FROM vector_operation_logs WHERE started_at < NOW() - INTERVAL '180 days';
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION cleanup_old_metrics IS '清理历史数据（指标、日志）';

-- =====================================================
-- 授权（根据实际用户调整）
-- =====================================================
-- GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO log_analysis_user;
-- GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO log_analysis_user;
