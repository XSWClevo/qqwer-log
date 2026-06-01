<template>
  <section class="drilldown-shell">
    <header class="drilldown-header">
      <div>
        <span class="drilldown-eyebrow">指标下钻</span>
        <h3>{{ title }}</h3>
        <p>{{ description }}</p>
      </div>
      <div class="headline-value">
        <strong>{{ metric?.value || '--' }}</strong>
        <span>{{ metric?.label || '暂无指标' }}</span>
      </div>
    </header>

    <div class="drilldown-body">
      <div class="signal-strip">
        <div
          v-for="(point, index) in sparklinePoints"
          :key="`${selectedMetricKey}-${index}`"
          class="signal-bar"
          :style="{ height: `${point}%` }"
        ></div>
      </div>

      <div class="drilldown-side">
        <div class="side-block">
          <span>关联视图</span>
          <div class="chip-row">
            <em v-for="view in drilldown?.relatedViews || []" :key="view">{{ formatViewLabel(view) }}</em>
          </div>
        </div>

        <div class="side-block">
          <span>观测要点</span>
          <ul>
            <li v-for="item in highlights" :key="item">{{ item }}</li>
          </ul>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { DashboardMetricCard, DashboardMetricDrilldown, DashboardWorkspaceData } from '../types'

const props = defineProps<{
  metric?: DashboardMetricCard
  drilldown?: DashboardMetricDrilldown
  selectedMetricKey: string
  workspace: DashboardWorkspaceData
}>()

const title = computed(() => props.drilldown?.title || props.metric?.label || '指标下钻')
const description = computed(() => props.drilldown?.description || props.metric?.hint || '选择指标后查看当前时间窗的主信号。')
const highlights = computed(() => props.drilldown?.highlights?.length
  ? props.drilldown.highlights
  : [props.metric?.hint || '暂无补充说明']
)

const totalSeries = computed(() => props.workspace.logTrend.items.map(item => item.info + item.warn + item.error + item.fatal))
const errorRateSeries = computed(() => props.workspace.logTrend.items.map(item => {
  const total = item.info + item.warn + item.error + item.fatal
  return total > 0 ? ((item.error + item.fatal) / total) * 100 : 0
}))
const criticalSeries = computed(() => props.workspace.logTrend.items.map(item => item.fatal))
const storageSeries = computed(() => {
  const estimatedBytes = props.workspace.logKpis?.storageVolume?.value || 0
  const total = props.workspace.logKpis?.totalLogs || 0
  const avgBytes = total > 0 ? estimatedBytes / total : 0
  return totalSeries.value.map(value => value * avgBytes)
})

const sparklinePoints = computed(() => {
  const baseSeries = (() => {
    switch (props.selectedMetricKey) {
      case 'error-rate':
        return errorRateSeries.value
      case 'critical-count':
        return criticalSeries.value
      case 'storage-volume':
        return storageSeries.value
      default:
        return totalSeries.value
    }
  })()

  if (!baseSeries.length) {
    return [24, 34, 28, 48, 36, 52, 42, 62]
  }

  // 下钻图只表达走势，不逐点还原全部数据，避免长时间窗撑出横向滚动。
  const maxVisiblePoints = 24
  const step = Math.max(1, Math.ceil(baseSeries.length / maxVisiblePoints))
  const compactSeries = baseSeries.filter((_, index) => index % step === 0).slice(-maxVisiblePoints)
  const max = Math.max(...compactSeries, 1)
  return compactSeries.map(value => Math.max(12, Math.round((value / max) * 100)))
})

const formatViewLabel = (view: string) => {
  const labels: Record<string, string> = {
    trend: '流量趋势',
    severity: '级别分布',
    errors: '错误模式',
    'recent-logs': '风险日志',
    hosts: '主机排行',
    apps: '应用排行'
  }
  return labels[view] || view
}
</script>

<style scoped lang="scss">
.drilldown-shell {
  display: grid;
  gap: 18px;
  padding: 16px;
  border: 1px solid color-mix(in srgb, var(--macos-border) 72%, transparent);
  border-radius: 8px;
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--macos-card-bg) 90%, transparent), color-mix(in srgb, var(--macos-bg-secondary) 96%, transparent));
}

.drilldown-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;

  h3 {
    margin: 8px 0 0;
    font-size: 20px;
    color: var(--macos-text-primary);
  }

  p {
    margin: 8px 0 0;
    font-size: 13px;
    line-height: 1.7;
    color: var(--macos-text-secondary);
  }
}

.drilldown-eyebrow {
  font-size: 12px;
  letter-spacing: 0;
  color: color-mix(in srgb, var(--macos-text-secondary) 92%, transparent);
}

.headline-value {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;

  strong {
    font-size: 28px;
    color: var(--macos-text-primary);
    line-height: 1;
  }

  span {
    font-size: 12px;
    color: var(--macos-text-secondary);
  }
}

.drilldown-body {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(260px, 0.85fr);
  gap: 18px;
  align-items: stretch;
}

.signal-strip {
  display: flex;
  align-items: end;
  gap: 6px;
  min-height: 160px;
  padding: 14px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--macos-bg-secondary) 88%, transparent);
  overflow: hidden;
}

.signal-bar {
  flex: 1;
  min-width: 0;
  border-radius: 4px 4px 2px 2px;
  background: linear-gradient(180deg, color-mix(in srgb, var(--macos-blue) 88%, #14b8a6 12%), color-mix(in srgb, #14b8a6 72%, transparent));
  box-shadow: 0 10px 24px color-mix(in srgb, var(--macos-blue) 18%, transparent);
}

.drilldown-side {
  display: grid;
  gap: 14px;
}

.side-block {
  padding: 14px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--macos-bg-secondary) 90%, transparent);

  span {
    display: block;
    margin-bottom: 12px;
    font-size: 12px;
    letter-spacing: 0;
    color: color-mix(in srgb, var(--macos-text-secondary) 92%, transparent);
  }

  ul {
    margin: 0;
    padding-left: 18px;
    color: var(--macos-text-secondary);
    line-height: 1.7;
    font-size: 13px;
  }
}

.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;

  em {
    padding: 8px 10px;
    border-radius: 6px;
    font-style: normal;
    font-size: 12px;
    color: var(--macos-text-primary);
    background: color-mix(in srgb, var(--macos-card-bg) 92%, transparent);
  }
}

@media (max-width: 960px) {
  .drilldown-body {
    grid-template-columns: 1fr;
  }

  .headline-value {
    align-items: flex-start;
  }
}
</style>
