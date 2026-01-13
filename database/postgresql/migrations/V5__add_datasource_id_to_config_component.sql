-- 为 vector_config_components 表添加 datasource_id 字段
-- 用于 Sinks 组件关联数据源

ALTER TABLE vector_config_components
ADD COLUMN IF NOT EXISTS datasource_id VARCHAR(36);

-- 添加外键约束（可选）
-- ALTER TABLE vector_config_components
-- ADD CONSTRAINT fk_vector_config_components_datasource
-- FOREIGN KEY (datasource_id) REFERENCES datasources(id) ON DELETE SET NULL;

-- 添加索引
CREATE INDEX IF NOT EXISTS idx_vector_config_components_datasource_id ON vector_config_components(datasource_id);

-- 添加注释
COMMENT ON COLUMN vector_config_components.datasource_id IS '关联的数据源ID（仅 Sinks 组件使用，用于引用已配置的数据源）';
