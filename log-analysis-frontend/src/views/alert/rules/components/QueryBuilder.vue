<template>
  <div class="query-builder">
    <!-- 查询示例 -->
    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="query-examples"
    >
      <template #title>
        <span style="font-weight: 600;">查询示例</span>
      </template>
      <div class="examples-list">
        <div
          v-for="example in examples"
          :key="example.label"
          class="example-item"
          @click="applyExample(example.query)"
        >
          <span class="example-label">{{ example.label }}:</span>
          <code class="example-code">{{ example.query }}</code>
        </div>
      </div>
    </el-alert>

    <!-- 可视化查询构建器 -->
    <div class="visual-builder">
      <div class="builder-header">
        <span class="header-title">可视化构建器</span>
        <el-button size="small" text @click="addCondition">
          <el-icon><Plus /></el-icon>
          添加条件
        </el-button>
      </div>

      <div v-if="conditions.length === 0" class="empty-state">
        <el-icon :size="40" color="#909399"><Filter /></el-icon>
        <p>点击"添加条件"开始构建查询</p>
      </div>

      <div v-else class="conditions-list">
        <div
          v-for="(condition, index) in conditions"
          :key="index"
          class="condition-row"
        >
          <el-select
            v-model="condition.field"
            placeholder="选择字段"
            style="width: 150px"
            @change="updateQuery"
          >
            <el-option
              v-for="field in availableFields"
              :key="field.value"
              :label="field.label"
              :value="field.value"
            >
              <span>{{ field.label }}</span>
              <span style="color: #8c8c8c; font-size: 12px; margin-left: 8px;">
                {{ field.type }}
              </span>
            </el-option>
          </el-select>

          <el-select
            v-model="condition.operator"
            style="width: 120px"
            @change="updateQuery"
          >
            <el-option label="等于" value="=" />
            <el-option label="不等于" value="!=" />
            <el-option label="包含" value="LIKE" />
            <el-option label="不包含" value="NOT LIKE" />
            <el-option label="大于" value=">" />
            <el-option label="大于等于" value=">=" />
            <el-option label="小于" value="<" />
            <el-option label="小于等于" value="<=" />
          </el-select>

          <el-input
            v-model="condition.value"
            placeholder="输入值"
            style="flex: 1"
            @input="updateQuery"
          >
            <template v-if="condition.operator === 'LIKE' || condition.operator === 'NOT LIKE'" #prepend>
              %
            </template>
            <template v-if="condition.operator === 'LIKE' || condition.operator === 'NOT LIKE'" #append>
              %
            </template>
          </el-input>

          <el-button
            type="danger"
            text
            @click="removeCondition(index)"
          >
            <el-icon><Delete /></el-icon>
          </el-button>

          <el-select
            v-if="index < conditions.length - 1"
            v-model="condition.logic"
            style="width: 80px; margin-left: 8px"
            @change="updateQuery"
          >
            <el-option label="AND" value="AND" />
            <el-option label="OR" value="OR" />
          </el-select>
        </div>
      </div>
    </div>

    <!-- 生成的查询语句 -->
    <div class="generated-query">
      <div class="query-header">
        <span class="header-title">生成的查询语句</span>
        <el-button size="small" text @click="copyQuery">
          <el-icon><CopyDocument /></el-icon>
          复制
        </el-button>
      </div>
      <el-input
        :model-value="generatedQuery"
        type="textarea"
        :rows="4"
        readonly
        class="query-textarea"
        @input="handleManualEdit"
      />
    </div>

    <!-- 可用字段说明 -->
    <el-collapse class="field-docs">
      <el-collapse-item title="可用字段说明" name="fields">
        <el-table :data="availableFields" size="small" :show-header="true">
          <el-table-column prop="label" label="字段名" width="120" />
          <el-table-column prop="value" label="数据库字段" width="140">
            <template #default="{ row }">
              <code>{{ row.value }}</code>
            </template>
          </el-table-column>
          <el-table-column prop="type" label="类型" width="100" />
          <el-table-column prop="description" label="说明" />
        </el-table>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Delete, Filter, CopyDocument } from '@element-plus/icons-vue'

interface Condition {
  field: string
  operator: string
  value: string
  logic: 'AND' | 'OR'
}

const props = defineProps<{
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

// 可用字段定义
const availableFields = [
  { label: '日志级别', value: 'severity', type: 'String', description: 'DEBUG, INFO, WARN, ERROR, CRITICAL' },
  { label: '主机名', value: 'hostname', type: 'String', description: '日志来源主机' },
  { label: '应用名', value: 'appname', type: 'String', description: '应用或服务名称' },
  { label: '来源类型', value: 'source_type', type: 'String', description: '日志来源类型' },
  { label: '消息内容', value: 'message', type: 'String', description: '日志消息文本' },
  { label: '设施类型', value: 'facility', type: 'String', description: 'Syslog 设施类型' },
  { label: '进程ID', value: 'procid', type: 'String', description: '进程标识符' },
  { label: '源IP', value: 'source_ip', type: 'String', description: '日志来源IP地址' },
  { label: '原始日志', value: 'raw', type: 'String', description: '完整的原始日志' }
]

// 查询示例
const examples = [
  { label: '错误日志', query: "severity = 'ERROR'" },
  { label: '特定应用', query: "appname = 'payment'" },
  { label: '消息匹配', query: "message LIKE '%timeout%'" },
  { label: '组合条件', query: "severity = 'ERROR' AND appname = 'payment'" },
  { label: '多条件OR', query: "severity = 'ERROR' OR severity = 'CRITICAL'" },
  { label: '排除条件', query: "appname != 'test' AND message LIKE '%failed%'" }
]

// 条件列表
const conditions = ref<Condition[]>([])

// 生成的查询语句
const generatedQuery = computed(() => {
  if (conditions.value.length === 0) {
    return ''
  }

  return conditions.value
    .map((condition, index) => {
      let query = ''
      
      // 构建条件
      if (condition.operator === 'LIKE' || condition.operator === 'NOT LIKE') {
        query = `${condition.field} ${condition.operator} '%${condition.value}%'`
      } else {
        const needsQuotes = !['>', '>=', '<', '<='].includes(condition.operator)
        const value = needsQuotes ? `'${condition.value}'` : condition.value
        query = `${condition.field} ${condition.operator} ${value}`
      }

      // 添加逻辑运算符
      if (index < conditions.value.length - 1) {
        query += ` ${condition.logic} `
      }

      return query
    })
    .join('')
})

// 监听生成的查询，同步到父组件
watch(generatedQuery, (newQuery) => {
  emit('update:modelValue', newQuery)
})

// 监听父组件的值变化
watch(() => props.modelValue, (newValue) => {
  if (newValue && newValue !== generatedQuery.value) {
    // 如果是手动编辑，清空可视化构建器
    // conditions.value = []
  }
})

// 添加条件
const addCondition = () => {
  conditions.value.push({
    field: 'severity',
    operator: '=',
    value: '',
    logic: 'AND'
  })
}

// 移除条件
const removeCondition = (index: number) => {
  conditions.value.splice(index, 1)
  updateQuery()
}

// 更新查询
const updateQuery = () => {
  // 触发 computed 重新计算
}

// 应用示例
const applyExample = (query: string) => {
  emit('update:modelValue', query)
  ElMessage.success('已应用示例查询')
}

// 复制查询
const copyQuery = () => {
  if (!generatedQuery.value) {
    ElMessage.warning('查询语句为空')
    return
  }
  
  navigator.clipboard.writeText(generatedQuery.value).then(() => {
    ElMessage.success('已复制到剪贴板')
  }).catch(() => {
    ElMessage.error('复制失败')
  })
}

// 手动编辑
const handleManualEdit = (value: string) => {
  emit('update:modelValue', value)
}
</script>

<style scoped lang="scss">
.query-builder {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.query-examples {
  :deep(.el-alert__content) {
    width: 100%;
  }

  .examples-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
    margin-top: 8px;
  }

  .example-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 12px;
    background: var(--macos-fill-secondary);
    border-radius: 4px;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      background: var(--macos-bg-tertiary);
    }

    .example-label {
      font-size: 13px;
      color: var(--macos-text-secondary);
      font-weight: 500;
      min-width: 80px;
    }

    .example-code {
      flex: 1;
      font-family: 'Monaco', 'Courier New', monospace;
      font-size: 12px;
      color: var(--macos-blue);
      background: var(--macos-info-bg);
      padding: 2px 8px;
      border-radius: 3px;
    }
  }
}

.visual-builder {
  border: 1px solid var(--macos-border);
  border-radius: 4px;
  padding: 16px;

  .builder-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;

    .header-title {
      font-size: 14px;
      font-weight: 600;
      color: var(--macos-text-primary);
    }
  }

  .empty-state {
    text-align: center;
    padding: 40px 0;
    color: var(--macos-text-tertiary);

    p {
      margin-top: 12px;
      font-size: 14px;
    }
  }

  .conditions-list {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .condition-row {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

.generated-query {
  .query-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;

    .header-title {
      font-size: 14px;
      font-weight: 600;
      color: var(--macos-text-primary);
    }
  }

  .query-textarea {
    :deep(textarea) {
      font-family: 'Monaco', 'Courier New', monospace;
      font-size: 13px;
      background: var(--macos-fill-secondary);
    }
  }
}

.field-docs {
  :deep(.el-collapse-item__header) {
    font-size: 14px;
    font-weight: 600;
  }

  :deep(.el-table) {
    font-size: 13px;

    code {
      background: var(--macos-fill-secondary);
      padding: 2px 6px;
      border-radius: 3px;
      color: var(--macos-blue);
      font-size: 12px;
    }
  }
}
</style>
