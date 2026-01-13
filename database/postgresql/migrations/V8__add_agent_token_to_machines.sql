-- 为 vector_machines 表添加 agent_token 字段
ALTER TABLE vector_machines ADD COLUMN IF NOT EXISTS agent_token VARCHAR(100);

-- 添加索引
CREATE INDEX IF NOT EXISTS idx_machines_agent_token ON vector_machines(agent_token);

-- 添加注释
COMMENT ON COLUMN vector_machines.agent_token IS 'Agent认证Token';
