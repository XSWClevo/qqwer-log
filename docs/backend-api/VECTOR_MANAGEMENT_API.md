# Vector 日志管理 API 文档

**版本**: 1.0.0
**创建日期**: 2025-12-26
**后端模块**: `cn.mw.loganalysis.vector`

---

## 1. 主机管理 API

### 1.1 分页查询主机列表

**接口**: `GET /api/vector/machines/page`

**请求参数**:
```json
{
  "pageNum": 1,           // 页码，默认 1
  "pageSize": 10,         // 每页数量，默认 10
  "keyword": "web",       // 可选，搜索关键词（主机名、IP）
  "status": "online"      // 可选，状态过滤：online/offline/error
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
        "id": "uuid",
        "name": "Web Server 01",
        "hostname": "web-01.example.com",
        "ipAddress": "10.0.1.10",
        "sshPort": 22,
        "sshUser": "root",
        "status": "online",
        "managementMethod": "systemctl",
        "vectorVersion": "0.35.0",
        "vectorInstallPath": "/usr/local/bin/vector",
        "vectorConfigPath": "/etc/vector/vector.yaml",
        "lastHeartbeat": "2025-12-26T10:30:00",
        "createdAt": "2025-12-25T08:00:00",
        "updatedAt": "2025-12-26T10:30:00"
      }
    ],
    "total": 10,
    "size": 10,
    "current": 1,
    "pages": 1
  }
}
```

---

### 1.2 查询主机列表（不分页）

**接口**: `GET /api/vector/machines/list`

**请求参数**:
```json
{
  "status": "online"  // 可选，状态过滤
}
```

**响应**: 返回主机数组

---

### 1.3 查询主机详情

**接口**: `GET /api/vector/machines/{id}`

**路径参数**: `id` - 主机ID

**响应**: 返回单个主机对象

---

### 1.4 添加主机

**接口**: `POST /api/vector/machines`

**请求体**:
```json
{
  "name": "Web Server 01",
  "hostname": "web-01.example.com",
  "ipAddress": "10.0.1.10",
  "sshPort": 22,
  "sshUser": "root",
  "sshKeyPath": "/root/.ssh/id_rsa",
  "osType": "linux",
  "managementMethod": "systemctl",
  "vectorInstallPath": "/usr/local/bin/vector",
  "vectorConfigPath": "/etc/vector/vector.yaml"
}
```

**响应**: 返回创建的主机对象

---

### 1.5 更新主机信息

**接口**: `PUT /api/vector/machines/{id}`

**路径参数**: `id` - 主机ID

**请求体**: 同添加主机

**响应**: 返回更新后的主机对象

---

### 1.6 删除主机

**接口**: `DELETE /api/vector/machines/{id}`

**路径参数**: `id` - 主机ID

**响应**: 成功返回 200

---

### 1.7 更新主机状态

**接口**: `PUT /api/vector/machines/{id}/status`

**请求参数**:
```json
{
  "status": "online"  // online/offline/error
}
```

---

### 1.8 更新心跳

**接口**: `POST /api/vector/machines/{id}/heartbeat`

**说明**: 更新最后心跳时间，并将状态设置为 online

---

## 2. 配置管理 API

### 2.1 分页查询配置列表

**接口**: `GET /api/vector/configs/page`

**请求参数**:
```json
{
  "pageNum": 1,
  "pageSize": 10,
  "keyword": "config",      // 可选，搜索关键词
  "isTemplate": true        // 可选，是否模板
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
        "id": "uuid",
        "name": "prod-vector-config",
        "description": "生产环境配置",
        "content": "sources:\n  file:\n    type: file\n    include:\n      - /var/log/*.log",
        "version": 2,
        "isTemplate": false,
        "parentConfigId": null,
        "createdAt": "2025-12-26T08:00:00",
        "updatedAt": "2025-12-26T09:00:00",
        "createdBy": "admin"
      }
    ],
    "total": 5,
    "size": 10,
    "current": 1,
    "pages": 1
  }
}
```

---

### 2.2 查询配置列表（不分页）

**接口**: `GET /api/vector/configs/list`

**请求参数**:
```json
{
  "isTemplate": false  // 可选，是否模板
}
```

---

### 2.3 查询模板配置列表

**接口**: `GET /api/vector/configs/templates`

**响应**: 返回所有模板配置数组

---

### 2.4 查询配置详情

**接口**: `GET /api/vector/configs/{id}`

**路径参数**: `id` - 配置ID

---

### 2.5 添加配置

**接口**: `POST /api/vector/configs`

**请求体**:
```json
{
  "name": "my-vector-config",
  "description": "我的 Vector 配置",
  "content": "sources:\n  file:\n    type: file\n    include:\n      - /var/log/*.log\nsinks:\n  clickhouse:\n    type: clickhouse\n    endpoint: http://localhost:8123\n    database: logs\n    table: log_entries",
  "isTemplate": false,
  "parentConfigId": null
}
```

**说明**: `version` 字段会自动生成（基于同名配置的最高版本号 +1）

---

### 2.6 更新配置

**接口**: `PUT /api/vector/configs/{id}`

**请求体**: 同添加配置

---

### 2.7 删除配置

**接口**: `DELETE /api/vector/configs/{id}`

---

### 2.8 复制配置

**接口**: `POST /api/vector/configs/{id}/copy`

**说明**: 创建一个新版本的配置副本

---

## 3. 数据模型

### 3.1 VectorMachine（主机）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | String | - | UUID |
| name | String | ✓ | 主机名称 |
| hostname | String | ✓ | 主机名 |
| ipAddress | String | ✓ | IP地址 |
| sshPort | Integer | ✓ | SSH端口 |
| sshUser | String | ✓ | SSH用户 |
| sshKeyPath | String | - | SSH密钥路径 |
| osType | String | - | 操作系统类型，默认 linux |
| status | String | - | 状态：online/offline/error |
| vectorVersion | String | - | Vector版本 |
| vectorInstallPath | String | - | Vector安装路径 |
| vectorConfigPath | String | - | Vector配置路径 |
| managementMethod | String | - | 管理方式：systemctl/binary |
| lastHeartbeat | LocalDateTime | - | 最后心跳时间 |
| createdAt | LocalDateTime | - | 创建时间 |
| updatedAt | LocalDateTime | - | 更新时间 |
| createdBy | String | - | 创建人ID |

---

### 3.2 VectorConfig（配置）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | String | - | UUID |
| name | String | ✓ | 配置名称 |
| description | String | - | 配置描述 |
| content | String | ✓ | 配置内容（YAML/TOML） |
| version | Integer | - | 版本号（自动生成） |
| isTemplate | Boolean | - | 是否为模板，默认 false |
| parentConfigId | String | - | 父配置ID（用于派生） |
| createdAt | LocalDateTime | - | 创建时间 |
| updatedAt | LocalDateTime | - | 更新时间 |
| createdBy | String | - | 创建人ID |

---

## 4. 数据库表

### 4.1 初始化脚本

数据库初始化脚本位于：
```
/database/postgresql/vector_management.sql
```

执行方式：
```bash
psql -h localhost -U postgres -d postgres -f database/postgresql/vector_management.sql
```

### 4.2 核心表

- `vector_machines` - 主机管理表
- `vector_configs` - 配置管理表
- `vector_deployments` - 配置部署记录表（TODO）
- `vector_service_operations` - 服务操作记录表（TODO）
- `vector_pipeline_metrics` - 管道性能指标表（TODO）
- `vector_health_checks` - 健康检查记录表（TODO）
- `vector_config_components` - 配置组件库表（可选）
- `vector_visual_configs` - 可视化配置表（可选）

---

## 5. 前端路由

### 5.1 主机管理页面

**路由**: `/vector/machines`
**组件**: `@/views/vector/MachineList.vue`

**功能**:
- 分页展示主机列表（卡片式布局）
- 搜索和筛选（关键词、状态）
- 添加/编辑/删除主机
- 查看主机详情
- 显示主机状态和心跳时间

---

### 5.2 配置管理页面

**路由**: `/vector/configs`
**组件**: `@/views/vector/ConfigList.vue`

**功能**:
- 分页展示配置列表
- 搜索和筛选（关键词、模板/普通）
- 添加/编辑/删除配置
- 查看配置详情（YAML/TOML内容）
- 复制配置（创建新版本）
- 配置内容预览

---

## 6. 待实现功能

以下功能的后端 Entity/Mapper 已创建，但 Service/Controller 和前端页面尚未实现：

### 6.1 配置部署功能
- 将配置部署到指定主机
- 查看部署历史
- 部署状态追踪

### 6.2 服务操作功能
- 启动/停止/重启 Vector 服务
- 查看服务状态
- 操作历史记录

### 6.3 性能监控功能
- 管道性能指标收集
- 实时数据流量监控
- 性能趋势图表

### 6.4 健康检查功能
- 连接性检查
- 配置有效性检查
- 服务状态检查
- 管道流程检查
- 健康状态仪表盘

---

## 7. 注意事项

### 7.1 数据源配置

所有 Vector 管理相关的 Mapper 都使用 `@DS("postgres")` 注解，数据存储在 PostgreSQL 数据库中。

### 7.2 用户认证

当前 Controller 中使用硬编码的 `userId = "system"`，需要与 Spring Security 集成后从当前登录用户获取。

### 7.3 安全配置

需要在 `SecurityConfig.java` 中将 Vector 管理接口添加到白名单（如果需要公开访问）：

```java
.requestMatchers(
    "/api/vector/machines/**",
    "/api/vector/configs/**"
).permitAll()
```

或者保持需要认证访问（推荐）。

---

## 8. 下一步计划

1. **完善后端功能**：实现部署、服务操作、监控、健康检查的 Service 和 Controller
2. **前端页面开发**：实现监控仪表盘、健康检查页面
3. **集成 SSH 功能**：实现真实的远程 SSH 连接和命令执行
4. **WebSocket 实时推送**：实现实时状态更新和日志流
5. **配置可视化编辑器**：拖拽式 Vector 配置生成器

---

**文档生成时间**: 2025-12-26
**API 版本**: v1.0.0
**维护人**: Claude Code
