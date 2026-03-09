# 日志分析系统

面向企业场景的日志采集、存储、查询与统计分析平台（单体架构）。

## 安装与启动

### 1. 启动基础设施

```bash
docker-compose up -d
docker-compose ps
```

说明：
- PostgreSQL / ClickHouse / Redis 由 `docker-compose.yml` 启动
- 初始化脚本位于 `database/postgresql/docker-init.sql` 与 `database/clickhouse/docker-init.sql`
- 若数据卷已存在，Docker 不会重复执行初始化脚本

### 2. 启动后端

```bash
cd log-analysis-backend/log-analysis-app
mvn spring-boot:run
```

### 3. 启动前端

```bash
cd log-analysis-frontend
npm install
npm run dev
```

## 详细安装部署文档

详见 `docs/install-deploy.md`。
