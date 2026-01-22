# Log Analysis AI Query Service

基于 LangChain 和 Claude API 的自然语言转 SQL 服务。

## 功能特性

- 🤖 自然语言转 SQL（Text-to-SQL）
- 🔍 支持 ClickHouse 数据库查询
- 🛡️ 安全限制：只读查询、结果行数限制
- ⚡ 自动 SQL 生成和执行
- 🔄 自动错误修正（最多3次重试）

## 技术栈

- **FastAPI**: Web 框架
- **LangChain**: SQL Agent 框架
- **Claude 3.5 Sonnet**: LLM 模型
- **ClickHouse**: 数据库

## 快速开始

### 1. 安装依赖

```bash
cd log-analysis-ai-service
pip install -r requirements.txt
```

### 2. 配置环境变量

复制 `.env.example` 为 `.env` 并填写配置：

```bash
cp .env.example .env
```

编辑 `.env` 文件：

```env
# Anthropic API配置
ANTHROPIC_API_KEY=your-api-key-here

# ClickHouse配置
CLICKHOUSE_HOST=10.180.5.72
CLICKHOUSE_PORT=8123
CLICKHOUSE_DB=MWLOGDB_ANALYSIS
CLICKHOUSE_USER=default
CLICKHOUSE_PASSWORD=mwclickhouse@2024
```

### 3. 启动服务

```bash
# 开发模式（自动重载）
python -m app.main

# 或使用 uvicorn
uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload
```

服务将在 `http://localhost:8001` 启动。

### 4. 测试接口

#### 健康检查

```bash
curl http://localhost:8001/health
```

#### 自然语言查询

```bash
curl -X POST http://localhost:8001/text-to-sql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "查询最近1小时内severity为error的日志数量",
    "table_name": "syslog"
  }'
```

响应示例：

```json
{
  "success": true,
  "sql": "SELECT COUNT(*) FROM syslog WHERE severity = 'error' AND timestamp >= now() - INTERVAL 1 HOUR",
  "result": "42",
  "error": null,
  "execution_time": 1.23
}
```

## API 文档

启动服务后访问：

- Swagger UI: http://localhost:8001/docs
- ReDoc: http://localhost:8001/redoc

## Docker 部署

### 构建镜像

```bash
docker build -t log-analysis-ai-service .
```

### 运行容器

```bash
docker run -d \
  --name ai-query-service \
  -p 8001:8001 \
  -e ANTHROPIC_API_KEY=your-api-key \
  -e CLICKHOUSE_HOST=10.180.5.72 \
  -e CLICKHOUSE_PASSWORD=mwclickhouse@2024 \
  log-analysis-ai-service
```

## 查询示例

### 统计查询

```
查询最近1小时内error级别的日志数量
```

### 分组查询

```
按主机名统计最近24小时的日志数量，按数量降序排列
```

### 时间范围查询

```
查询今天上午10点到11点之间的所有warning日志
```

### 条件查询

```
查询appname为nginx且source_ip包含192.168的日志，最多返回100条
```

## 安全限制

1. **只读查询**: 只允许 SELECT 查询，禁止 DROP/DELETE/UPDATE/INSERT
2. **表限制**: 只能查询 syslog 表
3. **结果限制**: 单次查询最多返回 1000 条记录
4. **超时限制**: 查询超时时间 30 秒
5. **重试限制**: SQL 生成失败最多重试 3 次

## 配置说明

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| ANTHROPIC_API_KEY | Claude API密钥 | 必填 |
| CLICKHOUSE_HOST | ClickHouse主机 | 10.180.5.72 |
| CLICKHOUSE_PORT | ClickHouse端口 | 8123 |
| CLICKHOUSE_DB | 数据库名 | MWLOGDB_ANALYSIS |
| CLICKHOUSE_USER | 用户名 | default |
| CLICKHOUSE_PASSWORD | 密码 | 必填 |
| SERVICE_HOST | 服务监听地址 | 0.0.0.0 |
| SERVICE_PORT | 服务端口 | 8001 |
| LOG_LEVEL | 日志级别 | INFO |

## 故障排查

### 1. 服务启动失败

检查环境变量是否正确配置：

```bash
python -c "from app.config import get_settings; print(get_settings())"
```

### 2. ClickHouse 连接失败

测试数据库连接：

```bash
curl http://localhost:8001/health
```

### 3. Claude API 调用失败

检查 API Key 是否有效：

```bash
echo $ANTHROPIC_API_KEY
```

### 4. SQL 生成不准确

查看详细日志（设置 `LOG_LEVEL=DEBUG`）：

```bash
export LOG_LEVEL=DEBUG
python -m app.main
```

## 开发指南

### 项目结构

```
log-analysis-ai-service/
├── app/
│   ├── __init__.py
│   ├── main.py              # FastAPI 应用入口
│   ├── config.py            # 配置管理
│   ├── models/
│   │   ├── __init__.py
│   │   └── schemas.py       # Pydantic 模型
│   └── services/
│       ├── __init__.py
│       └── text_to_sql.py   # LangChain SQL Agent
├── requirements.txt
├── .env.example
├── Dockerfile
└── README.md
```

### 添加新功能

1. 在 `app/services/` 中添加新服务
2. 在 `app/models/schemas.py` 中定义请求/响应模型
3. 在 `app/main.py` 中添加新路由

## 性能优化

1. **缓存**: 相同查询结果缓存（待实现）
2. **连接池**: ClickHouse 连接池（待实现）
3. **异步**: 异步查询执行（待实现）

## 许可证

MIT
