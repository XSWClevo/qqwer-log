# Dashboard Command Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将监控大屏改造成“科技指挥中心”风格的产品展示屏，支持动态指标卡、可下钻图表、按数据集能力自动编排视图，并避免字段差异导致的大面积空白。

**Architecture:** 后端在现有 `overview` 聚合接口上新增“能力模型、日志体积、指标下钻序列”三类数据；前端围绕“头图区、交互指标区、动态图表区、事件流”四层结构重组页面。视图不再写死，而是由当前数据集字段能力驱动选择图表组件和降级替代视图。

**Tech Stack:** Vue 3、TypeScript、Element Plus、ECharts、Spring Boot、ClickHouse、现有 dashboard overview 聚合链路

---

### Task 1: 定义能力模型与接口契约

**Files:**
- Create: `docs/superpowers/specs/2026-05-31-dashboard-command-center-design.md`
- Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard/dto/DashboardOverviewDTO.java`
- Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard/dto/DashboardLogKpisDTO.java`
- Create: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard/dto/DashboardCapabilityDTO.java`
- Create: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard/dto/DashboardMetricDrilldownDTO.java`
- Create: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard/dto/DashboardStorageVolumeDTO.java`
- Modify: `log-analysis-frontend/src/views/dashboard/types/index.ts`
- Test: `log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/dashboard/service/DashboardOverviewContractTest.java`

- [ ] **Step 1: 先补一条失败契约测试，锁定 overview 需要返回的新字段**

```java
@Test
void shouldExposeCapabilitiesAndDrilldownsInOverview() {
    DashboardOverviewDTO dto = dashboardService.getOverview(sampleRequest());

    assertThat(dto.getCapabilities()).isNotNull();
    assertThat(dto.getMetricDrilldowns()).isNotNull();
    assertThat(dto.getLogKpis()).isNotNull();
    assertThat(dto.getLogKpis().getStorageVolume()).isNotNull();
}
```

- [ ] **Step 2: 运行测试确认当前失败**

Run: `mvn -f log-analysis-backend/pom.xml -pl log-analysis-app -Dtest=DashboardOverviewContractTest test`
Expected: FAIL，提示 `getCapabilities`、`getMetricDrilldowns` 或 `storageVolume` 不存在

- [ ] **Step 3: 补 DTO 与类型定义，最小化让契约成立**

```java
@Data
@Builder
public class DashboardCapabilityDTO {
    private String key;
    private boolean supported;
    private String reason;
    private String fallbackView;
}
```

```ts
export interface DashboardCapability {
  key: string
  supported: boolean
  reason?: string
  fallbackView?: string
}
```

- [ ] **Step 4: 再跑契约测试确认通过**

Run: `mvn -f log-analysis-backend/pom.xml -pl log-analysis-app -Dtest=DashboardOverviewContractTest test`
Expected: PASS

- [ ] **Step 5: 提交这一小步**

```bash
git add docs/superpowers/specs/2026-05-31-dashboard-command-center-design.md \
  log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard/dto \
  log-analysis-frontend/src/views/dashboard/types/index.ts \
  log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/dashboard/service/DashboardOverviewContractTest.java
git commit -m "feat: add dashboard command center overview contract"
```

### Task 2: 后端补数据集能力识别与日志体积估算

**Files:**
- Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard/service/DashboardLogDatasetService.java`
- Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard/service/DashboardService.java`
- Create: `log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/dashboard/service/DashboardLogDatasetServiceTest.java`

- [ ] **Step 1: 写失败测试，覆盖缺主机/应用字段时的能力返回与体积估算**

```java
@Test
void shouldMarkEntityCapabilitiesUnsupportedWhenHostAndAppFieldsMissing() {
    DashboardDatasetContextDTO regexDataset = datasetWithMapping(Map.of(
        "timestamp", "timestamp",
        "severity", "level",
        "message", "message",
        "raw", "raw"
    ));

    DashboardLogDatasetSnapshot snapshot = service.buildSnapshot(regexDataset, sampleRequest(), new ArrayList<>());

    assertThat(snapshot.getCapabilities())
        .extracting(DashboardCapabilityDTO::getKey, DashboardCapabilityDTO::isSupported)
        .contains(tuple("top_hosts", false), tuple("top_apps", false));
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -f log-analysis-backend/pom.xml -pl log-analysis-app -Dtest=DashboardLogDatasetServiceTest test`
Expected: FAIL，当前 snapshot 不包含能力列表

- [ ] **Step 3: 实现能力推断与体积估算**

```java
private List<DashboardCapabilityDTO> buildCapabilities(Map<String, String> fieldMapping) {
    return List.of(
        capability("trend", hasColumnMapping(fieldMapping, "timestamp"), null, null),
        capability("severity_distribution", hasColumnMapping(fieldMapping, "severity"), "缺少 severity 字段", "message_density"),
        capability("top_hosts", hasColumnMapping(fieldMapping, "hostname"), "缺少 hostname 字段", "message_rank"),
        capability("top_apps", hasColumnMapping(fieldMapping, "appname"), "缺少 appname 字段", "message_rank")
    );
}
```

```java
private DashboardStorageVolumeDTO buildStorageVolume(...) {
    // v1: sum(lengthUTF8(raw)) 或 sum(lengthUTF8(message)) 估算
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `mvn -f log-analysis-backend/pom.xml -pl log-analysis-app -Dtest=DashboardLogDatasetServiceTest test`
Expected: PASS

- [ ] **Step 5: 提交这一小步**

```bash
git add log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard/service/DashboardLogDatasetService.java \
  log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard/service/DashboardService.java \
  log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/dashboard/service/DashboardLogDatasetServiceTest.java
git commit -m "feat: add dashboard capability detection and storage volume"
```

### Task 3: 后端补指标卡下钻序列

**Files:**
- Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard/service/DashboardLogDatasetService.java`
- Create: `log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/dashboard/service/DashboardMetricDrilldownTest.java`

- [ ] **Step 1: 写失败测试，锁定日志总量和错误率下钻序列**

```java
@Test
void shouldBuildMetricDrilldownsForCoreCards() {
    DashboardLogDatasetSnapshot snapshot = service.buildSnapshot(syslogDataset(), sampleRequest(), new ArrayList<>());

    assertThat(snapshot.getMetricDrilldowns()).extracting(DashboardMetricDrilldownDTO::getMetricKey)
        .contains("total_logs", "storage_volume", "error_rate", "critical_count");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -f log-analysis-backend/pom.xml -pl log-analysis-app -Dtest=DashboardMetricDrilldownTest test`
Expected: FAIL

- [ ] **Step 3: 实现最小下钻数据**

```java
private List<DashboardMetricDrilldownDTO> buildMetricDrilldowns(...) {
    return List.of(
        timeSeriesDrilldown("total_logs", totalLogSeries),
        timeSeriesDrilldown("storage_volume", storageVolumeSeries),
        timeSeriesDrilldown("error_rate", errorRateSeries),
        timeSeriesDrilldown("critical_count", criticalSeries)
    );
}
```

- [ ] **Step 4: 再跑测试确认通过**

Run: `mvn -f log-analysis-backend/pom.xml -pl log-analysis-app -Dtest=DashboardMetricDrilldownTest test`
Expected: PASS

- [ ] **Step 5: 提交这一小步**

```bash
git add log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/dashboard/service/DashboardLogDatasetService.java \
  log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/dashboard/service/DashboardMetricDrilldownTest.java
git commit -m "feat: add dashboard metric drilldown series"
```

### Task 4: 前端重构页面骨架为四层结构

**Files:**
- Create: `log-analysis-frontend/src/views/dashboard/components/CommandCenterHero.vue`
- Create: `log-analysis-frontend/src/views/dashboard/components/InteractiveMetricDeck.vue`
- Create: `log-analysis-frontend/src/views/dashboard/components/MetricDrilldownPanel.vue`
- Create: `log-analysis-frontend/src/views/dashboard/components/AdaptiveInsightBoard.vue`
- Modify: `log-analysis-frontend/src/views/dashboard/Index.vue`
- Modify: `log-analysis-frontend/src/views/dashboard/composables/useDashboardData.ts`
- Test: `log-analysis-frontend/src/views/dashboard/__tests__/Index.spec.ts`

- [ ] **Step 1: 先写一个前端失败测试，锁定新结构**

```ts
it('renders hero, metric deck and adaptive board', async () => {
  render(Index)
  expect(screen.getByText('日志运营总览')).toBeInTheDocument()
  expect(screen.getByText('科技指挥中心')).toBeInTheDocument()
  expect(screen.getByTestId('interactive-metric-deck')).toBeInTheDocument()
  expect(screen.getByTestId('adaptive-insight-board')).toBeInTheDocument()
})
```

- [ ] **Step 2: 运行测试确认失败**

Run: `npm run test -- src/views/dashboard/__tests__/Index.spec.ts`
Expected: FAIL

- [ ] **Step 3: 搭新骨架组件并替换旧布局**

```vue
<CommandCenterHero
  :dataset-context="workspace.datasetContext"
  :platform-metrics="workspace.platformMetrics"
  :primary-series="workspace.primaryTrend"
/>
<InteractiveMetricDeck
  data-testid="interactive-metric-deck"
  :metrics="workspace.logMetrics"
  :selected-metric-key="selectedMetricKey"
  @metric-select="handleMetricSelect"
/>
<MetricDrilldownPanel :drilldown="selectedMetricDrilldown" />
<AdaptiveInsightBoard
  data-testid="adaptive-insight-board"
  :views="workspace.insightViews"
/>
```

- [ ] **Step 4: 跑测试确认通过**

Run: `npm run test -- src/views/dashboard/__tests__/Index.spec.ts`
Expected: PASS

- [ ] **Step 5: 提交这一小步**

```bash
git add log-analysis-frontend/src/views/dashboard/Index.vue \
  log-analysis-frontend/src/views/dashboard/components/CommandCenterHero.vue \
  log-analysis-frontend/src/views/dashboard/components/InteractiveMetricDeck.vue \
  log-analysis-frontend/src/views/dashboard/components/MetricDrilldownPanel.vue \
  log-analysis-frontend/src/views/dashboard/components/AdaptiveInsightBoard.vue \
  log-analysis-frontend/src/views/dashboard/composables/useDashboardData.ts \
  log-analysis-frontend/src/views/dashboard/__tests__/Index.spec.ts
git commit -m "feat: restructure dashboard into command center layout"
```

### Task 5: 实现能力驱动的动态图表编排

**Files:**
- Create: `log-analysis-frontend/src/views/dashboard/composables/useDashboardCapabilityLayout.ts`
- Modify: `log-analysis-frontend/src/views/dashboard/composables/useDashboardData.ts`
- Modify: `log-analysis-frontend/src/views/dashboard/components/AdaptiveInsightBoard.vue`
- Modify: `log-analysis-frontend/src/views/dashboard/components/LogTrendChart.vue`
- Modify: `log-analysis-frontend/src/views/dashboard/components/LevelDistributionChart.vue`
- Test: `log-analysis-frontend/src/views/dashboard/__tests__/useDashboardCapabilityLayout.spec.ts`

- [ ] **Step 1: 写失败测试，锁定“字段缺失时自动替代视图”**

```ts
it('falls back to message rank when host capability is unsupported', () => {
  const views = buildDashboardLayout({
    capabilities: [{ key: 'top_hosts', supported: false, fallbackView: 'message_rank' }]
  })

  expect(views.some(view => view.key === 'message_rank')).toBe(true)
  expect(views.some(view => view.key === 'top_hosts')).toBe(false)
})
```

- [ ] **Step 2: 运行测试确认失败**

Run: `npm run test -- src/views/dashboard/__tests__/useDashboardCapabilityLayout.spec.ts`
Expected: FAIL

- [ ] **Step 3: 实现编排规则**

```ts
export function buildDashboardLayout(input: DashboardWorkspaceData): DashboardInsightView[] {
  const supported = new Map(input.capabilities.map(item => [item.key, item]))
  const views: DashboardInsightView[] = ['trend', 'severity_distribution'].flatMap(key => resolveView(key, supported))
  return ensureMinimumViews(views, supported)
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `npm run test -- src/views/dashboard/__tests__/useDashboardCapabilityLayout.spec.ts`
Expected: PASS

- [ ] **Step 5: 提交这一小步**

```bash
git add log-analysis-frontend/src/views/dashboard/composables/useDashboardCapabilityLayout.ts \
  log-analysis-frontend/src/views/dashboard/composables/useDashboardData.ts \
  log-analysis-frontend/src/views/dashboard/components/AdaptiveInsightBoard.vue \
  log-analysis-frontend/src/views/dashboard/components/LogTrendChart.vue \
  log-analysis-frontend/src/views/dashboard/components/LevelDistributionChart.vue \
  log-analysis-frontend/src/views/dashboard/__tests__/useDashboardCapabilityLayout.spec.ts
git commit -m "feat: add capability-driven dashboard layout"
```

### Task 6: 完成科技指挥中心视觉与动效

**Files:**
- Modify: `log-analysis-frontend/src/views/dashboard/Index.vue`
- Modify: `log-analysis-frontend/src/views/dashboard/components/CommandCenterHero.vue`
- Modify: `log-analysis-frontend/src/views/dashboard/components/InteractiveMetricDeck.vue`
- Modify: `log-analysis-frontend/src/views/dashboard/components/MetricDrilldownPanel.vue`
- Modify: `log-analysis-frontend/src/views/dashboard/components/RealtimeLogsTable.vue`
- Test: `log-analysis-frontend/src/views/dashboard/__tests__/theme-visual-regression.md`

- [ ] **Step 1: 先写一个视觉验收清单文件**

```md
- Hero 区必须有明显主舞台布局
- 指标卡 hover 有状态变化
- 深色主题无纯白突兀背景
- 空态、替代态和正常态视觉风格一致
```

- [ ] **Step 2: 实现视觉增强**

```scss
.dashboard-page {
  background:
    radial-gradient(circle at 20% 0%, rgba(24, 144, 255, 0.18), transparent 32%),
    radial-gradient(circle at 80% 10%, rgba(0, 214, 143, 0.12), transparent 28%),
    linear-gradient(180deg, var(--macos-bg-secondary), var(--macos-bg-primary));
}

.metric-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.16);
}
```

- [ ] **Step 3: 跑前端构建确认无类型和样式问题**

Run: `npm run build`
Expected: BUILD SUCCESS

- [ ] **Step 4: 用浏览器实测两类数据集**

Run:
`open http://localhost:5173/dashboard`

Expected:
- `syslog_logs` 有完整趋势和排行
- `regex_logs` 无大面积空白，实体视图被替代或优雅提示

- [ ] **Step 5: 提交这一小步**

```bash
git add log-analysis-frontend/src/views/dashboard \
  docs/superpowers/plans/2026-05-31-dashboard-command-center-implementation.md
git commit -m "feat: polish dashboard command center presentation"
```

### Task 7: 回归验证与文档收尾

**Files:**
- Modify: `docs/superpowers/specs/2026-05-31-dashboard-command-center-design.md`
- Modify: `docs/superpowers/plans/2026-05-31-dashboard-command-center-implementation.md`

- [ ] **Step 1: 跑后端验证**

Run: `mvn -f log-analysis-backend/pom.xml -pl log-analysis-app test`
Expected: PASS

- [ ] **Step 2: 跑前端验证**

Run: `npm run build`
Expected: BUILD SUCCESS

- [ ] **Step 3: 手工验证关键路径**

Run:
`curl -s -X POST http://localhost:8080/api/dashboard/overview -H 'Authorization: Bearer <token>' -H 'Content-Type: application/json' -d '{"startTime":"2026-05-31 21:00:00","endTime":"2026-05-31 22:30:00","granularity":"auto","pageNum":1,"pageSize":20,"datasourceId":"3e04f4f8392b83e2a7bfcdc1da0156e2"}' | jq '.'`

Expected:
- `capabilities` 存在
- `metricDrilldowns` 存在
- `storageVolume` 存在
- `regex_logs` 没有 page-level emptyState

- [ ] **Step 4: 更新文档中的实际结果**

```md
## Implementation Notes

- `regex_logs` 缺少 `hostname/appname` 时，首页使用替代视图
- 日志体积为近似估算值，不等同于 ClickHouse 物理存储占用
```

- [ ] **Step 5: 提交收尾**

```bash
git add docs/superpowers/specs/2026-05-31-dashboard-command-center-design.md \
  docs/superpowers/plans/2026-05-31-dashboard-command-center-implementation.md
git commit -m "docs: finalize dashboard command center rollout notes"
```
