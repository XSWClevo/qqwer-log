-- 日志分析系统 ClickHouse 数据库初始化脚本

-- 创建数据库
CREATE DATABASE IF NOT EXISTS MWLOGDB_ANALYSIS;

-- 使用数据库
USE MWLOGDB_ANALYSIS;

-- syslog 日志表 (优化设计)
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

-- 创建索引提升查询性能
ALTER TABLE syslog ADD INDEX idx_severity severity TYPE set(0) GRANULARITY 4;
ALTER TABLE syslog ADD INDEX idx_hostname hostname TYPE set(0) GRANULARITY 4;
ALTER TABLE syslog ADD INDEX idx_appname appname TYPE set(0) GRANULARITY 4;
ALTER TABLE syslog ADD INDEX idx_source_type source_type TYPE set(0) GRANULARITY 4;

-- 插入测试数据
INSERT INTO syslog (severity, hostname, appname, source_type, message, facility, procid, source_ip, timestamp, raw)
VALUES
    ('INFO', 'server-01', 'myapp', 'application', 'Application started successfully', 'user', '1234', '192.168.1.10', now() - INTERVAL 1 HOUR, '<134>1 2024-12-10T10:00:00Z server-01 myapp 1234 - - Application started successfully'),
    ('INFO', 'server-01', 'myapp', 'application', 'User login: admin', 'auth', '1234', '192.168.1.10', now() - INTERVAL 50 MINUTE, '<86>1 2024-12-10T10:10:00Z server-01 myapp 1234 - - User login: admin'),
    ('ERROR', 'server-02', 'database', 'database', 'Database connection failed', 'daemon', '5678', '192.168.1.20', now() - INTERVAL 40 MINUTE, '<195>1 2024-12-10T10:20:00Z server-02 database 5678 - - Database connection failed'),
    ('WARN', 'server-02', 'database', 'database', 'Retrying database connection', 'daemon', '5678', '192.168.1.20', now() - INTERVAL 30 MINUTE, '<196>1 2024-12-10T10:30:00Z server-02 database 5678 - - Retrying database connection'),
    ('INFO', 'server-01', 'nginx', 'web', 'Request processed in 150ms', 'user', '9012', '192.168.1.10', now() - INTERVAL 20 MINUTE, '<134>1 2024-12-10T10:40:00Z server-01 nginx 9012 - - Request processed in 150ms'),
    ('DEBUG', 'server-03', 'myapp', 'application', 'Cache hit for key: user_123', 'user', '3456', '192.168.1.30', now() - INTERVAL 10 MINUTE, '<135>1 2024-12-10T10:50:00Z server-03 myapp 3456 - - Cache hit for key: user_123'),
    ('CRITICAL', 'server-02', 'database', 'database', 'Disk space critical: 95% used', 'daemon', '5678', '192.168.1.20', now() - INTERVAL 5 MINUTE, '<194>1 2024-12-10T10:55:00Z server-02 database 5678 - - Disk space critical: 95% used'),
    ('WARN', 'server-01', 'nginx', 'web', 'Slow response: 2.5s', 'user', '9012', '192.168.1.10', now() - INTERVAL 3 MINUTE, '<132>1 2024-12-10T10:57:00Z server-01 nginx 9012 - - Slow response: 2.5s'),
    ('INFO', 'server-03', 'myapp', 'application', 'Task completed: export_report', 'user', '3456', '192.168.1.30', now() - INTERVAL 2 MINUTE, '<134>1 2024-12-10T10:58:00Z server-03 myapp 3456 - - Task completed: export_report'),
    ('ERROR', 'server-01', 'myapp', 'application', 'Failed to send email notification', 'mail', '1234', '192.168.1.10', now() - INTERVAL 1 MINUTE, '<147>1 2024-12-10T10:59:00Z server-01 myapp 1234 - - Failed to send email notification');

-- 创建物化视图用于实时统计
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

-- 创建物化视图用于按日统计
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
