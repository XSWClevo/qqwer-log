-- 插入测试日志数据到ClickHouse

-- 插入最近24小时的测试数据
INSERT INTO log_entries (timestamp, level, source, message, host, service, user) VALUES
-- ERROR级别日志
(now() - INTERVAL 1 HOUR, 'ERROR', 'api-service', 'Database connection failed: timeout after 30s', 'server-01', 'api-service', 'system'),
(now() - INTERVAL 2 HOUR, 'ERROR', 'web-service', 'Failed to render template: user-profile.html not found', 'server-02', 'web-service', 'admin'),
(now() - INTERVAL 3 HOUR, 'ERROR', 'db-service', 'Query execution timeout: SELECT * FROM large_table', 'server-01', 'db-service', 'user01'),
(now() - INTERVAL 4 HOUR, 'ERROR', 'api-service', 'Authentication failed: invalid token', 'server-01', 'api-service', 'user02'),
(now() - INTERVAL 5 HOUR, 'ERROR', 'cache-service', 'Redis connection lost', 'server-03', 'cache-service', 'system'),

-- WARN级别日志
(now() - INTERVAL 30 MINUTE, 'WARN', 'api-service', 'High memory usage detected: 85%', 'server-01', 'api-service', 'system'),
(now() - INTERVAL 1 HOUR, 'WARN', 'web-service', 'Slow response time: 3500ms for /api/users', 'server-02', 'web-service', 'admin'),
(now() - INTERVAL 2 HOUR, 'WARN', 'db-service', 'Connection pool near capacity: 95/100', 'server-01', 'db-service', 'system'),
(now() - INTERVAL 3 HOUR, 'WARN', 'mq-service', 'Message queue depth increasing: 10000 messages', 'server-04', 'mq-service', 'system'),
(now() - INTERVAL 4 HOUR, 'WARN', 'api-service', 'Rate limit approaching: 9500/10000 requests', 'server-01', 'api-service', 'user01'),

-- INFO级别日志
(now() - INTERVAL 5 MINUTE, 'INFO', 'api-service', 'User login successful', 'server-01', 'api-service', 'admin'),
(now() - INTERVAL 10 MINUTE, 'INFO', 'web-service', 'Page rendered successfully: /dashboard', 'server-02', 'web-service', 'user01'),
(now() - INTERVAL 15 MINUTE, 'INFO', 'api-service', 'API request processed: GET /api/logs', 'server-01', 'api-service', 'user02'),
(now() - INTERVAL 20 MINUTE, 'INFO', 'db-service', 'Database backup completed successfully', 'server-01', 'db-service', 'system'),
(now() - INTERVAL 25 MINUTE, 'INFO', 'cache-service', 'Cache cleared: 1000 entries removed', 'server-03', 'cache-service', 'admin'),
(now() - INTERVAL 30 MINUTE, 'INFO', 'api-service', 'Health check passed', 'server-01', 'api-service', 'system'),
(now() - INTERVAL 35 MINUTE, 'INFO', 'web-service', 'Static files served: 150 files', 'server-02', 'web-service', 'system'),
(now() - INTERVAL 40 MINUTE, 'INFO', 'mq-service', 'Messages processed: 500 messages', 'server-04', 'mq-service', 'system'),

-- DEBUG级别日志
(now() - INTERVAL 1 MINUTE, 'DEBUG', 'api-service', 'Entering function: processUserRequest()', 'server-01', 'api-service', 'system'),
(now() - INTERVAL 2 MINUTE, 'DEBUG', 'api-service', 'Variable value: userId=12345', 'server-01', 'api-service', 'system'),
(now() - INTERVAL 3 MINUTE, 'DEBUG', 'web-service', 'Template loaded: user-profile.html', 'server-02', 'web-service', 'system'),
(now() - INTERVAL 4 MINUTE, 'DEBUG', 'db-service', 'SQL executed: SELECT * FROM users WHERE id=12345', 'server-01', 'db-service', 'system'),
(now() - INTERVAL 5 MINUTE, 'DEBUG', 'cache-service', 'Cache hit: key=user:12345', 'server-03', 'cache-service', 'system');

-- 插入更多历史数据（最近7天）
INSERT INTO log_entries (timestamp, level, source, message, host, service, user)
SELECT
    now() - INTERVAL number HOUR as timestamp,
    arrayElement(['ERROR', 'WARN', 'INFO', 'DEBUG'], (number % 4) + 1) as level,
    arrayElement(['api-service', 'web-service', 'db-service', 'cache-service', 'mq-service'], (number % 5) + 1) as source,
    concat('Generated log message #', toString(number)) as message,
    arrayElement(['server-01', 'server-02', 'server-03', 'server-04'], (number % 4) + 1) as host,
    arrayElement(['api-service', 'web-service', 'db-service', 'cache-service', 'mq-service'], (number % 5) + 1) as service,
    arrayElement(['admin', 'user01', 'user02', 'system'], (number % 4) + 1) as user
FROM numbers(168); -- 7天 * 24小时 = 168条记录

-- 验证数据插入
SELECT
    level,
    count(*) as count
FROM log_entries
WHERE timestamp >= now() - INTERVAL 7 DAY
GROUP BY level
ORDER BY level;

SELECT
    toStartOfHour(timestamp) as hour,
    count(*) as count
FROM log_entries
WHERE timestamp >= now() - INTERVAL 24 HOUR
GROUP BY hour
ORDER BY hour DESC
LIMIT 24;
