# 日志分析系统

企业级日志分析系统，采用Spring Cloud Alibaba微服务架构，支持大规模日志数据的实时收集、提取、存储、告警和统计分析。

## 技术栈

### 后端
- Java 21 + Spring Boot 3.2
- Spring Cloud Alibaba + Nacos
- MyBatis Plus
- PostgreSQL 15 (元数据)
- ClickHouse 23 (日志数据)
- Redis 7 (缓存)
- Apache Kafka 3 (消息队列)

### 前端
- Vue 3 + TypeScript
- Vite
- Element Plus
- ECharts

## 项目结构

```
log-analysis-system/
├── log-analysis-backend/          # 后端服务
│   ├── common/                    # 公共模块
│   ├── gateway-service/           # API网关 (8080)
│   ├── auth-service/              # 认证服务 (8081)
│   ├── extraction-service/        # 日志提取服务 (8082)
│   ├── alert-service/             # 告警服务 (8083)
│   ├── stats-service/             # 统计服务 (8084)
│   └── config-service/            # 配置服务 (8085)
├── database/                      # 数据库初始化脚本
│   ├── postgresql/
│   └── clickhouse/
├── scripts/                       # 脚本文件
└── docker-compose.yml             # Docker编排文件
```

## 快速开始

### 前置要求

- Docker & Docker Compose
- JDK 21
- Maven 3.8+
- Node.js 18+

### 1. 启动基础设施

```bash
# 启动所有基础设施服务（Nacos、PostgreSQL、ClickHouse、Redis、Kafka）
./scripts/start-infrastructure.sh
```

### 2. 访问服务

- **Nacos控制台**: http://localhost:8848/nacos
  - 用户名: nacos
  - 密码: nacos

- **PostgreSQL**: localhost:5432
  - 数据库: log_analysis
  - 用户名: postgres
  - 密码: postgres

- **ClickHouse**: http://localhost:8123
  - 数据库: log_analysis
  - 用户名: default

- **Redis**: localhost:6379

- **Kafka**: localhost:9092

### 3. 编译后端服务

```bash
cd log-analysis-backend
mvn clean install
```

### 4. 启动微服务

```bash
# 启动网关服务
cd gateway-service
mvn spring-boot:run

# 启动认证服务
cd auth-service
mvn spring-boot:run

# 启动其他服务...
```

## 数据库说明

### PostgreSQL表结构

- `users` - 用户表
- `extraction_rules` - 日志提取规则表
- `alert_rules` - 告警规则表
- `alert_events` - 告警事件表
- `system_configs` - 系统配置表
- `config_history` - 配置历史表

### ClickHouse表结构

- `syslog` - 原始日志表（Vector直接写入）
- `log_entries` - 提取后的日志表

## 默认账号

- 管理员账号: admin
- 密码: admin123

## 开发指南

### 代码规范

- 遵循Google Java Style Guide
- 使用Lombok减少样板代码
- 所有API使用RESTful风格
- 统一使用Result类封装响应

### 测试

```bash
# 运行单元测试
mvn test

# 运行属性测试
mvn test -Dtest=*PropertyTest
```

## 停止服务

```bash
# 停止所有基础设施服务
docker-compose down

# 停止并删除数据卷
docker-compose down -v
```

## 许可证

MIT License
