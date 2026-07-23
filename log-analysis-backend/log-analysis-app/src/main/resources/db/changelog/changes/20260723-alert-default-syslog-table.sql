--liquibase formatted sql

--changeset codex:20260723-01-align-default-alert-syslog-table
UPDATE log_category_registry
SET table_name = 'syslog',
    updated_at = CURRENT_TIMESTAMP
WHERE category_code = 'syslog'
  AND datasource_id = ''
  AND table_name = 'syslog_logs';
