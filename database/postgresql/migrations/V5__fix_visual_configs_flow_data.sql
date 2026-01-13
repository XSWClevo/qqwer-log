-- 修改 flow_data 字段类型为 TEXT，避免 jsonb 类型转换问题
ALTER TABLE vector_visual_configs 
ALTER COLUMN flow_data TYPE TEXT USING flow_data::TEXT;

-- 允许 flow_data 为空
ALTER TABLE vector_visual_configs 
ALTER COLUMN flow_data DROP NOT NULL;
