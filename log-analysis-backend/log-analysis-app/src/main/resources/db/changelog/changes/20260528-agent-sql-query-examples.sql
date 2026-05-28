--liquibase formatted sql

--changeset codex:20260528-01-create-agent-sql-query-examples
CREATE TABLE IF NOT EXISTS agent_sql_query_examples (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    datasource_id VARCHAR(36) NOT NULL,
    datasource_type VARCHAR(50) NOT NULL,
    question TEXT NOT NULL,
    normalized_question TEXT NOT NULL,
    sql_template TEXT NOT NULL,
    result_type VARCHAR(50),
    hit_count INTEGER NOT NULL DEFAULT 0,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

--changeset codex:20260528-02-index-agent-sql-query-examples-lookup
CREATE INDEX IF NOT EXISTS idx_agent_sql_query_examples_lookup
ON agent_sql_query_examples(user_id, datasource_id, updated_at DESC);

--changeset codex:20260528-03-index-agent-sql-query-examples-normalized
CREATE INDEX IF NOT EXISTS idx_agent_sql_query_examples_normalized
ON agent_sql_query_examples(datasource_id, normalized_question);
