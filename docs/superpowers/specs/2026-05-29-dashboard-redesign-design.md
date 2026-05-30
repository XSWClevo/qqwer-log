# Dashboard 重构设计

日期：2026-05-30

## 背景

当前“日志监控大屏”存在两个根本问题：

1. 指标来源和真实业务链路不一致。后端 Dashboard 查询大量硬编码 `syslog` 表，而平台当前默认日志数据集可能来自动态创建的 ClickHouse 表，例如 `syslog_logs`，也可能根本没有默认表。
2. 指标体系不适合真实运维使用。当前大屏混合了后端宿主机资源、伪造的 backlog、Java 专属异常统计等内容，造成空白、误导和弱相关指标并存。

因此本次重构目标不是“修几个空白图”，而是把 Dashboard 改成一个对日志平台真实可用的“日志运营 + 采集健康”总览页。

## 目标

- 消除由于硬编码表名导致的空白卡片和空图表。
- 支持动态发现可统计的数据集，而不是默认依赖某一张表。
- 将平台健康指标和日志业务指标拆开，避免无日志时整屏失效。
- 对每张卡片提供明确的空状态、错误状态和数据来源说明。
- 为后续多数据集切换、按 queryable sink 切换、按业务域切换保留扩展点。
- 将首页视觉从“旧式大屏拼卡片”重构为“轻量控制台首页”，避免厚重黑框、大段说明文案和强解释型 hero 区块。

## 非目标

- 本次不做跨多个日志表的聚合统计。
- 本次不做完整自定义大屏编排。
- 本次不做复杂的缓存体系优化，只修正明显错误的缓存 key 和降级策略。
- 本次不改普通日志搜索页和智能助手查询链路。

## 现状问题

### 数据绑定问题

- Dashboard MyBatis 查询写死 `syslog` 表。
- `log_category_registry` 中的 `table_name` 是动态数据集元信息，不保证存在，也不保证有数据。
- 可查询数据源实际来自 `queryable=true` 的 ClickHouse sink 组件，这套能力与 Dashboard 目前没有打通。

### 指标设计问题

- `MachineStatus` 展示的是后端服务宿主机，而不是 Vector 主机集群。
- `processingDelay` 通过错误/WARN 数量推导，不代表真实采集或消费延迟。
- `RecurringExceptions` 假设日志消息包含 Java 异常类名，不适用于 syslog、安全日志、网络设备日志。
- `DatabaseStatus` 将总容量写死为 1TB，不具备可信度。

### 容错问题

- `/api/dashboard/overview` 聚合接口中任意 Future 抛错，整页核心数据都可能失败。
- Cache key 未包含时间范围参数，可能出现切换时间窗口后仍返回旧数据。
- 前端对空状态区分不清，0 值和“查不到数据”会混淆。
- 当前前端和后端 overview 结构不一致时，会把“结构不匹配”误表现成“数据全空”，需要增加兼容映射或同步完成接口升级。

### 视觉与体验问题

- 首页顶部存在过重的大卡片说明区，不像真实产品后台首页，更像概念展示稿。
- 卡片边框和背景过重，整体像“黑框大卡片拼图”，没有成熟控制台产品常见的轻量留白和节奏。
- 文案解释过多，用户进入首页时真正关心的是“哪里异常、当前看哪套数据、下一步去哪里”，而不是阅读页面自述。
- 空数据态面积过大，容易让人误以为页面坏了，而不是明确知道“当前没有可查询数据集”或“当前时间范围无数据”。

## 方案对比

### 方案 A：继续修补现有 Dashboard 接口

做法：

- 把 `syslog` 改成 `syslog_logs`。
- 补几个空判断和默认值。

优点：

- 改动最小。

缺点：

- 仍然依赖单一固定表名。
- 动态数据集场景继续失效。
- 指标体系仍然有大量伪指标。

结论：

不采用。只能短期止血，不能解决模型错误。

### 方案 B：以 `log_category_registry` 作为唯一数据集来源

做法：

- Dashboard 只从 `log_category_registry` 读取 enabled 数据集。
- 根据注册表里的字段映射和表名构造查询。

优点：

- 逻辑上统一。

缺点：

- 注册表记录不保证表存在。
- 注册表可能是空，或者数据滞后于真实 sink。
- 与当前 queryable sink 的可查询能力重复。

结论：

不作为唯一方案，可作为候选来源。

### 方案 C：以 queryable ClickHouse sink 为主，注册表为辅

做法：

- 优先从 `queryable=true` 的 ClickHouse sink 动态发现候选数据集。
- 如果没有 queryable sink，再回退到 `log_category_registry`。
- 对候选数据集逐个做表存在性、核心字段、最近数据时间探测。

优点：

- 与平台现有“可查询数据源”语义一致。
- 能适配动态创建表。
- 可以自然支持空状态和后续切换数据集。

缺点：

- 需要新增一层数据集发现和可用性探测。

结论：

采用方案 C。

### 方案 D：沿用“Hero + 多块均匀卡片”视觉方向

做法：

- 顶部使用大面积说明型 hero 卡片。
- 下方继续用多块视觉权重相近的卡片平铺。

优点：

- 容易快速拼装页面。

缺点：

- 不像成熟产品后台首页。
- 说明味过重，信息主次不清。
- 空数据态会被放大成“整页都很空”的感受。

结论：

不采用。视觉方向改为轻量控制台首页。

## 最终设计

### 1. 数据集发现模型

新增 Dashboard 数据集解析流程：

1. 查询所有 `queryable=true` 的 ClickHouse sink 组件。
2. 从 sink 配置中提取 `database`、`table`、显示名、组件 id。
3. 如果没有 queryable sink，再读取 `log_category_registry` 中 enabled 且有 `table_name` 的记录。
4. 对每个候选数据集执行探测：
   - 表是否存在。
   - 是否包含核心字段：`timestamp`、`severity`、`message`。
   - 总行数。
   - 最近一条日志时间。
5. 选择默认数据集：
   - 优先最近 24 小时有数据的 queryable sink。
   - 其次选择表存在但暂时无数据的数据集。
   - 如果都不可用，Dashboard 进入“无日志数据集”模式。

新增内部 DTO：

- `DashboardDatasetCandidate`
- `DashboardDatasetProbe`
- `DashboardDatasetContext`

### 2. 指标分层

Dashboard 分为两层指标。

#### 平台健康层

这层不依赖日志业务表，即使没有任何日志表，也必须可用：

- 在线 Vector 主机数 / 总主机数
- 最近心跳时间
- 最近 5 分钟组件错误数
- 最近 5 分钟 pipeline 吞吐
- 可查询数据集数量
- ClickHouse 连通状态

数据来源：

- PostgreSQL `vector_machines`
- ClickHouse `vector_pipeline_metrics`
- ClickHouse `vector_component_errors`
- ClickHouse 探测查询

#### 日志业务层

这层依赖当前选中的日志数据集：

- 总日志量
- 当前 EPS
- 错误数 / 严重数
- 错误率
- 活跃主机数
- 活跃应用数
- 级别趋势
- Top 主机
- Top 应用
- Top 错误消息
- 最近高危日志

### 3. 去除或降级的旧指标

以下旧卡片不再保留原逻辑：

- 后端宿主机 CPU / 内存 / 磁盘
- 伪造的 processing delay
- 基于 message 正则推导的 recurring exceptions
- 写死 1TB 的 ClickHouse 总容量

替换规则：

- `MachineStatusCard` 改成 `VectorFleetCard`
- `LogPipelineCard` 改成 `IngestionHealthCard`
- `TopExceptionsCard` 改成 `TopErrorMessagesCard`
- `DatabaseStatusCard` 改成 `StorageAndQueryCard`

### 4. 页面结构

新 Dashboard 结构：

- 顶部：轻量页头，仅保留页面标题、时间范围、自动刷新、当前数据集与主要动作，不使用大面积解释型 hero
- 第一行：6 个紧凑 KPI 卡，优先展示平台与当前数据集的关键状态
- 第二行：左侧主趋势图，右侧当前数据集上下文与候选数据集
- 第三行：Top 主机、Top 应用、高危日志三个紧凑块

视觉约束：

- 参考 Vercel、Linear、Datadog/Grafana 的轻量后台首页，而不是“监控大屏 + 说明卡片”风格
- 使用浅背景、轻边框、更多留白和少量重点色
- 颜色只用于强调趋势线、状态标签、异常数字、当前选中态，不用于大面积厚重底色
- 去掉大黑框、重阴影和大段说明文字
- 首页每个区块都要先回答“这块是否必要”，解释型内容默认删除

当没有可用日志数据集时：

- 不做整页大面积空白说明
- 顶部和数据集区给出短提示：“暂无可统计日志数据集”
- 平台健康卡片继续显示
- 日志业务区各自进入局部空状态
- 统一提供明确动作入口，例如“前往组件库”“创建日志解析”

当存在可查询数据集但当前时间范围无日志时：

- 保留当前数据集卡和 KPI 框架
- 趋势图、排行、高危日志显示局部空状态
- 文案只说明“当前时间范围暂无数据”，不与“无 queryable 数据集”混淆

### 5. 后端接口策略

不再让前端拼接多个旧接口，仍保留聚合接口，但重写其组织方式。

推荐保留：

- `POST /api/dashboard/overview`

新增返回：

- `datasetContext`
- `platformHealth`
- `logKpis`
- `topErrorMessages`
- `emptyState`
- `warnings`

内部实现要求：

- 每个模块单独 try/catch，失败时返回局部空数据和 warning，不允许整页失败。
- cache key 必须包含 `datasetId/tableName/startTime/endTime/granularity`。
- 动态表名必须经过白名单校验，只能来自数据集发现结果，不能直接拼用户输入。

### 6. 查询实现策略

日志业务查询不再通过 Dashboard 专用硬编码 Mapper 直接查 `syslog`。

采用两段式：

1. Dashboard 先解析出当前 `DashboardDatasetContext`。
2. 再通过统一的动态查询能力按 `database + table + timeField + severityField + messageField + hostnameField + appnameField` 执行统计。

如果现有 `StatsService / DynamicLogQueryService` 复用成本过高，则在 Dashboard 模块内部新增一个轻量 `DashboardQueryService`，专门接收已校验的数据集上下文后生成 SQL。

约束：

- 所有表名、字段名必须先校验合法字符集。
- 必须显式限制为当前发现到的数据集，不允许外部传任意表名。

### 7. 前端状态模型

前端每张卡片都要区分三种状态：

- `loading`
- `ready`
- `empty/error`

禁止把以下情况都显示为 0：

- 表不存在
- overview 接口结构不匹配
- 表为空
- 查询失败
- 指标真实值为 0

每张卡片需要可选显示：

- 数据来源
- 最近更新时间
- warning 提示

### 8. 兼容与迁移

- 保留现有 Dashboard 路由 `/`
- 新前端尽量复用图表组件容器和全局主题变量
- 旧接口方法可以保留一版兼容期，但前端统一走新 overview 结构
- 如果没有任何 queryable sink，也没有注册表可用数据集，大屏仍可进入平台健康模式
- 在后端 overview 尚未完全切换前，前端需要有一层兼容映射，明确区分“旧接口兼容展示”和“真实空数据”

## 视觉决策

本次首页的视觉方向采用：

- `Copilot 工作台 + 驾驶舱概览` 的混合模式
- 但视觉表现偏“成熟产品控制台首页”，而不是“解释型 Dashboard”

保留：

- 顶部轻量上下文
- KPI 指标带
- 主趋势图
- 数据集上下文块
- 紧凑排行与高危日志块

删除或避免：

- 大面积 hero 说明卡
- 黑重边框和厚重卡片底色
- 教学式文案
- 底部超高的说明块

空状态原则：

- 空状态必须内聚到对应模块，不扩大成整页“像坏掉”的视觉
- “没有 queryable 数据集”和“当前时间范围没数据”必须是两种不同的空状态
- 空状态默认只保留一句原因 + 一个动作

## 主要实现步骤

1. 重构后端数据集发现与探测能力。
2. 重构 Dashboard overview 聚合模型和局部降级。
3. 替换硬编码 `syslog` 查询。
4. 重写前端 Dashboard 数据模型。
5. 重做大屏 UI 和空状态。
6. 联调 queryable sink、空表、无数据、无数据集场景。

## 测试策略

### 后端

- queryable sink 存在且表有数据
- queryable sink 存在但表不存在
- queryable sink 存在但空表
- 没有 queryable sink，回退到注册表
- overview 某一子模块失败时其余模块仍返回
- cache 不同时间范围命中不同 key

### 前端

- 有数据时完整展示
- 无数据集时展示平台健康 + 空状态引导
- 空表时显示“暂无日志数据”
- 自动刷新时卡片状态稳定
- 深色模式/浅色模式兼容
- 大屏在 1440px 和 1920px 下布局正常

## 风险

- 当前 DashboardMapper 与动态查询体系割裂，复用中可能需要额外抽象。
- 如果 Vector sink 配置格式不统一，提取 `database/table` 需要兼容旧模板。
- `system.query_log` 在某些 ClickHouse 环境可能不可用，需要准备降级方案。
- 如果只改前端不改后端 overview，会继续出现“数据实际可查，但首页误判为空”的问题。
- 如果 queryable sink 的表信息不在统一字段中，需要增加对 `config_yaml` 或 `visual_data` 的兼容解析。

## 决策

本次重构按“平台健康 + 选中日志数据集”模型执行，默认使用 queryable ClickHouse sink 作为数据集主来源，`log_category_registry` 仅作为回退候选，不再把 `syslog` 或 `syslog_logs` 视为固定真相。

首页视觉按“轻量控制台首页”执行，参考成熟 SaaS/可观测性产品的布局节奏，不再采用大黑框、大 hero 和解释型卡片。
