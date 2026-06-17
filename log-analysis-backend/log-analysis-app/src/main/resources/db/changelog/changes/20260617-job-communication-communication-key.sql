--liquibase formatted sql

--changeset codex:20260617-05-add-job-communication-key
ALTER TABLE job_communication_records
    ADD COLUMN IF NOT EXISTS communication_key VARCHAR(512);

--changeset codex:20260617-06-backfill-job-communication-key
UPDATE job_communication_records
SET communication_key = CONCAT_WS(
    ':',
    platform,
    job_id,
    NULLIF(TRIM(company_name), ''),
    NULLIF(TRIM(COALESCE(hr_name, hr_key)), '')
)
WHERE communication_key IS NULL;

--changeset codex:20260617-07-index-job-communication-key
CREATE UNIQUE INDEX IF NOT EXISTS uk_job_communication_user_platform_key
ON job_communication_records(user_id, platform, communication_key)
WHERE communication_key IS NOT NULL;
