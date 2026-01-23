# AI 查询多数据源联合查询功能

## 功能概述

支持在 AI 自然语言查询中同时查询多个数据源，并自动合并结果。

**限制条件**：
- 所有数据源必须是同一类型（如都是 ClickHouse，或都是 Elasticsearch）
- 最多支持 10 个数据源联合查询
- 不支持跨不同类型数据库的查询

## 实现原理

### 后端实现（UNION 方式）

1. **验证数据源类型一致性**：检查所有数据源是否为同一类型
2. **生成基础 SQL**：使用第一个数据源的表结构生成 SQL
3. **分别执行查询**：对每个数据源执行相同的 SQL（替换表名）
4. **合并结果**：将所有查询结果合并为一个列表返回

### 前端实现

1. **动态切换选择模式**：
   - 普通查询模式：单选数据源
   - AI 查询模式：支持多选数据源

2. **UI 提示**：
   - 显示联合查询标签
   - 显示选中的数据源数量

## API 接口

### 请求接口

**URL**: `POST /api/stats/logs/ai-query`

**请求体**:

```json
{
  "query": "查询最近1小时的错误日志",
  "datasourceIds": [
    "datasource-id-1",
    "datasource-id-2",
    "datasource-id-3"
  ]
}
```

**字段说明**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| query | string | 是 | 自然语言查询内容 |
| datasourceId | string | 否 | 单数据源ID（兼容旧版） |
| datasourceIds | string[] | 否 | 多数据源ID列表（优先级高于 datasourceId） |

**注意**：
- `datasourceIds` 和 `datasourceId` 至少提供一个
- 如果同时提供，优先使用 `datasourceIds`
- 如果都不提供，使用默认的 syslog 表

### 响应格式

```json
{
  "success": true,
  "sql": "SELECT * FROM logs WHERE severity = 'error' AND timestamp >= now() - INTERVAL 1 HOUR (联合查询 3 个数据源)",
  "result": [
    {
      "id": "1",
      "severity": "error",
      "message": "Database connection failed",
      "timestamp": "2026-01-23 10:00:00"
    },
    {
      "id": "2",
      "severity": "error",
      "message": "API timeout",
      "timestamp": "2026-01-23 10:05:00"
    }
  ],
  "error": null,
  "sqlGenerationTime": 0.5,
  "sqlExecutionTime": 1.2,
  "totalExecutionTime": 1.7
}
```

## 使用示例

### 前端使用

```typescript
// 单数据源查询
await aiQuery({
  query: "查询最近1小时的错误日志",
  datasourceId: "clickhouse-prod"
})

// 多数据源联合查询
await aiQuery({
  query: "查询最近1小时的错误日志",
  datasourceIds: [
    "clickhouse-prod-1",
    "clickhouse-prod-2",
    "clickhouse-prod-3"
  ]
})
```

### 用户操作流程

1. 点击 "AI模式" 按钮切换到 AI 查询模式
2. 数据源选择框自动变为多选模式
3. 选择多个同类型的数据源（如 3 个 ClickHouse 实例）
4. 输入自然语言查询（如 "统计最近1小时各主机的错误日志数量"）
5. 点击查询按钮
6. 系统自动合并所有数据源的结果并展示

## 错误处理

### 常见错误

1. **数据源类型不一致**
   ```json
   {
     "success": false,
     "error": "数据源类型不一致：datasource-1 是 clickhouse，但 datasource-2 是 elasticsearch。联合查询要求所有数据源类型相同"
   }
   ```

2. **数据源数量超限**
   ```json
   {
     "success": false,
     "error": "联合查询最多支持10个数据源"
   }
   ```

3. **部分数据源查询失败**
   - 系统会继续执行其他数据源的查询
   - 只返回成功的数据源结果
   - 在日志中记录失败的数据源

## 性能考虑

### 查询性能

- **并发执行**：当前实现是串行执行，未来可优化为并发执行
- **超时控制**：建议设置合理的超时时间
- **结果限制**：建议限制每个数据源的返回结果数量

### 优化建议

1. **使用相同的时间范围**：确保所有数据源查询相同的时间段
2. **限制数据源数量**：建议不超过 5 个数据源
3. **使用索引字段**：查询条件尽量使用已建立索引的字段

## 后续优化方向

1. **并发执行**：使用 CompletableFuture 并发执行多个数据源查询
2. **结果去重**：对于相同的日志记录进行去重
3. **结果排序**：按时间戳统一排序所有结果
4. **进度提示**：显示各数据源的查询进度
5. **联邦查询**：对于支持的数据库（如 ClickHouse），使用 remote() 函数实现真正的联邦查询

## 代码位置

### 后端

- **DTO**: `AiQueryRequest.java:27` - 添加 `datasourceIds` 字段
- **Service**: `AiQueryService.java:47-280` - 实现多数据源查询逻辑
  - `executeMultiDatasourceQuery()` - 多数据源查询主方法
  - `mergeQueryResults()` - 结果合并方法

### 前端

- **API**: `log.ts:103-106` - 添加 `datasourceIds` 字段
- **页面**: `Index.vue:7-46` - 数据源选择器支持多选
- **逻辑**: `Index.vue:1056-1100` - AI 查询处理逻辑

## 测试建议

### 功能测试

1. 测试单数据源查询（兼容性）
2. 测试 2 个数据源联合查询
3. 测试 5 个数据源联合查询
4. 测试 10 个数据源联合查询（边界）
5. 测试超过 10 个数据源（错误处理）
6. 测试不同类型数据源混合（错误处理）

### 性能测试

1. 测试大数据量查询（每个数据源返回 1000+ 条记录）
2. 测试查询超时场景
3. 测试部分数据源失败场景

## 注意事项

1. **数据一致性**：不同数据源的数据可能存在时间差
2. **结果顺序**：合并后的结果顺序可能不是严格按时间排序
3. **字段兼容性**：确保所有数据源的表结构兼容
4. **权限控制**：确保用户有权限访问所有选中的数据源
