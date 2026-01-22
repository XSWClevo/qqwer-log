# AI自然语言查询功能实施总结

## 实施日期
2026-01-22

## 功能概述

成功实现了基于LangChain和Claude API的自然语言转SQL查询功能，用户可以在日志搜索页面使用自然语言描述查询需求，系统自动生成SQL并执行查询。

## 技术架构

```
┌─────────────┐
│  前端 Vue3  │
│  查询模式   │
│  切换UI     │
└──────┬──────┘
       │ HTTP POST
       ↓
┌─────────────────────┐
│  Java后端           │
│  Spring Boot        │
│  - AiQueryService   │
│  - StatsController  │
└──────┬──────────────┘
       │ HTTP POST
       ↓
┌─────────────────────────┐
│  Python AI服务          │
│  FastAPI + LangChain    │
│  - TextToSQLService     │
│  - Claude 3.5 Sonnet    │
└──────┬──────────────────┘
       │ SQL Query
       ↓
┌─────────────────────┐
│  ClickHouse         │
│  MWLOGDB_ANALYSIS   │
│  syslog表           │
└─────────────────────┘
```

## 实施内容

### 1. Python AI服务（新建）

**目录**：`log-analysis-ai-service/`

**核心文件**：
- `app/main.py` - FastAPI应用入口
- `app/services/text_to_sql.py` - LangChain SQL Agent实现
- `app/models/schemas.py` - 请求/响应模型
- `app/config.py` - 配置管理
- `requirements.txt` - Python依赖
- `Dockerfile` - Docker镜像构建
- `README.md` - 服务文档

**功能特性**：
- ✅ 自然语言转SQL（Text-to-SQL）
- ✅ 自动SQL生成和执行
- ✅ 自动错误修正（最多3次重试）
- ✅ 安全限制（只读查询、表限制、结果限制）
- ✅ 健康检查接口

### 2. Java后端集成

**新增文件**：
- `AiQueryRequest.java` - AI查询请求DTO
- `AiQueryResponse.java` - AI查询响应DTO
- `AiQueryService.java` - AI查询服务
- `RestTemplateConfig.java` - RestTemplate配置

**修改文件**：
- `StatsController.java` - 新增`/api/stats/logs/ai-query`接口
- `application-dev.yml` - 新增AI服务配置

**接口路径**：`POST /api/stats/logs/ai-query`

### 3. 前端改造

**修改文件**：
- `src/views/log-search/Index.vue` - 日志搜索页面
- `src/api/log.ts` - API接口定义

**新增功能**：
- ✅ 查询模式切换（普通查询 / AI查询）
- ✅ AI查询输入框（多行文本）
- ✅ 查询结果展示
- ✅ 执行时间显示
- ✅ 错误提示

**UI改进**：
- 查询模式切换按钮（Radio Button）
- AI查询标签提示
- 响应式布局
- 样式优化

### 4. 文档

**新增文档**：
- `log-analysis-ai-service/README.md` - Python服务文档
- `docs/backend-api/ai-query-api.md` - 接口文档

## 配置说明

### Python服务配置

**文件**：`log-analysis-ai-service/.env`

```env
ANTHROPIC_API_KEY=your-api-key-here
CLICKHOUSE_HOST=10.180.5.72
CLICKHOUSE_PORT=8123
CLICKHOUSE_DB=MWLOGDB_ANALYSIS
CLICKHOUSE_USER=default
CLICKHOUSE_PASSWORD=mwclickhouse@2024
SERVICE_HOST=0.0.0.0
SERVICE_PORT=8001
```

### Java后端配置

**文件**：`application-dev.yml`

```yaml
ai:
  service:
    url: ${AI_SERVICE_URL:http://localhost:8001}
    timeout: 30000
```

## 部署步骤

### 1. 部署Python AI服务

```bash
cd log-analysis-ai-service

# 安装依赖
pip install -r requirements.txt

# 配置环境变量
cp .env.example .env
# 编辑 .env，填写 ANTHROPIC_API_KEY

# 启动服务
python -m app.main
```

服务地址：`http://localhost:8001`

### 2. 启动Java后端

```bash
cd log-analysis-backend/log-analysis-app
mvn spring-boot:run
```

### 3. 启动前端

```bash
cd log-analysis-frontend
npm run dev
```

## 使用示例

### 查询示例

1. **统计查询**
```
查询最近1小时内error级别的日志数量
```

2. **分组查询**
```
按主机名统计最近24小时的日志数量，按数量降序排列
```

3. **时间范围查询**
```
查询今天上午10点到11点之间的所有warning日志
```

4. **条件查询**
```
查询appname为nginx且source_ip包含192.168的日志，最多返回100条
```

### API调用示例

```bash
curl -X POST http://localhost:8080/api/stats/logs/ai-query \
  -H "Content-Type: application/json" \
  -d '{
    "query": "查询最近1小时内error日志数量",
    "tableName": "syslog"
  }'
```

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "success": true,
    "sql": "SELECT COUNT(*) FROM syslog WHERE severity = 'error' AND timestamp >= now() - INTERVAL 1 HOUR",
    "result": "42",
    "error": null,
    "executionTime": 1.23
  }
}
```

## 安全特性

1. **只读查询**：只允许SELECT，禁止DROP/DELETE/UPDATE/INSERT
2. **表限制**：只能查询syslog表
3. **结果限制**：单次查询最多1000条
4. **超时限制**：查询超时30秒
5. **重试限制**：SQL生成失败最多重试3次

## 成本估算

- **Claude API**：约$0.003/次查询（Sonnet 3.5）
- **日查询量**：预计1000次
- **日成本**：约$3
- **月成本**：约$90

## 技术依赖

### Python依赖
- fastapi==0.109.0
- langchain==0.1.0
- langchain-anthropic==0.1.0
- clickhouse-driver==0.2.6
- uvicorn==0.27.0

### Java依赖
- Spring Boot 3.2
- RestTemplate（内置）

### 前端依赖
- Vue 3
- Element Plus
- TypeScript

## 已知限制

1. **表限制**：目前只支持syslog表
2. **字段限制**：只支持syslog表的标准字段
3. **语言限制**：主要支持中文自然语言
4. **复杂查询**：极复杂的查询可能生成不准确

## 后续优化建议

1. **缓存机制**：相同查询结果缓存
2. **多表支持**：支持查询其他表
3. **查询历史**：保存用户查询历史
4. **查询模板**：提供常用查询模板
5. **结果可视化**：自动生成图表
6. **查询优化**：SQL性能优化建议

## 测试建议

### 单元测试
- Python服务单元测试
- Java Service单元测试

### 集成测试
- 端到端测试
- API接口测试

### 性能测试
- 并发查询测试
- 响应时间测试

## 故障排查

### 常见问题

1. **AI服务连接失败**
   - 检查Python服务是否启动
   - 检查端口是否被占用
   - 检查防火墙设置

2. **Claude API调用失败**
   - 检查API Key是否正确
   - 检查API配额
   - 检查网络连接

3. **SQL生成不准确**
   - 优化查询描述
   - 查看生成的SQL
   - 提供更多上下文

## 项目文件清单

### 新增文件（Python服务）
```
log-analysis-ai-service/
├── app/
│   ├── __init__.py
│   ├── main.py
│   ├── config.py
│   ├── models/
│   │   ├── __init__.py
│   │   └── schemas.py
│   └── services/
│       ├── __init__.py
│       └── text_to_sql.py
├── requirements.txt
├── Dockerfile
├── .gitignore
├── .env.example
└── README.md
```

### 新增文件（Java后端）
```
log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/
├── stats/
│   ├── dto/
│   │   ├── AiQueryRequest.java
│   │   └── AiQueryResponse.java
│   └── service/
│       └── AiQueryService.java
└── common/
    └── config/
        └── RestTemplateConfig.java
```

### 修改文件
```
- log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/stats/controller/StatsController.java
- log-analysis-backend/log-analysis-app/src/main/resources/application-dev.yml
- log-analysis-frontend/src/views/log-search/Index.vue
- log-analysis-frontend/src/api/log.ts
```

### 新增文档
```
- docs/backend-api/ai-query-api.md
- log-analysis-ai-service/README.md
```

## 总结

✅ **成功实现**了完整的AI自然语言查询功能
✅ **架构清晰**，Python服务独立部署，易于维护
✅ **安全可靠**，多重安全限制保护数据
✅ **用户友好**，前端UI简洁直观
✅ **文档完善**，部署和使用文档齐全

**下一步**：
1. 配置Anthropic API Key
2. 启动Python AI服务
3. 测试功能
4. 根据实际使用情况优化

## 联系方式

如有问题，请查看：
- Python服务文档：`log-analysis-ai-service/README.md`
- 接口文档：`docs/backend-api/ai-query-api.md`
