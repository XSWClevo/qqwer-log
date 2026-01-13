-- 日志分析系统 - 指标和链路追踪表
-- 用于支持趋势分析和链路分析页面

USE MWLOGDB_ANALYSIS;

-- ==================== 系统指标表 ====================
-- 存储系统级别的指标数据（CPU、内存、磁盘、网络等）

CREATE TABLE IF NOT EXISTS system_metrics (
    id String DEFAULT generateUUIDv4() COMMENT '指标ID',
    hostname String COMMENT '主机名',
    metric_name String COMMENT '指标名称 (cpu.usage, memory.usage, disk.usage, network.in, network.out)',
    metric_value Float64 COMMENT '指标值',
    unit String COMMENT '单位 (percent, bytes, bytes/s)',
    tags Map(String, String) COMMENT '额外标签',
    timestamp DateTime DEFAULT now() COMMENT '采集时间'
) ENGINE = MergeTree()
PARTITION BY toYYYYMMDD(timestamp)
ORDER BY (hostname, metric_name, timestamp)
TTL timestamp + toIntervalDay(30)
SETTINGS index_granularity = 8192;

-- 创建索引
ALTER TABLE system_metrics ADD INDEX idx_metric_name metric_name TYPE set(0) GRANULARITY 4;
ALTER TABLE system_metrics ADD INDEX idx_hostname hostname TYPE set(0) GRANULARITY 4;

-- ==================== 应用指标表 ====================
-- 存储应用级别的指标数据（HTTP请求、延迟等）

CREATE TABLE IF NOT EXISTS app_metrics (
    id String DEFAULT generateUUIDv4() COMMENT '指标ID',
    appname String COMMENT '应用名',
    hostname String COMMENT '主机名',
    metric_name String COMMENT '指标名称 (http.requests, http.latency, http.errors)',
    metric_value Float64 COMMENT '指标值',
    unit String COMMENT '单位 (count, ms, percent)',
    tags Map(String, String) COMMENT '额外标签 (method, path, status_code)',
    timestamp DateTime DEFAULT now() COMMENT '采集时间'
) ENGINE = MergeTree()
PARTITION BY toYYYYMMDD(timestamp)
ORDER BY (appname, metric_name, timestamp)
TTL timestamp + toIntervalDay(30)
SETTINGS index_granularity = 8192;

-- 创建索引
ALTER TABLE app_metrics ADD INDEX idx_app_metric_name metric_name TYPE set(0) GRANULARITY 4;
ALTER TABLE app_metrics ADD INDEX idx_app_appname appname TYPE set(0) GRANULARITY 4;

-- ==================== 分布式追踪表 ====================
-- 存储 Trace 和 Span 数据，支持链路分析

CREATE TABLE IF NOT EXISTS traces (
    trace_id String COMMENT '追踪ID',
    span_id String COMMENT '跨度ID',
    parent_span_id String DEFAULT '' COMMENT '父跨度ID (根跨度为空)',
    operation_name String COMMENT '操作名称',
    service_name String COMMENT '服务名称',
    span_kind String DEFAULT 'internal' COMMENT '跨度类型 (client, server, producer, consumer, internal)',
    start_time DateTime64(3) COMMENT '开始时间 (毫秒精度)',
    end_time DateTime64(3) COMMENT '结束时间 (毫秒精度)',
    duration_ms UInt64 COMMENT '持续时间 (毫秒)',
    status_code String DEFAULT 'OK' COMMENT '状态码 (OK, ERROR, UNSET)',
    status_message String DEFAULT '' COMMENT '状态消息',
    tags Map(String, String) COMMENT '标签 (http.method, http.url, db.type 等)',
    logs Array(Tuple(timestamp DateTime64(3), message String)) COMMENT '日志事件',
    process_hostname String COMMENT '进程主机名',
    process_ip String COMMENT '进程IP',
    process_version String DEFAULT '' COMMENT '服务版本',
    timestamp DateTime DEFAULT now() COMMENT '记录时间'
) ENGINE = MergeTree()
PARTITION BY toYYYYMMDD(timestamp)
ORDER BY (trace_id, start_time, span_id)
TTL timestamp + toIntervalDay(7)
SETTINGS index_granularity = 8192;

-- 创建索引
ALTER TABLE traces ADD INDEX idx_trace_id trace_id TYPE bloom_filter() GRANULARITY 4;
ALTER TABLE traces ADD INDEX idx_service_name service_name TYPE set(0) GRANULARITY 4;
ALTER TABLE traces ADD INDEX idx_status_code status_code TYPE set(0) GRANULARITY 4;

-- ==================== 物化视图：指标聚合 ====================

-- 系统指标按分钟聚合
CREATE MATERIALIZED VIEW IF NOT EXISTS system_metrics_1m
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMMDD(minute)
ORDER BY (hostname, metric_name, minute)
AS SELECT
    toStartOfMinute(timestamp) AS minute,
    hostname,
    metric_name,
    avg(metric_value) AS avg_value,
    max(metric_value) AS max_value,
    min(metric_value) AS min_value,
    count() AS sample_count
FROM system_metrics
GROUP BY minute, hostname, metric_name;

-- 应用指标按分钟聚合
CREATE MATERIALIZED VIEW IF NOT EXISTS app_metrics_1m
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMMDD(minute)
ORDER BY (appname, metric_name, minute)
AS SELECT
    toStartOfMinute(timestamp) AS minute,
    appname,
    hostname,
    metric_name,
    avg(metric_value) AS avg_value,
    max(metric_value) AS max_value,
    min(metric_value) AS min_value,
    quantile(0.95)(metric_value) AS p95_value,
    quantile(0.99)(metric_value) AS p99_value,
    count() AS sample_count
FROM app_metrics
GROUP BY minute, appname, hostname, metric_name;

-- ==================== 物化视图：Trace 服务统计 ====================

CREATE MATERIALIZED VIEW IF NOT EXISTS trace_service_stats
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMMDD(hour)
ORDER BY (service_name, hour)
AS SELECT
    toStartOfHour(timestamp) AS hour,
    service_name,
    count() AS span_count,
    countIf(status_code = 'ERROR') AS error_count,
    avg(duration_ms) AS avg_duration,
    quantile(0.95)(duration_ms) AS p95_duration,
    quantile(0.99)(duration_ms) AS p99_duration
FROM traces
GROUP BY hour, service_name;

-- ==================== 插入测试数据 ====================

-- 系统指标测试数据
INSERT INTO system_metrics (hostname, metric_name, metric_value, unit, timestamp)
SELECT
    arrayElement(['server-01', 'server-02', 'server-03'], (number % 3) + 1) AS hostname,
    arrayElement(['cpu.usage', 'memory.usage', 'disk.usage'], (number % 3) + 1) AS metric_name,
    rand() % 100 AS metric_value,
    'percent' AS unit,
    now() - toIntervalMinute(number) AS timestamp
FROM numbers(1000);

-- 应用指标测试数据
INSERT INTO app_metrics (appname, hostname, metric_name, metric_value, unit, timestamp)
SELECT
    arrayElement(['frontend-gateway', 'order-service', 'payment-service', 'user-service'], (number % 4) + 1) AS appname,
    arrayElement(['server-01', 'server-02', 'server-03'], (number % 3) + 1) AS hostname,
    arrayElement(['http.requests', 'http.latency', 'http.errors'], (number % 3) + 1) AS metric_name,
    CASE 
        WHEN (number % 3) = 0 THEN rand() % 1000  -- requests count
        WHEN (number % 3) = 1 THEN rand() % 500   -- latency ms
        ELSE rand() % 10                           -- error count
    END AS metric_value,
    arrayElement(['count', 'ms', 'count'], (number % 3) + 1) AS unit,
    now() - toIntervalMinute(number) AS timestamp
FROM numbers(1000);

-- Trace 测试数据
INSERT INTO traces (trace_id, span_id, parent_span_id, operation_name, service_name, span_kind, start_time, end_time, duration_ms, status_code, tags, process_hostname, process_ip, process_version, timestamp)
VALUES
    -- Trace 1: 正常请求
    ('trace-001', 'span-001', '', 'HTTP GET /api/orders', 'frontend-gateway', 'server', now() - INTERVAL 1 HOUR, now() - INTERVAL 1 HOUR + INTERVAL 1450 MILLISECOND, 1450, 'OK', {'http.method': 'GET', 'http.url': '/api/orders', 'http.status_code': '200'}, 'gateway-01', '10.0.1.10', '2.1.0', now() - INTERVAL 1 HOUR),
    ('trace-001', 'span-002', 'span-001', 'getOrders', 'order-service', 'server', now() - INTERVAL 1 HOUR + INTERVAL 50 MILLISECOND, now() - INTERVAL 1 HOUR + INTERVAL 1250 MILLISECOND, 1200, 'OK', {'component': 'spring-boot'}, 'order-01', '10.0.2.20', '1.5.0', now() - INTERVAL 1 HOUR),
    ('trace-001', 'span-003', 'span-002', 'getUserInfo', 'user-service', 'server', now() - INTERVAL 1 HOUR + INTERVAL 100 MILLISECOND, now() - INTERVAL 1 HOUR + INTERVAL 250 MILLISECOND, 150, 'OK', {'user.id': '12345', 'cache.hit': 'true'}, 'user-01', '10.0.3.30', '1.2.0', now() - INTERVAL 1 HOUR),
    ('trace-001', 'span-004', 'span-002', 'processPayment', 'payment-service', 'server', now() - INTERVAL 1 HOUR + INTERVAL 300 MILLISECOND, now() - INTERVAL 1 HOUR + INTERVAL 1100 MILLISECOND, 800, 'OK', {'payment.method': 'credit_card'}, 'payment-01', '10.0.4.40', '2.0.1', now() - INTERVAL 1 HOUR),
    
    -- Trace 2: 带错误的请求
    ('trace-002', 'span-010', '', 'HTTP POST /api/checkout', 'frontend-gateway', 'server', now() - INTERVAL 30 MINUTE, now() - INTERVAL 30 MINUTE + INTERVAL 2500 MILLISECOND, 2500, 'ERROR', {'http.method': 'POST', 'http.url': '/api/checkout', 'http.status_code': '500'}, 'gateway-01', '10.0.1.10', '2.1.0', now() - INTERVAL 30 MINUTE),
    ('trace-002', 'span-011', 'span-010', 'createOrder', 'order-service', 'server', now() - INTERVAL 30 MINUTE + INTERVAL 50 MILLISECOND, now() - INTERVAL 30 MINUTE + INTERVAL 2400 MILLISECOND, 2350, 'ERROR', {'component': 'spring-boot'}, 'order-01', '10.0.2.20', '1.5.0', now() - INTERVAL 30 MINUTE),
    ('trace-002', 'span-012', 'span-011', 'chargePayment', 'payment-service', 'server', now() - INTERVAL 30 MINUTE + INTERVAL 200 MILLISECOND, now() - INTERVAL 30 MINUTE + INTERVAL 2200 MILLISECOND, 2000, 'ERROR', {'error': 'true', 'error.message': 'Connection timed out to payment gateway'}, 'payment-01', '10.0.4.40', '2.0.1', now() - INTERVAL 30 MINUTE),
    
    -- Trace 3: 另一个正常请求
    ('trace-003', 'span-020', '', 'HTTP GET /api/users/123', 'frontend-gateway', 'server', now() - INTERVAL 10 MINUTE, now() - INTERVAL 10 MINUTE + INTERVAL 200 MILLISECOND, 200, 'OK', {'http.method': 'GET', 'http.url': '/api/users/123', 'http.status_code': '200'}, 'gateway-01', '10.0.1.10', '2.1.0', now() - INTERVAL 10 MINUTE),
    ('trace-003', 'span-021', 'span-020', 'getUserById', 'user-service', 'server', now() - INTERVAL 10 MINUTE + INTERVAL 30 MILLISECOND, now() - INTERVAL 10 MINUTE + INTERVAL 180 MILLISECOND, 150, 'OK', {'user.id': '123', 'cache.hit': 'false'}, 'user-01', '10.0.3.30', '1.2.0', now() - INTERVAL 10 MINUTE),
    ('trace-003', 'span-022', 'span-021', 'DB: SELECT user', 'user-service', 'client', now() - INTERVAL 10 MINUTE + INTERVAL 50 MILLISECOND, now() - INTERVAL 10 MINUTE + INTERVAL 120 MILLISECOND, 70, 'OK', {'db.type': 'postgresql', 'db.statement': 'SELECT * FROM users WHERE id = ?'}, 'user-01', '10.0.3.30', '1.2.0', now() - INTERVAL 10 MINUTE);
