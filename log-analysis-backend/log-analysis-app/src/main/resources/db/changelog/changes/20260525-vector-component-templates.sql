--liquibase formatted sql

--changeset codex:20260525-00-enable-pgcrypto
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

--changeset codex:20260525-00-create-vector-config-components
CREATE TABLE IF NOT EXISTS vector_config_components (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::VARCHAR,
    name VARCHAR(100) NOT NULL,
    component_type VARCHAR(20) NOT NULL,
    vector_type VARCHAR(50) NOT NULL,
    config_yaml TEXT NOT NULL,
    visual_data TEXT,
    description TEXT,
    is_template BOOLEAN DEFAULT TRUE,
    queryable BOOLEAN DEFAULT FALSE,
    display_name VARCHAR(100),
    datasource_id VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    CONSTRAINT uk_vector_config_components_name UNIQUE (name)
);

CREATE INDEX IF NOT EXISTS idx_vector_config_components_datasource_id
    ON vector_config_components(datasource_id);
CREATE INDEX IF NOT EXISTS idx_vector_config_components_queryable
    ON vector_config_components(component_type, queryable)
    WHERE queryable = TRUE;

--changeset codex:20260525-01-vector-component-templates
INSERT INTO vector_config_components (
            id,
            name,
            component_type,
            vector_type,
            config_yaml,
            visual_data,
            description,
            is_template,
            queryable,
            display_name,
            datasource_id,
            created_at,
            updated_at,
            created_by
        ) VALUES
        (
            'template-source-syslog',
            'Syslog Source Template',
            'source',
            'syslog',
            $yaml$type: syslog
address: 0.0.0.0:514
mode: udp
max_length: 102400
$yaml$,
            '{"ports":[514],"protocol":"udp","template":true}'::jsonb::text,
            'Vector syslog source template for UDP syslog ingestion.',
            TRUE,
            FALSE,
            'Syslog Source',
            NULL,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP,
            'system'
        ),
        (
            'template-source-file',
            'File Source Template',
            'source',
            'file',
            $yaml$type: file
include:
  - /var/log/*.log
read_from: beginning
ignore_older_secs: 86400
$yaml$,
            '{"paths":["/var/log/*.log"],"template":true}'::jsonb::text,
            'Vector file source template for local log files.',
            TRUE,
            FALSE,
            'File Source',
            NULL,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP,
            'system'
        ),
        (
            'template-transform-remap',
            'Normalize Log Remap Template',
            'transform',
            'remap',
            $yaml$type: remap
source: |
  .timestamp = now()
  .message = string!(.message)
  .source_type = "vector"
$yaml$,
            '{"language":"vrl","template":true}'::jsonb::text,
            'Vector remap transform template for normalized log fields.',
            TRUE,
            FALSE,
            'Normalize Remap',
            NULL,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP,
            'system'
        ),
        (
            'template-sink-clickhouse',
            'ClickHouse Sink Template',
            'sink',
            'clickhouse',
            $yaml$type: clickhouse
endpoint: http://localhost:8123
database: default
table: syslog
compression: gzip
skip_unknown_fields: true
auth:
  strategy: basic
  user: default
  password: ""
$yaml$,
            '{"database":"default","table":"syslog","template":true}'::jsonb::text,
            'Queryable ClickHouse sink template for log search and alert evaluation.',
            TRUE,
            TRUE,
            'ClickHouse Logs',
            'template-sink-clickhouse',
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP,
            'system'
        ),
        (
            'template-sink-console',
            'Console Sink Template',
            'sink',
            'console',
            $yaml$type: console
encoding:
  codec: json
$yaml$,
            '{"codec":"json","template":true}'::jsonb::text,
            'Console sink template for debugging pipelines.',
            TRUE,
            FALSE,
            'Console Debug',
            NULL,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP,
            'system'
        )
        ON CONFLICT (id) DO UPDATE SET
            name = EXCLUDED.name,
            component_type = EXCLUDED.component_type,
            vector_type = EXCLUDED.vector_type,
            config_yaml = EXCLUDED.config_yaml,
            visual_data = EXCLUDED.visual_data,
            description = EXCLUDED.description,
            is_template = EXCLUDED.is_template,
            queryable = EXCLUDED.queryable,
            display_name = EXCLUDED.display_name,
            datasource_id = EXCLUDED.datasource_id,
            updated_at = CURRENT_TIMESTAMP,
            created_by = EXCLUDED.created_by;
