# 智能向导功能文档

## 功能概述

智能向导是一个4步向导流程，帮助用户快速创建日志表，无需手动编写 DDL 和配置 Vector 组件。

## 功能特性

### 1. 日志解析（步骤 1）

**支持的解析方式**：
- **JSON 格式**：自动解析 JSON 结构的日志
- **Syslog 格式**：解析标准 Syslog 格式（RFC 3164/5424）
- **Key-Value 格式**：解析 `key=value` 格式的日志
- **正则表达式**：使用自定义正则表达式提取字段
- **自定义 VRL**：编写自定义 VRL 脚本进行复杂解析

**功能**：
- 粘贴日志样本
- 选择解析方式
- 实时解析预览
- 错误提示和建议

### 2. 确认字段（步骤 2）

**字段类型推断**：
- 自动推断字段类型（String、Int32、Float64、DateTime 等）
- 特殊类型识别：IP 地址（IPv4/IPv6）
- 字段类型可手动调整

**字段管理**：
- 修改字段名
- 修改字段类型
- 添加字段备注
- 删除不需要的字段
- 添加新字段

**类型建议**：
- 带有 🔍 标记的字段表示检测到特殊类型
- 例如：检测到 IP 地址时建议使用 IPv4 类型

### 3. 配置表（步骤 3）

**表配置**：
- 选择 ClickHouse 数据源
- 输入表名（建议使用小写字母和下划线）
- 自动生成 DDL

**DDL 生成**：
- 使用系统配置的默认值（引擎、分区、排序键、TTL 等）
- 自动添加 UUID 主键
- 可选保留原始日志字段（raw）
- 自动配置索引
- DDL 预览和编辑

### 4. 完成（步骤 4）

**表创建**：
- 执行 DDL 创建表
- 显示创建结果
- 提供后续操作建议

**后续操作**：
- 前往可视化配置添加 Sink 组件
- 配置 Transform 组件使用解析脚本
- 部署配置到 Vector Agent

## 使用流程

### 1. 打开智能向导

在组件库页面点击"智能向导"按钮。

### 2. 解析日志样本

```
示例 1: Syslog 格式
<134>1 2024-01-07T10:30:45.123Z server01 nginx 1234 - - GET /api/users 200 0.123

示例 2: JSON 格式
{"timestamp":"2024-01-07T10:30:45Z","level":"INFO","message":"Request processed","user_id":123}

示例 3: Key-Value 格式
timestamp=2024-01-07T10:30:45 level=INFO message="Request processed" user_id=123
```

选择对应的解析方式，点击"解析日志"。

### 3. 确认字段类型

系统会自动推断字段类型，例如：
- `timestamp` → DateTime
- `level` → String
- `user_id` → Int32
- `response_time` → Float64
- `client_ip` → IPv4（带 🔍 标记）

根据需要调整字段类型和名称。

### 4. 配置表信息

- 选择目标 ClickHouse 数据源
- 输入表名，例如：`nginx_access_logs`
- 系统自动生成 DDL，预览确认

### 5. 创建表

点击"创建表"按钮，系统会：
1. 在 ClickHouse 中执行 DDL
2. 创建表结构
3. 显示创建结果

### 6. 后续配置

创建成功后，可以：
1. 前往可视化配置页面
2. 添加 Sink 组件，选择刚创建的表
3. 添加 Transform 组件，使用步骤 1 的 VRL 脚本
4. 部署配置到 Vector Agent

## 技术实现

### 前端组件

**文件位置**：
- `src/views/vector/SmartWizard.vue` - 智能向导主组件
- `src/api/wizard.ts` - 智能向导 API 服务
- `src/api/config.ts` - 系统配置 API 服务

**依赖组件**：
- Element Plus Steps - 步骤条
- Element Plus Form - 表单
- Element Plus Table - 字段列表
- Element Plus Dialog - 对话框

### 后端接口

**智能向导接口**：
- `POST /api/wizard/parse-log` - 解析日志样本
- `POST /api/wizard/generate-ddl` - 生成 DDL
- `POST /api/wizard/create-table` - 创建表
- `POST /api/wizard/list-tables` - 查询表列表
- `POST /api/wizard/describe-table` - 查询表结构
- `POST /api/wizard/add-column` - 添加字段

**系统配置接口**：
- `GET /api/config/settings` - 获取所有配置
- `GET /api/config/settings/{key}` - 获取指定配置
- `PUT /api/config/settings/{key}` - 更新配置
- `GET /api/config/history` - 获取配置历史

## 系统配置

### ClickHouse 配置

在"系统设置 → 数据库配置"页面可以配置 DDL 默认值：

- **表引擎**：MergeTree / ReplicatedMergeTree / ReplacingMergeTree
- **分区策略**：例如 `toYYYYMM(timestamp)`
- **排序键**：例如 `timestamp,hostname`
- **数据保留期**：TTL 天数
- **压缩编码**：LZ4 / ZSTD / None
- **保留原始日志**：是否保留 raw 字段
- **索引配置**：例如 `timestamp:minmax,hostname:set`

### PostgreSQL 配置

- **主键类型**：UUID / SERIAL / BIGSERIAL
- **索引配置**：例如 `timestamp:btree,hostname:btree`

### Elasticsearch 配置

- **分片数**：索引分片数量
- **副本数**：每个分片的副本数
- **分词器**：standard / ik_max_word / ik_smart
- **ILM 保留期**：索引生命周期管理天数

## 注意事项

1. **日志样本**：
   - 提供完整的日志样本，包含所有需要提取的字段
   - 确保样本格式正确，避免解析失败

2. **字段类型**：
   - 仔细确认字段类型，错误的类型会导致数据写入失败
   - IP 地址建议使用 IPv4/IPv6 类型，节省存储空间

3. **表名规范**：
   - 使用小写字母和下划线
   - 避免使用 ClickHouse 保留字
   - 建议使用有意义的名称，例如：`nginx_access_logs`

4. **DDL 配置**：
   - 分区策略影响查询性能，建议按时间分区
   - 排序键影响查询性能，建议包含常用查询字段
   - TTL 自动清理过期数据，避免磁盘占满

5. **后续配置**：
   - 创建表后需要在可视化配置中添加 Sink 组件
   - Transform 组件中使用相同的 VRL 脚本解析日志
   - 部署配置到 Vector Agent 后才能开始采集日志

## 常见问题

### Q: 解析失败怎么办？

A: 检查以下几点：
1. 日志样本格式是否正确
2. 选择的解析方式是否匹配
3. 正则表达式是否正确（使用命名捕获组）
4. 查看错误提示，根据提示调整

### Q: 如何测试正则表达式？

A: 在组件库中有正则表达式测试功能，可以先测试正则表达式是否正确。

### Q: 字段类型选择错误怎么办？

A: 在步骤 2 可以手动调整字段类型。如果表已创建，可以使用"添加字段"功能添加新字段。

### Q: 如何修改已创建的表？

A: ClickHouse 支持添加字段，但不支持删除字段。如果需要大幅修改表结构，建议重新创建表。

### Q: 如何查看生成的 DDL？

A: 在步骤 3 可以预览生成的 DDL，确认无误后再创建表。

## 未来规划

### Phase 2: PostgreSQL 支持

- PostgreSQL DDL 生成器
- 类型映射（ClickHouse ↔ PostgreSQL）
- PostgreSQL 表管理

### Phase 3: Elasticsearch 支持

- Mapping 生成器
- 索引管理
- ILM 策略配置

### Phase 4: 高级功能

- 模板市场（预定义的日志解析模板）
- 批量导入（从文件导入多个日志样本）
- 表结构演进（自动检测新字段并添加）
- 智能推荐（根据日志内容推荐最佳配置）
