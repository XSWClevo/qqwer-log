--liquibase formatted sql

--changeset codex:20260321-01-create-agent-conversations
CREATE TABLE IF NOT EXISTS agent_conversations (
    id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    preview TEXT,
    datasource_id VARCHAR(36),
    datasource_name VARCHAR(255),
    datasource_type VARCHAR(50),
    message_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_message_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

--changeset codex:20260321-02-create-agent-conversation-messages
CREATE TABLE IF NOT EXISTS agent_conversation_messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL REFERENCES agent_conversations(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    content TEXT,
    tool_calls_json TEXT,
    result_json TEXT,
    suggestions_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

--changeset codex:20260321-03-index-agent-conversations-user-last-message
CREATE INDEX IF NOT EXISTS idx_agent_conversations_user_last_message
ON agent_conversations(user_id, last_message_at DESC);

--changeset codex:20260321-04-index-agent-conversations-user-updated-at
CREATE INDEX IF NOT EXISTS idx_agent_conversations_user_updated_at
ON agent_conversations(user_id, updated_at DESC);

--changeset codex:20260321-05-index-agent-conversation-messages-conversation-time
CREATE INDEX IF NOT EXISTS idx_agent_conversation_messages_conversation_time
ON agent_conversation_messages(conversation_id, created_at ASC, id ASC);
