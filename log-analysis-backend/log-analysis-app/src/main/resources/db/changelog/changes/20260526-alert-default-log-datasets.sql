--liquibase formatted sql

--changeset codex:20260526-01-alert-default-syslog-dataset
INSERT INTO log_category_registry (
    category_code,
    category_name,
    datasource_id,
    table_name,
    database_name,
    time_field,
    message_field,
    raw_field,
    severity_field,
    source_ip_field,
    appname_field,
    hostname_field,
    extra_mapping,
    enabled,
    priority,
    created_at,
    updated_at
) VALUES (
    'syslog',
    'Syslog Logs',
    '',
    'syslog_logs',
    NULL,
    'timestamp',
    'message',
    'raw',
    'severity',
    'source_ip',
    'appname',
    'hostname',
    '{}'::jsonb,
    TRUE,
    10,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (category_code, datasource_id, table_name) DO UPDATE SET
    category_name = EXCLUDED.category_name,
    database_name = EXCLUDED.database_name,
    time_field = EXCLUDED.time_field,
    message_field = EXCLUDED.message_field,
    raw_field = EXCLUDED.raw_field,
    severity_field = EXCLUDED.severity_field,
    source_ip_field = EXCLUDED.source_ip_field,
    appname_field = EXCLUDED.appname_field,
    hostname_field = EXCLUDED.hostname_field,
    extra_mapping = EXCLUDED.extra_mapping,
    enabled = TRUE,
    priority = EXCLUDED.priority,
    updated_at = CURRENT_TIMESTAMP;
