# 智能向导 API 文档

## 概述

智能向导提供了从日志样本到表创建的完整流程，包括日志解析、字段类型推断、DDL 生成和表管理功能。

## API 列表

### 1. 解析日志样本

**接口路径**：`POST /api/wizard/parse-log`

**功能**：解析日志样本，识别格式并提取字段

**请求参数**：
```json
{
  "logSample": "<134>1 2024-01-07T10:30:00+08:00 web-server nginx 12345 - - {\"method\":\"GET\"}",
  "parseMethod": "parse_json",
  "regexPattern": "",
  "grokPattern": "",
  "customVrl": ""
}
```

**参数说明**：
- `logSample`：日志样本（必填）
- `parseMethod`：解析方式（必填）
  - `parse_json`：JSON 解析
  - `parse_syslog`：Syslog 解析
  - `parse_kv` / `parse_key_value`：Key-Value 解析
  - `parse_regex`：正则表达式解析
  - `parse_grok`：Grok 解析
  - `custom`：自定义 VRL
- `regexPattern`：正则表达式（parse_regex 时必填）
- `grokPattern`：Grok 模式（parse_grok 时必填）
- `customVrl`：自定义 VRL 脚本（custom 时必填）

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "success": true,
    "format": "Syslog RFC 5424",
    "fields": [
      {
        "name": "timestamp",
        "sampleValue": "2024-01-07T10:30:00+08:00",
        "type": "DateTime64",
        "suggestion": null
      },
      {
        "name": "source_ip",
        "sampleValue": "192.168.1.1",
        "type": "String",
        "suggestion": {
          "type": "IPv4",
          "reason": "检测到 IP 地址格式"
        }
      }
    ]
  }
}
```

---

### 2. 生成 DDL

**接口路径**：`POST /api/wizard/generate-ddl`

**功能**：根据字段定义生成 ClickHouse DDL

**请求参数**：
```json
{
  "datasourceId": "xxx",
  "tableName": "nginx_logs",
  "fields": [
    {
      "name": "timestamp",
      "type": "DateTime64",
      "nullable": false,
      "comment": "日志时间戳"
    },
    {
      "name": "hostname",
      "type": "String",
      "nullable": true,
      "comment": "主机名"
    }
  ]
}
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "success": true,
    "ddl": "CREATE TABLE IF NOT EXISTS MWLOGDB_ANALYSIS.nginx_logs\n(\n    id String DEFAULT generateUUIDv4(),\n    timestamp DateTime64,\n    hostname String,\n    raw String COMMENT '原始日志',\n    INDEX idx_timestamp timestamp TYPE minmax GRANULARITY 3\n)\nENGINE = MergeTree()\nPARTITION BY toYYYYMM(timestamp)\nORDER BY (timestamp, hostname)\nTTL timestamp + INTERVAL 30 DAY\nSETTINGS index_granularity = 8192;",
    "config": {
      "ddl.engine": "MergeTree",
      "ddl.partition_by": "toYYYYMM(timestamp)",
      "ddl.ttl_days": "30"
    }
  }
}
```

---

### 3. 创建表

**接口路径**：`POST /api/wizard/create-table`

**功能**：执行 DDL 创建表

**请求参数**：
```json
{
  "datasourceId": "xxx",
  "ddl": "CREATE TABLE IF NOT EXISTS ..."
}
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

---

### 4. 查询表列表

**接口路径**：`POST /api/wizard/list-tables`

**功能**：查询数据源中的所有表

**请求参数**：
```json
{
  "datasourceId": "xxx"
}
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {"name": "syslog"},
    {"name": "nginx_logs"}
  ]
}
```

---

### 5. 查询表结构

**接口路径**：`POST /api/wizard/describe-table`

**功能**：查询表的字段结构

**请求参数**：
```json
{
  "datasourceId": "xxx",
  "tableName": "nginx_logs"
}
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "name": "id",
      "type": "String",
      "default_type": "DEFAULT",
      "default_expression": "generateUUIDv4()",
      "comment": ""
    },
    {
      "name": "timestamp",
      "type": "DateTime64",
      "default_type": "",
      "default_expression": "",
      "comment": "日志时间戳"
    }
  ]
}
```

---

### 6. 添加字段

**接口路径**：`POST /api/wizard/add-column`

**功能**：向表中添加新字段

**请求参数**：
```json
{
  "datasourceId": "xxx",
  "tableName": "nginx_logs",
  "columnName": "trace_id",
  "columnType": "String",
  "comment": "追踪ID"
}
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

---

## 数据库配置 API

### 1. 获取数据库配置

**接口路径**：`POST /api/database-config/get`

**功能**：获取指定类型的数据库默认配置

**请求参数**：
```json
{
  "configType": "clickhouse"
}
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "configKey": "ddl.engine",
      "configValue": "MergeTree",
      "description": "表引擎"
    },
    {
      "configKey": "ddl.partition_by",
      "configValue": "toYYYYMM(timestamp)",
      "description": "分区策略"
    }
  ]
}
```

---

### 2. 更新数据库配置

**接口路径**：`POST /api/database-config/update`

**功能**：更新数据库默认配置

**请求参数**：
```json
{
  "configType": "clickhouse",
  "configs": {
    "ddl.ttl_days": "60",
    "ddl.partition_by": "toYYYYMMDD(timestamp)"
  }
}
```

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

---

### 3. 获取配置类型列表

**接口路径**：`POST /api/database-config/types`

**功能**：获取所有支持的配置类型

**响应示例**：
```json
{
  "code": 200,
  "message": "success",
  "data": ["clickhouse", "postgresql", "elasticsearch"]
}
```

---

## 使用流程

### 完整流程示例

```
1. 用户上传日志样本
   POST /api/wizard/parse-log
   
2. 系统解析日志，返回字段列表
   用户确认/修改字段类型
   
3. 生成 DDL
   POST /api/wizard/generate-ddl
   
4. 用户预览 DDL，确认后创建表
   POST /api/wizard/create-table
   
5. 完成！可以开始使用该表
```

---

## 错误码

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 500 | 服务器内部错误 |

---

## 注意事项

1. **日志解析**：
   - 支持多种解析方式，系统会自动尝试识别格式
   - 如果自动识别失败，可以使用自定义正则表达式

2. **字段类型**：
   - 系统会自动推断字段类型
   - 对于 IP 地址会提供 IPv4 类型建议
   - 用户可以手动修改推断的类型

3. **DDL 生成**：
   - 使用系统配置的默认策略
   - 管理员可以在"系统配置"页面修改默认策略
   - 生成的 DDL 可以手动编辑

4. **表管理**：
   - 目前仅支持 ClickHouse
   - 只支持添加字段，不支持删除/修改字段
   - 添加字段使用 `ADD COLUMN IF NOT EXISTS`，不会报错

---

## 代码位置

- Controller: `SmartWizardController.java`
- Service: 
  - `SmartWizardService.java`
  - `FieldTypeInferenceService.java`
  - `ClickHouseDDLGenerator.java`
  - `TableManagementService.java`
- DTO: `wizard/dto/` 目录
