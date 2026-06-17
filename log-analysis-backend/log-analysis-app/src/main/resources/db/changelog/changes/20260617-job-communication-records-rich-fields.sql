--liquibase formatted sql

--changeset codex:20260617-04-add-job-communication-rich-fields
ALTER TABLE job_communication_records
    ADD COLUMN IF NOT EXISTS company_logo TEXT,
    ADD COLUMN IF NOT EXISTS company_industry VARCHAR(255),
    ADD COLUMN IF NOT EXISTS company_size VARCHAR(255),
    ADD COLUMN IF NOT EXISTS hr_title VARCHAR(255),
    ADD COLUMN IF NOT EXISTS salary_range_normalized VARCHAR(255),
    ADD COLUMN IF NOT EXISTS last_message_content TEXT,
    ADD COLUMN IF NOT EXISTS last_message_role VARCHAR(32),
    ADD COLUMN IF NOT EXISTS last_message_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS conversation_timeline TEXT;
