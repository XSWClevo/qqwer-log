-- 更新 vector_visual_configs 表结构以支持流程图编辑器
-- 创建时间: 2025-12-26

-- 添加新字段
ALTER TABLE vector_visual_configs 
ADD COLUMN IF NOT EXISTS graph_data TEXT,
ADD COLUMN IF NOT EXISTS content TEXT,
ADD COLUMN IF NOT EXISTS node_count INTEGER DEFAULT 0;

-- 迁移旧数据：将 flow_data 转换为 graph_data
UPDATE vector_visual_configs 
SET graph_data = flow_data::TEXT 
WHERE graph_data IS NULL AND flow_data IS NOT NULL;

-- 删除旧字段（可选，保留以兼容）
-- ALTER TABLE vector_visual_configs DROP COLUMN IF EXISTS flow_data;

-- 更新注释
COMMENT ON COLUMN vector_visual_configs.graph_data IS '流程图数据 (JSON格式，AntV X6)';
COMMENT ON COLUMN vector_visual_configs.content IS '生成的配置内容 (YAML/TOML)';
COMMENT ON COLUMN vector_visual_configs.node_count IS '节点数量';
