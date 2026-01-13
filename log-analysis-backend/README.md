# 日志分析系统 - 单体应用架构

## 项目结构

```
log-analysis-backend/
├── common/                      # 公共模块
│   └── src/main/java/cn/mw/loganalysis/common/
│       ├── config/              # 公共配置
│       ├── exception/           # 异常定义
│       └── response/            # 统一响应
│
└── log-analysis-app/            # 单体应用主模块
    └── src/main/java/cn/mw/loganalysis/
        ├── LogAnalysisApplication.java    # 应用启动类
        ├── auth/                # 认证授权模块
        │   ├── controller/
        │   ├── service/
        │   ├── entity/
        │   ├── mapper/
        │   ├── dto/
        │   └── security/
        ├── extraction/          # 日志提取模块（待实现）
        ├── alert/               # 告警模块（待实现）
        ├── stats/               # 统计模块（待实现）
        └── config/              # 配置模块（待实现）
```

## 架构变更说明

本项目已从**微服务架构**重构为**单体应用架构**，主要变更如下：

### 已移除的组件

- ❌ Spring Cloud（服务发现、配置中心、网关等）
- ❌ Spring Cloud Alibaba
- ❌ Nacos（服务注册与配置中心）
- ❌ Spring Cloud Gateway（API网关）
- ❌ OpenFeign（服务间调用）
- ❌ Sentinel（限流熔断）

### 保留的核心技术

- ✅ Spring Boot 3.2.0
- ✅ Spring Security + JWT
- ✅ MyBatis Plus
- ✅ PostgreSQL（元数据、用户、配置）
- ✅ ClickHouse（日志数据）
- ✅ Redis（缓存）
- ✅ Kafka（异步消息）

### 架构优势

1. **部署简单**：只需部署一个应用实例
2. **开发效率高**：模块间直接方法调用，无需远程通信
3. **调试方便**：所有代码在一个进程中
4. **事务支持**：可使用本地事务
5. **运维成本低**：减少组件依赖

## 快速开始

### 前置要求

- JDK 21+
- Maven 3.8+
- PostgreSQL 15+
- ClickHouse 23+
- Redis 7+
- Kafka 3+

### 构建项目

```bash
cd log-analysis-backend
mvn clean package
```

### 运行应用

```bash
java -jar log-analysis-app/target/log-analysis-app-1.0.0-SNAPSHOT.jar
```

或使用Maven:

```bash
cd log-analysis-app
mvn spring-boot:run
```

### 配置说明

主要配置文件：`log-analysis-app/src/main/resources/application.yml`

**数据库配置：**
- PostgreSQL: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
- ClickHouse: `CLICKHOUSE_HOST`, `CLICKHOUSE_PORT`, `CLICKHOUSE_DB`
- Redis: `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`

**Kafka配置：**
- `KAFKA_BOOTSTRAP_SERVERS`

**JWT配置：**
- `JWT_SECRET`: JWT密钥（生产环境务必修改）

## 模块说明

### 已实现模块

#### 1. 认证授权模块 (auth)

- ✅ 用户登录/登出
- ✅ JWT令牌生成与验证
- ✅ 用户管理（CRUD）
- ✅ 密码加密（BCrypt）
- ✅ Spring Security配置

### 待实现模块

#### 2. 日志提取模块 (extraction)

- ⏳ 提取规则管理
- ⏳ 正则表达式提取器
- ⏳ Grok模式提取器
- ⏳ JSON路径提取器
- ⏳ 定时任务从ClickHouse读取日志
- ⏳ 批量写入提取结果

#### 3. 告警模块 (alert)

- ⏳ 告警规则管理
- ⏳ 实时告警评估
- ⏳ 告警通知发送（Kafka）
- ⏳ 告警静默逻辑
- ⏳ 聚合条件告警

#### 4. 统计模块 (stats)

- ⏳ 日志查询接口
- ⏳ 多维度统计分析
- ⏳ 数据透视
- ⏳ 报表导出（CSV/Excel）

#### 5. 配置模块 (config)

- ⏳ 系统配置管理
- ⏳ 配置历史记录
- ⏳ 动态配置更新

## API文档

应用启动后，可访问以下端点：

- 健康检查: `http://localhost:8080/actuator/health`
- API文档: `http://localhost:8080/swagger-ui.html` (待集成Swagger)

## 下一步工作

根据 `.kiro/specs/log-analysis-system/tasks.md` 文档，建议按以下顺序实施：

1. **补充认证模块测试**（任务3.1、3.2）
2. **实现日志提取模块**（任务4）
3. **实现告警模块**（任务5）
4. **实现统计模块**（任务6）
5. **实现配置模块**（任务7）
6. **前端开发**（任务11-19）

## 参考文档

- 需求文档: `.kiro/specs/log-analysis-system/requirements.md`
- 设计文档: `.kiro/specs/log-analysis-system/design.md`
- 任务清单: `.kiro/specs/log-analysis-system/tasks.md`
