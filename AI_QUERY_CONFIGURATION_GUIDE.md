# AI查询功能配置指南

## 配置架构说明

### 配置分层

```
┌─────────────────────────────────────────────────────────┐
│ Python AI服务配置                                        │
│ - Claude API Key（必填）                                 │
│ - 模型选择（可选）                                       │
│ - 服务端口（可选）                                       │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ Java后端配置                                             │
│ - AI服务地址（必填）                                     │
│ - 默认数据源配置（可选）                                 │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│ 数据源配置（动态）                                       │
│ - 通过前端组件库配置                                     │
│ - 存储在PostgreSQL数据库                                 │
│ - 包含连接信息、表名、字段结构                           │
└─────────────────────────────────────────────────────────┘
```

## 一、Python AI服务配置

### 1.1 配置文件位置

```
log-analysis-ai-service/.env
```

### 1.2 配置步骤

#### 步骤1：复制配置模板

```bash
cd log-analysis-ai-service
cp .env.example .env
```

#### 步骤2：编辑配置文件

```bash
vim .env  # 或使用其他编辑器
```

#### 步骤3：填写配置

```env
# ===================================
# Claude API配置（必填）
# ===================================
# 1. 访问 https://console.anthropic.com/settings/keys
# 2. 创建新的API Key
# 3. 复制并粘贴到这里
ANTHROPIC_API_KEY=sk-ant-api03-xxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# Claude模型配置（可选）
# 推荐使用默认值，性价比最高
CLAUDE_MODEL=claude-3-5-sonnet-20241022

# ===================================
# 服务配置（可选）
# ===================================
SERVICE_HOST=0.0.0.0
SERVICE_PORT=8001
LOG_LEVEL=INFO

# ===================================
# SQL生成配置（可选）
# ===================================
MAX_ITERATIONS=3
MAX_EXECUTION_TIME=30
MAX_RESULT_ROWS=1000
```

### 1.3 配置说明

#### 必填配置

| 配置项 | 说明 | 获取方式 |
|--------|------|----------|
| ANTHROPIC_API_KEY | Claude API密钥 | https://console.anthropic.com/settings/keys |

#### 可选配置

| 配置项 | 默认值 | 说明 | 可选值 |
|--------|--------|------|--------|
| CLAUDE_MODEL | claude-3-5-sonnet-20241022 | Claude模型 | sonnet(推荐), opus(最强), haiku(最快) |
| SERVICE_HOST | 0.0.0.0 | 服务监听地址 | - |
| SERVICE_PORT | 8001 | 服务端口 | - |
| LOG_LEVEL | INFO | 日志级别 | DEBUG, INFO, WARNING, ERROR |
| MAX_ITERATIONS | 3 | SQL生成最大重试次数 | - |
| MAX_EXECUTION_TIME | 30 | 最大执行时间（秒） | - |
| MAX_RESULT_ROWS | 1000 | 最大返回行数 | - |

### 1.4 模型选择指南

| 模型 | 模型ID | 性能 | 成本 | 推荐场景 |
|------|--------|------|------|----------|
| Sonnet 3.5 | claude-3-5-sonnet-20241022 | ⭐⭐⭐⭐ | $$ | **推荐**，性价比最高 |
| Opus 3 | claude-3-opus-20240229 | ⭐⭐⭐⭐⭐ | $$$$ | 复杂查询，准确率要求高 |
| Haiku 3 | claude-3-haiku-20240307 | ⭐⭐⭐ | $ | 简单查询，成本敏感 |

**成本对比**（每1000次查询）：
- Haiku: ~$1
- Sonnet: ~$3
- Opus: ~$15

### 1.5 验证配置

```bash
# 启动服务
python -m app.main

# 测试健康检查
curl http://localhost:8001/health

# 预期响应
{
  "status": "healthy",
  "version": "1.0.0"
}
```

## 二、Java后端配置

### 2.1 配置文件位置

```
log-analysis-backend/log-analysis-app/src/main/resources/application-dev.yml
```

### 2.2 配置内容

```yaml
# AI服务配置
ai:
  service:
    url: ${AI_SERVICE_URL:http://localhost:8001}  # Python AI服务地址
    timeout: 30000  # 超时时间(毫秒)
```

### 2.3 配置说明

| 配置项 | 默认值 | 说明 | 环境变量 |
|--------|--------|------|----------|
| ai.service.url | http://localhost:8001 | Python AI服务地址 | AI_SERVICE_URL |
| ai.service.timeout | 30000 | 请求超时时间（毫秒） | - |

### 2.4 环境变量配置（可选）

如果Python服务部署在其他服务器：

```bash
export AI_SERVICE_URL=http://192.168.1.100:8001
```

或在启动命令中指定：

```bash
java -jar app.jar --ai.service.url=http://192.168.1.100:8001
```

## 三、数据源配置

### 3.1 配置方式

**数据源信息不是在配置文件中配置的，而是通过前端界面动态配置的。**

### 3.2 配置流程

```
1. 前端访问"组件库"页面
2. 创建Sink组件（数据源）
3. 配置连接信息（YAML格式）
4. 启用"可查询"选项
5. 保存配置
```

### 3.3 数据源配置示例

#### ClickHouse数据源

```yaml
# Vector ClickHouse Sink配置
endpoint: "http://10.180.5.72:8123"
database: "MWLOGDB_ANALYSIS"
table: "logs"
auth:
  user: "default"
  password: "mwclickhouse@2024"
```

#### PostgreSQL数据源

```yaml
# Vector PostgreSQL Sink配置
endpoint: "postgresql://localhost:5432"
database: "logs_db"
table: "application_logs"
auth:
  user: "postgres"
  password: "password123"
```

#### Elasticsearch数据源

```yaml
# Vector Elasticsearch Sink配置
endpoint: "http://localhost:9200"
index: "logs-*"
auth:
  user: "elastic"
  password: "elastic123"
```

### 3.4 数据源存储位置

**存储在PostgreSQL数据库中**：

```sql
-- 表：config_component
SELECT
  id,                    -- 数据源ID
  name,                  -- 数据源名称
  vector_type,           -- 类型（clickhouse, postgresql, elasticsearch）
  config_yaml,           -- 连接配置（YAML格式）
  queryable              -- 是否可查询
FROM config_component
WHERE component_type = 'sink' AND queryable = true;
```

### 3.5 数据源信息传递流程

```
1. 用户在前端选择数据源
   ↓
2. 前端传递datasourceId到后端
   ↓
3. Java后端从PostgreSQL查询数据源配置
   ↓
4. 解析YAML配置，提取连接信息
   ↓
5. 获取表结构信息
   ↓
6. 传递给Python服务生成SQL
   ↓
7. 使用连接信息执行SQL
```

## 四、默认数据源配置

### 4.1 默认ClickHouse配置

如果用户不选择数据源，系统使用默认配置：

**位置**：`DynamicLogQueryService.java:executeRawSQL()`

```java
DatasourceConnectionConfig defaultConfig = DatasourceConnectionConfig.builder()
    .type("clickhouse")
    .endpoint("10.180.5.72:8123")
    .database("MWLOGDB_ANALYSIS")
    .table("syslog")
    .username("default")
    .password("mwclickhouse@2024")
    .build();
```

### 4.2 修改默认配置

如果需要修改默认数据源，编辑Java代码：

```java
// 文件：DynamicLogQueryService.java
// 方法：executeRawSQL()
// 行号：约415行

DatasourceConnectionConfig defaultConfig = DatasourceConnectionConfig.builder()
    .type("clickhouse")
    .endpoint("your-host:8123")        // 修改主机
    .database("your-database")         // 修改数据库
    .table("your-table")               // 修改表名
    .username("your-username")         // 修改用户名
    .password("your-password")         // 修改密码
    .build();
```

## 五、完整部署流程

### 5.1 Python AI服务部署

```bash
# 1. 进入目录
cd log-analysis-ai-service

# 2. 安装依赖
pip install -r requirements.txt

# 3. 配置环境变量
cp .env.example .env
vim .env  # 填写ANTHROPIC_API_KEY

# 4. 启动服务
python -m app.main

# 5. 验证服务
curl http://localhost:8001/health
```

### 5.2 Java后端部署

```bash
# 1. 进入目录
cd log-analysis-backend/log-analysis-app

# 2. 配置AI服务地址（如果需要）
export AI_SERVICE_URL=http://localhost:8001

# 3. 启动服务
mvn spring-boot:run

# 4. 验证服务
curl http://localhost:8080/actuator/health
```

### 5.3 配置数据源

```
1. 访问前端：http://localhost:3000
2. 进入"组件库"页面
3. 创建Sink组件
4. 配置连接信息
5. 启用"可查询"
6. 保存
```

### 5.4 测试AI查询

```
1. 进入"日志搜索"页面
2. 选择数据源（或使用默认）
3. 切换到"AI查询"模式
4. 输入自然语言查询
5. 点击"AI查询"按钮
6. 查看结果
```

## 六、常见问题

### Q1: 如何获取Claude API Key？

**答**：
1. 访问 https://console.anthropic.com
2. 注册/登录账号
3. 进入 Settings → API Keys
4. 点击 "Create Key"
5. 复制API Key

### Q2: API Key需要付费吗？

**答**：
- 新用户有$5免费额度
- 用完后需要绑定信用卡
- 按使用量计费

### Q3: 数据源密码安全吗？

**答**：
- 数据源配置存储在PostgreSQL数据库
- 建议对数据库进行加密
- 生产环境建议使用密钥管理服务（KMS）

### Q4: 如何修改默认数据源？

**答**：
- 方式1：在前端组件库配置新数据源
- 方式2：修改Java代码中的默认配置（不推荐）

### Q5: Python服务需要连接数据库吗？

**答**：
- **不需要**！Python服务只负责生成SQL
- 数据库连接由Java后端管理
- 这样更安全，权限控制更好

### Q6: 如何切换Claude模型？

**答**：
编辑 `.env` 文件：
```env
# 使用Opus（最强）
CLAUDE_MODEL=claude-3-opus-20240229

# 使用Haiku（最快）
CLAUDE_MODEL=claude-3-haiku-20240307
```

### Q7: 如何查看生成的SQL？

**答**：
- 方式1：浏览器控制台（Console）
- 方式2：Java后端日志
- 方式3：Python服务日志

## 七、安全建议

### 7.1 API Key安全

- ✅ 使用环境变量，不要硬编码
- ✅ 不要提交到Git仓库
- ✅ 定期轮换API Key
- ✅ 设置使用限额

### 7.2 数据源安全

- ✅ 使用只读账号
- ✅ 限制IP白名单
- ✅ 加密存储密码
- ✅ 定期审计访问日志

### 7.3 网络安全

- ✅ Python服务使用内网地址
- ✅ 配置防火墙规则
- ✅ 使用HTTPS（生产环境）
- ✅ 启用访问日志

## 八、监控和日志

### 8.1 Python服务日志

```bash
# 查看实时日志
tail -f logs/app.log

# 查看错误日志
grep ERROR logs/app.log
```

### 8.2 Java后端日志

```bash
# 查看AI查询日志
grep "AI查询" logs/application.log

# 查看SQL生成日志
grep "生成的SQL" logs/application.log
```

### 8.3 监控指标

- API调用次数
- SQL生成成功率
- 平均响应时间
- 错误率

## 九、故障排查

### 问题1：Python服务启动失败

**错误**：`ValidationError: ANTHROPIC_API_KEY field required`

**解决**：
```bash
# 检查.env文件是否存在
ls -la .env

# 检查API Key是否配置
cat .env | grep ANTHROPIC_API_KEY

# 重新配置
cp .env.example .env
vim .env
```

### 问题2：Java后端连接失败

**错误**：`AI服务调用失败: Connection refused`

**解决**：
```bash
# 检查Python服务是否启动
curl http://localhost:8001/health

# 检查端口是否被占用
lsof -i :8001

# 检查Java配置
grep "ai.service.url" application-dev.yml
```

### 问题3：SQL生成失败

**错误**：`SQL生成失败: Invalid API key`

**解决**：
```bash
# 验证API Key
curl https://api.anthropic.com/v1/messages \
  -H "x-api-key: $ANTHROPIC_API_KEY" \
  -H "anthropic-version: 2023-06-01"

# 重新配置API Key
vim .env
```

## 十、总结

### 配置清单

- [ ] Python服务配置（.env）
  - [ ] ANTHROPIC_API_KEY
  - [ ] CLAUDE_MODEL（可选）
  - [ ] SERVICE_PORT（可选）

- [ ] Java后端配置（application-dev.yml）
  - [ ] ai.service.url
  - [ ] ai.service.timeout（可选）

- [ ] 数据源配置（前端界面）
  - [ ] 创建Sink组件
  - [ ] 配置连接信息
  - [ ] 启用"可查询"

### 关键点

1. **Python服务不连接数据库**，只生成SQL
2. **数据源信息动态配置**，存储在PostgreSQL
3. **API Key必须配置**，否则无法使用
4. **模型可以选择**，根据需求和预算

### 下一步

1. 配置Python服务
2. 启动服务并测试
3. 配置数据源
4. 开始使用AI查询！

---

**需要帮助？**
- 查看日志：`tail -f logs/app.log`
- 查看文档：`docs/backend-api/ai-query-api.md`
- 提交Issue：https://github.com/your-repo/issues
