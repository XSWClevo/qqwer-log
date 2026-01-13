# 操作日志埋点接口文档

## 概述

用户操作日志埋点功能提供系统级操作追踪，支持安全审计、行为分析和问题追溯。

**技术特性**:
- 基于 Spring AOP 切面，非侵入式设计
- 异步批量写入，高性能
- 自动脱敏敏感信息
- 实时告警检测
- 定时归档历史数据

---

## API 接口

### 1. 分页查询操作日志

**接口**: `POST /api/operation-logs/list`

**功能**: 分页查询用户操作日志，支持多维度筛选

**请求参数**:
```json
{
  "pageNum": 1,                    // 页码，默认 1
  "pageSize": 20,                  // 每页大小，默认 20
  "userId": 123,                   // 用户ID (可选)
  "username": "admin",             // 用户名 (模糊查询，可选)
  "operationType": "CREATE",       // 操作类型 (可选): CREATE/UPDATE/DELETE/QUERY/LOGIN/LOGOUT/EXPORT/IMPORT/EXECUTE/CONFIG
  "module": "alert",               // 模块 (可选): auth/stats/alert/vector/config/datasource/extraction/dashboard
  "isSuccess": true,               // 是否成功 (可选)
  "ipAddress": "192.168.1.1",      // IP 地址 (可选)
  "startTime": "2026-01-01T00:00:00",  // 开始时间 (可选)
  "endTime": "2026-01-07T23:59:59",    // 结束时间 (可选)
  "resourceType": "AlertRule",     // 资源类型 (可选)
  "resourceId": "123"              // 资源ID (可选)
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 1,
        "userId": 1,
        "username": "admin",
        "operationType": "CREATE",
        "module": "alert",
        "resourceType": "AlertRule",
        "resourceId": "123",
        "action": "create_alert_rule",
        "requestMethod": "POST",
        "requestUrl": "/api/alert/rules",
        "requestParams": {
          "name": "CPU告警",
          "password": "******"
        },
        "responseStatus": 200,
        "responseMessage": "success",
        "ipAddress": "192.168.1.1",
        "userAgent": "Mozilla/5.0...",
        "executionTime": 150,
        "isSuccess": true,
        "errorMessage": null,
        "createdAt": "2026-01-07 10:30:00"
      }
    ],
    "total": 100,
    "size": 20,
    "current": 1,
    "pages": 5
  }
}
```

**代码位置**: `OperationLogController.java:29`

---

### 2. 获取操作日志详情

**接口**: `GET /api/operation-logs/{id}`

**功能**: 根据日志ID获取详细信息

**路径参数**:
- `id`: 日志ID

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "userId": 1,
    "username": "admin",
    "operationType": "CREATE",
    "module": "alert",
    "resourceType": "AlertRule",
    "resourceId": "123",
    "action": "create_alert_rule",
    "requestMethod": "POST",
    "requestUrl": "/api/alert/rules",
    "requestParams": {
      "name": "CPU告警"
    },
    "responseStatus": 200,
    "responseMessage": "success",
    "ipAddress": "192.168.1.1",
    "executionTime": 150,
    "isSuccess": true,
    "createdAt": "2026-01-07 10:30:00"
  }
}
```

**代码位置**: `OperationLogController.java:45`

---

### 3. 获取用户最近操作日志

**接口**: `GET /api/operation-logs/user/{userId}/recent`

**功能**: 获取某用户最近的N条操作记录

**路径参数**:
- `userId`: 用户ID

**查询参数**:
- `limit`: 数量限制，默认 10

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "userId": 1,
      "username": "admin",
      "operationType": "CREATE",
      "action": "create_alert_rule",
      "createdAt": "2026-01-07 10:30:00"
    }
  ]
}
```

**代码位置**: `OperationLogController.java:60`

---

### 4. 统计按操作类型分组

**接口**: `POST /api/operation-logs/stats/by-operation-type`

**功能**: 统计指定时间范围内各操作类型的数量和成功率

**查询参数**:
- `startTime`: 开始时间 (格式: yyyy-MM-dd HH:mm:ss)，可选，默认最近7天
- `endTime`: 结束时间 (格式: yyyy-MM-dd HH:mm:ss)，可选，默认当前时间

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "name": "CREATE",
      "count": 150,
      "successCount": 145,
      "failureCount": 5,
      "successRate": 96.67
    },
    {
      "name": "UPDATE",
      "count": 80,
      "successCount": 78,
      "failureCount": 2,
      "successRate": 97.50
    }
  ]
}
```

**代码位置**: `OperationLogController.java:80`

---

### 5. 统计按模块分组

**接口**: `POST /api/operation-logs/stats/by-module`

**功能**: 统计指定时间范围内各模块的操作数量和成功率

**查询参数**:
- `startTime`: 开始时间 (格式: yyyy-MM-dd HH:mm:ss)，可选
- `endTime`: 结束时间 (格式: yyyy-MM-dd HH:mm:ss)，可选

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "name": "alert",
      "count": 200,
      "successCount": 195,
      "failureCount": 5,
      "successRate": 97.50
    },
    {
      "name": "auth",
      "count": 150,
      "successCount": 140,
      "failureCount": 10,
      "successRate": 93.33
    }
  ]
}
```

**代码位置**: `OperationLogController.java:105`

---

### 6. 统计按用户分组 (TOP N 活跃用户)

**接口**: `POST /api/operation-logs/stats/by-user`

**功能**: 统计最活跃的 TOP N 用户

**查询参数**:
- `startTime`: 开始时间 (格式: yyyy-MM-dd HH:mm:ss)，可选
- `endTime`: 结束时间 (格式: yyyy-MM-dd HH:mm:ss)，可选
- `limit`: TOP N 用户数，默认 10

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "name": "admin",
      "count": 500,
      "successCount": 490,
      "failureCount": 10,
      "successRate": 98.00
    },
    {
      "name": "user1",
      "count": 300,
      "successCount": 295,
      "failureCount": 5,
      "successRate": 98.33
    }
  ]
}
```

**代码位置**: `OperationLogController.java:130`

---

## 数据模型

### 操作类型 (OperationType)

| 枚举值   | 描述     | 使用场景                |
|----------|----------|-------------------------|
| CREATE   | 创建     | 创建资源 (规则、配置等) |
| UPDATE   | 更新     | 更新资源                |
| DELETE   | 删除     | 删除资源                |
| QUERY    | 查询     | 查询数据                |
| LOGIN    | 登录     | 用户登录                |
| LOGOUT   | 登出     | 用户登出                |
| EXPORT   | 导出     | 导出数据                |
| IMPORT   | 导入     | 导入数据                |
| EXECUTE  | 执行     | 执行任务                |
| CONFIG   | 配置     | 修改系统配置            |

### 模块列表 (Module)

| 模块代码   | 模块名称   | 说明                 |
|------------|------------|----------------------|
| auth       | 认证授权   | 登录、登出、用户管理 |
| stats      | 统计查询   | 日志查询、统计分析   |
| alert      | 告警管理   | 告警规则、告警事件   |
| vector     | Vector配置 | Vector配置管理、部署 |
| config     | 系统配置   | 系统配置、字段配置   |
| datasource | 数据源管理 | 数据源增删改查       |
| extraction | 日志提取   | 提取规则管理         |
| dashboard  | 仪表盘     | 仪表盘查询           |

---

## 使用示例

### 1. 在 Controller 中添加操作日志注解

**示例: 用户登录**
```java
@PostMapping("/login")
@OperationLog(
    module = "auth",
    operationType = OperationType.LOGIN,
    action = "user_login",
    resourceType = "User",
    resourceIdSpEL = "#result.data.userId",
    sensitiveFields = {"password"}
)
public Result<LoginResponse> login(@RequestBody LoginRequest request) {
    return Result.success(authService.login(request));
}
```

**示例: 创建告警规则**
```java
@PostMapping
@OperationLog(
    module = "alert",
    operationType = OperationType.CREATE,
    action = "create_alert_rule",
    resourceType = "AlertRule",
    resourceIdSpEL = "#result.data.id"
)
public Result<AlertRuleDTO> createRule(@RequestBody CreateAlertRuleRequest request) {
    return Result.success(alertRuleService.createRule(request));
}
```

**示例: 删除数据源**
```java
@DeleteMapping("/{id}")
@OperationLog(
    module = "datasource",
    operationType = OperationType.DELETE,
    action = "delete_datasource",
    resourceType = "Datasource",
    resourceIdSpEL = "#id"
)
public Result<Void> deleteDatasource(@PathVariable String id) {
    datasourceService.delete(id);
    return Result.success();
}
```

---

## 告警规则

系统自动检测以下异常操作并触发告警：

| 告警类型         | 触发条件                           | 告警级别 | 处理建议                        |
|------------------|-----------------------------------|----------|--------------------------------|
| 高频失败         | 5分钟内同一用户失败 >20次          | 严重     | 可能是密码爆破，建议锁定账户    |
| 异常 IP 登录     | 新 IP 登录成功 (且非白名单)        | 警告     | 通知用户确认                    |
| 批量删除         | 5分钟内删除操作 >10次              | 警告     | 可能是误操作或恶意行为          |
| 敏感配置修改     | 修改系统配置、数据源连接           | 警告     | 记录操作人，便于审计            |

---

## 性能优化

1. **异步处理**: 使用 `@Async` 异步记录，不阻塞主流程 (< 5ms 开销)
2. **批量插入**: 每 5 秒批量写入最多 500 条日志
3. **索引优化**: 针对常用查询条件建立联合索引
4. **数据归档**: 每月自动归档 6 个月前的数据到归档表
5. **JSONB 存储**: PostgreSQL JSONB 高效存储请求参数

---

## 注意事项

1. **敏感信息脱敏**: 自动脱敏 `password`、`token`、`secret` 等字段
2. **权限控制**: 需在 SecurityConfig 中配置接口访问权限
3. **数据保留**: 主表仅保留 6 个月数据，归档表按年分区
4. **采样率控制**: 高频查询接口可配置采样率，如 `samplingRate = 0.1` (仅记录 10%)

---

## 数据库表结构

**主表**: `user_operation_logs`
**归档表**: `user_operation_logs_archive` (按年分区)

**核心字段**:
- `user_id`, `username`: 用户信息
- `operation_type`, `module`, `action`: 操作分类
- `resource_type`, `resource_id`: 资源标识
- `request_method`, `request_url`, `request_params`: 请求信息
- `response_status`, `response_message`: 响应信息
- `ip_address`, `user_agent`: 客户端信息
- `execution_time`: 执行耗时 (毫秒)
- `is_success`, `error_message`: 执行结果

**索引策略**:
- `(user_id, created_at)`: 用户操作历史查询
- `(operation_type, created_at)`: 按操作类型统计
- `(module, created_at)`: 按模块统计
- `(user_id, ip_address, is_success, created_at)`: 告警检测

---

## 版本历史

- **v1.0** (2026-01-07): 初始版本
  - 基础操作日志记录
  - 多维度查询和统计
  - 实时告警检测
  - 定时归档任务
