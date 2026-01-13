# 后端 API 接口文档

本文档列出所有后端接口的路径、作用和代码位置。

## 目录

- [认证模块 (Auth)](#认证模块-auth)
- [用户模块 (User)](#用户模块-user)
- [统计查询模块 (Stats)](#统计查询模块-stats)
- [数据源管理模块 (Datasource)](#数据源管理模块-datasource)
- [Vector Agent 模块](#vector-agent-模块)
- [Vector 机器管理模块](#vector-机器管理模块)
- [Vector 配置管理模块](#vector-配置管理模块)
- [Vector 部署管理模块](#vector-部署管理模块)
- [Vector 日志管理模块](#vector-日志管理模块)
- [机器指标模块](#机器指标模块)
- [组件库管理模块](#组件库管理模块)
- [共享组件模块](#共享组件模块)
- [可视化配置模块](#可视化配置模块)
- [VRL 编辑器模块](#vrl-编辑器模块)
- [告警规则模块](#告警规则模块)
- [告警事件模块](#告警事件模块)
- [日志提取规则模块](#日志提取规则模块)
- [仪表盘模块](#仪表盘模块)
- [系统配置模块](#系统配置模块)
- [用户字段配置模块](#用户字段配置模块)

---

## 认证模块 (Auth)

**Controller**: `AuthController.java`
**路径**: `log-analysis-app/src/main/java/cn/mw/loganalysis/auth/controller/AuthController.java`

| 接口路径 | HTTP方法 | 作用 | 代码位置 |
|---------|---------|------|---------|
| `/api/auth/login` | POST | 用户登录 | AuthController.java:28 |
| `/api/auth/refresh` | POST | 刷新 Token | AuthController.java:38 |
| `/api/auth/logout` | POST | 用户登出 | AuthController.java:48 |
| `/api/auth/user/info` | GET | 获取当前用户信息 | AuthController.java:58 |

---

## 用户模块 (User)

**Controller**: `UserController.java`
**路径**: `log-analysis-app/src/main/java/cn/mw/loganalysis/auth/controller/UserController.java`

| 接口路径 | HTTP方法 | 作用 | 代码位置 |
|---------|---------|------|---------|
| `/api/users` | GET | 获取用户列表 | UserController.java |
| `/api/users/{id}` | GET | 获取用户详情 | UserController.java |
| `/api/users` | POST | 创建用户 | UserController.java |
| `/api/users/{id}` | PUT | 更新用户信息 | UserController.java |
| `/api/users/{id}` | DELETE | 删除用户 | UserController.java |

---

## 统计查询模块 (Stats)

**Controller**: `StatsController.java`
**路径**: `log-analysis-app/src/main/java/cn/mw/loganalysis/stats/controller/StatsController.java`

| 接口路径 | HTTP方法 | 作用 | 代码位置 |
|---------|---------|------|---------|
| `/api/stats/datasource/{datasourceId}/schema` | GET | 获取数据源表结构（字段列表） | StatsController.java:35 |
| `/api/stats/logs/query` | POST | 查询日志（支持动态数据源） | StatsController.java:45 |
| `/api/stats/logs/context` | POST | 查询日志上下文（前后日志） | StatsController.java:68 |
| `/api/stats/query` | POST | 统计查询（字段维度统计） | StatsController.java:83 |
| `/api/stats/timeseries` | POST | 时间序列查询 | StatsController.java:102 |
| `/api/stats/field-timeseries` | POST | 字段时序查询 | StatsController.java:116 |
| `/api/stats/export` | POST | 导出报表（CSV/Excel） | StatsController.java:130 |

---

## 数据源管理模块 (Datasource)

**Controller**: `DatasourceManagementController.java`
**路径**: `log-analysis-app/src/main/java/cn/mw/loganalysis/datasource/controller/DatasourceManagementController.java`

| 接口路径 | HTTP方法 | 作用 | 代码位置 |
|---------|---------|------|---------|
| `/api/datasources` | POST | 创建数据源 | DatasourceManagementController.java:32 |
| `/api/datasources/{id}` | PUT | 更新数据源 | DatasourceManagementController.java:42 |
| `/api/datasources/{id}` | DELETE | 删除数据源 | DatasourceManagementController.java:54 |
| `/api/datasources/{id}` | GET | 获取数据源详情 | DatasourceManagementController.java:64 |
| `/api/datasources` | GET | 分页查询数据源列表（支持关键字、类型、状态筛选） | DatasourceManagementController.java:76 |
| `/api/datasources/active` | GET | 获取所有活跃的数据源 | DatasourceManagementController.java:92 |
| `/api/datasources/by-type/{type}` | GET | 根据类型查询数据源 | DatasourceManagementController.java:100 |
| `/api/datasources/{id}/test` | POST | 测试数据源连接（已存在的数据源） | DatasourceManagementController.java:109 |
| `/api/datasources/test` | POST | 测试新数据源连接（创建前测试） | DatasourceManagementController.java:118 |

**支持的数据源类型**:
- `clickhouse` - ClickHouse 数据库
- `postgresql` - PostgreSQL 数据库
- `mysql` - MySQL 数据库
- `elasticsearch` - Elasticsearch（待实现）
- `loki` - Loki（待实现）

**数据源状态**:
- `active` - 活跃
- `inactive` - 停用
- `error` - 错误

---

## Vector Agent 模块

**Controller**: `VectorAgentController.java`
**路径**: `log-analysis-app/src/main/java/cn/mw/loganalysis/vector/controller/VectorAgentController.java`

| 接口路径 | HTTP方法 | 作用 | 代码位置 |
|---------|---------|------|---------|
| `/api/vector/agents/register` | POST | Agent 注册 | VectorAgentController.java:50 |
| `/api/vector/agents/heartbeat` | POST | Agent 心跳 | VectorAgentController.java:73 |
| `/api/vector/agents/config` | GET | Agent 拉取配置 | VectorAgentController.java:95 |
| `/api/vector/agents/config/deploy-status` | POST | Agent 上报部署状态 | VectorAgentController.java:139 |
| `/api/vector/agents/metrics` | POST | Agent 上报系统指标（含网卡信息） | VectorAgentController.java:161 |
| `/api/vector/agents/component-status/{machineId}` | GET | 获取组件状态 | VectorAgentController.java:193 |
| `/api/vector/agents/logs` | POST | Agent 上报日志 | VectorAgentController.java:202 |
| `/api/vector/agents/install-script` | GET | 获取 Agent 安装脚本 | VectorAgentController.java:215 |
| `/api/vector/agents/download` | GET | 下载 Agent 安装包 | VectorAgentController.java:242 |
| `/api/vector/agents/command` | GET | Agent 拉取待执行命令 | VectorAgentController.java:275 |
| `/api/vector/agents/command/status` | POST | Agent 上报命令执行状态 | VectorAgentController.java:319 |
| `/api/vector/agents/send-command` | POST | 向 Agent 发送命令 | VectorAgentController.java:339 |
| `/api/vector/agents/commands/{machineId}` | GET | 获取机器的命令历史 | VectorAgentController.java:366 |
| `/api/vector/agents/debug/config-dir/{machineId}` | GET | 获取配置目录（调试用） | VectorAgentController.java:378 |

---

## Vector 机器管理模块

**Controller**: `VectorMachineController.java`
**路径**: `log-analysis-app/src/main/java/cn/mw/loganalysis/vector/controller/VectorMachineController.java`

| 接口路径 | HTTP方法 | 作用 | 代码位置 |
|---------|---------|------|---------|
| `/api/vector/machines` | GET | 获取机器列表 | VectorMachineController.java |
| `/api/vector/machines/{id}` | GET | 获取机器详情 | VectorMachineController.java |
| `/api/vector/machines` | POST | 创建机器 | VectorMachineController.java |
| `/api/vector/machines/{id}` | PUT | 更新机器信息 | VectorMachineController.java |
| `/api/vector/machines/{id}` | DELETE | 删除机器 | VectorMachineController.java |
| `/api/vector/machines/{id}/status` | PUT | 更新机器状态 | VectorMachineController.java |

---

## Vector 配置管理模块

**Controller**: `VectorConfigController.java`
**路径**: `log-analysis-app/src/main/java/cn/mw/loganalysis/vector/controller/VectorConfigController.java`

| 接口路径 | HTTP方法 | 作用 | 代码位置 |
|---------|---------|------|---------|
| `/api/vector/configs` | GET | 获取配置列表 | VectorConfigController.java |
| `/api/vector/configs/{id}` | GET | 获取配置详情 | VectorConfigController.java |
| `/api/vector/configs` | POST | 创建配置 | VectorConfigController.java |
| `/api/vector/configs/{id}` | PUT | 更新配置 | VectorConfigController.java |
| `/api/vector/configs/{id}` | DELETE | 删除配置 | VectorConfigController.java |
| `/api/vector/configs/{id}/validate` | POST | 验证配置 | VectorConfigController.java |

---

## Vector 部署管理模块

**Controller**: `VectorDeploymentController.java`
**路径**: `log-analysis-app/src/main/java/cn/mw/loganalysis/vector/controller/VectorDeploymentController.java`

| 接口路径 | HTTP方法 | 作用 | 代码位置 |
|---------|---------|------|---------|
| `/api/vector/deployments` | GET | 获取部署列表 | VectorDeploymentController.java |
| `/api/vector/deployments/{id}` | GET | 获取部署详情 | VectorDeploymentController.java |
| `/api/vector/deployments` | POST | 创建部署 | VectorDeploymentController.java |
| `/api/vector/deployments/{id}/status` | GET | 获取部署状态 | VectorDeploymentController.java |
| `/api/vector/deployments/{id}/rollback` | POST | 回滚部署 | VectorDeploymentController.java |

---

## Vector 日志管理模块

**Controller**: `VectorLogController.java`
**路径**: `log-analysis-app/src/main/java/cn/mw/loganalysis/vector/controller/VectorLogController.java`

| 接口路径 | HTTP方法 | 作用 | 代码位置 |
|---------|---------|------|---------|
| `/api/vector/logs` | GET | 获取 Vector 运行日志列表 | VectorLogController.java |
| `/api/vector/logs/stream` | GET | 实时流式获取日志（SSE） | VectorLogController.java |

---

## 机器指标模块

**Controller**: `MachineMetricsController.java`
**路径**: `log-analysis-app/src/main/java/cn/mw/loganalysis/vector/controller/MachineMetricsController.java`

| 接口路径 | HTTP方法 | 作用 | 代码位置 |
|---------|---------|------|---------|
| `/api/vector/machines/{id}/detail` | GET | 获取机器详情（含最新指标） | MachineMetricsController.java:29 |
| `/api/vector/machines/{id}/metrics` | GET | 获取机器指标历史 | MachineMetricsController.java:64 |
| `/api/vector/machines/{id}/metrics/latest` | GET | 获取机器最新指标（含网卡信息） | MachineMetricsController.java:81 |

---

## 组件库管理模块

**Controller**: `ConfigComponentController.java`
**路径**: `log-analysis-app/src/main/java/cn/mw/loganalysis/vector/controller/ConfigComponentController.java`

| 接口路径 | HTTP方法 | 作用 | 代码位置 |
|---------|---------|------|---------|
| `/api/vector/components` | GET | 获取组件列表（支持分页和筛选） | ConfigComponentController.java |
| `/api/vector/components/{id}` | GET | 获取组件详情 | ConfigComponentController.java |
| `/api/vector/components` | POST | 创建组件 | ConfigComponentController.java |
| `/api/vector/components/{id}` | PUT | 更新组件 | ConfigComponentController.java |
| `/api/vector/components/{id}` | DELETE | 删除组件 | ConfigComponentController.java |
| `/api/vector/components/batch` | POST | 批量创建组件 | ConfigComponentController.java |
| `/api/vector/components/types` | GET | 获取组件类型列表 | ConfigComponentController.java |
| `/api/vector/components/validate` | POST | 验证组件配置 | ConfigComponentController.java |

---

## 共享组件模块

**Controller**: `SharedComponentController.java`
**路径**: `log-analysis-app/src/main/java/cn/mw/loganalysis/vector/controller/SharedComponentController.java`

| 接口路径 | HTTP方法 | 作用 | 代码位置 |
|---------|---------|------|---------|
| `/api/vector/shared-components` | GET | 获取共享组件列表 | SharedComponentController.java |
| `/api/vector/shared-components/{id}` | GET | 获取共享组件详情 | SharedComponentController.java |
| `/api/vector/shared-components` | POST | 创建共享组件 | SharedComponentController.java |
| `/api/vector/shared-components/{id}` | PUT | 更新共享组件 | SharedComponentController.java |
| `/api/vector/shared-components/{id}` | DELETE | 删除共享组件 | SharedComponentController.java |

---

## 可视化配置模块

**Controller**: `VisualConfigController.java`
**路径**: `log-analysis-app/src/main/java/cn/mw/loganalysis/vector/controller/VisualConfigController.java`

| 接口路径 | HTTP方法 | 作用 | 代码位置 |
|---------|---------|------|---------|
| `/api/vector/visual-configs` | GET | 获取可视化配置列表 | VisualConfigController.java |
| `/api/vector/visual-configs/{id}` | GET | 获取可视化配置详情 | VisualConfigController.java |
| `/api/vector/visual-configs` | POST | 创建可视化配置 | VisualConfigController.java |
| `/api/vector/visual-configs/{id}` | PUT | 更新可视化配置 | VisualConfigController.java |
| `/api/vector/visual-configs/{id}` | DELETE | 删除可视化配置 | VisualConfigController.java |
| `/api/vector/visual-configs/{id}/generate-yaml` | POST | 根据可视化配置生成 YAML | VisualConfigController.java |
| `/api/vector/visual-configs/{id}/deploy` | POST | 部署可视化配置 | VisualConfigController.java |

---

## VRL 编辑器模块

**Controller**: `VrlController.java`
**路径**: `log-analysis-app/src/main/java/cn/mw/loganalysis/vector/controller/VrlController.java`

| 接口路径 | HTTP方法 | 作用 | 代码位置 |
|---------|---------|------|---------|
| `/api/vector/vrl/validate` | POST | 验证 VRL 脚本语法 | VrlController.java |
| `/api/vector/vrl/test` | POST | 测试 VRL 脚本执行 | VrlController.java |
| `/api/vector/vrl/functions` | GET | 获取 VRL 内置函数列表 | VrlController.java |

---

## 告警规则模块

**Controller**: `AlertRuleController.java`
**路径**: `log-analysis-app/src/main/java/cn/mw/loganalysis/alert/controller/AlertRuleController.java`

| 接口路径 | HTTP方法 | 作用 | 代码位置 |
|---------|---------|------|---------|
| `/api/alerts/rules` | GET | 获取告警规则列表 | AlertRuleController.java |
| `/api/alerts/rules/{id}` | GET | 获取告警规则详情 | AlertRuleController.java |
| `/api/alerts/rules` | POST | 创建告警规则 | AlertRuleController.java |
| `/api/alerts/rules/{id}` | PUT | 更新告警规则 | AlertRuleController.java |
| `/api/alerts/rules/{id}` | DELETE | 删除告警规则 | AlertRuleController.java |
| `/api/alerts/rules/{id}/enable` | PUT | 启用告警规则 | AlertRuleController.java |
| `/api/alerts/rules/{id}/disable` | PUT | 禁用告警规则 | AlertRuleController.java |

---

## 告警事件模块

**Controller**: `AlertEventController.java`
**路径**: `log-analysis-app/src/main/java/cn/mw/loganalysis/alert/controller/AlertEventController.java`

| 接口路径 | HTTP方法 | 作用 | 代码位置 |
|---------|---------|------|---------|
| `/api/alerts/events` | GET | 获取告警事件列表 | AlertEventController.java |
| `/api/alerts/events/{id}` | GET | 获取告警事件详情 | AlertEventController.java |
| `/api/alerts/events/{id}/acknowledge` | PUT | 确认告警事件 | AlertEventController.java |
| `/api/alerts/events/{id}/resolve` | PUT | 解决告警事件 | AlertEventController.java |
| `/api/alerts/events/stats` | GET | 获取告警统计信息 | AlertEventController.java |

---

## 日志提取规则模块

**Controller**: `ExtractionRuleController.java`
**路径**: `log-analysis-app/src/main/java/cn/mw/loganalysis/extraction/controller/ExtractionRuleController.java`

| 接口路径 | HTTP方法 | 作用 | 代码位置 |
|---------|---------|------|---------|
| `/api/extraction/rules` | GET | 获取提取规则列表 | ExtractionRuleController.java |
| `/api/extraction/rules/{id}` | GET | 获取提取规则详情 | ExtractionRuleController.java |
| `/api/extraction/rules` | POST | 创建提取规则 | ExtractionRuleController.java |
| `/api/extraction/rules/{id}` | PUT | 更新提取规则 | ExtractionRuleController.java |
| `/api/extraction/rules/{id}` | DELETE | 删除提取规则 | ExtractionRuleController.java |
| `/api/extraction/rules/{id}/test` | POST | 测试提取规则 | ExtractionRuleController.java |

---

## 仪表盘模块

**Controller**: `DashboardController.java`
**路径**: `log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard/controller/DashboardController.java`

| 接口路径 | HTTP方法 | 作用 | 代码位置 |
|---------|---------|------|---------|
| `/api/dashboards` | GET | 获取仪表盘列表 | DashboardController.java |
| `/api/dashboards/{id}` | GET | 获取仪表盘详情 | DashboardController.java |
| `/api/dashboards` | POST | 创建仪表盘 | DashboardController.java |
| `/api/dashboards/{id}` | PUT | 更新仪表盘 | DashboardController.java |
| `/api/dashboards/{id}` | DELETE | 删除仪表盘 | DashboardController.java |

---

## 系统配置模块

**Controller**: `SystemConfigController.java`
**路径**: `log-analysis-app/src/main/java/cn/mw/loganalysis/config/controller/SystemConfigController.java`

| 接口路径 | HTTP方法 | 作用 | 代码位置 |
|---------|---------|------|---------|
| `/api/system/config` | GET | 获取系统配置 | SystemConfigController.java |
| `/api/system/config` | PUT | 更新系统配置 | SystemConfigController.java |

---

## 用户字段配置模块

**Controller**: `UserFieldConfigController.java`
**路径**: `log-analysis-app/src/main/java/cn/mw/loganalysis/config/controller/UserFieldConfigController.java`

| 接口路径 | HTTP方法 | 作用 | 代码位置 |
|---------|---------|------|---------|
| `/api/user-field-configs` | GET | 获取用户字段配置列表 | UserFieldConfigController.java |
| `/api/user-field-configs/{id}` | GET | 获取用户字段配置详情 | UserFieldConfigController.java |
| `/api/user-field-configs` | POST | 创建用户字段配置 | UserFieldConfigController.java |
| `/api/user-field-configs/{id}` | PUT | 更新用户字段配置 | UserFieldConfigController.java |
| `/api/user-field-configs/{id}` | DELETE | 删除用户字段配置 | UserFieldConfigController.java |

---

## 重要说明

### 网卡信息相关接口

**Agent 上报网卡信息**：
- 接口：`POST /api/vector/agents/metrics`
- 位置：`VectorAgentController.java:161`
- 说明：Agent 通过此接口上报系统指标，包括 CPU、内存、磁盘、网卡信息等

**前端获取网卡信息**：
- 接口：`GET /api/vector/machines/{id}/metrics/latest`
- 位置：`MachineMetricsController.java:81`
- 说明：前端通过此接口获取机器最新指标，包含网卡信息

### 数据流向

```
Agent 上报 → /api/vector/agents/metrics → MachineMetricsService.recordMetrics()
           → 序列化网卡信息为 JSON → ClickHouse 存储

前端查询 → /api/vector/machines/{id}/metrics/latest → MachineMetricsService.getLatestMetrics()
        → 反序列化 JSON 为网卡对象 → 返回给前端
```

---

## 更新日志

- **2026-01-06**: 添加网卡信息持久化功能，修改 `machine_metrics` 表结构
- **2026-01-06**: 创建 API 接口文档

---

**文档生成时间**: 2026-01-06
**文档版本**: v1.0
