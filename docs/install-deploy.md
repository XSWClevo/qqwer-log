# 安装与部署（详细）

本说明覆盖本地开发、生产部署与 Vector Agent 安装流程。文中不包含明文账号、密码或地址，请通过环境变量或本地配置自行注入。

## 前置要求

- Docker & Docker Compose
- JDK 21 + Maven 3.8+
- Node.js 18+（或 pnpm）
- Go 1.21+（仅编译 Vector Agent 时需要）

## 一、基础设施部署

### 1. 启动基础设施

```bash
docker-compose up -d
docker-compose ps
```

说明：
- 初始化脚本位于 `database/postgresql/docker-init.sql` 与 `database/clickhouse/docker-init.sql`
- 数据卷已存在时 Docker 不会重复执行初始化脚本

### 2. 手动初始化（可选）

若需手动执行初始化（例如首次自建环境）：

```bash
psql -h <postgres-host> -U <postgres-user> -d <postgres-db> -f database/postgresql/docker-init.sql
clickhouse-client --host <clickhouse-host> --query "$(cat database/clickhouse/docker-init.sql)"
```

## 二、后端部署

### 1. 配置环境变量（示例占位符）

```bash
export DB_HOST=<postgres-host>
export DB_PORT=<postgres-port>
export DB_NAME=<postgres-db>
export DB_USER=<postgres-user>
export DB_PASSWORD=<postgres-password>

export CLICKHOUSE_HOST=<clickhouse-host>
export CLICKHOUSE_PORT=<clickhouse-port>
export CLICKHOUSE_DB=<clickhouse-db>
export CLICKHOUSE_USER=<clickhouse-user>
export CLICKHOUSE_PASSWORD=<clickhouse-password>

export REDIS_HOST=<redis-host>
export REDIS_PORT=<redis-port>
```

### 2. 编译与启动

```bash
cd log-analysis-backend
mvn clean install -DskipTests

cd log-analysis-app
mvn spring-boot:run
```

生产环境可以改为 jar 启动：

```bash
java -jar log-analysis-app/target/log-analysis-app-1.0.0.jar
```

### 3. 健康检查

```bash
curl http://<backend-host>:<backend-port>/actuator/health
```

## 三、前端部署

### 1. 安装依赖并构建

```bash
cd log-analysis-frontend
npm install
npm run build
```

### 2. 配置后端 API 地址

`.env.production` 示例（占位符）：

```bash
VITE_API_BASE_URL=<backend-url>
```

### 3. 部署到 Nginx

```bash
cp -r dist/* /usr/share/nginx/html/
```

Nginx 配置示例：

```nginx
server {
    listen 80;
    server_name <your-domain>;

    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api {
        proxy_pass http://<backend-host>:<backend-port>;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## 四、Vector Agent 部署

Vector Agent 是 Go 编写的日志采集代理，负责安装 Vector 与配置同步。

### 1. 构建 Bundle（可选）

```bash
cd vector-agent
./scripts/build-bundle.sh <agent-version> <vector-version>
```

### 2. 一键安装（管理端生成 Token 后）

```bash
curl -fsSL "http://<backend-host>:<backend-port>/api/vector/agents/install-script?token=<token>" | sudo bash
```

### 3. 服务状态检查

```bash
systemctl status vector-agent
systemctl status vector
```

## 五、停止服务

```bash
docker-compose down
docker-compose down -v
systemctl stop vector-agent vector
```

## 备注

- 不要在文档或仓库中提交明文账号/密码
- 真实账号、密码、地址请通过环境变量或本地配置注入
