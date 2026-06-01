<template>
  <section class="dataset-context-bar">
    <div class="dataset-summary">
      <div class="dataset-eyebrow">Copilot Workspace</div>
      <div class="dataset-title-row">
        <h1>日志运营总览</h1>
        <span class="status-pill" :class="statusTone">{{ statusText }}</span>
      </div>
      <div class="dataset-selector-row">
        <el-select
          v-model="currentDatasourceId"
          class="dataset-selector"
          placeholder="选择要查看的数据集"
          clearable
          :disabled="!availableDatasets.length"
        >
          <el-option
            v-for="item in availableDatasets"
            :key="item.datasourceId || item.tableName"
            :label="buildDatasetLabel(item)"
            :value="item.datasourceId"
          />
        </el-select>
        <p>{{ datasetDescription }}</p>
      </div>
    </div>

    <div class="dataset-meta-panel">
      <div class="meta-chip" :class="{ empty: !datasetContext }">
        <span>数据表</span>
        <strong>{{ datasetContext?.tableName || '暂无' }}</strong>
      </div>
      <div class="meta-chip" :class="{ empty: !datasetContext?.tableName }">
        <span>存储库</span>
        <strong>{{ datasetContext?.databaseName || 'default' }}</strong>
      </div>
      <div class="meta-chip">
        <span>最近日志</span>
        <strong>{{ datasetContext?.latestLogTime || '暂无' }}</strong>
      </div>
      <div class="meta-chip">
        <span>最近刷新</span>
        <strong>{{ lastUpdatedLabel }}</strong>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { DashboardDatasetContext } from '../types'

const props = defineProps<{
  datasetContext: DashboardDatasetContext | null
  availableDatasets: DashboardDatasetContext[]
  lastUpdatedLabel: string
  selectedDatasourceId?: string
}>()

const emit = defineEmits<{
  (event: 'dataset-change', datasourceId: string): void
}>()

const currentDatasourceId = computed({
  get: () => props.selectedDatasourceId || '',
  set: (value: string) => emit('dataset-change', value || '')
})

/**
 * 组合数据集下拉项标题，明确区分数据源与物理表。
 */
const buildDatasetLabel = (dataset: DashboardDatasetContext) => {
  const datasourceName = dataset.datasourceName || '未命名数据集'
  return dataset.tableName ? `${datasourceName} (${dataset.tableName})` : datasourceName
}

/**
 * 数据集说明文案跟随当前选中项变化。
 */
const datasetDescription = computed(() => {
  if (!props.availableDatasets.length) {
    return '当前还没有可查询日志数据集。'
  }
  if (!props.datasetContext) {
    return '选择一个数据集后，页面会按该表的字段映射重新加载统计。'
  }
  return `${props.datasetContext.datasourceName || '未命名数据集'} 正在展示 ${props.datasetContext.tableName || '未知表'} 的统计结果。`
})

const statusText = computed(() => {
  if (!props.datasetContext) {
    return '待接入'
  }
  if (props.datasetContext.hasData) {
    return '运行中'
  }
  if (props.datasetContext.status) {
    return props.datasetContext.status
  }
  return '无数据'
})

const statusTone = computed(() => {
  if (!props.datasetContext) {
    return 'muted'
  }
  return props.datasetContext.hasData ? 'ready' : 'warning'
})

</script>

<style scoped lang="scss">
.dataset-context-bar {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr);
  gap: 18px;
  padding: 22px 24px;
  border: 1px solid color-mix(in srgb, var(--macos-border) 78%, transparent);
  border-radius: 20px;
  background:
    radial-gradient(circle at top left, color-mix(in srgb, var(--macos-blue) 18%, transparent), transparent 32%),
    linear-gradient(180deg, color-mix(in srgb, var(--macos-card-bg) 86%, transparent), color-mix(in srgb, var(--macos-bg-secondary) 92%, transparent));
  box-shadow: 0 14px 36px rgba(15, 23, 42, 0.08);
}

.dataset-summary {
  display: flex;
  flex-direction: column;
  gap: 8px;
  justify-content: center;

  h1 {
    margin: 0;
    font-size: 26px;
    line-height: 1.1;
    color: var(--macos-text-primary);
  }

  p {
    margin: 0;
    max-width: 560px;
    font-size: 14px;
    line-height: 1.6;
    color: var(--macos-text-secondary);
  }
}

.dataset-selector-row {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.dataset-selector {
  width: min(420px, 100%);
}

.dataset-eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0;
  color: var(--macos-blue);
}

.dataset-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: var(--macos-text-primary);
  background: color-mix(in srgb, var(--macos-bg-secondary) 92%, transparent);

  &.ready {
    color: color-mix(in srgb, var(--macos-success) 80%, black);
    background: color-mix(in srgb, var(--macos-success) 16%, transparent);
  }

  &.warning {
    color: color-mix(in srgb, var(--macos-warning) 82%, black);
    background: color-mix(in srgb, var(--macos-warning) 16%, transparent);
  }

  &.muted {
    color: var(--macos-text-secondary);
  }
}

.dataset-meta-panel {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  align-content: start;
}

.meta-chip {
  display: flex;
  flex-direction: column;
  gap: 7px;
  min-height: 78px;
  padding: 14px 16px;
  border: 1px solid color-mix(in srgb, var(--macos-border) 76%, transparent);
  border-radius: 16px;
  background: color-mix(in srgb, var(--macos-card-bg) 82%, transparent);

  span {
    font-size: 12px;
    color: var(--macos-text-tertiary);
  }

  strong {
    font-size: 15px;
    color: var(--macos-text-primary);
    word-break: break-word;
  }

  &.empty strong {
    color: var(--macos-text-secondary);
  }
}

@media (max-width: 1180px) {
  .dataset-context-bar {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .dataset-context-bar {
    padding: 18px;
  }

  .dataset-selector {
    width: 100%;
  }

  .dataset-meta-panel {
    grid-template-columns: 1fr;
  }
}
</style>
