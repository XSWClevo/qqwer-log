ALTER TABLE alert_rules
    ADD COLUMN IF NOT EXISTS rule_type VARCHAR(50) DEFAULT 'aggregation',
    ADD COLUMN IF NOT EXISTS scope_type VARCHAR(30) DEFAULT 'all',
    ADD COLUMN IF NOT EXISTS category_codes JSONB DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS datasource_ids JSONB DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS table_names JSONB DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS eval_every VARCHAR(20) DEFAULT '1m',
    ADD COLUMN IF NOT EXISTS consecutive_hits INTEGER DEFAULT 1,
    ADD COLUMN IF NOT EXISTS dedup_key_fields JSONB DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS message_template TEXT;

CREATE INDEX IF NOT EXISTS idx_alert_rules_rule_type ON alert_rules(rule_type);
CREATE INDEX IF NOT EXISTS idx_alert_rules_scope_type ON alert_rules(scope_type);

CREATE TABLE IF NOT EXISTS log_category_registry (
    id BIGSERIAL PRIMARY KEY,
    category_code VARCHAR(50) NOT NULL,
    category_name VARCHAR(100) NOT NULL,
    datasource_id VARCHAR(36),
    table_name VARCHAR(255) NOT NULL,
    database_name VARCHAR(100),
    time_field VARCHAR(50) DEFAULT 'timestamp',
    message_field VARCHAR(50) DEFAULT 'message',
    raw_field VARCHAR(50) DEFAULT 'raw',
    severity_field VARCHAR(50) DEFAULT 'severity',
    source_ip_field VARCHAR(50) DEFAULT 'source_ip',
    appname_field VARCHAR(50) DEFAULT 'appname',
    hostname_field VARCHAR(50) DEFAULT 'hostname',
    extra_mapping JSONB DEFAULT '{}'::jsonb,
    enabled BOOLEAN DEFAULT TRUE,
    priority INTEGER DEFAULT 100,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_log_category_registry_category_code
    ON log_category_registry(category_code);
CREATE INDEX IF NOT EXISTS idx_log_category_registry_datasource_id
    ON log_category_registry(datasource_id);
CREATE INDEX IF NOT EXISTS idx_log_category_registry_enabled
    ON log_category_registry(enabled);
CREATE UNIQUE INDEX IF NOT EXISTS uk_log_category_registry_unique_target
    ON log_category_registry(category_code, datasource_id, table_name);
