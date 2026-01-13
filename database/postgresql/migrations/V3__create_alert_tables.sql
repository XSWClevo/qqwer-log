-- 告警规则表
CREATE TABLE IF NOT EXISTS alert_rules (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    condition JSONB NOT NULL,
    severity VARCHAR(50) NOT NULL,
    notification_channels JSONB,
    silence_period INTEGER DEFAULT 300,
    enabled BOOLEAN DEFAULT TRUE,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


COMMENT ON TABLE alert_rules IS '告警规则表';
COMMENT ON COLUMN alert_rules.name IS '规则名称';
COMMENT ON COLUMN alert_rules.description IS '规则描述';
COMMENT ON COLUMN alert_rules.condition IS '告警条件(JSON格式)';
COMMENT ON COLUMN alert_rules.severity IS '严重程度: INFO, WARNING, ERROR, CRITICAL';
COMMENT ON COLUMN alert_rules.notification_channels IS '通知渠道列表(JSON数组)';
COMMENT ON COLUMN alert_rules.silence_period IS '静默期(秒)';
COMMENT ON COLUMN alert_rules.enabled IS '是否启用';

-- 告警事件表
CREATE TABLE IF NOT EXISTS alert_events (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL,
    rule_name VARCHAR(255) NOT NULL,
    severity VARCHAR(50) NOT NULL,
    message TEXT,
    log_data JSONB,
    triggered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    acknowledged BOOLEAN DEFAULT FALSE,
    acknowledged_by BIGINT,
    acknowledged_at TIMESTAMP
);

COMMENT ON TABLE alert_events IS '告警事件表';
COMMENT ON COLUMN alert_events.rule_id IS '关联的规则ID';
COMMENT ON COLUMN alert_events.rule_name IS '规则名称(冗余字段)';
COMMENT ON COLUMN alert_events.severity IS '严重程度';
COMMENT ON COLUMN alert_events.message IS '告警消息';
COMMENT ON COLUMN alert_events.log_data IS '触发告警的日志数据(JSON格式)';
COMMENT ON COLUMN alert_events.triggered_at IS '触发时间';
COMMENT ON COLUMN alert_events.acknowledged IS '是否已确认';

-- 通知记录表
CREATE TABLE IF NOT EXISTS alert_notifications (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL,
    channel VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    message TEXT,
    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    error_message TEXT
);

COMMENT ON TABLE alert_notifications IS '告警通知记录表';
COMMENT ON COLUMN alert_notifications.event_id IS '关联的事件ID';
COMMENT ON COLUMN alert_notifications.channel IS '通知渠道: slack, email, webhook';
COMMENT ON COLUMN alert_notifications.status IS '发送状态: success, failed';
COMMENT ON COLUMN alert_notifications.message IS '通知内容';
COMMENT ON COLUMN alert_notifications.error_message IS '错误信息(如果失败)';

-- 插入示例数据
INSERT INTO alert_rules (name, description, condition, severity, notification_channels, enabled) VALUES
('Payment Service 5xx Errors > 10/min', 'service=''payment'' AND status >= 500', 
 '{"type": "log_query", "query": "service=''payment'' AND status >= 500", "metric": "count", "operator": "gt", "value": 10, "timeWindow": "1m"}',
 'CRITICAL', '["slack", "email"]', true),
('Database Connection Pool Exhausted', 'message LIKE ''%connection pool%'' AND level=''ERROR''',
 '{"type": "log_query", "query": "message LIKE ''%connection pool%'' AND level=''ERROR''", "metric": "count", "operator": "gt", "value": 5, "timeWindow": "5m"}',
 'CRITICAL', '["slack", "webhook"]', true),
('High Memory Usage Warning', 'memory_usage > 85%',
 '{"type": "metric_threshold", "metric": "memory_usage", "operator": "gt", "value": 85, "timeWindow": "3m"}',
 'WARNING', '["email"]', false),
('API Response Time Anomaly', 'response_time anomaly detection',
 '{"type": "anomaly", "metric": "response_time", "operator": "deviation", "value": 2, "timeWindow": "10m"}',
 'WARNING', '["slack"]', true);
