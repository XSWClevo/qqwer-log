-- 添加 demo_logs 源组件到组件库
-- demo_logs 用于生成模拟日志数据，便于测试和演示

INSERT INTO vector_config_components (name, component_type, vector_type, config_yaml, description) VALUES
('Demo日志源', 'source', 'demo_logs', 'type: demo_logs
format: apache_common
interval: 1.0
count: 100', '生成模拟日志数据，支持多种格式：apache_common, apache_error, syslog, json 等')
ON CONFLICT (name) DO NOTHING;

-- 同时更新 vector_management.sql 中的初始数据
