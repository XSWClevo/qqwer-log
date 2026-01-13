# Vector 日志收集管理器 - API 接口文档

## 版本信息
- 版本: v1.0.0
- 最后更新: 2025-12-26
- 基础URL: `http://localhost:8080/api/vector`

---

## 一、机器管理 API

### 1.1 生成 Agent Token

**接口**: `POST /api/vector/hosts/generate-token`

**描述**: 管理员在前端页面点击"添加机器"时调用,生成用于Agent注册的Token

**请求参数**: 无

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "token": "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6",
    "expireHours": 24
  }
}
```

---

### 1.2 检查 Token 是否已注册

**接口**: `GET /api/vector/hosts/check-token/{token}`

**描述**: 前端轮询检测Agent是否已完成注册

**路径参数**:
- `token` (string): Agent Token

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "registered": true
  }
}
```

---

### 1.3 Agent 注册

**接口**: `POST /api/vector/hosts/register`

**描述**: Agent 启动后自动调用此接口完成注册

**请求头**:
无需认证(公开接口)

**请求体**:
```json
{
  "hostname": "web-server-01",
  "ipAddress": "192.168.1.100",
  "agentToken": "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6",
  "agentVersion": "1.0.0",
  "vectorVersion": "0.39.0",
  "osType": "Linux",
  "osVersion": "CentOS 7.9",
  "cpuCores": 8,
  "totalMemoryMb": 16384
}
```

**字段说明**:
- `hostname` (string, 必填): 主机名
- `ipAddress` (string, 必填): IP地址
- `agentToken` (string, 必填): Agent Token
- `agentVersion` (string): Agent版本号
- `vectorVersion` (string): Vector版本号
- `osType` (string): 操作系统类型
- `osVersion` (string): 操作系统版本
- `cpuCores` (integer): CPU核心数
- `totalMemoryMb` (long): 总内存(MB)

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "hostId": 1,
    "message": "注册成功"
  }
}
```

---

### 1.4 Agent 心跳

**接口**: `POST /api/vector/hosts/heartbeat`

**描述**: Agent 定时(30秒)发送心跳,上报状态

**请求头**:
```
Authorization: Bearer {agentToken}
```

**请求体**:
```json
{
  "agentUptimeSeconds": 3600,
  "vectorRunning": true,
  "status": "online"
}
```

**字段说明**:
- `agentUptimeSeconds` (long): Agent运行时长(秒)
- `vectorRunning` (boolean, 必填): Vector是否运行
- `status` (string): 状态(online/offline/error)

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "hasNewConfig": true,
    "latestConfigVersion": "v1.0.5"
  }
}
```

**响应字段说明**:
- `hasNewConfig` (boolean): 是否有新配置
- `latestConfigVersion` (string): 最新配置版本号

---

### 1.5 查询机器列表

**接口**: `GET /api/vector/hosts`

**描述**: 前端查询机器列表,支持筛选

**请求参数**:
- `keyword` (string, 可选): 关键词搜索(主机名/IP)
- `status` (string, 可选): 状态筛选(online/offline/error)
- `environment` (string, 可选): 环境筛选(production/staging/test)

**请求示例**:
```
GET /api/vector/hosts?keyword=web&status=online&environment=production
```

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": [
    {
      "id": 1,
      "hostname": "web-server-01",
      "ipAddress": "192.168.1.100",
      "agentToken": "a1b2c3d4...",
      "agentVersion": "1.0.0",
      "vectorVersion": "0.39.0",
      "status": "online",
      "lastHeartbeat": "2025-12-26T10:30:00",
      "tags": ["web", "production"],
      "environment": "production",
      "currentConfigVersion": "v1.0.5",
      "targetConfigVersion": "v1.0.5",
      "osType": "Linux",
      "osVersion": "CentOS 7.9",
      "cpuCores": 8,
      "totalMemoryMb": 16384,
      "createdAt": "2025-12-26T08:00:00",
      "updatedAt": "2025-12-26T10:30:00"
    }
  ]
}
```

---

### 1.6 查询机器详情

**接口**: `GET /api/vector/hosts/{id}`

**描述**: 查询单台机器的详细信息

**路径参数**:
- `id` (long): 机器ID

**响应示例**: 同1.5中的单个机器对象

---

### 1.7 删除机器

**接口**: `DELETE /api/vector/hosts/{id}`

**描述**: 删除机器记录(注意:不会卸载Agent)

**路径参数**:
- `id` (long): 机器ID

**响应示例**:
```json
{
  "code": 200,
  "message": "删除成功",
  "data": null
}
```

---

### 1.8 重启 Vector

**接口**: `POST /api/vector/hosts/{id}/restart`

**描述**: 远程重启指定机器的 Vector 服务

**路径参数**:
- `id` (long): 机器ID

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": "重启命令已发送"
}
```

---

### 1.9 停止 Vector

**接口**: `POST /api/vector/hosts/{id}/stop`

**描述**: 远程停止指定机器的 Vector 服务

**路径参数**:
- `id` (long): 机器ID

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": "停止命令已发送"
}
```

---

### 1.10 启动 Vector

**接口**: `POST /api/vector/hosts/{id}/start`

**描述**: 远程启动指定机器的 Vector 服务

**路径参数**:
- `id` (long): 机器ID

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": "启动命令已发送"
}
```

---

## 二、配置管理 API

### 2.1 创建配置版本

**接口**: `POST /api/vector/configs`

**描述**: 创建新的配置版本

**请求体**:
```json
{
  "version": "v1.0.6",
  "name": "生产环境Syslog配置",
  "description": "收集514端口Syslog并写入ClickHouse",
  "yamlContent": "sources:\n  syslog_source:\n    type: syslog\n    address: 0.0.0.0:514\n...",
  "targetTags": ["production", "web"],
  "targetEnvironment": "production"
}
```

**字段说明**:
- `version` (string, 必填): 版本号(唯一)
- `name` (string, 必填): 配置名称
- `description` (string): 描述
- `yamlContent` (string, 必填): YAML配置内容
- `targetTags` (array): 目标标签
- `targetEnvironment` (string): 目标环境

**响应示例**:
```json
{
  "code": 200,
  "message": "创建成功",
  "data": {
    "id": 10,
    "version": "v1.0.6"
  }
}
```

---

### 2.2 查询配置列表

**接口**: `GET /api/vector/configs`

**描述**: 分页查询配置版本列表

**请求参数**:
- `pageNum` (int, 默认1): 页码
- `pageSize` (int, 默认20): 每页条数
- `keyword` (string, 可选): 关键词搜索
- `status` (string, 可选): 状态筛选(draft/testing/released/deprecated)

**请求示例**:
```
GET /api/vector/configs?pageNum=1&pageSize=20&status=released
```

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "total": 100,
    "pageNum": 1,
    "pageSize": 20,
    "records": [
      {
        "id": 10,
        "version": "v1.0.6",
        "name": "生产环境Syslog配置",
        "description": "收集514端口Syslog并写入ClickHouse",
        "status": "released",
        "releasedAt": "2025-12-26T09:00:00",
        "targetTags": ["production", "web"],
        "targetEnvironment": "production",
        "isValidated": true,
        "createdAt": "2025-12-26T08:00:00",
        "updatedAt": "2025-12-26T09:00:00"
      }
    ]
  }
}
```

---

### 2.3 查询配置详情

**接口**: `GET /api/vector/configs/{id}`

**描述**: 查询配置版本详情(包含完整YAML内容)

**路径参数**:
- `id` (long): 配置ID

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "id": 10,
    "version": "v1.0.6",
    "name": "生产环境Syslog配置",
    "description": "收集514端口Syslog并写入ClickHouse",
    "yamlContent": "sources:\n  syslog_source:\n    type: syslog\n    address: 0.0.0.0:514\n...",
    "configJson": {},
    "status": "released",
    "releasedAt": "2025-12-26T09:00:00",
    "targetTags": ["production", "web"],
    "targetEnvironment": "production",
    "isValidated": true,
    "validationError": null,
    "createdAt": "2025-12-26T08:00:00",
    "updatedAt": "2025-12-26T09:00:00"
  }
}
```

---

### 2.4 更新配置

**接口**: `PUT /api/vector/configs/{id}`

**描述**: 更新配置版本

**路径参数**:
- `id` (long): 配置ID

**请求体**: 同2.1(可部分更新)

**响应示例**:
```json
{
  "code": 200,
  "message": "更新成功",
  "data": null
}
```

---

### 2.5 删除配置

**接口**: `DELETE /api/vector/configs/{id}`

**描述**: 删除配置版本(仅能删除draft状态的配置)

**路径参数**:
- `id` (long): 配置ID

**响应示例**:
```json
{
  "code": 200,
  "message": "删除成功",
  "data": null
}
```

---

### 2.6 校验配置

**接口**: `POST /api/vector/configs/{id}/validate`

**描述**: 校验配置的YAML语法和逻辑

**路径参数**:
- `id` (long): 配置ID

**响应示例**:
```json
{
  "code": 200,
  "message": "校验成功",
  "data": {
    "isValid": true,
    "errors": []
  }
}
```

**校验失败示例**:
```json
{
  "code": 200,
  "message": "校验失败",
  "data": {
    "isValid": false,
    "errors": [
      "sources.syslog_source.address: 格式错误",
      "sinks.clickhouse_sink.endpoint: 缺少必填字段"
    ]
  }
}
```

---

### 2.7 发布配置

**接口**: `POST /api/vector/configs/{id}/release`

**描述**: 发布配置(状态从draft变为released)

**路径参数**:
- `id` (long): 配置ID

**响应示例**:
```json
{
  "code": 200,
  "message": "发布成功",
  "data": {
    "version": "v1.0.6",
    "releasedAt": "2025-12-26T10:00:00"
  }
}
```

---

### 2.8 Agent 拉取最新配置

**接口**: `GET /api/vector/configs/latest`

**描述**: Agent 拉取最新的已发布配置(根据机器标签和环境匹配)

**请求头**:
```
Authorization: Bearer {agentToken}
```

**请求参数**:
- `tags` (array, 可选): 机器标签

**请求示例**:
```
GET /api/vector/configs/latest?tags=production,web
```

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "id": 10,
    "version": "v1.0.6",
    "name": "生产环境Syslog配置",
    "yamlContent": "sources:\n  syslog_source:\n    type: syslog\n    address: 0.0.0.0:514\n..."
  }
}
```

**无配置时**:
```json
{
  "code": 200,
  "message": "成功",
  "data": null
}
```

---

## 三、配置部署 API

### 3.1 创建部署任务

**接口**: `POST /api/vector/deployments`

**描述**: 创建配置部署任务(单机或批量)

**请求体**:
```json
{
  "configId": 10,
  "targetHostIds": [1, 2, 3, 4],
  "deploymentType": "manual",
  "executionMode": "parallel",
  "batchSize": 10
}
```

**字段说明**:
- `configId` (long, 必填): 配置ID
- `targetHostIds` (array): 目标机器ID列表(与targetTags二选一)
- `targetTags` (array): 目标标签(动态匹配机器)
- `deploymentType` (string): 部署类型(manual/auto/canary)
- `executionMode` (string): 执行模式(parallel并行/sequential顺序/canary金丝雀)
- `batchSize` (int): 批量大小(每批多少台)

**响应示例**:
```json
{
  "code": 200,
  "message": "部署任务已创建",
  "data": {
    "taskId": 100,
    "totalHosts": 4
  }
}
```

---

### 3.2 查询部署任务详情

**接口**: `GET /api/vector/deployments/{taskId}`

**描述**: 查询部署任务详情和进度

**路径参数**:
- `taskId` (long): 任务ID

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "id": 100,
    "taskName": "部署配置v1.0.6到生产环境",
    "taskType": "deploy",
    "status": "running",
    "progress": 50,
    "totalHosts": 4,
    "successCount": 2,
    "failedCount": 0,
    "startedAt": "2025-12-26T10:00:00",
    "deployments": [
      {
        "id": 201,
        "hostId": 1,
        "hostname": "web-server-01",
        "ipAddress": "192.168.1.100",
        "configVersion": "v1.0.6",
        "status": "success",
        "startedAt": "2025-12-26T10:00:00",
        "completedAt": "2025-12-26T10:00:30",
        "errorMessage": null
      },
      {
        "id": 202,
        "hostId": 2,
        "hostname": "web-server-02",
        "ipAddress": "192.168.1.101",
        "configVersion": "v1.0.6",
        "status": "deploying",
        "startedAt": "2025-12-26T10:00:00",
        "completedAt": null,
        "errorMessage": null
      }
    ]
  }
}
```

---

### 3.3 查询部署历史

**接口**: `GET /api/vector/deployments`

**描述**: 分页查询部署历史

**请求参数**:
- `pageNum` (int, 默认1): 页码
- `pageSize` (int, 默认20): 每页条数
- `hostId` (long, 可选): 按机器筛选
- `status` (string, 可选): 按状态筛选

**响应示例**: 类似3.2,返回分页列表

---

### 3.4 回滚配置

**接口**: `POST /api/vector/deployments/{taskId}/rollback`

**描述**: 回滚部署任务(将所有机器回滚到上一版本)

**路径参数**:
- `taskId` (long): 任务ID

**响应示例**:
```json
{
  "code": 200,
  "message": "回滚任务已创建",
  "data": {
    "rollbackTaskId": 101
  }
}
```

---

### 3.5 Agent 上报部署状态

**接口**: `POST /api/vector/deployments/report`

**描述**: Agent 应用配置后上报结果

**请求头**:
```
Authorization: Bearer {agentToken}
```

**请求体**:
```json
{
  "configVersion": "v1.0.6",
  "status": "success",
  "errorMessage": null,
  "completedAt": "2025-12-26T10:00:30"
}
```

**字段说明**:
- `configVersion` (string, 必填): 配置版本号
- `status` (string, 必填): 状态(success/failed)
- `errorMessage` (string): 错误信息
- `completedAt` (datetime): 完成时间

**响应示例**:
```json
{
  "code": 200,
  "message": "状态已记录",
  "data": null
}
```

---

## 四、指标监控 API

### 4.1 Agent 上报指标

**接口**: `POST /api/vector/metrics`

**描述**: Agent 定时(60秒)上报系统和Vector指标

**请求头**:
```
Authorization: Bearer {agentToken}
```

**请求体**:
```json
{
  "collectedAt": "2025-12-26T10:00:00",
  "cpuUsagePercent": 45.5,
  "memoryUsagePercent": 60.2,
  "memoryUsedMb": 9830,
  "diskUsagePercent": 75.0,
  "diskUsedGb": 300,
  "agentUptimeSeconds": 3600,
  "agentMemoryMb": 50,
  "vectorRunning": true,
  "vectorUptimeSeconds": 3500,
  "vectorConfigReloadCount": 2,
  "vectorErrorCount": 0,
  "eventsInTotal": 1000000,
  "eventsOutTotal": 1000000,
  "eventsInRate": 1000.5,
  "eventsOutRate": 1000.5
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "指标已记录",
  "data": null
}
```

---

### 4.2 查询机器指标

**接口**: `GET /api/vector/hosts/{id}/metrics`

**描述**: 查询机器的历史指标数据(用于图表展示)

**路径参数**:
- `id` (long): 机器ID

**请求参数**:
- `startTime` (datetime): 开始时间
- `endTime` (datetime): 结束时间
- `interval` (string): 时间间隔(1m/5m/1h/1d)

**请求示例**:
```
GET /api/vector/hosts/1/metrics?startTime=2025-12-26T00:00:00&endTime=2025-12-26T23:59:59&interval=5m
```

**响应示例**:
```json
{
  "code": 200,
  "message": "成功",
  "data": [
    {
      "timestamp": "2025-12-26T10:00:00",
      "cpuUsagePercent": 45.5,
      "memoryUsagePercent": 60.2,
      "diskUsagePercent": 75.0,
      "vectorRunning": true,
      "eventsInRate": 1000.5,
      "eventsOutRate": 1000.5
    },
    {
      "timestamp": "2025-12-26T10:05:00",
      "cpuUsagePercent": 46.0,
      "memoryUsagePercent": 61.0,
      "diskUsagePercent": 75.1,
      "vectorRunning": true,
      "eventsInRate": 1050.2,
      "eventsOutRate": 1050.2
    }
  ]
}
```

---

### 4.3 查询机器最新指标

**接口**: `GET /api/vector/hosts/{id}/metrics/latest`

**描述**: 查询机器的最新指标(用于实时监控)

**路径参数**:
- `id` (long): 机器ID

**响应示例**: 同4.2中的单条记录

---

## 五、错误码说明

| 错误码 | 说明 |
|-------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未授权(Token无效或过期) |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

**错误响应格式**:
```json
{
  "code": 400,
  "message": "Token 无效或已过期",
  "data": null
}
```

---

## 六、通用响应格式

所有接口统一返回格式:

```json
{
  "code": 200,
  "message": "成功",
  "data": {}
}
```

**字段说明**:
- `code` (int): 状态码
- `message` (string): 提示信息
- `data` (object/array/null): 响应数据

---

## 七、分页响应格式

分页接口统一返回格式:

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "total": 100,
    "pageNum": 1,
    "pageSize": 20,
    "pages": 5,
    "records": []
  }
}
```

**字段说明**:
- `total` (long): 总记录数
- `pageNum` (int): 当前页码
- `pageSize` (int): 每页条数
- `pages` (int): 总页数
- `records` (array): 当前页数据

---

## 八、接口调用示例

### 8.1 前端添加机器流程

```javascript
// 1. 生成Token
const tokenRes = await axios.post('/api/vector/hosts/generate-token')
const token = tokenRes.data.data.token

// 2. 显示安装命令给用户
const installCommand = `curl -fsSL http://server/install.sh | sudo bash -s ${token} http://server:8080`

// 3. 轮询检测注册
const checkInterval = setInterval(async () => {
  const checkRes = await axios.get(`/api/vector/hosts/check-token/${token}`)
  if (checkRes.data.data.registered) {
    clearInterval(checkInterval)
    message.success('机器注册成功!')
    refreshHostList()
  }
}, 3000)
```

---

### 8.2 Agent 注册和心跳流程

```go
// Agent 启动时注册
func (a *Agent) Register() error {
    req := RegisterRequest{
        Hostname:      getHostname(),
        IPAddress:     getIPAddress(),
        AgentToken:    a.config.AgentToken,
        AgentVersion:  "1.0.0",
        VectorVersion: getVectorVersion(),
        OSType:        runtime.GOOS,
        // ...
    }

    resp, err := a.client.Post("/api/vector/hosts/register", req)
    if err != nil {
        return err
    }

    a.hostID = resp.HostID
    return nil
}

// 定时心跳
func (a *Agent) HeartbeatLoop() {
    ticker := time.NewTicker(30 * time.Second)
    for range ticker.C {
        req := HeartbeatRequest{
            AgentUptimeSeconds: int64(time.Since(a.startTime).Seconds()),
            VectorRunning:      a.vector.IsRunning(),
            Status:             "online",
        }

        resp, _ := a.client.Post("/api/vector/hosts/heartbeat", req)

        // 检查是否有新配置
        if resp.HasNewConfig {
            a.fetchAndApplyConfig()
        }
    }
}
```

---

## 九、安全说明

### 9.1 认证方式

- **Agent接口**: 使用 `Authorization: Bearer {agentToken}` 认证
- **管理接口**: 使用 JWT Token 认证(继承现有auth模块)

### 9.2 接口权限

| 接口分类 | 访问权限 |
|---------|---------|
| Agent注册 | 公开(仅需有效Token) |
| Agent心跳/上报 | 需要Agent Token |
| 配置拉取 | 需要Agent Token |
| 管理接口 | 需要管理员JWT Token |

---

## 十、性能建议

1. **心跳间隔**: 建议30秒(避免过于频繁)
2. **指标上报间隔**: 建议60秒
3. **配置轮询间隔**: 建议30秒(可通过心跳响应优化)
4. **历史数据查询**: 建议限制时间范围(≤7天)
5. **批量部署**: 建议分批执行(每批≤50台)

---

**文档更新日期**: 2025-12-26
**维护者**: Claude Code
