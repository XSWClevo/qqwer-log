-- 为 vector_config_components 表添加数据源相关字段
-- 执行时间: 2026-01-01

-- 添加 queryable 字段（是否可作为查询数据源）
ALTER TABLE vector_config_components 
ADD COLUMN IF NOT EXISTS queryable BOOLEAN DEFAULT FALSE;

-- 添加 display_name 字段（数据源显示名称）
ALTER TABLE vector_config_components 
ADD COLUMN IF NOT EXISTS display_name VARCHAR(100);

-- 为 queryable 字段添加索引，方便查询可用数据源
CREATE INDEX IF NOT EXISTS idx_config_components_queryable 
ON vector_config_components(component_type, queryable) 
WHERE queryable = TRUE;

-- 添加注释
COMMENT ON COLUMN vector_config_components.queryable IS '是否可作为查询数据源（仅 Sink 组件有效）';
COMMENT ON COLUMN vector_config_components.display_name IS '数据源显示名称（用于日志搜索页面展示）';
