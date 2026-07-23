--liquibase formatted sql

--changeset codex:20260723-01-create-default-syslog-table
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

--changeset codex:20260723-02-add-default-syslog-indexes
ALTER TABLE syslog
    ADD INDEX IF NOT EXISTS idx_syslog_severity severity TYPE set(0) GRANULARITY 4;
ALTER TABLE syslog
    ADD INDEX IF NOT EXISTS idx_syslog_hostname hostname TYPE bloom_filter(0.01) GRANULARITY 4;
ALTER TABLE syslog
    ADD INDEX IF NOT EXISTS idx_syslog_appname appname TYPE bloom_filter(0.01) GRANULARITY 4;
ALTER TABLE syslog
    ADD INDEX IF NOT EXISTS idx_syslog_source_type source_type TYPE set(0) GRANULARITY 4;
ALTER TABLE syslog
    ADD INDEX IF NOT EXISTS idx_syslog_facility facility TYPE set(0) GRANULARITY 4;
ALTER TABLE syslog
    ADD INDEX IF NOT EXISTS idx_syslog_source_ip source_ip TYPE bloom_filter(0.01) GRANULARITY 4;

--changeset codex:20260723-03-create-default-syslog-hourly-view
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

--changeset codex:20260723-04-create-default-syslog-daily-view
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
