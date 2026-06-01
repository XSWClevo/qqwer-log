# Dashboard Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将首页 Dashboard 重构为“平台健康 + 选中日志数据集”工作台，消除 `syslog` 硬编码和空白伪指标问题。

**Architecture:** 后端拆成“数据集发现/探测、平台健康聚合、日志数据集聚合、总览装配”四层，前端改成围绕新 `overview` 模型的单页工作台。保留 `POST /api/dashboard/overview`，但改写返回结构与局部降级策略；前端围绕 `datasetContext`、`platformHealth`、`logKpis` 与空状态重新组织 UI。

**Tech Stack:** Spring Boot, MyBatis/MyBatis-Plus, ClickHouse, PostgreSQL, Vue 3, TypeScript, Element Plus, ECharts, SCSS

---

### Task 1: 重构后端 DTO 与数据集发现层

**Files:**
- Create: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard/dto/DashboardDatasetCandidateDTO.java`
- Create: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard/dto/DashboardDatasetContextDTO.java`
- Create: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard/dto/DashboardWarningDTO.java`
- Create: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard/service/DashboardDatasetDiscoveryService.java`
- Create: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard/service/DashboardDatasetProbeService.java`
- Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard/dto/DashboardOverviewDTO.java`
- Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/vector/service/ConfigComponentService.java`
- Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/stats/service/DynamicLogQueryService.java`

- [ ] **Step 1: 先定义新的总览返回骨架**

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardOverviewDTO {
    private DashboardDatasetContextDTO datasetContext;
    private PlatformHealthDTO platformHealth;
    private DashboardLogKpisDTO logKpis;
    private DashboardTrendDTO logTrend;
    private DashboardDistributionDTO severityDistribution;
    private DashboardTopListDTO topHosts;
    private DashboardTopListDTO topApps;
    private DashboardTopListDTO topErrorMessages;
    private DashboardRecentLogsDTO recentHighRiskLogs;
    private DashboardEmptyStateDTO emptyState;
    private List<DashboardWarningDTO> warnings;
    private String traceId;
}
```

- [ ] **Step 2: 定义数据集候选与上下文 DTO**

```java
@Builder
public record DashboardDatasetCandidateDTO(
        String source,
        String datasourceId,
        String datasourceName,
        String databaseName,
        String tableName,
        String componentType,
        Boolean queryable) {
}

@Builder
public record DashboardDatasetContextDTO(
        String datasourceId,
        String datasourceName,
        String databaseName,
        String tableName,
        String source,
        String status,
        Long totalRows,
        String latestLogTime,
        Boolean hasData) {
}
```

- [ ] **Step 3: 为 `ConfigComponentService` 增加只读的 queryable sink 配置解析入口**

```java
public List<ConfigComponent> getQueryableClickHouseSinks() {
    return getQueryableDataSources().stream()
            .filter(component -> StringUtils.equalsIgnoreCase(component.getVectorType(), "clickhouse"))
            .toList();
}
```

- [ ] **Step 4: 在 `DynamicLogQueryService` 中暴露 ClickHouse sink 配置解析能力，避免 Dashboard 重复抄 YAML 解析**

```java
public DatasourceConnectionConfig getQueryableDatasourceConfig(String datasourceId) {
    ConfigComponent component = configComponentService.getQueryableDataSourceById(datasourceId);
    if (component == null) {
        throw new IllegalArgumentException("可查询数据源不存在: " + datasourceId);
    }
    return parseYamlConfig(component);
}
```

- [ ] **Step 5: 新增数据集发现服务，优先 queryable sink，再回退注册表**

```java
public List<DashboardDatasetCandidateDTO> discoverCandidates() {
    List<DashboardDatasetCandidateDTO> sinkCandidates = fromQueryableSinks();
    if (CollectionUtils.isNotEmpty(sinkCandidates)) {
        return sinkCandidates;
    }
    return fromRegistry();
}
```

- [ ] **Step 6: 新增数据集探测服务，校验表存在、核心字段、行数、最近日志时间**

```java
public DashboardDatasetContextDTO probeAndSelectDefault(List<DashboardDatasetCandidateDTO> candidates) {
    return candidates.stream()
            .map(this::probeCandidate)
            .sorted(this::compareDatasetPriority)
            .findFirst()
            .orElse(null);
}
```

- [ ] **Step 7: 编译后端，确认 DTO 和服务签名先成立**

Run: `./mvnw -pl log-analysis-backend/log-analysis-app -DskipTests compile`
Expected: `BUILD SUCCESS` 或仅出现与本次改动无关的既有告警

- [ ] **Step 8: 提交这一层拆分**

```bash
git add log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard/dto \
  log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard/service \
  log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/vector/service/ConfigComponentService.java \
  log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/stats/service/DynamicLogQueryService.java
git commit -m "refactor: add dashboard dataset discovery layer"
```

### Task 2: 替换 Dashboard 后端聚合逻辑与 `syslog` 硬编码

**Files:**
- Create: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard/dto/PlatformHealthDTO.java`
- Create: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard/dto/DashboardLogKpisDTO.java`
- Create: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard/dto/DashboardTopListDTO.java`
- Create: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard/dto/DashboardRecentLogsDTO.java`
- Create: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard/dto/DashboardEmptyStateDTO.java`
- Create: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard/service/DashboardPlatformHealthService.java`
- Create: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard/service/DashboardLogDatasetService.java`
- Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard/service/DashboardService.java`
- Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard/controller/DashboardController.java`
- Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard/mapper/DashboardMapper.java`
- Modify: `log-analysis-backend/log-analysis-app/src/main/resources/mapper/DashboardMapper.xml`

- [ ] **Step 1: 先新增平台健康 DTO，明确与日志数据集隔离**

```java
@Data
@Builder
public class PlatformHealthDTO {
    private Integer onlineVectorHosts;
    private Integer totalVectorHosts;
    private Long componentErrorsLast5m;
    private Double pipelineThroughputLast5m;
    private Integer queryableDatasetCount;
    private String clickHouseStatus;
    private String lastHeartbeatTime;
}
```

- [ ] **Step 2: 把旧 `DashboardService` 拆成装配器，不再自己直接查一堆 `syslog`**

```java
public DashboardOverviewDTO getOverview(DashboardQueryRequest request) {
    List<DashboardWarningDTO> warnings = new ArrayList<>();
    DashboardDatasetContextDTO datasetContext = datasetDiscoveryService.resolveDefaultDataset(warnings);
    PlatformHealthDTO platformHealth = platformHealthService.getPlatformHealth(warnings);
    DashboardLogDatasetSnapshot snapshot = logDatasetService.buildSnapshot(datasetContext, request, warnings);
    return assembleOverview(datasetContext, platformHealth, snapshot, warnings, traceId);
}
```

- [ ] **Step 3: 在 `DashboardLogDatasetService` 中用已校验上下文动态拼接 ClickHouse SQL**

```java
String tableRef = sqlIdentifier(datasetContext.databaseName()) + "." + sqlIdentifier(datasetContext.tableName());
String sql = """
    SELECT severity, count() AS cnt
    FROM %s
    WHERE timestamp >= parseDateTimeBestEffort(?)
      AND timestamp <= parseDateTimeBestEffort(?)
    GROUP BY severity
    """.formatted(tableRef);
```

- [ ] **Step 4: 把 `DashboardMapper.xml` 中所有硬编码 `syslog` 的查询降到只保留平台级探针或彻底删除未使用项**

```xml
<select id="pingClickHouse" resultType="java.lang.Integer">
    SELECT 1
</select>
```

- [ ] **Step 5: 调整 `DashboardController`，`/overview` 只走新聚合方法，旧接口保留但标注兼容**

```java
@PostMapping("/overview")
public Result<DashboardOverviewDTO> getDashboardOverview(@Valid @RequestBody DashboardQueryRequest request) {
    return Result.success(dashboardService.getOverview(request));
}
```

- [ ] **Step 6: 给关键公开方法补充方法注释，说明职责边界**

```java
/**
 * 组装 Dashboard 总览。
 * 这里只负责编排，不直接关心 queryable sink 解析或具体 SQL 细节。
 */
public DashboardOverviewDTO getOverview(DashboardQueryRequest request) { ... }
```

- [ ] **Step 7: 运行后端编译与 Dashboard 相关测试/最小验证**

Run: `./mvnw -pl log-analysis-backend/log-analysis-app -DskipTests compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 8: 提交后端主链路重构**

```bash
git add log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard \
  log-analysis-backend/log-analysis-app/src/main/resources/mapper/DashboardMapper.xml
git commit -m "refactor: redesign dashboard overview backend"
```

### Task 3: 重构前端 Dashboard 数据模型与 Copilot 工作台 UI

**Files:**
- Create: `log-analysis-frontend/src/views/dashboard/components/DatasetContextBar.vue`
- Create: `log-analysis-frontend/src/views/dashboard/components/PlatformHealthGrid.vue`
- Create: `log-analysis-frontend/src/views/dashboard/components/LogKpiGrid.vue`
- Create: `log-analysis-frontend/src/views/dashboard/components/InsightPanel.vue`
- Create: `log-analysis-frontend/src/views/dashboard/components/EmptyDatasetState.vue`
- Modify: `log-analysis-frontend/src/views/dashboard/Index.vue`
- Modify: `log-analysis-frontend/src/views/dashboard/composables/useDashboardData.ts`
- Modify: `log-analysis-frontend/src/views/dashboard/types/index.ts`
- Modify: `log-analysis-frontend/src/api/dashboard.ts`
- Modify: `log-analysis-frontend/src/views/dashboard/components/LogTrendChart.vue`
- Modify: `log-analysis-frontend/src/views/dashboard/components/LevelDistributionChart.vue`

- [ ] **Step 1: 先重写前端类型，映射新的 `overview` 结构**

```ts
export interface DashboardOverview {
  datasetContext: DatasetContext | null
  platformHealth: PlatformHealth
  logKpis: LogKpis | null
  logTrend: TrendSeriesState
  severityDistribution: SeverityDistributionState
  topHosts: RankedListState
  topApps: RankedListState
  topErrorMessages: RankedListState
  recentHighRiskLogs: RecentLogsState
  emptyState: EmptyState | null
  warnings: DashboardWarning[]
}
```

- [ ] **Step 2: 重写 `useDashboardData.ts`，统一处理 `loading/ready/empty/error`**

```ts
const state = ref<DashboardOverview | null>(null)
const status = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')

const fetchOverview = async (timeRange: TimeRangeKey) => {
  status.value = 'loading'
  const response = await dashboardApi.getDashboardOverview(buildQuery(timeRange))
  state.value = normalizeOverview(response.data || response)
  status.value = 'ready'
}
```

- [ ] **Step 3: 用新 `Index.vue` 改成 Copilot 工作台布局，而不是旧大屏拼卡片**

```vue
<template>
  <AppLayout>
    <div class="dashboard-page">
      <DatasetContextBar ... />
      <PlatformHealthGrid ... />
      <LogKpiGrid v-if="hasDatasetData" ... />
      <div class="dashboard-main">
        <LogTrendChart ... />
        <LevelDistributionChart ... />
        <InsightPanel title="Top 主机" ... />
        <InsightPanel title="Top 应用" ... />
        <InsightPanel title="高频错误消息" ... />
        <RealtimeLogsTable ... />
      </div>
      <EmptyDatasetState v-if="showEmptyState" ... />
    </div>
  </AppLayout>
</template>
```

- [ ] **Step 4: 样式遵循现有主题变量，并补齐深色模式兼容**

```scss
.dashboard-page {
  color: var(--app-text-primary);
  background:
    radial-gradient(circle at top left, color-mix(in srgb, var(--el-color-primary) 14%, transparent), transparent 42%),
    linear-gradient(180deg, var(--app-bg-elevated) 0%, var(--app-bg-page) 100%);
}
```

- [ ] **Step 5: 统一图表/列表空态文案，不再把空数据展示成 0**

```ts
const emptyCopy = {
  noDataset: '暂无可统计日志数据集',
  noLogs: '当前数据集已接入，但所选时间范围内暂无日志',
  degraded: '部分指标暂不可用，已显示可用结果'
}
```

- [ ] **Step 6: 运行前端构建，修正 Dashboard 改造引入的类型问题**

Run: `npm run build --prefix log-analysis-frontend`
Expected: `vite build` 完成；若仍有仓库既有错误，只允许保留与本次 Dashboard 无关的旧错误并逐项记录

- [ ] **Step 7: 提交前端重构**

```bash
git add log-analysis-frontend/src/views/dashboard log-analysis-frontend/src/api/dashboard.ts
git commit -m "feat: redesign dashboard workspace ui"
```

### Task 4: 联调、验证与回归整理

**Files:**
- Modify: `docs/superpowers/specs/2026-05-29-dashboard-redesign-design.md`
- Modify: `docs/superpowers/plans/2026-05-29-dashboard-redesign-implementation.md`

- [ ] **Step 1: 跑一次后端编译，确认 Dashboard 后端可过**

Run: `./mvnw -pl log-analysis-backend/log-analysis-app -DskipTests compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 2: 跑一次前端构建，确认 Dashboard 页面可编译**

Run: `npm run build --prefix log-analysis-frontend`
Expected: `build` 成功；若失败，记录剩余失败点并修复至与 Dashboard 无关

- [ ] **Step 3: 如果本地应用可启动，用浏览器打开首页做目测验证**

Run: `npm run dev --prefix log-analysis-frontend`
Expected: 本地可访问 Dashboard 首页，浅色/深色模式都不出现布局错乱

- [ ] **Step 4: 回查设计规格，确认以下场景被覆盖**

```text
1. queryable sink 有数据
2. queryable sink 表不存在
3. 只有注册表候选
4. 完全没有日志数据集
5. 平台健康可用但日志业务为空
```

- [ ] **Step 5: 只在验证证据齐全后更新计划状态并准备合并**

```bash
git status --short
git log --oneline -n 5
```

Plan complete and saved to `docs/superpowers/plans/2026-05-29-dashboard-redesign-implementation.md`. Two execution options:

1. Subagent-Driven (recommended) - I dispatch a fresh subagent per task, review between tasks, fast iteration

2. Inline Execution - Execute tasks in this session using executing-plans, batch execution with checkpoints
