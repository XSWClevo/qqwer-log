<template>
  <el-card class="ai-query-result-card" shadow="hover">
    <!-- 头部：标题 + 操作按钮 -->
    <template #header>
      <div class="card-header">
        <div class="title">
          <el-icon :size="20"><DataAnalysis /></el-icon>
          <span>{{ resultTitle }}</span>
          <el-tag v-if="resultType" :type="resultTypeTag" size="small">
            {{ resultTypeLabel }}
          </el-tag>
        </div>
        <div class="actions">
          <el-button size="small" @click="showSql = !showSql">
            <el-icon><View /></el-icon>
            {{ showSql ? '隐藏' : '查看' }} SQL
          </el-button>
          <el-button size="small" @click="handleDownload">
            <el-icon><Download /></el-icon>
            导出
          </el-button>
        </div>
      </div>
    </template>

    <!-- SQL 展示（可折叠） -->
    <el-collapse-transition>
      <div v-show="showSql" class="sql-display">
        <div class="sql-header">
          <span>生成的 SQL</span>
          <el-button size="small" text @click="copySql">
            <el-icon><CopyDocument /></el-icon>
            复制
          </el-button>
        </div>
        <pre><code>{{ sql }}</code></pre>
      </div>
    </el-collapse-transition>

    <!-- 主内容区 -->
    <div class="result-content">
      <!-- 单值展示 -->
      <MetricCard
        v-if="resultType === 'metric'"
        :value="metricValue"
        :label="metricLabel"
      />

      <!-- 分类统计 -->
      <CategoryChart
        v-else-if="resultType === 'category'"
        :data="result"
      />

      <!-- 时序统计 -->
      <TimeSeriesChart
        v-else-if="resultType === 'timeseries'"
        :data="result"
      />

      <!-- 列表数据（使用父组件的表格） -->
      <div v-else-if="resultType === 'list'" class="list-hint">
        <el-icon :size="48"><Document /></el-icon>
        <p>列表数据已在下方表格中展示</p>
      </div>

      <!-- 未知类型 -->
      <div v-else class="unknown-type">
        <el-icon :size="48"><QuestionFilled /></el-icon>
        <p>无法识别的结果类型</p>
      </div>
    </div>

    <!-- 底部：统计信息 -->
    <template #footer>
      <div class="card-footer">
        <span v-if="executionTime">
          <el-icon><Timer /></el-icon>
          执行时间: {{ executionTime.toFixed(2) }}s
        </span>
        <span v-if="dataCount !== undefined">
          <el-icon><DataLine /></el-icon>
          数据行数: {{ dataCount }}
        </span>
      </div>
    </template>
  </el-card>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  DataAnalysis,
  View,
  Download,
  CopyDocument,
  Document,
  QuestionFilled,
  Timer,
  DataLine
} from '@element-plus/icons-vue'
import MetricCard from './MetricCard.vue'
import CategoryChart from './CategoryChart.vue'
import TimeSeriesChart from './TimeSeriesChart.vue'

interface Props {
  result: any
  sql?: string
  executionTime?: number
  resultType?: 'metric' | 'category' | 'timeseries' | 'list' | 'unknown'
}

const props = defineProps<Props>()

const showSql = ref(false)

// 结果标题
const resultTitle = computed(() => {
  switch (props.resultType) {
    case 'metric':
      return '统计结果'
    case 'category':
      return '分类统计'
    case 'timeseries':
      return '时序统计'
    case 'list':
      return '查询结果'
    default:
      return 'AI 查询结果'
  }
})

// 结果类型标签
const resultTypeTag = computed(() => {
  switch (props.resultType) {
    case 'metric':
      return 'success'
    case 'category':
      return 'warning'
    case 'timeseries':
      return 'primary'
    case 'list':
      return 'info'
    default:
      return ''
  }
})

const resultTypeLabel = computed(() => {
  switch (props.resultType) {
    case 'metric':
      return '单值'
    case 'category':
      return '聚合'
    case 'timeseries':
      return '时序'
    case 'list':
      return '列表'
    default:
      return '未知'
  }
})

// 数据行数
const dataCount = computed(() => {
  if (Array.isArray(props.result)) {
    return props.result.length
  } else if (typeof props.result === 'object' && props.result !== null) {
    return 1
  }
  return undefined
})

// 单值数据
const metricValue = computed(() => {
  if (typeof props.result === 'number') {
    return props.result
  } else if (typeof props.result === 'object' && props.result !== null && !Array.isArray(props.result)) {
    const keys = Object.keys(props.result)
    if (keys.length === 1 && keys[0]) {
      return props.result[keys[0]]
    }
  } else if (Array.isArray(props.result) && props.result.length === 1) {
    const keys = Object.keys(props.result[0])
    if (keys.length === 1 && keys[0]) {
      return props.result[0][keys[0]]
    }
  }
  return 0
})

const metricLabel = computed(() => {
  if (typeof props.result === 'object' && props.result !== null && !Array.isArray(props.result)) {
    const keys = Object.keys(props.result)
    if (keys.length === 1 && keys[0]) {
      return formatFieldLabel(keys[0])
    }
  } else if (Array.isArray(props.result) && props.result.length === 1) {
    const keys = Object.keys(props.result[0])
    if (keys.length === 1 && keys[0]) {
      return formatFieldLabel(keys[0])
    }
  }
  return '统计值'
})

// 格式化字段标签
const formatFieldLabel = (field: string): string => {
  return field
    .split('_')
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}

// 复制 SQL
const copySql = async () => {
  if (!props.sql) return

  try {
    await navigator.clipboard.writeText(props.sql)
    ElMessage.success('SQL 已复制到剪贴板')
  } catch (error) {
    ElMessage.error('复制失败')
  }
}

// 导出数据
const handleDownload = () => {
  // TODO: 实现数据导出功能
  ElMessage.info('导出功能开发中...')
}
</script>

<style scoped lang="scss">
.ai-query-result-card {
  margin-bottom: 16px;
  border-radius: 12px;
  border: 1px solid var(--el-border-color);

  :deep(.el-card__header) {
    padding: 16px 20px;
    background: var(--el-fill-color-light);
    border-bottom: 1px solid var(--el-border-color);
  }

  :deep(.el-card__body) {
    padding: 20px;
  }

  :deep(.el-card__footer) {
    padding: 12px 20px;
    background: var(--el-fill-color-lighter);
    border-top: 1px solid var(--el-border-color);
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 16px;
      font-weight: 600;
      color: var(--el-text-color-primary);

      .el-icon {
        color: var(--el-color-primary);
      }
    }

    .actions {
      display: flex;
      gap: 8px;
    }
  }

  .sql-display {
    margin-bottom: 20px;
    padding: 16px;
    background: var(--el-fill-color-darker);
    border-radius: 8px;

    .sql-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;
      font-size: 14px;
      font-weight: 500;
      color: var(--el-text-color-regular);
    }

    pre {
      margin: 0;
      padding: 12px;
      background: var(--el-bg-color);
      border-radius: 4px;
      overflow-x: auto;

      code {
        font-family: 'Monaco', 'Consolas', monospace;
        font-size: 13px;
        line-height: 1.6;
        color: var(--el-text-color-primary);
      }
    }
  }

  .result-content {
    min-height: 200px;

    .list-hint,
    .unknown-type {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 40px;
      color: var(--el-text-color-secondary);

      .el-icon {
        margin-bottom: 16px;
        color: var(--el-text-color-placeholder);
      }

      p {
        margin: 0;
        font-size: 14px;
      }
    }
  }

  .card-footer {
    display: flex;
    gap: 24px;
    font-size: 13px;
    color: var(--el-text-color-secondary);

    span {
      display: flex;
      align-items: center;
      gap: 4px;

      .el-icon {
        font-size: 14px;
      }
    }
  }
}
</style>
