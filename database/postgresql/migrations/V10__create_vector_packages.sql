-- Vector 安装包版本管理表
CREATE TABLE IF NOT EXISTS vector_packages (
    id VARCHAR(64) PRIMARY KEY,
    package_type VARCHAR(32) NOT NULL,  -- vector-agent, vector
    version VARCHAR(32) NOT NULL,
    os_type VARCHAR(16) NOT NULL,       -- linux, darwin
    arch VARCHAR(16) NOT NULL DEFAULT 'amd64',  -- amd64, arm64
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT,
    checksum VARCHAR(64),               -- SHA256
    download_path VARCHAR(512),
    changelog TEXT,
    is_latest BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    uploaded_by VARCHAR(64)
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_packages_type_os_arch ON vector_packages(package_type, os_type, arch);
CREATE INDEX IF NOT EXISTS idx_packages_latest ON vector_packages(package_type, os_type, arch, is_latest) WHERE is_latest = true;

-- 更新命令表，添加升级相关字段
ALTER TABLE vector_commands ADD COLUMN IF NOT EXISTS target_version VARCHAR(32);
ALTER TABLE vector_commands ADD COLUMN IF NOT EXISTS package_id VARCHAR(64);

COMMENT ON TABLE vector_packages IS 'Vector 安装包版本管理表';
COMMENT ON COLUMN vector_packages.package_type IS '包类型: vector-agent, vector';
COMMENT ON COLUMN vector_packages.os_type IS '操作系统: linux, darwin';
COMMENT ON COLUMN vector_packages.arch IS 'CPU架构: amd64, arm64';
COMMENT ON COLUMN vector_packages.checksum IS 'SHA256 校验和';
