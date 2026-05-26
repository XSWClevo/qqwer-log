--liquibase formatted sql

--changeset codex:20260526-03-alert-monitor-state-datasource-key
DELETE FROM alert_monitor_states state
USING (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY rule_id, COALESCE(datasource_id, ''), table_name, group_key
               ORDER BY updated_at DESC, id DESC
           ) AS row_number
    FROM alert_monitor_states
) duplicate_state
WHERE state.id = duplicate_state.id
  AND duplicate_state.row_number > 1;

UPDATE alert_monitor_states
SET datasource_id = ''
WHERE datasource_id IS NULL;

ALTER TABLE alert_monitor_states
    ALTER COLUMN datasource_id SET DEFAULT '',
    ALTER COLUMN datasource_id SET NOT NULL;
