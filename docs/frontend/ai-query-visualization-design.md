# AI 查询结果可视化设计方案

## 设计目标

为 AI 查询结果提供智能化的可视化展示，根据数据类型自动选择最佳展示方式，提升用户体验。

## 核心理念

**"数据说话，智能展示"** - 让数据以最直观的方式呈现给用户。

## 展示方式分类

### 1. 单值结果（Metric Card）

**适用场景**：
- 单个数值统计（如总数、平均值）
- 简单的聚合结果

**展示方式**：大号数据卡片

**示例查询**：
```
查询error日志的总数
计算最近1小时的平均响应时间
```

**UI 设计**：
```
┌─────────────────────────────────────┐
│  📊 Error 日志总数                   │
│                                     │
│         1,234                       │
│                                     │
│  ↑ 12% 较昨天                       │
└─────────────────────────────────────┘
```

---

### 2. 分类统计（Category Chart）

**适用场景**：
- 按维度分组统计（GROUP BY）
- 分类数据对比

**展示方式**：柱状图 + 饼图 + 表格（可切换）

**示例查询**：
```
统计最近24小时内各个severity的日志数量
按主机名统计日志分布
```

**UI 设计**：
```
┌─────────────────────────────────────────────────────────┐
│  📊 Severity 分布统计                                    │
│  ┌─────────────────────────────────────────────────┐   │
│  │  [柱状图] [饼图] [表格] [下载]                   │   │
│  └─────────────────────────────────────────────────┘   │
│                                                         │
│  ┌─────────────────────────────────────────────────┐   │
│  │                                                   │   │
│  │     ████████████  Error (45%)                    │   │
│  │     ██████        Warning (25%)                  │   │
│  │     ████          Info (20%)                     │   │
│  │     ██            Debug (10%)                    │   │
│  │                                                   │   │
│  └─────────────────────────────────────────────────┘   │
│                                                         │
│  详细数据表格 ▼                                         │
└─────────────────────────────────────────────────────────┘
```

---

### 3. 时序统计（Time Series Chart）

**适用场景**：
- 按时间维度统计
- 趋势分析

**展示方式**：折线图 + 面积图（可切换）

**示例查询**：
```
查询最近24小时内每小时的日志数量
统计error日志的时间分布趋势
```

**UI 设计**：
```
┌─────────────────────────────────────────────────────────┐
│  📈 日志数量趋势（最近24小时）                           │
│  ┌─────────────────────────────────────────────────┐   │
│  │  [折线图] [面积图] [柱状图] [下载]               │   │
│  └─────────────────────────────────────────────────┘   │
│                                                         │
│  ┌─────────────────────────────────────────────────┐   │
│  │  1000 ┤                                          │   │
│  │       │        ╱╲                                │   │
│  │  500  ┤    ╱╲╱  ╲╱╲                             │   │
│  │       │  ╱          ╲                            │   │
│  │  0    ┴──────────────────────────────────────   │   │
│  │       00:00  06:00  12:00  18:00  24:00         │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

---

### 4. 多维度统计（Multi-Dimension Chart）

**适用场景**：
- 多个维度组合统计
- 复杂的聚合查询

**展示方式**：堆叠柱状图 + 分组柱状图 + 热力图（可切换）

**示例查询**：
```
统计各个主机上不同severity的日志数量
按时间和severity统计日志分布
```

**UI 设计**：
```
┌─────────────────────────────────────────────────────────┐
│  📊 主机 × Severity 分布                                 │
│  ┌─────────────────────────────────────────────────┐   │
│  │  [堆叠柱状图] [分组柱状图] [热力图] [表格]       │   │
│  └─────────────────────────────────────────────────┘   │
│                                                         │
│  ┌─────────────────────────────────────────────────┐   │
│  │         ████ Error                                │   │
│  │         ████ Warning                              │   │
│  │  Host1  ████ Info                                 │   │
│  │         ████                                      │   │
│  │                                                   │   │
│  │         ████                                      │   │
│  │  Host2  ████                                      │   │
│  │         ████                                      │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

---

### 5. 列表数据（Data Table）

**适用场景**：
- 详细日志列表
- 非聚合查询

**展示方式**：表格（保持现有实现）

**示例查询**：
```
查询最近1小时内severity为error的日志
查询包含关键词的日志详情
```

---

## 智能识别规则

### 规则 1：单值识别

```typescript
function isSingleValue(result: any): boolean {
  // 1. 基本类型
  if (typeof result !== 'object') return true

  // 2. 对象只有一个字段
  if (Object.keys(result).length === 1) return true

  // 3. 数组只有一个元素且该元素只有一个字段
  if (Array.isArray(result) && result.length === 1 && Object.keys(result[0]).length === 1) {
    return true
  }

  return false
}
```

**示例**：
```javascript
// 单值
1234
{ "count": 1234 }
[{ "total": 1234 }]
```

---

### 规则 2：分类统计识别

```typescript
function isCategoryStats(result: any): boolean {
  if (!Array.isArray(result) || result.length === 0) return false

  const firstRow = result[0]
  const keys = Object.keys(firstRow)

  // 必须有 2 个字段：1个维度字段 + 1个聚合字段
  if (keys.length !== 2) return false

  // 检查是否有聚合字段
  const hasAggregateField = keys.some(key =>
    /count|sum|avg|max|min|total/i.test(key)
  )

  return hasAggregateField
}
```

**示例**：
```javascript
[
  { "severity": "error", "count": 1234 },
  { "severity": "warning", "count": 567 },
  { "severity": "info", "count": 890 }
]
```

---

### 规则 3：时序统计识别

```typescript
function isTimeSeriesStats(result: any): boolean {
  if (!Array.isArray(result) || result.length === 0) return false

  const firstRow = result[0]
  const keys = Object.keys(firstRow)

  // 必须有时间字段
  const hasTimeField = keys.some(key =>
    /time|date|timestamp|hour|day|month/i.test(key)
  )

  // 必须有聚合字段
  const hasAggregateField = keys.some(key =>
    /count|sum|avg|max|min|total/i.test(key)
  )

  return hasTimeField && hasAggregateField
}
```

**示例**：
```javascript
[
  { "hour": "2026-01-22 00:00", "count": 100 },
  { "hour": "2026-01-22 01:00", "count": 150 },
  { "hour": "2026-01-22 02:00", "count": 120 }
]
```

---

### 规则 4：多维度统计识别

```typescript
function isMultiDimensionStats(result: any): boolean {
  if (!Array.isArray(result) || result.length === 0) return false

  const firstRow = result[0]
  const keys = Object.keys(firstRow)

  // 必须有 3 个或更多字段
  if (keys.length < 3) return false

  // 至少有 2 个维度字段 + 1 个聚合字段
  const aggregateFields = keys.filter(key =>
    /count|sum|avg|max|min|total/i.test(key)
  )

  const dimensionFields = keys.length - aggregateFields.length

  return dimensionFields >= 2 && aggregateFields.length >= 1
}
```

**示例**：
```javascript
[
  { "hostname": "host1", "severity": "error", "count": 100 },
  { "hostname": "host1", "severity": "warning", "count": 50 },
  { "hostname": "host2", "severity": "error", "count": 80 }
]
```

---

## 图表类型推荐

### 柱状图（Bar Chart）

**适用场景**：
- 分类数据对比
- 数据量较少（< 20 个分类）

**优点**：
- 直观对比不同类别的数值
- 易于阅读

**示例**：Severity 分布、主机分布

---

### 饼图（Pie Chart）

**适用场景**：
- 占比展示
- 分类数量较少（< 8 个）

**优点**：
- 直观展示占比关系
- 适合展示部分与整体的关系

**示例**：日志级别占比、来源类型占比

---

### 折线图（Line Chart）

**适用场景**：
- 时序数据
- 趋势分析

**优点**：
- 清晰展示趋势变化
- 适合连续数据

**示例**：日志数量趋势、错误率变化

---

### 面积图（Area Chart）

**适用场景**：
- 时序数据
- 强调数量累积

**优点**：
- 视觉冲击力强
- 适合展示总量变化

**示例**：日志总量趋势

---

### 堆叠柱状图（Stacked Bar Chart）

**适用场景**：
- 多维度数据
- 展示部分与整体关系

**优点**：
- 同时展示总量和分类
- 适合对比不同维度的组成

**示例**：各主机上不同级别的日志分布

---

### 热力图（Heatmap）

**适用场景**：
- 多维度数据
- 数据量较大

**优点**：
- 直观展示数据密度
- 适合发现模式和异常

**示例**：时间 × 主机的日志分布

---

## UI 组件设计

### 1. AiQueryResultCard 组件

**功能**：智能展示 AI 查询结果

**Props**：
```typescript
interface AiQueryResultCardProps {
  result: any                    // 查询结果
  sql?: string                   // 生成的 SQL
  executionTime?: number         // 执行时间
  resultType?: 'auto' | 'metric' | 'category' | 'timeseries' | 'multi' | 'table'
}
```

**结构**：
```vue
<template>
  <el-card class="ai-query-result-card">
    <!-- 头部：标题 + 操作按钮 -->
    <template #header>
      <div class="card-header">
        <div class="title">
          <el-icon><DataAnalysis /></el-icon>
          <span>{{ resultTitle }}</span>
        </div>
        <div class="actions">
          <el-button-group v-if="showViewToggle">
            <el-button :type="viewMode === 'chart' ? 'primary' : ''" @click="viewMode = 'chart'">
              <el-icon><PieChart /></el-icon>
              图表
            </el-button>
            <el-button :type="viewMode === 'table' ? 'primary' : ''" @click="viewMode = 'table'">
              <el-icon><Grid /></el-icon>
              表格
            </el-button>
          </el-button-group>
          <el-button @click="downloadData">
            <el-icon><Download /></el-icon>
            导出
          </el-button>
          <el-button @click="showSql = !showSql">
            <el-icon><Code /></el-icon>
            SQL
          </el-button>
        </div>
      </div>
    </template>

    <!-- SQL 展示（可折叠） -->
    <el-collapse-transition>
      <div v-show="showSql" class="sql-display">
        <pre><code>{{ sql }}</code></pre>
      </div>
    </el-collapse-transition>

    <!-- 主内容区 -->
    <div class="result-content">
      <!-- 单值展示 -->
      <MetricCard v-if="resultType === 'metric'" :data="result" />

      <!-- 分类统计 -->
      <CategoryChart v-else-if="resultType === 'category'"
        :data="result"
        :view-mode="viewMode"
      />

      <!-- 时序统计 -->
      <TimeSeriesChart v-else-if="resultType === 'timeseries'"
        :data="result"
        :view-mode="viewMode"
      />

      <!-- 多维度统计 -->
      <MultiDimensionChart v-else-if="resultType === 'multi'"
        :data="result"
        :view-mode="viewMode"
      />

      <!-- 表格数据 -->
      <DataTable v-else :data="result" />
    </div>

    <!-- 底部：统计信息 -->
    <template #footer>
      <div class="card-footer">
        <span>执行时间: {{ executionTime }}s</span>
        <span>数据行数: {{ dataCount }}</span>
      </div>
    </template>
  </el-card>
</template>
```

---

### 2. MetricCard 组件

**功能**：展示单值统计结果

```vue
<template>
  <div class="metric-card">
    <div class="metric-icon">
      <el-icon :size="48"><TrendCharts /></el-icon>
    </div>
    <div class="metric-value">
      {{ formatNumber(value) }}
    </div>
    <div class="metric-label">
      {{ label }}
    </div>
    <div v-if="trend" class="metric-trend" :class="trendClass">
      <el-icon><CaretTop v-if="trend > 0" /><CaretBottom v-else /></el-icon>
      {{ Math.abs(trend) }}% 较昨天
    </div>
  </div>
</template>
```

---

### 3. CategoryChart 组件

**功能**：展示分类统计结果

```vue
<template>
  <div class="category-chart">
    <!-- 图表类型切换 -->
    <div class="chart-type-selector">
      <el-radio-group v-model="chartType" size="small">
        <el-radio-button value="bar">柱状图</el-radio-button>
        <el-radio-button value="pie">饼图</el-radio-button>
        <el-radio-button value="table">表格</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 图表展示 -->
    <div v-show="chartType !== 'table'" ref="chartRef" class="chart-container"></div>

    <!-- 表格展示 -->
    <el-table v-show="chartType === 'table'" :data="data" border>
      <el-table-column v-for="col in columns" :key="col.prop"
        :prop="col.prop"
        :label="col.label"
      />
    </el-table>
  </div>
</template>
```

---

### 4. TimeSeriesChart 组件

**功能**：展示时序统计结果

```vue
<template>
  <div class="timeseries-chart">
    <!-- 图表类型切换 -->
    <div class="chart-type-selector">
      <el-radio-group v-model="chartType" size="small">
        <el-radio-button value="line">折线图</el-radio-button>
        <el-radio-button value="area">面积图</el-radio-button>
        <el-radio-button value="bar">柱状图</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 图表展示 -->
    <div ref="chartRef" class="chart-container"></div>

    <!-- 数据摘要 -->
    <div class="data-summary">
      <div class="summary-item">
        <span class="label">最大值:</span>
        <span class="value">{{ maxValue }}</span>
      </div>
      <div class="summary-item">
        <span class="label">最小值:</span>
        <span class="value">{{ minValue }}</span>
      </div>
      <div class="summary-item">
        <span class="label">平均值:</span>
        <span class="value">{{ avgValue }}</span>
      </div>
    </div>
  </div>
</template>
```

---

## 交互设计

### 1. 自动展示

- AI 查询完成后，自动识别结果类型
- 根据结果类型选择最佳展示方式
- 默认显示图表视图（如果适用）

### 2. 视图切换

- 提供图表/表格切换按钮
- 切换时保持数据不变
- 记住用户的偏好设置

### 3. 图表交互

- **悬停提示**：显示详细数值
- **点击筛选**：点击图表元素可以筛选数据
- **缩放**：时序图支持缩放和平移
- **导出**：支持导出为图片或 Excel

### 4. SQL 展示

- 默认折叠，点击展开
- 语法高亮
- 支持复制

---

## 实现优先级

### P0（必须实现）

1. ✅ 智能结果类型识别
2. ✅ 单值卡片展示
3. ✅ 分类统计柱状图
4. ✅ 图表/表格切换

### P1（重要）

1. 分类统计饼图
2. 时序统计折线图
3. SQL 展示和复制
4. 数据导出

### P2（可选）

1. 多维度统计热力图
2. 图表交互（点击筛选）
3. 趋势对比（较昨天）
4. 自定义图表配置

---

## 技术实现

### 依赖库

- **ECharts**: 图表渲染（已有）
- **Element Plus**: UI 组件（已有）
- **Vue 3**: 框架（已有）

### 核心代码结构

```
src/
├── components/
│   └── ai-query-result/
│       ├── AiQueryResultCard.vue      # 主容器
│       ├── MetricCard.vue             # 单值卡片
│       ├── CategoryChart.vue          # 分类统计图表
│       ├── TimeSeriesChart.vue        # 时序统计图表
│       ├── MultiDimensionChart.vue    # 多维度统计图表
│       └── DataTable.vue              # 数据表格
├── utils/
│   └── chart-helper.ts                # 图表工具函数
└── types/
    └── ai-query.ts                    # 类型定义
```

---

## 示例效果

### 示例 1：单值统计

**查询**：`查询error日志的总数`

**展示**：
```
┌─────────────────────────────────┐
│  📊 Error 日志总数               │
│                                 │
│         1,234                   │
│                                 │
│  ↑ 12% 较昨天                   │
└─────────────────────────────────┘
```

---

### 示例 2：分类统计

**查询**：`统计各个severity的日志数量`

**展示**：
```
┌─────────────────────────────────────────┐
│  📊 Severity 分布统计                    │
│  [柱状图] [饼图] [表格]                  │
│                                         │
│  ████████████  Error (1234)             │
│  ██████        Warning (567)            │
│  ████          Info (890)               │
│                                         │
│  执行时间: 2.5s  |  数据行数: 3          │
└─────────────────────────────────────────┘
```

---

### 示例 3：时序统计

**查询**：`查询最近24小时内每小时的日志数量`

**展示**：
```
┌─────────────────────────────────────────┐
│  📈 日志数量趋势（最近24小时）           │
│  [折线图] [面积图] [柱状图]              │
│                                         │
│  1000 ┤        ╱╲                       │
│       │    ╱╲╱  ╲╱╲                     │
│  500  ┤  ╱          ╲                   │
│       │╱              ╲                 │
│  0    ┴──────────────────────────       │
│       00:00  06:00  12:00  18:00        │
│                                         │
│  最大值: 1000  |  最小值: 200  |  平均: 600 │
└─────────────────────────────────────────┘
```

---

## 总结

这个设计方案提供了：

1. **智能识别**：自动识别查询结果类型
2. **多样展示**：支持卡片、图表、表格等多种展示方式
3. **灵活切换**：用户可以自由切换视图
4. **交互友好**：提供丰富的交互功能
5. **可扩展性**：易于添加新的图表类型

这将大大提升 AI 查询的用户体验，让数据分析更加直观和高效！
