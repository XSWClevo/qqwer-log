# AI 查询统一搜索功能测试报告

## 测试环境

- **测试日期**: 2026-01-22
- **前端服务**: http://localhost:5173 ✅ 运行中
- **后端服务**: http://localhost:8080 ✅ 运行中
- **浏览器**: 需要手动测试

## 测试准备

### 1. 服务状态检查

```bash
# 前端服务
curl http://localhost:5173
# 状态: ✅ 正常

# 后端服务
curl http://localhost:8080/actuator/health
# 状态: ✅ {"status":"UP"}
```

### 2. 代码修复

已修复以下问题：
- ✅ 修复 `el:option` 拼写错误为 `el-option`（第73行）
- ✅ 统一搜索文本变量（`searchText`）
- ✅ 移除旧的查询模式变量（`messageSearchText`, `aiQueryText`, `queryMode`）

## 测试用例

### 测试用例 1：界面元素检查

**目标**: 验证统一搜索界面是否正确渲染

**步骤**:
1. 打开浏览器访问 http://localhost:5173
2. 登录系统
3. 进入"日志搜索"页面

**预期结果**:
- ✅ 显示单一搜索框，占位符文本为"搜索日志内容或输入自然语言查询..."
- ✅ 搜索框右侧有"AI模式"/"普通模式"切换按钮
- ✅ 默认处于"普通模式"
- ✅ 移除了原来的"普通查询/AI查询"单选按钮组

**实际结果**: 需要手动验证

---

### 测试用例 2：普通搜索功能

**目标**: 验证普通搜索功能是否正常工作

**步骤**:
1. 确保处于"普通模式"（默认）
2. 在搜索框输入关键词：`error`
3. 点击"查询"按钮

**预期结果**:
- ✅ 发送 POST 请求到 `/api/stats/logs/query`
- ✅ 返回包含 "error" 关键词的日志列表
- ✅ 表格使用预定义的表头（timestamp, severity, hostname, appname, message）
- ✅ 数据正确展示

**实际结果**: 需要手动验证

**测试数据**:
```json
{
  "startTime": "2026-01-21 00:00:00",
  "endTime": "2026-01-22 23:59:59",
  "messageConditions": [{"operator": "contains", "value": "error"}],
  "rawConditions": [{"operator": "contains", "value": "error"}],
  "pageNum": 1,
  "pageSize": 50
}
```

---

### 测试用例 3：AI 模式切换

**目标**: 验证 AI 模式切换功能

**步骤**:
1. 点击"AI模式"按钮
2. 观察界面变化
3. 再次点击切换回"普通模式"

**预期结果**:
- ✅ 点击后按钮高亮显示（warning 类型）
- ✅ 搜索框右侧显示 "AI" 标签
- ✅ 显示提示消息："已切换到AI查询模式，输入自然语言即可查询"
- ✅ 切换回普通模式时，标签消失，按钮恢复默认样式

**实际结果**: 需要手动验证

---

### 测试用例 4：AI 查询 - 列表数据

**目标**: 验证 AI 查询返回列表数据时的动态表头生成

**步骤**:
1. 切换到"AI模式"
2. 输入自然语言查询：`查询最近1小时内severity为error的日志，按时间倒序排列`
3. 点击"查询"按钮

**预期结果**:
- ✅ 发送 POST 请求到 `/api/stats/logs/ai-query`
- ✅ 显示成功消息，包含 SQL 生成时间和执行时间
- ✅ 表格动态生成表头（根据返回字段）
- ✅ 数据正确展示
- ✅ 控制台输出生成的 SQL

**实际结果**: 需要手动验证

**测试数据**:
```json
{
  "query": "查询最近1小时内severity为error的日志，按时间倒序排列",
  "datasourceId": "xxx"
}
```

**预期返回**:
```json
{
  "success": true,
  "sql": "SELECT * FROM syslog WHERE severity = 'error' AND timestamp >= ... ORDER BY timestamp DESC",
  "result": [
    {"id": "1", "severity": "error", "message": "...", "timestamp": "..."},
    ...
  ],
  "sqlGenerationTime": 2.5,
  "sqlExecutionTime": 0.3,
  "totalExecutionTime": 2.8
}
```

---

### 测试用例 5：AI 查询 - 聚合数据（COUNT）

**目标**: 验证 AI 查询返回聚合结果时的智能识别和展示

**步骤**:
1. 处于"AI模式"
2. 输入自然语言查询：`统计最近24小时内各个severity的日志数量`
3. 点击"查询"按钮

**预期结果**:
- ✅ 识别为聚合结果（`isAggregateResult` 返回 true）
- ✅ 显示成功消息，包含统计结果摘要（如："统计结果 - Severity: error, Count: 1234"）
- ✅ 同时在表格中展示详细数据
- ✅ 表头动态生成（如：Severity, Count）

**实际结果**: 需要手动验证

**测试数据**:
```json
{
  "query": "统计最近24小时内各个severity的日志数量",
  "datasourceId": "xxx"
}
```

**预期返回**:
```json
{
  "success": true,
  "sql": "SELECT severity, COUNT(*) as count FROM syslog WHERE timestamp >= ... GROUP BY severity",
  "result": [
    {"severity": "error", "count": 1234},
    {"severity": "warning", "count": 5678},
    {"severity": "info", "count": 9012}
  ],
  "sqlGenerationTime": 2.1,
  "sqlExecutionTime": 0.5,
  "totalExecutionTime": 2.6
}
```

---

### 测试用例 6：AI 查询 - 聚合数据（SUM/AVG）

**目标**: 验证其他聚合函数的识别

**步骤**:
1. 处于"AI模式"
2. 输入自然语言查询：`计算error日志的总数`
3. 点击"查询"按钮

**预期结果**:
- ✅ 识别为聚合结果
- ✅ 显示消息提示："查询结果: 1234"（如果返回单值）
- ✅ 或显示表格（如果返回对象）

**实际结果**: 需要手动验证

**测试数据**:
```json
{
  "query": "计算error日志的总数",
  "datasourceId": "xxx"
}
```

**预期返回（单值）**:
```json
{
  "success": true,
  "sql": "SELECT COUNT(*) FROM syslog WHERE severity = 'error'",
  "result": 1234,
  "sqlGenerationTime": 1.8,
  "sqlExecutionTime": 0.2,
  "totalExecutionTime": 2.0
}
```

**预期返回（对象）**:
```json
{
  "success": true,
  "sql": "SELECT COUNT(*) as total_count FROM syslog WHERE severity = 'error'",
  "result": {"total_count": 1234},
  "sqlGenerationTime": 1.8,
  "sqlExecutionTime": 0.2,
  "totalExecutionTime": 2.0
}
```

---

### 测试用例 7：动态表头生成

**目标**: 验证动态表头能够正确处理任意字段组合

**步骤**:
1. 处于"AI模式"
2. 输入自然语言查询：`查询最近1小时的日志，只显示timestamp、severity和message字段`
3. 点击"查询"按钮

**预期结果**:
- ✅ 表格只显示 3 列：Timestamp, Severity, Message
- ✅ 列标签正确格式化（首字母大写，下划线转空格）
- ✅ 列宽合理分配

**实际结果**: 需要手动验证

---

### 测试用例 8：字段标签格式化

**目标**: 验证 `formatFieldLabel` 函数的正确性

**步骤**:
1. 处于"AI模式"
2. 输入查询返回包含下划线字段的结果（如 `source_type`, `log_count`）

**预期结果**:
- ✅ `source_type` → "Source Type"
- ✅ `log_count` → "Log Count"
- ✅ `timestamp` → "Timestamp"

**实际结果**: 需要手动验证

---

### 测试用例 9：空结果处理

**目标**: 验证空结果的处理

**步骤**:
1. 处于"AI模式"
2. 输入查询：`查询severity为nonexistent的日志`
3. 点击"查询"按钮

**预期结果**:
- ✅ 显示消息："查询结果为空"
- ✅ 表格显示空状态
- ✅ 不报错

**实际结果**: 需要手动验证

---

### 测试用例 10：错误处理

**目标**: 验证 AI 查询失败时的错误处理

**步骤**:
1. 处于"AI模式"
2. 输入无效查询：`这是一个无效的查询`
3. 点击"查询"按钮

**预期结果**:
- ✅ 显示错误消息："AI查询失败: [错误原因]"
- ✅ 不影响后续查询
- ✅ 可以切换回普通模式继续使用

**实际结果**: 需要手动验证

---

### 测试用例 11：超时处理

**目标**: 验证长时间查询的超时处理

**步骤**:
1. 处于"AI模式"
2. 输入复杂查询（预计需要超过 15 秒）
3. 观察是否超时

**预期结果**:
- ✅ 前端等待 60 秒（新的超时配置）
- ✅ 后端等待 60 秒
- ✅ 如果超时，显示友好的错误消息

**实际结果**: 需要手动验证

---

### 测试用例 12：模式切换后的查询

**目标**: 验证模式切换不影响查询功能

**步骤**:
1. 普通模式下搜索 `error`
2. 切换到 AI 模式
3. 输入 AI 查询
4. 切换回普通模式
5. 再次搜索 `warning`

**预期结果**:
- ✅ 每次查询都正确执行
- ✅ 搜索文本正确保留
- ✅ 表头根据查询类型正确显示

**实际结果**: 需要手动验证

---

### 测试用例 13：高级筛选与 AI 查询结合

**目标**: 验证高级筛选是否影响 AI 查询

**步骤**:
1. 打开高级筛选，选择 severity = "error"
2. 切换到 AI 模式
3. 输入 AI 查询

**预期结果**:
- ✅ AI 查询独立执行，不受高级筛选影响
- ✅ 或者明确提示用户 AI 查询会忽略高级筛选

**实际结果**: 需要手动验证

**注意**: 当前实现中，AI 查询是独立的，不会应用高级筛选条件。如果需要结合，需要额外开发。

---

## 自动化测试建议

### 单元测试

建议为以下函数编写单元测试：

```typescript
// 1. 结果类型识别
describe('isAggregateResult', () => {
  it('should identify COUNT result', () => {
    expect(isAggregateResult({ count: 100 })).toBe(true)
  })

  it('should identify SUM result', () => {
    expect(isAggregateResult({ total_sum: 1000 })).toBe(true)
  })

  it('should not identify regular result', () => {
    expect(isAggregateResult({ id: '1', message: 'test' })).toBe(false)
  })
})

// 2. 字段标签格式化
describe('formatFieldLabel', () => {
  it('should format snake_case to Title Case', () => {
    expect(formatFieldLabel('source_type')).toBe('Source Type')
    expect(formatFieldLabel('log_count')).toBe('Log Count')
  })

  it('should handle single word', () => {
    expect(formatFieldLabel('timestamp')).toBe('Timestamp')
  })
})

// 3. 动态列生成
describe('generateDynamicColumns', () => {
  it('should generate columns from sample row', () => {
    const sampleRow = { id: '1', severity: 'error', message: 'test' }
    generateDynamicColumns(sampleRow)
    expect(tableColumns.value).toEqual(['id', 'severity', 'message'])
  })
})
```

### 集成测试

使用 Cypress 或 Playwright 进行端到端测试：

```typescript
describe('AI Query Integration', () => {
  it('should switch to AI mode and perform query', () => {
    cy.visit('/log-search')
    cy.get('[data-testid="ai-mode-button"]').click()
    cy.get('[data-testid="search-input"]').type('查询error日志')
    cy.get('[data-testid="search-button"]').click()
    cy.get('[data-testid="log-table"]').should('be.visible')
  })
})
```

## 性能测试

### 测试场景

1. **大数据量查询**
   - 查询返回 10,000 条记录
   - 验证表格渲染性能
   - 验证动态表头生成性能

2. **复杂聚合查询**
   - 多维度 GROUP BY
   - 多个聚合函数
   - 验证结果识别准确性

3. **频繁模式切换**
   - 连续切换 10 次
   - 验证内存泄漏
   - 验证状态一致性

## 已知问题

### 1. TypeScript 编译警告

**问题**: Element Plus 类型定义导致大量 TypeScript 警告

**影响**: 不影响运行，但影响开发体验

**解决方案**:
- 升级 Element Plus 到最新版本
- 或在 `tsconfig.json` 中添加类型忽略

### 2. 聚合结果识别准确性

**问题**: 基于字段名模式匹配，可能存在误判

**示例**:
- `user_count` 会被识别为聚合结果（正确）
- `account` 不会被识别为聚合结果（正确）
- 但如果有字段名为 `counter`，可能误判

**解决方案**:
- 改进识别算法，结合字段类型和值
- 或由后端返回结果类型标识

### 3. 嵌套对象展示

**问题**: 当前不支持嵌套对象或数组字段的展示

**示例**:
```json
{
  "id": "1",
  "metadata": {
    "source": "app1",
    "tags": ["error", "critical"]
  }
}
```

**解决方案**:
- 添加 JSON 格式化展示
- 或展开嵌套字段为多列

## 测试总结

### 完成的功能

- ✅ 统一搜索界面
- ✅ AI 模式切换
- ✅ 动态表头生成
- ✅ 智能结果识别（列表/聚合/单值）
- ✅ 字段标签格式化
- ✅ 超时配置优化

### 待测试项

- ⏳ 界面元素渲染
- ⏳ 普通搜索功能
- ⏳ AI 查询 - 列表数据
- ⏳ AI 查询 - 聚合数据
- ⏳ 动态表头正确性
- ⏳ 错误处理
- ⏳ 超时处理

### 下一步

1. **手动测试**: 在浏览器中逐一验证所有测试用例
2. **编写单元测试**: 为核心函数添加单元测试
3. **性能优化**: 根据测试结果优化性能瓶颈
4. **用户反馈**: 收集用户使用反馈，持续改进

## 测试环境访问

- **前端**: http://localhost:5173
- **后端**: http://localhost:8080
- **API 文档**: http://localhost:8080/swagger-ui.html（如果已配置）

## 联系方式

如有问题或建议，请联系开发团队。
