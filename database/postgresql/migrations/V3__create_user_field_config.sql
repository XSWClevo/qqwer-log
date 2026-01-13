-- 用户字段配置表
CREATE TABLE IF NOT EXISTS user_field_config (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    config_type VARCHAR(50) NOT NULL,
    selected_fields JSONB NOT NULL DEFAULT '[]'::jsonb,
    field_order JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (username, config_type)
);

-- 添加索引
CREATE INDEX IF NOT EXISTS idx_user_field_config_username ON user_field_config(username);
CREATE INDEX IF NOT EXISTS idx_user_field_config_type ON user_field_config(config_type);

-- 添加注释
COMMENT ON TABLE user_field_config IS '用户字段配置表';
COMMENT ON COLUMN user_field_config.id IS '主键ID';
COMMENT ON COLUMN user_field_config.username IS '用户名';
COMMENT ON COLUMN user_field_config.config_type IS '配置类型(如：log_list)';
COMMENT ON COLUMN user_field_config.selected_fields IS '已选择的字段列表(JSON数组)';
COMMENT ON COLUMN user_field_config.field_order IS '字段顺序(JSON数组)';
COMMENT ON COLUMN user_field_config.created_at IS '创建时间';
COMMENT ON COLUMN user_field_config.updated_at IS '更新时间';
