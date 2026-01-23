# AI 查询图表不显示问题调试指南

## 问题描述

前端执行 AI 查询后，没有看到图表展示。

## 已修复的问题

1. ✅ 修复了结果类型不匹配问题：
   - 原来：`'aggregate'` 和 `'single'`
   - 现在：`'metric'` 和 `'category'`

2. ✅ 添加了调试日志和信息面板

## 调试步骤

### 步骤 1：刷新浏览器

清除缓存并刷新：
```
Ctrl + Shift + R (Windows/Linux)
Cmd + Shift + R (Mac)
```

### 步骤 2：打开浏览器控制台

按 `F12` 打开开发者工具，切换到 Console 标签。

### 步骤 3：执行 AI 查询

1. 切换到 AI 模式
2. 输入查询：`统计各个severity的日志数量`
3. 点击"查询"按钮

### 步骤 4：查看控制台输出

应该看到类似以下的调试信息：

```javascript
=== AI 查询结果调试 ===
原始结果: [
  { severity: 'error', count: 1234 },
  { severity: 'warning', count: 567 },
  { severity: 'info', count: 890 }
]
结果类型: object
是否数组: true
识别的结果类型: category
SQL: SELECT severity, COUNT(*) as count FROM syslog ...
logs.value: [...]
========================
```

### 步骤 5：查看页面上的调试信息

在日志列表上方应该看到一个蓝色的调试信息框：

```
调试信息：
SQL存在: true, 结果类型: category, 显示条件: true
```

### 步骤 6：检查图表是否显示

如果调试信息显示 `显示条件: true`，但仍然没有看到图表，请检查：

1. **浏览器控制台是否有错误**
   - 查看是否有组件加载错误
   - 查看是否有 ECharts 错误

2. **网络请求是否成功**
   - 切换到 Network 标签
   - 查看 `/api/stats/logs/ai-query` 请求
   - 检查响应状态和数据

3. **组件是否正确导入**
   - 查看是否有 `Cannot find module` 错误
   - 查看是否有图标导入错误

## 常见问题排查

### 问题 1：显示条件为 false

**症状**：
```
显示条件: false
```

**可能原因**：
1. `aiQueryMetadata.sql` 为空
2. `aiQueryResultType` 不是 `'metric'` 或 `'category'`

**解决方案**：
- 检查控制台输出的 `识别的结果类型`
- 检查 SQL 是否正确保存

---

### 问题 2：结果类型识别错误

**症状**：
```
识别的结果类型: list
```
但实际应该是 `category`

**可能原因**：
- `isAggregateResult` 函数没有正确识别聚合字段

**解决方案**：
在控制台手动测试：
```javascript
const result = [
  { severity: 'error', count: 1234 }
]

// 测试识别函数
const keys = Object.keys(result[0])
const hasAggregateField = keys.some(key =>
  /count|sum|avg|max|min|total/i.test(key)
)
console.log('包含聚合字段:', hasAggregateField)
```

---

### 问题 3：组件未渲染

**症状**：
- 显示条件为 `true`
- 但页面上没有图表

**可能原因**：
1. 组件导入失败
2. 组件内部错误
3. CSS 样式问题

**解决方案**：

1. 检查组件导入：
```javascript
// 在控制台执行
console.log('AiQueryResultCard 组件:', AiQueryResultCard)
```

2. 检查组件是否在 DOM 中：
```javascript
// 在控制台执行
document.querySelector('.ai-query-result-card')
```

3. 检查是否有 CSS 隐藏：
```javascript
// 在控制台执行
const card = document.querySelector('.ai-query-result-card')
if (card) {
  console.log('display:', getComputedStyle(card).display)
  console.log('visibility:', getComputedStyle(card).visibility)
}
```

---

### 问题 4：ECharts 图表不显示

**症状**：
- 卡片显示了
- 但图表区域是空白的

**可能原因**：
1. ECharts 初始化失败
2. 容器高度为 0
3. 数据格式不正确

**解决方案**：

1. 检查容器高度：
```javascript
const chartContainer = document.querySelector('.chart-container')
if (chartContainer) {
  console.log('容器高度:', chartContainer.offsetHeight)
}
```

2. 检查 ECharts 实例：
```javascript
// 在 CategoryChart 组件中添加
console.log('ECharts 实例:', chartInstance)
console.log('图表配置:', option)
```

---

## 手动测试

如果自动识别有问题，可以手动测试组件：

### 测试 MetricCard

在浏览器控制台执行：
```javascript
// 找到 Vue 实例
const app = document.querySelector('#app').__vueParentComponent
console.log('Vue 实例:', app)
```

### 测试数据格式

```javascript
// 测试单值数据
const singleValue = { count: 1234 }
console.log('单值测试:', isAggregateResult(singleValue))

// 测试分类统计
const categoryData = [
  { severity: 'error', count: 1234 },
  { severity: 'warning', count: 567 }
]
console.log('分类统计测试:', isAggregateResult(categoryData[0]))
```

---

## 临时解决方案

如果图表仍然不显示，可以尝试以下临时方案：

### 方案 1：强制显示

修改显示条件，移除类型限制：
```vue
<AiQueryResultCard
  v-if="aiQueryMetadata.sql"
  :result="logs"
  :sql="aiQueryMetadata.sql"
  :execution-time="aiQueryMetadata.executionTime"
  :result-type="aiQueryResultType"
/>
```

### 方案 2：使用固定测试数据

在组件中使用固定数据测试：
```vue
<AiQueryResultCard
  :result="[
    { severity: 'error', count: 1234 },
    { severity: 'warning', count: 567 }
  ]"
  sql="SELECT severity, COUNT(*) as count FROM syslog GROUP BY severity"
  :execution-time="2.5"
  result-type="category"
/>
```

---

## 下一步

完成调试后，请告诉我：

1. **控制台输出的内容**：
   - 原始结果是什么？
   - 识别的结果类型是什么？
   - 显示条件是 true 还是 false？

2. **页面上的调试信息**：
   - SQL存在吗？
   - 结果类型是什么？
   - 显示条件是什么？

3. **是否有错误信息**：
   - 控制台是否有红色错误？
   - 网络请求是否成功？

4. **实际看到的效果**：
   - 是否看到调试信息框？
   - 是否看到图表卡片？
   - 表格是否正常显示？

根据这些信息，我可以进一步帮你定位和解决问题。

---

## 联系方式

如果问题仍然存在，请提供：
- 截图（包括控制台和页面）
- 控制台完整输出
- 网络请求的响应数据

我会立即帮你解决！
