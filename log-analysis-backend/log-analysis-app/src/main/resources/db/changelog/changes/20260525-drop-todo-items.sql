--liquibase formatted sql

--changeset codex:20260525-01-drop-todo-items
DROP TABLE IF EXISTS todo_items CASCADE;
