--liquibase formatted sql

--changeset codex:20260526-02-alert-page-notification-channel
ALTER TABLE alert_rules
    ALTER COLUMN notification_channels SET DEFAULT '["page"]'::jsonb;

UPDATE alert_rules
SET notification_channels = '["page"]'::jsonb,
    updated_at = CURRENT_TIMESTAMP
WHERE notification_channels IS NULL
   OR notification_channels = '[]'::jsonb
   OR NOT (notification_channels @> '["page"]'::jsonb);
