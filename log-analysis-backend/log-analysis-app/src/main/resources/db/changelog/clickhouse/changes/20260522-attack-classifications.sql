--liquibase formatted sql

--changeset codex:20260522-01-create-attack-classifications
CREATE TABLE IF NOT EXISTS attack_classifications (
    id String DEFAULT generateUUIDv4(),
    classification_key String,
    datasource_type LowCardinality(String),
    datasource_id String DEFAULT '',
    database_name String DEFAULT '',
    table_name String DEFAULT '',
    index_name String DEFAULT '',
    log_fingerprint String,
    log_timestamp DateTime64(3),
    source_ip String DEFAULT '',
    hostname String DEFAULT '',
    message String DEFAULT '',
    raw String DEFAULT '',
    attack_type LowCardinality(String),
    attack_sub_type LowCardinality(String) DEFAULT '',
    severity LowCardinality(String),
    confidence Float32 DEFAULT 0,
    rule_id String,
    rule_name String,
    reason String DEFAULT '',
    mitre_tactic String DEFAULT '',
    mitre_technique String DEFAULT '',
    status LowCardinality(String) DEFAULT 'unconfirmed',
    classified_at DateTime64(3) DEFAULT now64()
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(classified_at)
ORDER BY (classified_at, attack_type, database_name, table_name, classification_key)
TTL classified_at + toIntervalDay(180)
SETTINGS index_granularity = 8192;

--changeset codex:20260522-02-add-attack-classification-indexes
ALTER TABLE attack_classifications ADD INDEX IF NOT EXISTS idx_attack_classification_key classification_key TYPE bloom_filter GRANULARITY 4;
ALTER TABLE attack_classifications ADD INDEX IF NOT EXISTS idx_attack_source_ip source_ip TYPE bloom_filter GRANULARITY 4;
ALTER TABLE attack_classifications ADD INDEX IF NOT EXISTS idx_attack_hostname hostname TYPE set(100) GRANULARITY 4;
ALTER TABLE attack_classifications ADD INDEX IF NOT EXISTS idx_attack_rule_id rule_id TYPE set(100) GRANULARITY 4;
