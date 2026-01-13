-- 为组件库表添加可视化配置数据字段
ALTER TABLE vector_config_components ADD COLUMN IF NOT EXISTS visual_data TEXT;

COMMENT ON COLUMN vector_config_components.visual_data IS '可视化配置数据（JSON格式）';
