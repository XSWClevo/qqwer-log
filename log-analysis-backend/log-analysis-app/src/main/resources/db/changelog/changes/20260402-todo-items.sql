--liquibase formatted sql

--changeset codex:20260402-01-create-todo-items
CREATE TABLE IF NOT EXISTS todo_items (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'TODO',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    due_at TIMESTAMP,
    completed_at TIMESTAMP,
    tags JSONB DEFAULT '[]'::jsonb,
    created_by BIGINT NOT NULL,
    updated_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

--changeset codex:20260402-02-index-todo-items-created-by
CREATE INDEX IF NOT EXISTS idx_todo_items_created_by ON todo_items(created_by);

--changeset codex:20260402-03-index-todo-items-status
CREATE INDEX IF NOT EXISTS idx_todo_items_status ON todo_items(created_by, status);

--changeset codex:20260402-04-index-todo-items-priority
CREATE INDEX IF NOT EXISTS idx_todo_items_priority ON todo_items(created_by, priority);

--changeset codex:20260402-05-index-todo-items-due-at
CREATE INDEX IF NOT EXISTS idx_todo_items_due_at ON todo_items(created_by, due_at);
