-- Vector 日志管理系统 - PostgreSQL 数据库表结构
-- 创建时间: 2025-12-26
-- 说明: Vector 日志收集器管理功能的数据库表
-- 数据库: PostgreSQL 12+

-- 启用 UUID 扩展（如果未启用）
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. 机器管理表
CREATE TABLE IF NOT EXISTS vector_machines (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::VARCHAR,
    name VARCHAR(100) NOT NULL,
    hostname VARCHAR(255) NOT NULL,
    ip_address VARCHAR(50) NOT NULL,
    ssh_port INTEGER DEFAULT 22,
    ssh_user VARCHAR(50) DEFAULT 'root',
    ssh_key_path VARCHAR(500),
    os_type VARCHAR(50) DEFAULT 'linux',
    status VARCHAR(20) DEFAULT 'offline',
    vector_version VARCHAR(50),
    vector_install_path VARCHAR(500) DEFAULT '/usr/local/bin/vector',
    vector_config_path VARCHAR(500) DEFAULT '/etc/vector/vector.yaml',
    management_method VARCHAR(20) DEFAULT 'systemctl',
    last_heartbeat TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    CONSTRAINT uk_vm_hostname UNIQUE (hostname),
    CONSTRAINT uk_vm_ip UNIQUE (ip_address)
);

-- 表注释
COMMENT ON TABLE vector_machines IS 'Vector日志收集器部署的机器列表';

-- 列注释
COMMENT ON COLUMN vector_machines.id IS '主键ID';
COMMENT ON COLUMN vector_machines.name IS '机器名称';
COMMENT ON COLUMN vector_machines.hostname IS '主机名';
COMMENT ON COLUMN vector_machines.ip_address IS 'IP地址';
COMMENT ON COLUMN vector_machines.ssh_port IS 'SSH端口';
COMMENT ON COLUMN vector_machines.ssh_user IS 'SSH用户';
COMMENT ON COLUMN vector_machines.ssh_key_path IS 'SSH密钥路径';
COMMENT ON COLUMN vector_machines.os_type IS '操作系统类型';
COMMENT ON COLUMN vector_machines.status IS '状态: online/offline/error';
COMMENT ON COLUMN vector_machines.vector_version IS 'Vector版本';
COMMENT ON COLUMN vector_machines.vector_install_path IS 'Vector安装路径';
COMMENT ON COLUMN vector_machines.vector_config_path IS 'Vector配置文件路径';
COMMENT ON COLUMN vector_machines.management_method IS '管理方式: systemctl/binary';
COMMENT ON COLUMN vector_machines.last_heartbeat IS '最后心跳时间';
COMMENT ON COLUMN vector_machines.created_at IS '创建时间';
COMMENT ON COLUMN vector_machines.updated_at IS '更新时间';
COMMENT ON COLUMN vector_machines.created_by IS '创建人ID';

-- 2. 配置管理表
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
    CONSTRAINT uk_vc_name_version UNIQUE (name, version)
);

COMMENT ON TABLE vector_configs IS 'Vector配置文件和模板';
COMMENT ON COLUMN vector_configs.id IS '主键ID';
COMMENT ON COLUMN vector_configs.name IS '配置名称';
COMMENT ON COLUMN vector_configs.description IS '配置描述';
COMMENT ON COLUMN vector_configs.content IS '配置内容(YAML/TOML)';
COMMENT ON COLUMN vector_configs.version IS '版本号';
COMMENT ON COLUMN vector_configs.is_template IS '是否为模板';
COMMENT ON COLUMN vector_configs.parent_config_id IS '父配置ID（用于派生）';
COMMENT ON COLUMN vector_configs.created_at IS '创建时间';
COMMENT ON COLUMN vector_configs.updated_at IS '更新时间';
COMMENT ON COLUMN vector_configs.created_by IS '创建人ID';

-- 3. 配置部署记录表
CREATE TABLE IF NOT EXISTS vector_deployments (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::VARCHAR,
    machine_id VARCHAR(36) NOT NULL,
    config_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) DEFAULT 'pending',
    deployment_method VARCHAR(50) DEFAULT 'ssh',
    error_message TEXT,
    deployed_at TIMESTAMP,
    deployed_by VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vd_machine FOREIGN KEY (machine_id) REFERENCES vector_machines(id) ON DELETE CASCADE,
    CONSTRAINT fk_vd_config FOREIGN KEY (config_id) REFERENCES vector_configs(id) ON DELETE CASCADE
);

COMMENT ON TABLE vector_deployments IS 'Vector配置部署记录';
COMMENT ON COLUMN vector_deployments.id IS '主键ID';
COMMENT ON COLUMN vector_deployments.machine_id IS '机器ID';
COMMENT ON COLUMN vector_deployments.config_id IS '配置ID';
COMMENT ON COLUMN vector_deployments.status IS '状态: pending/deploying/deployed/failed/rolled_back';
COMMENT ON COLUMN vector_deployments.deployment_method IS '部署方式';
COMMENT ON COLUMN vector_deployments.error_message IS '错误信息';
COMMENT ON COLUMN vector_deployments.deployed_at IS '部署完成时间';
COMMENT ON COLUMN vector_deployments.deployed_by IS '部署人ID';
COMMENT ON COLUMN vector_deployments.created_at IS '创建时间';

-- 4. 服务操作记录表
CREATE TABLE IF NOT EXISTS vector_service_operations (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::VARCHAR,
    machine_id VARCHAR(36) NOT NULL,
    operation_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) DEFAULT 'pending',
    result TEXT,
    error_message TEXT,
    executed_by VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    CONSTRAINT fk_vso_machine FOREIGN KEY (machine_id) REFERENCES vector_machines(id) ON DELETE CASCADE
);

COMMENT ON TABLE vector_service_operations IS 'Vector服务操作记录';
COMMENT ON COLUMN vector_service_operations.id IS '主键ID';
COMMENT ON COLUMN vector_service_operations.machine_id IS '机器ID';
COMMENT ON COLUMN vector_service_operations.operation_type IS '操作类型: start/stop/restart/reload/status';
COMMENT ON COLUMN vector_service_operations.status IS '状态: pending/running/success/failed';
COMMENT ON COLUMN vector_service_operations.result IS '执行结果';
COMMENT ON COLUMN vector_service_operations.error_message IS '错误信息';
COMMENT ON COLUMN vector_service_operations.executed_by IS '执行人ID';
COMMENT ON COLUMN vector_service_operations.created_at IS '创建时间';
COMMENT ON COLUMN vector_service_operations.completed_at IS '完成时间';

-- 5. 管道指标表
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
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vpm_machine FOREIGN KEY (machine_id) REFERENCES vector_machines(id) ON DELETE CASCADE
);

COMMENT ON TABLE vector_pipeline_metrics IS 'Vector管道性能指标';
COMMENT ON COLUMN vector_pipeline_metrics.id IS '主键ID';
COMMENT ON COLUMN vector_pipeline_metrics.machine_id IS '机器ID';
COMMENT ON COLUMN vector_pipeline_metrics.source_name IS 'Source组件名';
COMMENT ON COLUMN vector_pipeline_metrics.transform_name IS 'Transform组件名';
COMMENT ON COLUMN vector_pipeline_metrics.sink_name IS 'Sink组件名';
COMMENT ON COLUMN vector_pipeline_metrics.events_in IS '输入事件数';
COMMENT ON COLUMN vector_pipeline_metrics.events_out IS '输出事件数';
COMMENT ON COLUMN vector_pipeline_metrics.bytes_in IS '输入字节数';
COMMENT ON COLUMN vector_pipeline_metrics.bytes_out IS '输出字节数';
COMMENT ON COLUMN vector_pipeline_metrics.errors IS '错误数';
COMMENT ON COLUMN vector_pipeline_metrics.latency_ms IS '延迟(毫秒)';
COMMENT ON COLUMN vector_pipeline_metrics.recorded_at IS '记录时间';

-- 创建索引以提高查询性能
CREATE INDEX IF NOT EXISTS idx_vpm_machine_time ON vector_pipeline_metrics(machine_id, recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_vpm_source ON vector_pipeline_metrics(source_name, recorded_at DESC);

-- 6. 健康检查表
CREATE TABLE IF NOT EXISTS vector_health_checks (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::VARCHAR,
    machine_id VARCHAR(36) NOT NULL,
    check_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) DEFAULT 'healthy',
    details JSONB,
    checked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vhc_machine FOREIGN KEY (machine_id) REFERENCES vector_machines(id) ON DELETE CASCADE
);

COMMENT ON TABLE vector_health_checks IS 'Vector健康检查记录';
COMMENT ON COLUMN vector_health_checks.id IS '主键ID';
COMMENT ON COLUMN vector_health_checks.machine_id IS '机器ID';
COMMENT ON COLUMN vector_health_checks.check_type IS '检查类型: connectivity/config_valid/service_status/pipeline_flow';
COMMENT ON COLUMN vector_health_checks.status IS '状态: healthy/degraded/unhealthy';
COMMENT ON COLUMN vector_health_checks.details IS '检查详情';
COMMENT ON COLUMN vector_health_checks.checked_at IS '检查时间';

CREATE INDEX IF NOT EXISTS idx_vhc_machine_time ON vector_health_checks(machine_id, checked_at DESC);
CREATE INDEX IF NOT EXISTS idx_vhc_type_status ON vector_health_checks(check_type, status);

-- 7. 配置组件库表（可选，用于可视化配置）
CREATE TABLE IF NOT EXISTS vector_config_components (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::VARCHAR,
    name VARCHAR(100) NOT NULL,
    component_type VARCHAR(20) NOT NULL,
    vector_type VARCHAR(50) NOT NULL,
    config_yaml TEXT NOT NULL,
    description TEXT,
    is_template BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    CONSTRAINT uk_vcc_name UNIQUE (name)
);

COMMENT ON TABLE vector_config_components IS 'Vector配置组件模板库';
COMMENT ON COLUMN vector_config_components.id IS '主键ID';
COMMENT ON COLUMN vector_config_components.name IS '组件名称';
COMMENT ON COLUMN vector_config_components.component_type IS '组件类型: source/transform/sink';
COMMENT ON COLUMN vector_config_components.vector_type IS 'Vector类型（如 file, http, kafka）';
COMMENT ON COLUMN vector_config_components.config_yaml IS '配置YAML模板';
COMMENT ON COLUMN vector_config_components.description IS '组件描述';
COMMENT ON COLUMN vector_config_components.is_template IS '是否为模板';
COMMENT ON COLUMN vector_config_components.created_at IS '创建时间';
COMMENT ON COLUMN vector_config_components.updated_at IS '更新时间';
COMMENT ON COLUMN vector_config_components.created_by IS '创建人ID';

-- 8. 可视化配置表（用于拖拽式配置）
CREATE TABLE IF NOT EXISTS vector_visual_configs (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::VARCHAR,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    format VARCHAR(20) DEFAULT 'namespace_yaml',
    flow_data JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36)
);

COMMENT ON TABLE vector_visual_configs IS 'Vector可视化配置（拖拽式）';
COMMENT ON COLUMN vector_visual_configs.id IS '主键ID';
COMMENT ON COLUMN vector_visual_configs.name IS '配置名称';
COMMENT ON COLUMN vector_visual_configs.description IS '配置描述';
COMMENT ON COLUMN vector_visual_configs.format IS '格式: namespace_yaml/single_toml';
COMMENT ON COLUMN vector_visual_configs.flow_data IS '流程图数据（nodes和edges）';
COMMENT ON COLUMN vector_visual_configs.created_at IS '创建时间';
COMMENT ON COLUMN vector_visual_configs.updated_at IS '更新时间';
COMMENT ON COLUMN vector_visual_configs.created_by IS '创建人ID';

-- 创建更新时间触发器函数
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 为各表添加更新时间触发器
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

-- 插入一些示例配置组件模板
INSERT INTO vector_config_components (name, component_type, vector_type, config_yaml, description) VALUES
('文件日志源', 'source', 'file', 'type: file
include:
  - /var/log/*.log
read_from: beginning', '从文件读取日志'),
('Syslog源', 'source', 'syslog', 'type: syslog
address: 0.0.0.0:514
mode: tcp', '接收Syslog日志'),
('Demo日志源', 'source', 'demo_logs', 'type: demo_logs
format: apache_common
interval: 1.0
count: 100', '生成模拟日志数据，支持多种格式：apache_common, apache_error, syslog, json 等'),
('ClickHouse目标', 'sink', 'clickhouse', 'type: clickhouse
endpoint: http://localhost:8123
database: logs
table: log_entries
compression: gzip', '写入ClickHouse数据库'),
('JSON解析转换', 'transform', 'remap', 'type: remap
source: |
  . = parse_json!(.message)', '解析JSON格式日志')
ON CONFLICT (name) DO NOTHING;
