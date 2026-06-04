# Project Conventions

## Project Background

- 这是一个日志分析平台，核心链路是前端管理、后端 API、Vector Agent 采集/下发、ClickHouse/PostgreSQL 存储、AI 查询服务。
- 后续处理需求时默认先按本文件理解项目背景，不要反复要求用户解释模块职责。
- 涉及数据库、Vector 初始化、告警、前端菜单或 AI 查询时，优先在对应模块内找现有实现和约定，再做修改。

## Project Structure

- `log-analysis-backend/log-analysis-app` 是后端主应用模块，负责平台 API、业务逻辑、Vector 配置模板初始化、PostgreSQL/ClickHouse Liquibase 脚本入口、告警等核心能力。
- `log-analysis-frontend` 是前端模块，使用 Vue/Element Plus，菜单路由集中在 `src/router/index.ts`，页面主要在 `src/views`，接口封装在 `src/api`。
- `vector-agent` 是平台下发后安装在目标机器上的 Agent 模块，负责采集端运行、Vector 配置拉取/应用、自愈、升级等 Agent 主流程。
- `vector-agent/scripts` 是 Agent 相关开发工具目录，包括打包、安装、卸载、构建和辅助脚本，不要把它当成平台后端初始化入口。
- `log-analysis-ai-service` 是 AI 查询服务模块，供日志自然语言查询、分析解释等 AI 能力使用。
- `docs` 目录主要存放 mock 数据脚本、调试脚本和开发辅助文档，例如发送测试日志、攻击日志样例等。
- `database`、`docker-compose.yml` 等根目录资源用于本地环境或历史辅助配置；正式应用数据库变更仍以 `log-analysis-app` 下 Liquibase 为准。

## Module Boundaries

- 平台侧需要新增 API、业务表、初始化数据、Vector 组件模板、告警逻辑时，优先改 `log-analysis-backend/log-analysis-app`。
- 前端交互、菜单、页面样式、API 调用类型和用户体验问题，优先改 `log-analysis-frontend`。
- 采集端安装包、Agent 启停、卸载、打包、升级、自愈、配置拉取执行逻辑，才改 `vector-agent`。
- AI 查询语义解析、模型调用、查询生成、AI 服务接口，才改 `log-analysis-ai-service`。
- mock 数据、压测数据、演示日志、攻击识别测试数据脚本，放在 `docs`，不要混进生产后端代码。

## Liquibase

- PostgreSQL DDL 放在 `log-analysis-backend/log-analysis-app/src/main/resources/db/changelog/changes`，并追加到 `db.changelog-master.yaml`。
- ClickHouse DDL 放在 `log-analysis-backend/log-analysis-app/src/main/resources/db/changelog/clickhouse/changes`，并追加到 ClickHouse master。
- 新 SQL 使用 `--liquibase formatted sql` 和稳定 changeset id。
- 破坏性变更必须显式写在新的 changeset 中，避免手工改库造成环境漂移。
- 初始化或种子数据应尽量幂等，优先使用 `IF EXISTS`、`IF NOT EXISTS`、`ON CONFLICT` 或显式存在性判断。
- 不要直接修改已执行过的 changeset 内容，除非用户明确接受 checksum 变化；新增修复应追加新的 changeset。
- Todo、旧告警等废弃功能如果需要删表，也必须通过 Liquibase changeset 管理。

## Vector Initialization

- Vector 初始化配置在后端 `log-analysis-app` 中进行，主要通过 Liquibase 初始化平台侧模板、元数据和 ClickHouse 表结构。
- 平台侧负责维护可下发的 Vector 组件模板、pipeline 元数据、UI 配置项和查询统计所需表。
- 不在平台侧重复实现 `vector-agent` 的安装、卸载、默认配置、自愈和配置拉取主流程。
- Agent 端运行逻辑、安装包脚本和 Vector 本地执行细节属于 `vector-agent`；平台只下发配置和管理状态。
- Vector 组件模板至少覆盖 syslog/file source、remap transform、clickhouse sink、console sink，并保持幂等。
- ClickHouse 中日志、机器指标、pipeline 指标、组件错误/吞吐指标等 Vector 观测表通过后端 Liquibase 管理。

## Alert Monitor

- 告警管理保留 `/api/alert/**` 接口和前端菜单，但后端模型以归一化 Monitor 表为准，不再使用旧版 `condition` JSON 作为主模型。
- 告警状态机只使用 `OK`、`WARNING`、`CRITICAL`、`NO_DATA`、`RECOVERED`。
- 阈值、恢复阈值、No Data、evaluation delay、renotify、downtime 都应通过 `alert_rule_thresholds`、`alert_monitor_options`、`alert_monitor_states` 和 `alert_downtimes` 实现。
- Java Service 需要拆为 `XxxService` 接口和 `XxxServiceImpl` 实现类，事务注解放在实现类方法上，避免代理和事务边界混乱。
- 告警创建入口要优先简单可用，默认快速创建路径只暴露必要字段，高级 Monitor 能力放到高级设置中。

## Java

- Java 开发中，涉及判空时优先使用 Apache Commons 提供的工具类。
- 常见场景优先使用 `org.apache.commons.lang3.StringUtils`、`org.apache.commons.lang3.ObjectUtils`、`org.apache.commons.collections4.CollectionUtils`、`org.apache.commons.collections4.MapUtils`。
- 如果当前场景没有合适的 Apache 工具类，再使用 Java 自带的判空方式，例如 `Objects.isNull`、`Objects.nonNull` 或显式 `null` 判断。

## Backend

- 后端服务代码位于 `log-analysis-backend/log-analysis-app/src/main/java`。
- MyBatis mapper XML 位于 `log-analysis-backend/log-analysis-app/src/main/resources/mapper`。
- 涉及事务、异步执行、定时任务、告警评估、配置下发等逻辑时，注意 Spring 代理边界，避免同类内部直接调用导致事务或异步不生效。
- Service 默认使用 `XxxService` 接口 + `XxxServiceImpl` 实现，不要直接让业务类只继承 `ServiceImpl<>` 后被控制器依赖。

## Frontend

- 前端模块为 `log-analysis-frontend`，主要使用 Vue 3、Vite、Element Plus。
- 新增或删除菜单时同步检查 `src/router/index.ts`、`src/components/layout/AppLayout.vue`、面包屑和命令面板等入口。
- 所有前端页面开发都必须先按 UI/UE 视角处理，再进入编码；如果环境中存在可用的 UI/UE skills，优先使用这些 skills 做设计和体验方案。
- 如果没有独立 UI/UE skills，也必须执行同等流程：先明确用户目标、主路径、信息层级、空/错/加载状态、响应式布局、视觉风格和可操作反馈，再实现页面。
- 做页面体验调整时优先保持现有设计系统和变量，不要引入突兀的默认模板风格，不要只堆 Element Plus 默认组件。
- 每次新建 UI 或修改 UI 时，都必须同时匹配浅色主题和深色主题。
- 新页面或大改页面必须有清晰的视觉重点、合理留白、统一间距、明确按钮层级、可读文案和顺畅交互；开发完成后要用浏览器或截图验证真实观感。
- 表单类页面要优先做“快速可用路径”，高级配置默认收起或分组，不允许把所有技术字段一次性摊给普通用户。
- 告警、Vector、攻击识别等复杂配置页要优先提供快速路径，避免把高级配置一次性全部暴露给普通用户。

## AI Service

- `log-analysis-ai-service` 服务 AI 查询能力，通常负责自然语言理解、查询生成、解释和模型服务集成。
- 修改 AI 查询链路时，需要同时确认前端 AI 查询入口、后端代理/API，以及 AI service 的请求响应结构是否一致。

## Mock And Docs

- `docs` 下脚本用于 mock 数据、测试数据和开发验证，例如 syslog/攻击日志发送脚本。
- mock 脚本可以面向本地调试优化，但不要让生产逻辑依赖 `docs` 目录。
