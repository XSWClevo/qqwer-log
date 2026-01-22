# AI自然语言查询接口文档

## 概述

AI自然语言查询功能允许用户使用自然语言描述查询需求，系统自动将其转换为SQL并执行查询。

**技术栈**：
- 后端：Java Spring Boot + RestTemplate
- AI服务：Python FastAPI + LangChain + Claude API
- 数据库：ClickHouse

**架构**：
```
前端 → Java后端 → Python AI服务 → ClickHouse
```

## 接口列表

### 1. AI自然语言查询

**接口路径**：`POST /api/stats/logs/ai-query`

**接口作用**：将用户的自然语言查询转换为SQL并执行

**代码位置**：`StatsController.java:145`

**请求参数**：

```json
{
  "query": "查询最近1小时内severity为error的日志数量",
  "tableName": "syslog"
}
```

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| query | String | 是 | 自然语言查询，长度1-1000字符 |
| tableName | String | 否 | 表名，默认为"syslog" |

**响应参数**：

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

| 参数名 | 类型 | 说明 |
|--------|------|------|
| success | Boolean | 查询是否成功 |
| sql | String | 生成的SQL语句（可能为null） |
| result | Any | 查询结果（可能是字符串、数字、数组等） |
| error | String | 错误信息（成功时为null） |
| executionTime | Double | 执行时间（秒） |

**查询示例**：

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

5. **复杂查询**
```
查询最近7天内，每天error和warning级别的日志数量趋势
```

**错误响应**：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "success": false,
    "sql": null,
    "result": null,
    "error": "AI服务调用失败: Connection refused",
    "executionTime": null
  }
}
```

## 前端集成

### API调用

**文件位置**：`src/api/log.ts`

```typescript
// AI查询请求类型
export interface AiQueryRequest {
  query: string
  tableName?: string
}

// AI查询响应类型
export interface AiQueryResponse {
  success: boolean
  sql: string | null
  result: any
  error: string | null
  executionTime: number | null
}

// AI自然语言查询
export function aiQuery(data: AiQueryRequest) {
  return request<AiQueryResponse>({
    url: '/api/stats/logs/ai-query',
    method: 'POST',
    data
  })
}
```

### 页面集成

**文件位置**：`src/views/log-search/Index.vue`

**功能点**：
1. 查询模式切换（普通查询 / AI查询）
2. AI查询输入框（支持多行文本）
3. 查询结果展示
4. SQL语句显示（控制台）
5. 执行时间显示

**使用方式**：
1. 点击"AI查询"切换到AI模式
2. 在输入框中输入自然语言查询
3. 点击"AI查询"按钮或按Ctrl+Enter执行
4. 查看查询结果

## 配置说明

### Java后端配置

**文件位置**：`application-dev.yml`

```yaml
# AI服务配置
ai:
  service:
    url: ${AI_SERVICE_URL:http://localhost:8001}  # AI服务地址
    timeout: 30000  # 超时时间(毫秒)
```

**环境变量**：
- `AI_SERVICE_URL`: AI服务地址（默认：http://localhost:8001）

### Python AI服务配置

**文件位置**：`log-analysis-ai-service/.env`

```env
# Anthropic API配置
ANTHROPIC_API_KEY=your-api-key-here

# ClickHouse配置
CLICKHOUSE_HOST=10.180.5.72
CLICKHOUSE_PORT=8123
CLICKHOUSE_DB=MWLOGDB_ANALYSIS
CLICKHOUSE_USER=default
CLICKHOUSE_PASSWORD=mwclickhouse@2024

# 服务配置
SERVICE_HOST=0.0.0.0
SERVICE_PORT=8001
```

## 部署指南

### 1. 部署Python AI服务

```bash
cd log-analysis-ai-service

# 安装依赖
pip install -r requirements.txt

# 配置环境变量
cp .env.example .env
# 编辑 .env 文件，填写 ANTHROPIC_API_KEY

# 启动服务
python -m app.main
```

服务将在 `http://localhost:8001` 启动。

### 2. 配置Java后端

确保 `application-dev.yml` 中的 `ai.service.url` 指向Python服务地址。

### 3. 重启Java后端

```bash
cd log-analysis-backend/log-analysis-app
mvn spring-boot:run
```

### 4. 测试接口

```bash
curl -X POST http://localhost:8080/api/stats/logs/ai-query \
  -H "Content-Type: application/json" \
  -d '{
    "query": "查询最近1小时内error日志数量",
    "tableName": "syslog"
  }'
```

## 安全限制

1. **只读查询**：只允许SELECT查询，禁止DROP/DELETE/UPDATE/INSERT
2. **表限制**：只能查询syslog表
3. **结果限制**：单次查询最多返回1000条记录
4. **超时限制**：查询超时时间30秒
5. **重试限制**：SQL生成失败最多重试3次

## 成本估算

- Claude API调用：约$0.003/次查询（Sonnet 3.5）
- 预计每天1000次查询：$3/天
- 月成本：约$90

## 故障排查

### 1. AI服务连接失败

**错误信息**：`AI服务调用失败: Connection refused`

**解决方案**：
1. 检查Python服务是否启动：`curl http://localhost:8001/health`
2. 检查Java配置中的`ai.service.url`是否正确
3. 检查防火墙设置

### 2. Claude API调用失败

**错误信息**：`AI查询失败: Invalid API key`

**解决方案**：
1. 检查`.env`文件中的`ANTHROPIC_API_KEY`是否正确
2. 验证API Key是否有效：访问 https://console.anthropic.com
3. 检查API配额是否用完

### 3. SQL生成不准确

**解决方案**：
1. 查看生成的SQL（浏览器控制台）
2. 优化查询描述，提供更多上下文
3. 使用具体的字段名和条件

### 4. 查询超时

**错误信息**：`查询超时`

**解决方案**：
1. 缩小时间范围
2. 添加更多筛选条件
3. 增加超时时间配置

## 更新日志

### 2026-01-22
- ✅ 初始版本发布
- ✅ 支持自然语言转SQL
- ✅ 集成LangChain + Claude API
- ✅ 前端UI集成
- ✅ 安全限制实现

## 相关文档

- [Python AI服务README](../log-analysis-ai-service/README.md)
- [LangChain文档](https://python.langchain.com/)
- [Claude API文档](https://docs.anthropic.com/)
