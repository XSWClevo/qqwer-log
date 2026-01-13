drop TABLE IF EXISTS vector_deployments;
-- Vector配置部署记录表
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

-- 索引
CREATE INDEX IF NOT EXISTS idx_deployments_machine_id ON vector_deployments(machine_id);
CREATE INDEX IF NOT EXISTS idx_deployments_config_id ON vector_deployments(config_id);
CREATE INDEX IF NOT EXISTS idx_deployments_config_version ON vector_deployments(config_version);
CREATE INDEX IF NOT EXISTS idx_deployments_status ON vector_deployments(status);
CREATE INDEX IF NOT EXISTS idx_deployments_created_at ON vector_deployments(created_at);

COMMENT ON TABLE vector_deployments IS 'Vector配置部署记录表';
COMMENT ON COLUMN vector_deployments.id IS '主键ID';
COMMENT ON COLUMN vector_deployments.machine_id IS '机器ID';
COMMENT ON COLUMN vector_deployments.config_id IS '配置ID';
COMMENT ON COLUMN vector_deployments.config_version IS '配置版本号';
COMMENT ON COLUMN vector_deployments.config_content IS '配置内容(YAML)';
COMMENT ON COLUMN vector_deployments.deploy_mode IS '部署方式: restart/reload';
COMMENT ON COLUMN vector_deployments.status IS '部署状态: pending/deploying/success/failed';
COMMENT ON COLUMN vector_deployments.error_message IS '错误信息';
COMMENT ON COLUMN vector_deployments.started_at IS '部署开始时间';
COMMENT ON COLUMN vector_deployments.finished_at IS '部署完成时间';
COMMENT ON COLUMN vector_deployments.created_at IS '创建时间';
COMMENT ON COLUMN vector_deployments.created_by IS '创建人ID';
