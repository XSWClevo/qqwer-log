-- ClickHouse Docker 初始化总脚本
-- 说明：
-- 1. 合并当前后端默认查询依赖的表结构
-- 2. 仅包含项目实际使用到的 syslog / vector_logs / machine_metrics
-- 3. 测试数据脚本仍保留在原目录，需要时可单独执行

-- ============================================================================
-- 默认日志表
-- ============================================================================

CREATE TABLE IF NOT EXISTS syslog (
    id String DEFAULT generateUUIDv4() COMMENT '日志ID',
    severity String COMMENT '等级',
    hostname String COMMENT '主机名称',
    appname String COMMENT '应用名',
    source_type String COMMENT '源类型',
    message String COMMENT '日志消息内容',
    facility String COMMENT '日志来源类型',
    procid String COMMENT '进程ID',
    source_ip String COMMENT '源地址',
    timestamp DateTime DEFAULT now() COMMENT '时间戳',
    raw String COMMENT '原始日志'
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (timestamp, id)
TTL timestamp + toIntervalDay(180)
SETTINGS index_granularity = 8192;

ALTER TABLE syslog
    ADD INDEX IF NOT EXISTS idx_severity severity TYPE set(0) GRANULARITY 4;
ALTER TABLE syslog
    ADD INDEX IF NOT EXISTS idx_hostname hostname TYPE bloom_filter(0.01) GRANULARITY 4;
ALTER TABLE syslog
    ADD INDEX IF NOT EXISTS idx_appname appname TYPE bloom_filter(0.01) GRANULARITY 4;
ALTER TABLE syslog
    ADD INDEX IF NOT EXISTS idx_source_type source_type TYPE set(0) GRANULARITY 4;
ALTER TABLE syslog
    ADD INDEX IF NOT EXISTS idx_facility facility TYPE set(0) GRANULARITY 4;
ALTER TABLE syslog
    ADD INDEX IF NOT EXISTS idx_source_ip source_ip TYPE bloom_filter(0.01) GRANULARITY 4;

CREATE MATERIALIZED VIEW IF NOT EXISTS syslog_stats_hourly
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(hour)
ORDER BY (hour, severity, hostname, appname)
AS SELECT
    toStartOfHour(timestamp) AS hour,
    severity,
    hostname,
    appname,
    count() AS log_count
FROM syslog
GROUP BY hour, severity, hostname, appname;

CREATE MATERIALIZED VIEW IF NOT EXISTS syslog_stats_daily
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(day)
ORDER BY (day, severity, hostname)
AS SELECT
    toDate(timestamp) AS day,
    severity,
    hostname,
    count() AS log_count,
    uniqExact(appname) AS unique_apps
FROM syslog
GROUP BY day, severity, hostname;

-- ============================================================================
-- Vector 运行日志
-- ============================================================================

CREATE TABLE IF NOT EXISTS vector_logs (
    id String DEFAULT generateUUIDv4(),
    machine_id String,
    hostname String,
    ip_address String,
    log_level LowCardinality(String),
    message String,
    timestamp DateTime64(3),
    raw_log String,
    metadata String DEFAULT '',
    created_at DateTime DEFAULT now()
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (timestamp, machine_id, log_level)
TTL timestamp + INTERVAL 180 DAY
SETTINGS index_granularity = 8192;

ALTER TABLE vector_logs
    ADD INDEX IF NOT EXISTS idx_vector_logs_timestamp timestamp TYPE minmax GRANULARITY 4;
ALTER TABLE vector_logs
    ADD INDEX IF NOT EXISTS idx_vector_logs_log_level log_level TYPE set(10) GRANULARITY 4;
ALTER TABLE vector_logs
    ADD INDEX IF NOT EXISTS idx_vector_logs_hostname hostname TYPE bloom_filter GRANULARITY 4;
ALTER TABLE vector_logs
    ADD INDEX IF NOT EXISTS idx_vector_logs_ip_address ip_address TYPE bloom_filter GRANULARITY 4;
ALTER TABLE vector_logs
    ADD INDEX IF NOT EXISTS idx_vector_logs_message message TYPE tokenbf_v1(32768, 3, 0) GRANULARITY 4;

-- ============================================================================
-- 机器指标
-- ============================================================================

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
    network_interfaces String DEFAULT '[]' COMMENT '网卡信息(JSON数组)',
    created_at DateTime DEFAULT now() COMMENT '记录创建时间'
) ENGINE = MergeTree()
PARTITION BY toYYYYMMDD(collected_at)
ORDER BY (machine_id, collected_at)
TTL collected_at + toIntervalDay(7)
SETTINGS index_granularity = 8192;

ALTER TABLE machine_metrics
    ADD COLUMN IF NOT EXISTS network_interfaces String DEFAULT '[]' COMMENT '网卡信息(JSON数组)';
ALTER TABLE machine_metrics
    ADD INDEX IF NOT EXISTS idx_machine_metrics_machine_id machine_id TYPE set(0) GRANULARITY 4;

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
