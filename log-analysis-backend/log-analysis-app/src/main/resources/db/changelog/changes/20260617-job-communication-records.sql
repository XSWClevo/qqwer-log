--liquibase formatted sql

--changeset codex:20260617-01-create-job-communication-records
CREATE TABLE IF NOT EXISTS job_communication_records (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    platform VARCHAR(32) NOT NULL DEFAULT 'BOSS',
    job_id VARCHAR(128) NOT NULL,
    job_title VARCHAR(255),
    company_name VARCHAR(255),
    job_location VARCHAR(255),
    salary_range VARCHAR(255),
    job_url TEXT,
    hr_name VARCHAR(255),
    hr_key VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'CONTACTED',
    first_communicated_at TIMESTAMP,
    last_replied_at TIMESTAMP,
    last_status_changed_at TIMESTAMP,
    communication_count INTEGER NOT NULL DEFAULT 0,
    source_payload TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_job_communication_user_platform_job UNIQUE (user_id, platform, job_id)
);

--changeset codex:20260617-02-index-job-communication-records
CREATE INDEX IF NOT EXISTS idx_job_communication_user_status
ON job_communication_records(user_id, status);

--changeset codex:20260617-03-index-job-communication-created-updated
CREATE INDEX IF NOT EXISTS idx_job_communication_created_updated
ON job_communication_records(user_id, created_at DESC, updated_at DESC);
