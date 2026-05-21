-- Vector 运行日志表（兼容旧表：存在则删除重建）
DROP TABLE IF EXISTS vector_logs;

CREATE TABLE vector_logs (
    machine_id String,
    file_name String,
    message String,
    timestamp DateTime DEFAULT now()
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (timestamp, machine_id, file_name)
TTL timestamp + toIntervalDay(30)
SETTINGS index_granularity = 8192;

ALTER TABLE vector_logs ADD INDEX IF NOT EXISTS idx_vector_logs_machine_id machine_id TYPE bloom_filter GRANULARITY 4;
ALTER TABLE vector_logs ADD INDEX IF NOT EXISTS idx_vector_logs_file_name file_name TYPE set(20) GRANULARITY 4;
