-- 机器指标表
-- 用于存储 Vector Agent 上报的系统指标

USE MWLOGDB_ANALYSIS;

-- 机器指标表
CREATE TABLE IF NOT EXISTS machine_metrics (
    machine_id String COMMENT '机器ID',
    collected_at DateTime COMMENT '采集时间',
    cpu_usage_percent Float64 COMMENT 'CPU使用率(%)',
    memory_usage_percent Float64 COMMENT '内存使用率(%)',
    memory_used_mb Int64 COMMENT '已用内存(MB)',
    disk_usage_percent Float64 COMMENT '磁盘使用率(%)',
    disk_used_gb Int64 COMMENT '已用磁盘(GB)',
    agent_memory_mb Int32 COMMENT 'Agent内存占用(MB)',
    vector_running UInt8 COMMENT 'Vector是否运行(0/1)',
    created_at DateTime DEFAULT now() COMMENT '记录创建时间'
) ENGINE = MergeTree()
PARTITION BY toYYYYMMDD(collected_at)
ORDER BY (machine_id, collected_at)
TTL collected_at + toIntervalDay(7)
SETTINGS index_granularity = 8192;

-- 创建索引
ALTER TABLE machine_metrics ADD INDEX idx_machine_id machine_id TYPE set(0) GRANULARITY 4;

-- 按小时聚合的物化视图（用于长期趋势分析）
CREATE MATERIALIZED VIEW IF NOT EXISTS machine_metrics_hourly
ENGINE = AggregatingMergeTree()
PARTITION BY toYYYYMM(hour)
ORDER BY (machine_id, hour)
AS SELECT
    machine_id,
    toStartOfHour(collected_at) AS hour,
    avgState(cpu_usage_percent) AS avg_cpu,
    maxState(cpu_usage_percent) AS max_cpu,
    avgState(memory_usage_percent) AS avg_memory,
    maxState(memory_usage_percent) AS max_memory,
    avgState(disk_usage_percent) AS avg_disk,
    maxState(disk_usage_percent) AS max_disk,
    count() AS sample_count
FROM machine_metrics
GROUP BY machine_id, hour;
