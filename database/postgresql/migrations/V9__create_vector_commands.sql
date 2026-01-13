-- Vector 控制命令表
CREATE TABLE IF NOT EXISTS vector_commands (
    id VARCHAR(64) PRIMARY KEY,
    machine_id VARCHAR(64) NOT NULL,
    command_type VARCHAR(32) NOT NULL,  -- start_vector, stop_vector, restart_vector, reload_vector
    status VARCHAR(16) NOT NULL DEFAULT 'pending',  -- pending, executing, success, failed
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    executed_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_by VARCHAR(64),
    
    CONSTRAINT fk_command_machine FOREIGN KEY (machine_id) REFERENCES vector_machines(id) ON DELETE CASCADE
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_commands_machine_status ON vector_commands(machine_id, status);
CREATE INDEX IF NOT EXISTS idx_commands_created_at ON vector_commands(created_at DESC);

COMMENT ON TABLE vector_commands IS 'Vector 控制命令表';
COMMENT ON COLUMN vector_commands.command_type IS '命令类型: start_vector, stop_vector, restart_vector, reload_vector';
COMMENT ON COLUMN vector_commands.status IS '状态: pending-待执行, executing-执行中, success-成功, failed-失败';
