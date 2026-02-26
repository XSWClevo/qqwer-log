# 日志源白名单 - Vector 实时检测方案

## 方案概述

通过 Vector 的 transform 功能实时检测新的日志源 IP，并通过 HTTP sink 通知后端，实现真正的实时白名单管理。

## 架构流程

```
日志源 (Syslog Client)
    ↓
Vector Source (syslog)
    ↓
Vector Transform (提取 source_ip)
    ↓
Vector Transform (聚合检测)
    ↓
Vector Sink (HTTP 通知后端)
    ↓
后端 API (/api/log-sources/notify-new-ip)
    ↓
检查白名单数据库
    ↓
如果不在白名单 → 记录为 pending 状态
    ↓
前端轮询 (/api/log-sources/pending-notifications)
    ↓
显示实时通知
    ↓
用户点击"信任"或"拉黑"
```

## 1. Vector 配置

### 完整配置示例

```toml
# ============================================
# Source: 接收 Syslog 日志
# ============================================
[sources.syslog_input]
type = "syslog"
address = "0.0.0.0:9000"
mode = "tcp"

# ============================================
# Transform 1: 提取 source_ip
# ============================================
[transforms.extract_source_ip]
type = "remap"
inputs = ["syslog_input"]
source = '''
# 从 TCP 连接信息中提取 source_ip
# Vector 会自动将客户端 IP 存储在 .source_ip 字段中
.source_ip = .source_ip ?? .host ?? "unknown"
.detected_at = now()

# 保留原始日志的所有字段
'''

# ============================================
# Transform 2: 聚合检测新 IP（每分钟）
# ============================================
[transforms.aggregate_new_ips]
type = "aggregate"
inputs = ["extract_source_ip"]
interval_ms = 60000  # 每 60 秒聚合一次

# 按 source_ip 分组
group_by = ["source_ip"]

# 聚合函数
[transforms.aggregate_new_ips.metrics]
# 统计每个 IP 的日志数量
count = { type = "count" }
# 记录首次出现时间
first_seen = { type = "min", field = "detected_at" }
# 记录主机名
hostname = { type = "last", field = "hostname" }

# ============================================
# Transform 3: 构建通知数据
# ============================================
[transforms.build_notification]
type = "remap"
inputs = ["aggregate_new_ips"]
source = '''
# 构建发送给后端的通知数据
.notificationType = "new_log_source"
.sourceIp = .source_ip
.hostname = .hostname ?? "unknown"
.firstSeenAt = .first_seen
.logCount = .count
'''

# ============================================
# Sink 1: 发送通知到后端 API
# ============================================
[sinks.new_ip_notification]
type = "http"
inputs = ["build_notification"]
uri = "http://localhost:8080/api/log-sources/notify-new-ip"
method = "post"

# 编码格式
[sinks.new_ip_notification.encoding]
codec = "json"

# HTTP 头
[sinks.new_ip_notification.request]
headers = { "Content-Type" = "application/json" }

# 批量发送配置（减少请求次数）
[sinks.new_ip_notification.batch]
max_events = 10
timeout_secs = 60

# 重试配置
[sinks.new_ip_notification.request.retry_attempts]
max_duration_secs = 300

# ============================================
# Sink 2: 原有的日志存储（ClickHouse）
# ============================================
[sinks.clickhouse_sink]
type = "clickhouse"
inputs = ["extract_source_ip"]
endpoint = "http://10.180.5.72:8123"
database = "MWLOGDB_ANALYSIS"
table = "syslog"
# ... 其他 ClickHouse 配置
```

### 简化配置（YAML 格式）

```yaml
sources:
  syslog_input:
    type: syslog
    address: 0.0.0.0:9000
    mode: tcp

transforms:
  # 提取 source_ip
  extract_source_ip:
    type: remap
    inputs:
      - syslog_input
    source: |
      .source_ip = .source_ip ?? .host ?? "unknown"
      .detected_at = now()

  # 聚合检测（每分钟）
  aggregate_new_ips:
    type: aggregate
    inputs:
      - extract_source_ip
    interval_ms: 60000
    group_by:
      - source_ip
    metrics:
      count:
        type: count
      first_seen:
        type: min
        field: detected_at
      hostname:
        type: last
        field: hostname

  # 构建通知
  build_notification:
    type: remap
    inputs:
      - aggregate_new_ips
    source: |
      .notificationType = "new_log_source"
      .sourceIp = .source_ip
      .hostname = .hostname ?? "unknown"
      .firstSeenAt = .first_seen
      .logCount = .count

sinks:
  # 通知后端
  new_ip_notification:
    type: http
    inputs:
      - build_notification
    uri: http://localhost:8080/api/log-sources/notify-new-ip
    method: post
    encoding:
      codec: json
    batch:
      max_events: 10
      timeout_secs: 60

  # 存储日志
  clickhouse_sink:
    type: clickhouse
    inputs:
      - extract_source_ip
    # ... ClickHouse 配置
```

## 2. 后端实现

### 新增接口

#### 接收 Vector 通知

**URL**: `POST /api/log-sources/notify-new-ip`

**请求体**:
```json
{
  "notificationType": "new_log_source",
  "sourceIp": "192.168.1.100",
  "hostname": "web-server-01",
  "firstSeenAt": "2026-01-23T15:30:00",
  "logCount": 523
}
```

**处理逻辑**:
1. 检查 IP 是否在数据库中
2. 如果不存在，创建 `pending` 状态记录
3. 如果存在，更新最后活跃时间和日志数量
4. 返回是否需要审核

#### 获取待审核通知

**URL**: `POST /api/log-sources/pending-notifications`

**响应**:
```json
{
  "code": 200,
  "data": [
    {
      "sourceIp": "192.168.1.100",
      "hostname": "web-server-01",
      "firstSeenAt": "2026-01-23T15:30:00",
      "logCount": 523,
      "recentLogPreview": null
    }
  ]
}
```

## 3. 前端实现

### 实时通知组件

**轮询频率**: 每 30 秒检查一次待审核通知

**显示位置**: 页面右上角

**功能**:
- 显示新发现的日志源
- 快速信任/拉黑操作
- 查看详情跳转

## 4. 部署步骤

### 4.1 更新 Vector 配置

```bash
# 1. 备份现有配置
cp /etc/vector/vector.toml /etc/vector/vector.toml.backup

# 2. 添加新的 transform 和 sink
vim /etc/vector/vector.toml
# 粘贴上面的配置

# 3. 验证配置
vector validate /etc/vector/vector.toml

# 4. 重启 Vector
systemctl restart vector

# 5. 查看日志
journalctl -u vector -f
```

### 4.2 配置后端白名单

编辑 `SecurityConfig.java`，添加 Vector 通知接口到白名单：

```java
.requestMatchers(
    "/api/log-sources/notify-new-ip",  // Vector 通知接口
    "/api/log-sources/**"
).permitAll()
```

### 4.3 测试

```bash
# 1. 发送测试日志
echo "<134>Jan 23 15:30:00 test-host test-app: Test message" | nc localhost 9000

# 2. 等待 1 分钟（聚合间隔）

# 3. 检查后端日志
tail -f /var/log/log-analysis/app.log | grep "收到 Vector 新 IP 通知"

# 4. 检查数据库
psql -U postgres -d postgres -c "SELECT * FROM trusted_log_sources WHERE status='pending';"

# 5. 访问前端，应该看到通知
```

## 5. 优势

### 相比查询历史数据的方案

| 特性 | 查询方案 | Vector 实时方案 |
|------|---------|----------------|
| 实时性 | 延迟 5 分钟 | 延迟 1 分钟 |
| 性能 | 需要扫描大量数据 | 流式处理，无需扫描 |
| 准确性 | 可能遗漏 | 100% 捕获 |
| 资源消耗 | 高（定期查询） | 低（事件驱动） |
| 扩展性 | 受数据库性能限制 | 可水平扩展 |

### 实时检测的优势

1. **真正的实时**：日志到达后 1 分钟内就能检测到
2. **低延迟**：不需要等待数据写入数据库再查询
3. **高效率**：Vector 在内存中聚合，不需要扫描数据库
4. **可靠性**：Vector 的 HTTP sink 支持重试和批量发送

## 6. 监控和告警

### Vector 监控

```bash
# 查看 Vector 指标
curl http://localhost:9598/metrics

# 关键指标
# - component_sent_events_total{component_id="new_ip_notification"} - 发送的通知数量
# - component_errors_total{component_id="new_ip_notification"} - 发送失败数量
```

### 后端监控

```java
// 添加监控指标
@Timed(value = "log_source.notification.received")
public Result<Void> notifyNewIp(VectorNewIpNotification notification) {
    // ...
}
```

## 7. 故障处理

### Vector 无法发送通知

**症状**: 后端没有收到通知

**排查**:
```bash
# 1. 检查 Vector 日志
journalctl -u vector -f | grep "new_ip_notification"

# 2. 检查网络连接
curl -X POST http://localhost:8080/api/log-sources/notify-new-ip \
  -H "Content-Type: application/json" \
  -d '{"sourceIp":"test","hostname":"test","logCount":1}'

# 3. 检查 Vector 配置
vector validate /etc/vector/vector.toml
```

**解决**:
- 检查后端是否启动
- 检查防火墙规则
- 检查 Vector 配置中的 URI 是否正确

### 通知延迟过高

**症状**: 新 IP 出现后很久才收到通知

**排查**:
- 检查 `interval_ms` 配置（默认 60 秒）
- 检查 `batch.timeout_secs` 配置

**优化**:
```toml
# 减少聚合间隔（但会增加通知频率）
interval_ms = 30000  # 30 秒

# 减少批量超时
[sinks.new_ip_notification.batch]
timeout_secs = 30
```

## 8. 性能优化

### 减少通知频率

如果通知过于频繁，可以：

1. **增加聚合间隔**
```toml
interval_ms = 300000  # 5 分钟
```

2. **添加过滤条件**
```toml
[transforms.filter_significant_ips]
type = "filter"
inputs = ["aggregate_new_ips"]
condition = '.count > 10'  # 只通知日志数量 > 10 的 IP
```

3. **批量发送**
```toml
[sinks.new_ip_notification.batch]
max_events = 50  # 增加批量大小
timeout_secs = 300  # 增加超时时间
```

## 9. 安全考虑

### 防止恶意通知

1. **添加认证**
```toml
[sinks.new_ip_notification.request]
headers = {
  "Content-Type" = "application/json",
  "Authorization" = "Bearer ${VECTOR_API_TOKEN}"
}
```

2. **IP 白名单**
```java
// 在 Controller 中验证请求来源
@PostMapping("/notify-new-ip")
public Result<Void> notifyNewIp(
    @RequestBody VectorNewIpNotification notification,
    HttpServletRequest request) {

    String remoteIp = request.getRemoteAddr();
    if (!isVectorServer(remoteIp)) {
        throw new SecurityException("Unauthorized");
    }
    // ...
}
```

## 10. 总结

这个方案实现了真正的实时日志源检测：

✅ **实时性**: 1 分钟内检测到新 IP
✅ **高效性**: 流式处理，无需扫描数据库
✅ **可靠性**: Vector 的重试机制保证通知送达
✅ **可扩展**: 支持水平扩展
✅ **低成本**: 减少数据库查询压力

相比之前的定期查询方案，这个方案更符合实时监控的需求。
