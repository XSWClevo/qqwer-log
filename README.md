# 日志分析系统

企业级日志分析系统，采用单体架构（已从微服务架构简化），支持大规模日志数据的实时收集、存储、查询和统计分析。

## 演示视频

[查看部署演示视频](./素材.mov)

## 技术栈

### 后端服务
- **Java 后端**: Java 21 + Spring Boot 3.2 + MyBatis Plus + Spring Security (JWT)
- **Go Agent**: Go 1.21 + Vector 日志收集代理管理程序

### 数据库
- **PostgreSQL 15**: 元数据存储（用户、规则、配置等）
- **ClickHouse 23**: 日志数据存储（使用 MWLOGDB_ANALYSIS 库）
- **Redis 7**: 缓存和会话管理
- **Apache Kafka 3**: 消息队列（可选）

### 前端
- Vue 3 + TypeScript
- Vite 构建工具
- Element Plus UI 组件库
- ECharts 数据可视化

### 日志收集
- **Vector**: 高性能日志收集器
- **Vector Agent**: Go 语言编写的 Vector 管理代理，支持自动注册、配置拉取、自愈机制

## 项目结构

```
qqwer/
├── log-analysis-backend/          # Java 后端服务（单体架构）
│   └── log-analysis-app/          # 主应用模块
│       ├── auth/                   # 认证授权模块 (JWT + Spring Security)
│       ├── extraction/             # 日志提取规则模块
│       ├── alert/                  # 告警规则和事件模块
│       ├── stats/                  # 统计查询模块（核心）
│       ├── config/                 # 系统配置模块
│       └── common/                 # 公共模块
├── log-analysis-frontend/         # Vue 3 前端
├── vector-agent/                  # Go 语言 Vector 代理
│   ├── cmd/agent/                 # 主程序入口
│   ├── internal/                  # 内部实现
│   │   ├── agent/                 # Agent 核心逻辑
│   │   ├── config/                # 配置管理
│   │   ├── vector/                # Vector 管理
│   │   ├── collector/             # 指标收集
│   │   └── upgrade/               # 自动升级
│   ├── pkg/api/                   # API 客户端
│   └── scripts/                   # 构建脚本
├── database/                      # 数据库初始化脚本
│   ├── postgresql/
│   └── clickhouse/
├── docs/                          # 项目文档
└── docker-compose.yml             # Docker 编排文件
```

## 部署指南

### 前置要求

**基础设施**:
- Docker & Docker Compose
- PostgreSQL 15
- ClickHouse 23
- Redis 7

**Java 后端**:
- JDK 21
- Maven 3.8+

**Go Agent**:
- Go 1.21+（仅开发构建需要）

**前端**:
- Node.js 18+
- pnpm 或 npm

---

## 一、基础设施部署

### 1. 启动基础设施服务

使用 Docker Compose 启动 PostgreSQL、ClickHouse、Redis 等服务：

```bash
# 启动所有基础设施服务
docker-compose up -d

# 查看服务状态
docker-compose ps
```

### 2. 初始化数据库

```bash
# PostgreSQL 数据库初始化
psql -h localhost -U postgres -d postgres -f database/postgresql/init.sql

# ClickHouse 数据库初始化
clickhouse-client --host localhost --query "$(cat database/clickhouse/init.sql)"
```

### 3. 验证基础设施

- **PostgreSQL**: `localhost:5432` (用户名: postgres, 密码: 123456)
- **ClickHouse**: `10.180.5.72:8123` (用户名: default, 密码: mwclickhouse@2024)
- **Redis**: `localhost:6379`

---

## 二、Java 后端部署

### 1. 配置环境变量（可选）

```bash
# PostgreSQL 配置
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=postgres
export DB_USER=postgres
export DB_PASSWORD=123456

# ClickHouse 配置
export CLICKHOUSE_HOST=10.180.5.72
export CLICKHOUSE_PORT=8123
export CLICKHOUSE_DB=MWLOGDB_ANALYSIS
export CLICKHOUSE_USER=default
export CLICKHOUSE_PASSWORD=mwclickhouse@2024
```

### 2. 编译后端服务

```bash
cd log-analysis-backend
mvn clean install -DskipTests
```

### 3. 启动后端服务

```bash
# 方式一：使用 Maven 启动（开发环境）
cd log-analysis-app
mvn spring-boot:run

# 方式二：使用 JAR 包启动（生产环境）
java -jar log-analysis-app/target/log-analysis-app-1.0.0.jar
```

### 4. 验证后端服务

```bash
# 健康检查
curl http://localhost:8080/actuator/health

# 登录测试
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

**默认管理员账号**:
- 用户名: `admin`
- 密码: `admin123`

---

## 三、Go Vector Agent 部署

Vector Agent 是用 Go 语言编写的日志收集代理管理程序，负责管理 Vector 的配置和运行状态。

### 1. 构建 Vector Agent Bundle 安装包

Bundle 包含 `vector-agent` + `vector` 二进制，适合内网环境一键部署：

```bash
cd vector-agent

# 构建所有平台的 Bundle（linux/darwin, amd64/arm64）
./scripts/build-bundle.sh 1.0.0 0.34.0

# 生成的安装包在 dist/ 目录：
# - vector-agent-bundle-1.0.0-linux-amd64.tar.gz
# - vector-agent-bundle-1.0.0-linux-arm64.tar.gz
# - vector-agent-bundle-1.0.0-darwin-amd64.tar.gz
# - vector-agent-bundle-1.0.0-darwin-arm64.tar.gz
```

### 2. 上传安装包到管理后台

1. 打开管理页面 → **安装包管理**
2. 点击 **"上传安装包"**
3. 选择类型为 **"Bundle (Agent+Vector)"**
4. 填写版本号、系统类型、架构
5. 选择对应的 `.tar.gz` 文件上传

### 3. 在目标机器上一键安装

在管理页面生成 Token 后，在目标机器上执行：

```bash
# 一键安装（自动下载并安装 vector-agent + vector）
curl -fsSL "http://YOUR_SERVER:8080/api/vector/agents/install-script?token=YOUR_TOKEN" | sudo bash
```

安装完成后：
- Vector 和 Agent 自动启动
- 机器自动注册到管理页面
- 等待服务器下发配置即可

### 4. 验证 Agent 安装

```bash
# 查看服务状态
systemctl status vector-agent
systemctl status vector

# 查看 Vector 版本
vector --version

# 查看 Agent 日志
journalctl -u vector-agent -f

# 查看 Vector 日志
journalctl -u vector -f
```

### 5. Agent 目录结构

```
/opt/vector-agent/
├── bin/
│   ├── vector-agent    # Agent 主程序
│   └── vector          # Vector 二进制
├── config/
│   ├── agent.yaml      # Agent 配置（只需 token + server_url）
│   ├── vector.yaml     # Vector 配置（由 Agent 管理）
│   └── history/        # 配置备份
├── data/               # Vector 数据目录
└── logs/               # 日志目录
```

### 6. Agent 配置说明

Agent 配置极简，只需两项：

```yaml
# /opt/vector-agent/config/agent.yaml
server_url: "http://192.168.1.100:8080"
agent_token: "your-token-here"
```

可选配置（使用默认值即可）：
```yaml
heartbeat_interval: 30      # 心跳间隔（秒）
config_poll_interval: 30    # 配置轮询间隔（秒）
log_level: "info"           # 日志级别
```

### 7. Agent 常用命令

```bash
# 服务管理
systemctl start vector-agent    # 启动 Agent
systemctl stop vector-agent     # 停止 Agent
systemctl restart vector-agent  # 重启 Agent
systemctl status vector-agent   # 查看状态

systemctl start vector          # 启动 Vector
systemctl stop vector           # 停止 Vector
systemctl restart vector        # 重启 Vector
systemctl status vector         # 查看状态

# 查看日志
journalctl -u vector-agent -f   # Agent 日志
journalctl -u vector -f         # Vector 日志

# Vector 命令
vector --version                # 查看版本
vector validate /opt/vector-agent/config/vector.yaml  # 验证配置
```

### 8. Agent 工作原理

```
┌─────────────────────────────────────────────────┐
│            Vector Agent                          │
│  ┌──────────────────────────────────────────┐  │
│  │   心跳协程 (30s)                          │  │
│  │   └─> 发送心跳到服务器                    │  │
│  ├──────────────────────────────────────────┤  │
│  │   配置监听协程 (30s)                      │  │
│  │   ├─> 拉取配置                            │  │
│  │   ├─> 验证配置                            │  │
│  │   ├─> 备份旧配置                          │  │
│  │   ├─> 应用新配置                          │  │
│  │   └─> 回滚（如果失败）                    │  │
│  ├──────────────────────────────────────────┤  │
│  │   自愈协程 (60s)                          │  │
│  │   ├─> 检查 Vector 进程                    │  │
│  │   └─> 自动重启（如果异常）                │  │
│  └──────────────────────────────────────────┘  │
└─────────────────────────────────────────────────┘
          │                          │
          │ HTTP API                 │ systemctl
          ▼                          ▼
   ┌─────────────┐          ┌───────────────┐
   │  管理服务器  │          │    Vector     │
   └─────────────┘          └───────────────┘
```

### 9. 卸载 Agent

```bash
# 方式一：使用卸载脚本
curl -fsSL "http://YOUR_SERVER:8080/api/vector/agents/uninstall-script" | sudo bash

# 方式二：手动卸载
sudo systemctl stop vector-agent vector
sudo systemctl disable vector-agent vector
sudo rm -rf /opt/vector-agent
sudo rm -f /etc/systemd/system/vector-agent.service
sudo rm -f /etc/systemd/system/vector.service
sudo rm -f /usr/local/bin/vector
sudo systemctl daemon-reload
```

---

## 四、前端部署

### 1. 安装依赖

```bash
cd log-analysis-frontend
npm install
# 或使用 pnpm
pnpm install
```

### 2. 配置后端 API 地址

编辑 `.env.production` 文件：

```bash
VITE_API_BASE_URL=http://your-backend-server:8080
```

### 3. 构建生产版本

```bash
npm run build
# 或
pnpm build
```

### 4. 部署到 Nginx

```bash
# 复制构建产物到 Nginx 目录
cp -r dist/* /usr/share/nginx/html/

# 配置 Nginx 反向代理
# 编辑 /etc/nginx/conf.d/log-analysis.conf
```

Nginx 配置示例：

```nginx
server {
    listen 80;
    server_name your-domain.com;

    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### 5. 重启 Nginx

```bash
nginx -t
nginx -s reload
```

---

## 五、验证部署

### 1. 访问前端页面

打开浏览器访问: `http://your-domain.com`

### 2. 登录系统

使用默认管理员账号登录：
- 用户名: `admin`
- 密码: `admin123`

### 3. 查看 Agent 列表

进入 **Vector 管理** → **Agent 列表**，确认已安装的 Agent 显示在线状态。

### 4. 查看日志数据

进入 **日志查询** → **实时日志**，确认能够查询到日志数据。

---

## 六、开发环境部署

### 1. 启动后端（开发模式）

```bash
cd log-analysis-backend/log-analysis-app
mvn spring-boot:run
```

### 2. 启动前端（开发模式）

```bash
cd log-analysis-frontend
npm run dev
# 或
pnpm dev
```

访问: `http://localhost:5173`

### 3. 本地测试 Vector Agent

```bash
cd vector-agent

# 编译
go build -o vector-agent cmd/agent/main.go

# 生成配置
./vector-agent -gen-config -server http://localhost:8080 -token test123

# 运行
./vector-agent -config /path/to/agent.yaml
```

---

## 七、生产环境建议

### 1. 安全配置

- 修改默认管理员密码
- 配置 HTTPS 证书
- 启用防火墙规则
- 配置 JWT 密钥

### 2. 性能优化

- 调整 JVM 堆内存: `-Xms2g -Xmx4g`
- 配置 ClickHouse 分片和副本
- 启用 Redis 持久化
- 配置 Nginx 缓存

### 3. 监控告警

- 配置 Prometheus + Grafana 监控
- 启用应用日志收集
- 配置告警规则

### 4. 备份策略

- 定期备份 PostgreSQL 数据库
- 配置 ClickHouse 数据备份
- 备份配置文件

---

## 数据库说明

### PostgreSQL 表结构

- `users` - 用户表
- `extraction_rules` - 日志提取规则表
- `alert_rules` - 告警规则表
- `alert_events` - 告警事件表
- `system_configs` - 系统配置表
- `config_history` - 配置历史表

### ClickHouse 表结构

- `syslog` - 原始日志表（Vector 直接写入）
- `log_entries` - 提取后的日志表

---

## 开发指南

### 代码规范

- 遵循 KISS 原则，非必要不要过度设计
- 精益求精（YAGNI 原则）：仅实现当前明确所需的功能
- 坚实基础（SOLID 原则）：遵循面向对象设计的基本原则
- 杜绝重复（DRY 原则）：识别并消除代码或逻辑中的重复模式
- 使用 Lombok 减少样板代码
- 所有 API 统一使用 POST 方法
- 统一使用 Result 类封装响应
- 必须使用 MapStruct 进行对象转换

### 测试

```bash
# 运行单元测试
mvn test

# 运行集成测试
mvn verify
```

---

## 停止服务

```bash
# 停止所有基础设施服务
docker-compose down

# 停止并删除数据卷
docker-compose down -v

# 停止 Vector Agent
systemctl stop vector-agent vector
```

---

## 常见问题

### 1. 403 错误

检查 `SecurityConfig` 白名单配置，重启应用生效。

### 2. Lombok 编译错误

确保 Lombok 版本 1.18.34，检查 IDE 插件是否安装。

### 3. ClickHouse 连接失败

检查数据库名是否为 `MWLOGDB_ANALYSIS`，确认网络连通性。

### 4. 数据源切换失败

确认 `@DS` 注解正确，检查动态数据源配置。

### 5. Vector Agent 无法启动

检查 Token 是否正确，确认服务器地址可访问。

---

## 相关文档

- **动态数据源**: `log-analysis-backend/DYNAMIC_DATASOURCE_GUIDE.md`
- **ClickHouse 迁移**: `log-analysis-backend/CLICKHOUSE_MIGRATION_GUIDE.md`
- **后端实现**: `log-analysis-backend/BACKEND_IMPLEMENTATION.md`
- **接口文档**: `docs/backend-api/`
- **Vector Agent**: `vector-agent/README.md`

---

## 许可证

MIT License
