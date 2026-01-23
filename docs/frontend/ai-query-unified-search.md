# AI 查询统一搜索功能文档

## 更新日期
2026-01-22

## 功能概述

将 AI 查询和普通查询合并为统一的搜索体验，支持动态表头生成和智能结果展示。

## 主要改进

### 1. 统一搜索界面

**改进前**：
- 需要在"普通查询"和"AI查询"两种模式之间切换
- 两种模式使用不同的输入框
- 用户体验割裂

**改进后**：
- 单一搜索框，支持普通搜索和 AI 查询
- 通过"AI模式"按钮快速切换查询模式
- 搜索框右侧显示当前模式标识（AI 标签）

### 2. 动态表头生成

**问题**：
- AI 查询返回的字段可能与固定表头不匹配
- 聚合查询（如 COUNT、AVG）无法正确展示

**解决方案**：
- 根据查询结果动态生成表头
- 自动识别字段类型并设置合适的列宽
- 支持任意字段组合的展示

### 3. 智能结果识别

AI 查询结果会被自动识别为以下三种类型：

#### 类型 1：列表数据（List）
- **识别条件**：返回数组，且不是聚合结果
- **展示方式**：表格展示，动态生成表头
- **示例查询**：
  ```
  查询最近1小时内severity为error的日志
  ```

#### 类型 2：聚合数据（Aggregate）
- **识别条件**：返回对象，包含聚合函数字段（count、sum、avg、max、min等）
- **展示方式**：
  - 卡片形式的消息提示（显示统计结果）
  - 同时在表格中展示（单行数据）
- **示例查询**：
  ```
  统计最近24小时内各个severity的日志数量
  查询error日志的总数
  ```

#### 类型 3：单值结果（Single）
- **识别条件**：返回基本类型（数字、字符串等）
- **展示方式**：消息提示
- **示例查询**：
  ```
  查询日志总数
  ```

## 技术实现

### 核心函数

#### 1. `handleUnifiedSearch()`
统一搜索入口，根据 `isAiQuery` 状态决定调用普通搜索还是 AI 查询。

```typescript
const handleUnifiedSearch = () => {
  if (isAiQuery.value) {
    handleAiQuery()
  } else {
    handleMessageSearch()
  }
}
```

#### 2. `handleAiQueryResult(result)`
智能识别 AI 查询结果类型并处理。

```typescript
const handleAiQueryResult = (result: any) => {
  if (Array.isArray(result)) {
    if (result.length === 1 && isAggregateResult(result[0])) {
      // 聚合结果
      aiQueryResultType.value = 'aggregate'
      displayAggregateResult(result[0])
    } else {
      // 列表数据
      aiQueryResultType.value = 'list'
      logs.value = result
      total.value = result.length
      generateDynamicColumns(result[0])
    }
  } else if (typeof result === 'object' && result !== null) {
    // 单个对象
    if (isAggregateResult(result)) {
      aiQueryResultType.value = 'aggregate'
      displayAggregateResult(result)
    } else {
      aiQueryResultType.value = 'list'
      logs.value = [result]
      total.value = 1
      generateDynamicColumns(result)
    }
  } else {
    // 单值结果
    aiQueryResultType.value = 'single'
    ElMessage.info({ message: `查询结果: ${result}`, duration: 5000 })
  }
}
```

#### 3. `isAggregateResult(obj)`
判断是否是聚合结果。

```typescript
const isAggregateResult = (obj: any): boolean => {
  if (!obj || typeof obj !== 'object') return false

  const keys = Object.keys(obj)
  const aggregatePatterns = [
    /^count/i, /^sum/i, /^avg/i, /^max/i, /^min/i,
    /^total/i, /^average/i,
    /_count$/i, /_sum$/i, /_avg$/i
  ]

  return keys.some(key =>
    aggregatePatterns.some(pattern => pattern.test(key))
  )
}
```

#### 4. `generateDynamicColumns(sampleRow)`
根据查询结果动态生成表头。

```typescript
const generateDynamicColumns = (sampleRow: any) => {
  if (!sampleRow || typeof sampleRow !== 'object') return

  const fields = Object.keys(sampleRow)
  tableColumns.value = fields

  // 动态添加列定义
  fields.forEach(field => {
    if (!COLUMN_DEFS.value[field]) {
      COLUMN_DEFS.value[field] = {
        label: formatFieldLabel(field),
        width: 120
      }
    }
  })
}
```

#### 5. `formatFieldLabel(field)`
格式化字段标签（下划线转空格，首字母大写）。

```typescript
const formatFieldLabel = (field: string): string => {
  return field
    .split('_')
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}
```

### 数据结构变更

#### 新增状态变量

```typescript
const searchText = ref('') // 统一搜索文本
const isAiQuery = ref(false) // 是否使用AI查询模式
const aiQueryResultType = ref<'list' | 'aggregate' | 'single'>('list')
const aiQueryMetadata = ref<{ sql?: string; executionTime?: number }>({})
```

#### 移除的变量

```typescript
// 已移除
const messageSearchText = ref('')
const aiQueryText = ref('')
const queryMode = ref<'normal' | 'ai'>('normal')
```

#### 列定义改为响应式

```typescript
// 改进前（computed）
const COLUMN_DEFS = computed(() => { ... })

// 改进后（ref + 初始化函数）
const COLUMN_DEFS = ref<Record<string, { label: string; width?: number; minWidth?: number }>>({})
const initializeColumnDefs = () => { ... }
```

## 用户界面变更

### 搜索栏布局

```vue
<div class="main-search-row">
  <!-- 统一搜索输入框 -->
  <el-input
    v-model="searchText"
    placeholder="搜索日志内容或输入自然语言查询..."
    @keyup.enter="handleUnifiedSearch"
  >
    <template #suffix>
      <el-tag v-if="isAiQuery" type="warning" size="small">
        <el-icon><MagicStick /></el-icon>
        AI
      </el-tag>
    </template>
  </el-input>

  <!-- 时间范围选择 -->
  <el-select v-model="timeRange" />

  <!-- 查询按钮 -->
  <el-button type="primary" @click="handleUnifiedSearch">
    查询
  </el-button>

  <!-- AI模式切换按钮 -->
  <el-button :type="isAiQuery ? 'warning' : ''" @click="toggleAiQuery">
    <el-icon><MagicStick /></el-icon>
    {{ isAiQuery ? 'AI模式' : '普通模式' }}
  </el-button>
</div>
```

### 移除的UI元素

- 查询模式切换单选按钮组（普通查询/AI查询）
- 独立的 AI 查询文本域

## 使用示例

### 普通搜索

1. 确保处于"普通模式"（默认）
2. 在搜索框输入关键词：`error`
3. 点击"查询"按钮
4. 结果以表格形式展示，使用预定义的表头

### AI 查询 - 列表数据

1. 点击"AI模式"按钮切换到 AI 模式
2. 输入自然语言：`查询最近1小时内severity为error的日志，按时间倒序排列`
3. 点击"查询"按钮
4. 结果以表格形式展示，表头根据返回字段动态生成

### AI 查询 - 聚合数据

1. 处于 AI 模式
2. 输入自然语言：`统计最近24小时内各个severity的日志数量`
3. 点击"查询"按钮
4. 结果以两种方式展示：
   - 消息提示：显示统计结果摘要
   - 表格：展示详细数据（单行或多行）

### AI 查询 - 单值结果

1. 处于 AI 模式
2. 输入自然语言：`查询error日志的总数`
3. 点击"查询"按钮
4. 结果以消息提示形式展示

## 兼容性说明

### 向后兼容

- 普通查询功能完全保留，行为不变
- 高级筛选、字段统计等功能正常工作
- 导出、上下文查看等功能不受影响

### 数据源支持

- 支持所有已配置的数据源（ClickHouse、Elasticsearch、PostgreSQL等）
- 动态表头适配不同数据源的字段结构

## 性能优化

### 超时配置

已将前后端超时时间统一调整为 60 秒，以适配 AI 查询的长时间处理：

- **前端 Axios 超时**：60秒（`request.ts:8`）
- **后端 AI 服务超时**：60秒（`application-dev.yml:103`）
- **Tomcat 连接超时**：60秒（`application-dev.yml:3`）

### 动态表头缓存

- 列定义（`COLUMN_DEFS`）在数据源切换时重新初始化
- AI 查询结果的动态列定义会追加到现有定义中，避免重复计算

## 已知限制

1. **聚合结果识别**：基于字段名模式匹配，可能存在误判
2. **复杂嵌套结果**：暂不支持嵌套对象或数组字段的展示
3. **图表展示**：聚合结果暂时使用消息提示，未来可改为图表组件

## 后续优化方向

1. **智能模式切换**：根据输入内容自动判断是否使用 AI 查询
2. **结果可视化**：为聚合结果添加图表展示（柱状图、饼图等）
3. **查询历史**：保存 AI 查询历史，支持快速重用
4. **SQL 展示**：在界面上显示生成的 SQL，支持手动编辑
5. **结果导出**：支持导出 AI 查询的聚合结果

## 相关文件

### 前端文件
- `/log-analysis-frontend/src/views/log-search/Index.vue` - 主搜索页面
- `/log-analysis-frontend/src/api/log.ts` - API 接口定义
- `/log-analysis-frontend/src/utils/request.ts` - HTTP 请求配置

### 后端文件
- `/log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/stats/service/AiQueryService.java` - AI 查询服务
- `/log-analysis-backend/log-analysis-app/src/main/resources/application-dev.yml` - 配置文件

## 测试建议

### 测试场景

1. **普通搜索**：
   - 关键词搜索
   - 高级筛选
   - 时间范围筛选

2. **AI 查询 - 列表**：
   - 简单条件查询
   - 多条件组合查询
   - 排序查询

3. **AI 查询 - 聚合**：
   - COUNT 统计
   - GROUP BY 分组统计
   - 多维度聚合

4. **模式切换**：
   - 普通模式 → AI 模式
   - AI 模式 → 普通模式
   - 切换后查询结果正确性

5. **边界情况**：
   - 空查询
   - 超长查询
   - 特殊字符
   - 超时处理

## 总结

本次更新实现了 AI 查询和普通查询的统一，提升了用户体验，同时解决了 AI 查询结果展示的核心问题（动态表头）。通过智能结果识别，系统能够自动选择最合适的展示方式，为用户提供更加流畅的查询体验。
