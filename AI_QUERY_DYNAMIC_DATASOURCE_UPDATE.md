# AI查询功能 - 动态数据源支持更新

## 更新日期
2026-01-22

## 问题描述

原实现硬编码只支持查询 `syslog` 表，但实际上：
1. 用户可以在前端选择不同的数据源
2. 每个数据源的表名和表结构都不同
3. 数据源类型也不同（ClickHouse、PostgreSQL、Elasticsearch等）

## 解决方案

### 架构调整

**原架构**：
```
前端 → Java后端 → Python服务（生成SQL并执行） → ClickHouse(syslog)
```

**新架构**：
```
前端选择数据源 → 传递datasourceId
                ↓
Java后端 → 1. 获取数据源配置（表名、类型、表结构）
          2. 调用Python服务生成SQL（传递表结构）
          3. 使用DynamicLogQueryService执行SQL
                ↓
Python AI服务 → 根据表结构生成SQL（只生成，不执行）
```

### 核心改进

#### 1. Python服务改进

**文件**: `log-analysis-ai-service/app/services/text_to_sql.py`

- ✅ 改为只生成SQL，不执行查询
- ✅ 支持动态表名
- ✅ 支持动态表结构（通过参数传入）
- ✅ 支持多种数据源类型（ClickHouse、PostgreSQL、Elasticsearch、MySQL）
- ✅ 根据数据源类型调整SQL语法

**请求参数**：
```python
{
  "query": "查询最近1小时内error日志",
  "table_name": "logs",  # 动态表名
  "table_schema": [      # 动态表结构
    {
      "name": "id",
      "type": "String",
      "label": "ID",
      "isTimestamp": false,
      "isStatsDimension": false,
      "isContentField": false
    },
    ...
  ],
  "datasource_type": "clickhouse"  # 数据源类型
}
```

**响应**：
```python
{
  "success": true,
  "sql": "SELECT ...",  # 生成的SQL
  "error": null,
  "execution_time": 1.23  # SQL生成时间
}
```

#### 2. Java后端改进

**新增方法**：

`DynamicLogQueryService.java`:
- `getTableName(datasourceId)` - 获取表名
- `getDatasourceType(datasourceId)` - 获取数据源类型
- `executeRawSQL(datasourceId, sql)` - 执行原始SQL

`LogQueryStrategy.java`:
- `executeRawSQL(sql, config)` - 接口方法

`ClickHouseQueryStrategy.java`:
- `executeRawSQL(sql, config)` - 实现方法

**AiQueryService改进**：
```java
public AiQueryResponse query(AiQueryRequest request) {
    // 1. 获取数据源配置
    String datasourceId = request.getDatasourceId();
    List<FieldInfo> tableSchema = dynamicLogQueryService.getTableSchema(datasourceId);
    String tableName = dynamicLogQueryService.getTableName(datasourceId);
    String datasourceType = dynamicLogQueryService.getDatasourceType(datasourceId);

    // 2. 调用Python服务生成SQL
    Map<String, Object> aiRequest = new HashMap<>();
    aiRequest.put("query", request.getQuery());
    aiRequest.put("table_name", tableName);
    aiRequest.put("table_schema", tableSchema);
    aiRequest.put("datasource_type", datasourceType);

    // 3. 执行SQL
    Object result = dynamicLogQueryService.executeRawSQL(datasourceId, sql);

    return response;
}
```

#### 3. 前端改进

**传递datasourceId**：
```typescript
const handleAiQuery = async () => {
  const { data } = await aiQuery({
    query: aiQueryText.value.trim(),
    datasourceId: selectedDatasource.value || undefined  // 传递当前选择的数据源
  })

  // 处理结果...
}
```

## 支持的数据源类型

| 数据源类型 | SQL语法 | 时间函数 | 状态 |
|-----------|---------|---------|------|
| ClickHouse | ClickHouse SQL | now(), toStartOfHour(), INTERVAL | ✅ 已实现 |
| PostgreSQL | PostgreSQL SQL | NOW(), INTERVAL '1 hour' | ✅ 已支持 |
| Elasticsearch | Elasticsearch SQL | NOW(), INTERVAL | ✅ 已支持 |
| MySQL | MySQL SQL | NOW(), DATE_SUB() | ✅ 已支持 |

## 使用示例

### 1. 使用默认syslog表

```
前端不选择数据源 → datasourceId为空 → 使用默认syslog表
```

### 2. 使用自定义数据源

```
前端选择数据源 → 传递datasourceId → 自动获取表结构 → 生成SQL → 执行查询
```

### 3. 查询示例

**ClickHouse数据源**：
```
查询最近1小时内error级别的日志数量
```
生成SQL：
```sql
SELECT COUNT(*) FROM logs WHERE severity = 'error' AND timestamp >= now() - INTERVAL 1 HOUR
```

**PostgreSQL数据源**：
```
查询最近1小时内error级别的日志数量
```
生成SQL：
```sql
SELECT COUNT(*) FROM logs WHERE severity = 'error' AND timestamp >= NOW() - INTERVAL '1 hour'
```

## 修改的文件

### Python服务
- `app/models/schemas.py` - 更新请求/响应模型
- `app/services/text_to_sql.py` - 重写为只生成SQL
- `app/main.py` - 更新接口

### Java后端
- `AiQueryRequest.java` - 增加datasourceId字段
- `AiQueryResponse.java` - 增加时间统计字段
- `AiQueryService.java` - 重写查询逻辑
- `DynamicLogQueryService.java` - 增加辅助方法
- `LogQueryStrategy.java` - 增加executeRawSQL接口
- `ClickHouseQueryStrategy.java` - 实现executeRawSQL方法

### 前端
- `src/api/log.ts` - 更新API接口定义
- `src/views/log-search/Index.vue` - 传递datasourceId

## 优势

1. ✅ **灵活性**：支持任意数据源和表结构
2. ✅ **可扩展性**：易于添加新的数据源类型
3. ✅ **安全性**：SQL在Java后端执行，可以进行权限控制
4. ✅ **准确性**：根据实际表结构生成SQL，更准确
5. ✅ **性能**：Python服务只负责生成SQL，执行由Java后端完成

## 测试建议

### 1. 测试不同数据源

- ClickHouse数据源
- PostgreSQL数据源
- Elasticsearch数据源

### 2. 测试不同查询类型

- 统计查询（COUNT、SUM等）
- 分组查询（GROUP BY）
- 时间范围查询
- 条件查询（WHERE）

### 3. 测试边界情况

- 不选择数据源（使用默认）
- 数据源不存在
- 表结构为空
- SQL生成失败

## 注意事项

1. **数据源必须可查询**：在组件库中设置为"可查询"
2. **表结构必须正确**：确保数据源配置正确
3. **SQL语法差异**：不同数据源的SQL语法可能不同
4. **权限控制**：确保用户有查询权限

## 后续优化

1. **缓存表结构**：避免每次都查询表结构
2. **SQL验证**：在执行前验证SQL安全性
3. **查询历史**：保存用户的查询历史
4. **查询模板**：提供常用查询模板
5. **结果可视化**：自动生成图表

## 总结

通过这次更新，AI查询功能现在完全支持动态数据源，用户可以：
- 选择任意数据源进行AI查询
- 系统自动适配不同的表结构和SQL语法
- 获得更准确的查询结果

这使得AI查询功能更加实用和灵活！🎉
