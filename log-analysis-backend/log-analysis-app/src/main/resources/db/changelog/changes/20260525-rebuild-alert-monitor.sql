--liquibase formatted sql

--changeset codex:20260525-01-rebuild-alert-monitor
DROP TABLE IF EXISTS alert_notifications CASCADE;
DROP TABLE IF EXISTS alert_events CASCADE;
DROP TABLE IF EXISTS alert_evaluation_runs CASCADE;
DROP TABLE IF EXISTS alert_monitor_states CASCADE;
DROP TABLE IF EXISTS alert_monitor_options CASCADE;
DROP TABLE IF EXISTS alert_rule_thresholds CASCADE;
DROP TABLE IF EXISTS alert_rule_conditions CASCADE;
DROP TABLE IF EXISTS alert_downtimes CASCADE;
DROP TABLE IF EXISTS alert_rules CASCADE;

CREATE TABLE alert_rules (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    rule_type VARCHAR(50) NOT NULL DEFAULT 'aggregation',
    scope_type VARCHAR(30) NOT NULL DEFAULT 'all',
    category_codes JSONB DEFAULT '[]'::jsonb,
    datasource_ids JSONB DEFAULT '[]'::jsonb,
    table_names JSONB DEFAULT '[]'::jsonb,
    eval_every VARCHAR(20) NOT NULL DEFAULT '1m',
    consecutive_hits INTEGER NOT NULL DEFAULT 1,
    severity VARCHAR(30) NOT NULL DEFAULT 'WARNING',
    notification_channels JSONB DEFAULT '[]'::jsonb,
    message_template TEXT,
    silence_period INTEGER NOT NULL DEFAULT 300,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE alert_rule_conditions (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL REFERENCES alert_rules(id) ON DELETE CASCADE,
    query TEXT,
    filters JSONB DEFAULT '{}'::jsonb,
    aggregate_function VARCHAR(40) NOT NULL DEFAULT 'count',
    aggregate_field VARCHAR(255) NOT NULL DEFAULT '*',
    group_by JSONB DEFAULT '[]'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_alert_rule_conditions_rule_id UNIQUE (rule_id)
);

CREATE TABLE alert_rule_thresholds (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL REFERENCES alert_rules(id) ON DELETE CASCADE,
    level VARCHAR(30) NOT NULL,
    operator VARCHAR(20) NOT NULL DEFAULT 'gt',
    threshold NUMERIC(24, 6) NOT NULL,
    time_window VARCHAR(20) NOT NULL DEFAULT '5m',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_alert_rule_thresholds_rule_level UNIQUE (rule_id, level)
);

CREATE TABLE alert_monitor_options (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL REFERENCES alert_rules(id) ON DELETE CASCADE,
    notify_no_data BOOLEAN NOT NULL DEFAULT FALSE,
    no_data_timeframe VARCHAR(20) DEFAULT '5m',
    require_full_window BOOLEAN NOT NULL DEFAULT FALSE,
    evaluation_delay_seconds INTEGER NOT NULL DEFAULT 0,
    new_group_delay_seconds INTEGER NOT NULL DEFAULT 0,
    renotify_interval_minutes INTEGER NOT NULL DEFAULT 0,
    renotify_occurrences INTEGER NOT NULL DEFAULT 0,
    include_tags BOOLEAN NOT NULL DEFAULT TRUE,
    priority VARCHAR(20),
    team VARCHAR(100),
    tags JSONB DEFAULT '[]'::jsonb,
    alert_mode VARCHAR(20) NOT NULL DEFAULT 'simple',
    escalation_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_alert_monitor_options_rule_id UNIQUE (rule_id)
);

CREATE TABLE alert_monitor_states (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL REFERENCES alert_rules(id) ON DELETE CASCADE,
    datasource_id VARCHAR(100),
    table_name VARCHAR(255),
    group_key VARCHAR(512) NOT NULL DEFAULT 'default',
    group_values JSONB DEFAULT '{}'::jsonb,
    state VARCHAR(30) NOT NULL DEFAULT 'OK',
    previous_state VARCHAR(30),
    last_value NUMERIC(24, 6),
    last_threshold NUMERIC(24, 6),
    last_evaluated_at TIMESTAMP,
    last_state_changed_at TIMESTAMP,
    last_notified_at TIMESTAMP,
    renotify_count INTEGER NOT NULL DEFAULT 0,
    no_data_since TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_alert_monitor_states_target UNIQUE (rule_id, datasource_id, table_name, group_key)
);

CREATE TABLE alert_evaluation_runs (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT REFERENCES alert_rules(id) ON DELETE SET NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP,
    window_start TIMESTAMP,
    window_end TIMESTAMP,
    status VARCHAR(30) NOT NULL DEFAULT 'RUNNING',
    matched_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    details JSONB DEFAULT '{}'::jsonb
);

CREATE TABLE alert_events (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT REFERENCES alert_rules(id) ON DELETE SET NULL,
    rule_name VARCHAR(255),
    severity VARCHAR(30) NOT NULL,
    state VARCHAR(30) NOT NULL,
    previous_state VARCHAR(30),
    threshold_level VARCHAR(30),
    message TEXT,
    log_data JSONB DEFAULT '{}'::jsonb,
    evaluation_run_id BIGINT REFERENCES alert_evaluation_runs(id) ON DELETE SET NULL,
    triggered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    acknowledged_by BIGINT,
    acknowledged_at TIMESTAMP
);

CREATE TABLE alert_notifications (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES alert_events(id) ON DELETE CASCADE,
    channel VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    error_message TEXT,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE alert_downtimes (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT REFERENCES alert_rules(id) ON DELETE CASCADE,
    scope_type VARCHAR(30) NOT NULL DEFAULT 'rule',
    scope_values JSONB DEFAULT '{}'::jsonb,
    reason TEXT,
    starts_at TIMESTAMP NOT NULL,
    ends_at TIMESTAMP NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_alert_rules_enabled ON alert_rules(enabled);
CREATE INDEX idx_alert_rules_severity ON alert_rules(severity);
CREATE INDEX idx_alert_rules_rule_type ON alert_rules(rule_type);
CREATE INDEX idx_alert_rules_scope_type ON alert_rules(scope_type);
CREATE INDEX idx_alert_monitor_states_rule_state ON alert_monitor_states(rule_id, state);
CREATE INDEX idx_alert_evaluation_runs_rule_started ON alert_evaluation_runs(rule_id, started_at DESC);
CREATE INDEX idx_alert_events_rule_triggered ON alert_events(rule_id, triggered_at DESC);
CREATE INDEX idx_alert_events_severity_triggered ON alert_events(severity, triggered_at DESC);
CREATE INDEX idx_alert_events_state_triggered ON alert_events(state, triggered_at DESC);
CREATE INDEX idx_alert_notifications_event_id ON alert_notifications(event_id);
CREATE INDEX idx_alert_downtimes_rule_window ON alert_downtimes(rule_id, starts_at, ends_at);
