-- =====================================================
-- Vector Pipeline 混合方案 - 数据库初始化脚本
-- 支持 config-dir 模式和共享组件
-- =====================================================

-- 1. 共享组件表
CREATE TABLE IF NOT EXISTS vector_shared_components (
    id VARCHAR(36) PRIMARY KEY,
    
    -- 组件信息
    name VARCHAR(200) NOT NULL,
    description TEXT,
    component_type VARCHAR(20) NOT NULL,  -- source, transform, sink
    vector_type VARCHAR(50) NOT NULL,     -- file, syslog, kafka, remap, clickhouse 等
    
    -- 配置内容
    config_yaml TEXT NOT NULL,
    
    -- 唯一标识（用于 Vector 配置中的组件名）
    component_key VARCHAR(100) NOT NULL UNIQUE,
    
    -- 状态
    is_active BOOLEAN DEFAULT true,
    
    -- 审计
    created_by VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_shared_components_type ON vector_shared_components(component_type);
CREATE INDEX idx_shared_components_key ON vector_shared_components(component_key);

COMMENT ON TABLE vector_shared_components IS '共享组件表，存储可被多个配置引用的组件';
COMMENT ON COLUMN vector_shared_components.component_key IS '组件唯一标识，如 shared_syslog_514';
COMMENT ON COLUMN vector_shared_components.component_type IS 'source, transform, sink';

-- 2. 配置引用共享组件关系表
CREATE TABLE IF NOT EXISTS vector_config_component_refs (
    id VARCHAR(36) PRIMARY KEY,
    
    -- 配置ID（可视化配置）
    config_id VARCHAR(36) NOT NULL,
    
    -- 共享组件ID
    shared_component_id VARCHAR(36) NOT NULL REFERENCES vector_shared_components(id) ON DELETE RESTRICT,
    
    -- 引用信息
    ref_type VARCHAR(20) DEFAULT 'input',  -- input: 作为输入源, output: 作为输出目标
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_config_component_ref UNIQUE (config_id, shared_component_id)
);

CREATE INDEX idx_config_refs_config ON vector_config_component_refs(config_id);
CREATE INDEX idx_config_refs_component ON vector_config_component_refs(shared_component_id);

COMMENT ON TABLE vector_config_component_refs IS '配置与共享组件的引用关系';
COMMENT ON COLUMN vector_config_component_refs.ref_type IS 'input: 引用为输入, output: 引用为输出';

-- 3. 修改 vector_visual_configs 表，添加 pipeline 相关字段
ALTER TABLE vector_visual_configs 
ADD COLUMN IF NOT EXISTS pipeline_name VARCHAR(100),
ADD COLUMN IF NOT EXISTS deploy_mode VARCHAR(20) DEFAULT 'pipeline',
ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT true;

COMMENT ON COLUMN vector_visual_configs.pipeline_name IS '管道名称，用于 config-dir 目录名';
COMMENT ON COLUMN vector_visual_configs.deploy_mode IS 'pipeline: 独立管道, shared: 使用共享组件';
COMMENT ON COLUMN vector_visual_configs.is_active IS '是否激活（部署时是否包含）';

-- 4. 机器已部署配置关系表（记录每台机器部署了哪些配置）
CREATE TABLE IF NOT EXISTS vector_machine_configs (
    id VARCHAR(36) PRIMARY KEY,
    
    machine_id VARCHAR(36) NOT NULL,
    config_id VARCHAR(36) NOT NULL,
    
    -- 部署状态
    status VARCHAR(20) DEFAULT 'pending',  -- pending, deployed, failed
    deployed_version VARCHAR(50),
    
    -- 时间
    deployed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_machine_config UNIQUE (machine_id, config_id)
);

CREATE INDEX idx_machine_configs_machine ON vector_machine_configs(machine_id);
CREATE INDEX idx_machine_configs_config ON vector_machine_configs(config_id);

COMMENT ON TABLE vector_machine_configs IS '机器与配置的部署关系，支持一台机器部署多个配置';

-- 5. 创建查询共享组件依赖的视图
CREATE OR REPLACE VIEW v_shared_component_usage AS
SELECT 
    sc.id AS component_id,
    sc.name AS component_name,
    sc.component_key,
    sc.component_type,
    COUNT(DISTINCT cr.config_id) AS usage_count,
    ARRAY_AGG(DISTINCT vc.name) AS used_by_configs
FROM vector_shared_components sc
LEFT JOIN vector_config_component_refs cr ON sc.id = cr.shared_component_id
LEFT JOIN vector_visual_configs vc ON cr.config_id = vc.id
GROUP BY sc.id, sc.name, sc.component_key, sc.component_type;

COMMENT ON VIEW v_shared_component_usage IS '共享组件使用情况视图';

-- 6. 插入示例共享组件
INSERT INTO vector_shared_components (id, name, description, component_type, vector_type, component_key, config_yaml) VALUES
(
    'shared-syslog-514',
    'Syslog 514端口',
    '监听 514 端口接收 Syslog 日志，可被多个配置共享',
    'source',
    'syslog',
    'shared_syslog_514',
    'type: syslog
mode: tcp
address: 0.0.0.0:514'
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
  password: ${CLICKHOUSE_PASSWORD:-}'
)
ON CONFLICT DO NOTHING;
