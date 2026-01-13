-- Vector 运行日志表
-- 用于存储 Vector 自身的运行日志，支持实时查询和滚动显示
use MWLOGDB_ANALYSIS;
CREATE TABLE IF NOT EXISTS vector_logs
(
    `id` String,                      -- 日志ID
    `machine_id` String,              -- 关联的机器ID (vector_machines.id)
    `hostname` String,                -- 主机名
    `ip_address` String,              -- IP地址
    `log_level` LowCardinality(String), -- 日志级别: error, warn, info, debug, trace
    `message` String,                 -- 日志消息
    `timestamp` DateTime64(3),        -- 时间戳（毫秒精度）
    `raw_log` String,                 -- 原始日志内容
    `metadata` String DEFAULT '',     -- 额外的元数据（JSON格式）
    `created_at` DateTime DEFAULT now() -- 创建时间
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (timestamp, machine_id, log_level)
TTL timestamp + INTERVAL 180 DAY
SETTINGS index_granularity = 8192;

-- 创建索引以提升查询性能
-- 按时间范围查询
ALTER TABLE vector_logs ADD INDEX idx_timestamp timestamp TYPE minmax GRANULARITY 4;

-- 按日志级别查询
ALTER TABLE vector_logs ADD INDEX idx_log_level log_level TYPE set(10) GRANULARITY 4;

-- 按主机名查询
ALTER TABLE vector_logs ADD INDEX idx_hostname hostname TYPE bloom_filter GRANULARITY 4;

-- 按IP地址查询
ALTER TABLE vector_logs ADD INDEX idx_ip_address ip_address TYPE bloom_filter GRANULARITY 4;

-- 消息全文搜索
ALTER TABLE vector_logs ADD INDEX idx_message message TYPE tokenbf_v1(32768, 3, 0) GRANULARITY 4;

-- 查询示例
-- 1. 查询最近100条日志
-- SELECT * FROM vector_logs ORDER BY timestamp DESC LIMIT 100;

-- 2. 查询特定主机的日志
-- SELECT * FROM vector_logs WHERE hostname = 'web-server-01' ORDER BY timestamp DESC LIMIT 100;

-- 3. 查询错误日志
-- SELECT * FROM vector_logs WHERE log_level = 'error' ORDER BY timestamp DESC LIMIT 100;

-- 4. 搜索包含关键词的日志
-- SELECT * FROM vector_logs WHERE message LIKE '%error%' ORDER BY timestamp DESC LIMIT 100;

-- 5. 统计各主机的日志数量
-- SELECT hostname, count() as cnt FROM vector_logs GROUP BY hostname ORDER BY cnt DESC;
