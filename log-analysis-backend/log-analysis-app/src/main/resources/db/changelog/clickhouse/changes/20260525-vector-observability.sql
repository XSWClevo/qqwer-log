--liquibase formatted sql

--changeset codex:20260525-01-vector-machine-metrics
CREATE TABLE IF NOT EXISTS machine_metrics (
    machine_id String,
    collected_at DateTime DEFAULT now(),
    cpu_usage_percent Float64,
    memory_usage_percent Float64,
    memory_used_mb UInt64,
    disk_usage_percent Float64,
    disk_used_gb UInt64,
    agent_memory_mb UInt32,
    vector_running UInt8,
    network_interfaces String,
    created_at DateTime DEFAULT now()
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(collected_at)
ORDER BY (machine_id, collected_at)
TTL collected_at + toIntervalDay(30)
SETTINGS index_granularity = 8192;

--changeset codex:20260525-02-vector-pipeline-metrics
CREATE TABLE IF NOT EXISTS vector_pipeline_metrics (
    id String,
    machine_id String,
    source_name String,
    transform_name String,
    sink_name String,
    events_in UInt64,
    events_out UInt64,
    bytes_in UInt64,
    bytes_out UInt64,
    errors UInt32,
    latency_ms Decimal(18, 3),
    recorded_at DateTime DEFAULT now()
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(recorded_at)
ORDER BY (machine_id, recorded_at, source_name, sink_name)
TTL recorded_at + toIntervalDay(30)
SETTINGS index_granularity = 8192;

--changeset codex:20260525-03-vector-component-errors
CREATE TABLE IF NOT EXISTS vector_component_errors (
    machine_id String,
    component_id String,
    component_type String,
    component_kind String,
    error_code String,
    error_message String,
    error_count UInt64,
    observed_at DateTime DEFAULT now()
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(observed_at)
ORDER BY (machine_id, component_id, observed_at)
TTL observed_at + toIntervalDay(30)
SETTINGS index_granularity = 8192;

--changeset codex:20260525-04-vector-component-throughput
CREATE TABLE IF NOT EXISTS vector_component_throughput (
    machine_id String,
    component_id String,
    component_type String,
    events_in_rate Float64,
    events_out_rate Float64,
    bytes_in_rate Float64,
    bytes_out_rate Float64,
    utilization_percent Float64,
    observed_at DateTime DEFAULT now()
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(observed_at)
ORDER BY (machine_id, component_id, observed_at)
TTL observed_at + toIntervalDay(30)
SETTINGS index_granularity = 8192;
