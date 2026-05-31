<template>
  <AppLayout>
    <div class="dashboard-page">
      <section class="topbar">
        <div class="topbar-group">
          <el-select v-model="timeRange" class="time-selector" @change="handleTimeRangeChange">
            <el-option label="最近 1 小时" value="1h" />
            <el-option label="最近 6 小时" value="6h" />
            <el-option label="最近 24 小时" value="24h" />
            <el-option label="最近 7 天" value="7d" />
          </el-select>

          <div class="refresh-controls">
            <span>自动刷新</span>
            <el-switch v-model="autoRefresh" @change="handleAutoRefreshChange" />
            <el-select
              v-if="autoRefresh"
              v-model="refreshInterval"
              class="interval-selector"
              @change="handleIntervalChange"
            >
              <el-option label="5s" :value="5000" />
              <el-option label="10s" :value="10000" />
              <el-option label="30s" :value="30000" />
              <el-option label="60s" :value="60000" />
            </el-select>
          </div>
        </div>

        <div class="topbar-group">
          <el-button plain @click="fetchAllData(timeRange)">立即刷新</el-button>
        </div>
      </section>

      <CommandCenterHero
        :dataset-context="workspace.datasetContext"
        :platform-metrics="workspace.platformMetrics"
        :last-updated-label="workspace.lastUpdatedLabel"
      />

      <DatasetContextBar
        :dataset-context="workspace.datasetContext"
        :available-datasets="workspace.availableDatasets"
        :last-updated-label="workspace.lastUpdatedLabel"
        :selected-datasource-id="selectedDatasourceId"
        @dataset-change="handleDatasetChange"
      />

      <section v-if="workspace.warnings.length" class="warning-stack">
        <el-alert
          v-for="(warning, index) in workspace.warnings"
          :key="`${warning.scope || 'warning'}-${index}`"
          :title="warning.message"
          :type="warning.level === 'error' ? 'error' : 'warning'"
          :closable="false"
          show-icon
        />
      </section>

      <InteractiveMetricDeck
        :metrics="workspace.logMetrics.length ? workspace.logMetrics : workspace.platformMetrics"
        :selected-metric-key="selectedMetricKey"
        @metric-select="handleMetricSelect"
      />

      <MetricDrilldownPanel
        :metric="selectedMetric"
        :drilldown="selectedMetricDrilldown"
        :selected-metric-key="selectedMetricKey"
        :workspace="workspace"
      />

      <EmptyDatasetState
        v-if="workspace.emptyState"
        :title="workspace.emptyState.title"
        :description="workspace.emptyState.description"
        :action-label="workspace.emptyState.actionLabel"
        :action-route="workspace.emptyState.actionRoute"
      />

      <AdaptiveInsightBoard
        v-else
        :views="insightTiles"
        :workspace="workspace"
      />

      <RealtimeLogsTable
        :data="workspace.recentLogs.items"
        :loading="loading"
        @row-click="handleLogRowClick"
      />

      <LogDetailDialog v-model:visible="logDetailVisible" :log="selectedLog" />
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import AppLayout from '@/components/layout/AppLayout.vue'
import DatasetContextBar from './components/DatasetContextBar.vue'
import EmptyDatasetState from './components/EmptyDatasetState.vue'
import RealtimeLogsTable from './components/RealtimeLogsTable.vue'
import LogDetailDialog from './components/LogDetailDialog.vue'
import CommandCenterHero from './components/CommandCenterHero.vue'
import InteractiveMetricDeck from './components/InteractiveMetricDeck.vue'
import MetricDrilldownPanel from './components/MetricDrilldownPanel.vue'
import AdaptiveInsightBoard from './components/AdaptiveInsightBoard.vue'
import { useDashboardData } from './composables/useDashboardData'
import { buildDashboardLayout } from './composables/useDashboardCapabilityLayout'
import type { DashboardMetricCard, LogRecord } from './types'

const timeRange = ref('1h')
const autoRefresh = ref(true)
const refreshInterval = ref(10000)
const logDetailVisible = ref(false)
const selectedLog = ref<LogRecord | null>(null)
const selectedMetricKey = ref('total-logs')
let refreshTimer: ReturnType<typeof setInterval> | null = null

const { loading, selectedDatasourceId, workspace, fetchAllData } = useDashboardData()

const metricPool = computed(() => (workspace.value.logMetrics.length ? workspace.value.logMetrics : workspace.value.platformMetrics))
const selectedMetric = computed<DashboardMetricCard | undefined>(() => (
  metricPool.value.find(item => item.key === selectedMetricKey.value) || metricPool.value[0]
))
const selectedMetricDrilldown = computed(() => (
  workspace.value.metricDrilldowns.find(item => item.metricKey === selectedMetricKey.value) || workspace.value.metricDrilldowns[0]
))
const insightTiles = computed(() => buildDashboardLayout(workspace.value))

watch(metricPool, metrics => {
  if (!metrics.length) {
    selectedMetricKey.value = ''
    return
  }
  if (!metrics.some(item => item.key === selectedMetricKey.value)) {
    const firstMetric = metrics[0]
    selectedMetricKey.value = firstMetric ? firstMetric.key : ''
  }
}, { immediate: true })

const handleTimeRangeChange = () => {
  fetchAllData(timeRange.value)
}

const handleDatasetChange = (datasourceId: string) => {
  selectedDatasourceId.value = datasourceId
  fetchAllData(timeRange.value, datasourceId)
}

const handleAutoRefreshChange = (value: boolean) => {
  if (value) {
    startAutoRefresh()
  } else {
    stopAutoRefresh()
  }
}

const handleIntervalChange = () => {
  if (autoRefresh.value) {
    startAutoRefresh()
  }
}

const handleMetricSelect = (metricKey: string) => {
  selectedMetricKey.value = metricKey
}

const handleLogRowClick = (log: LogRecord) => {
  selectedLog.value = log
  logDetailVisible.value = true
}

const startAutoRefresh = () => {
  stopAutoRefresh()
  refreshTimer = setInterval(() => {
    fetchAllData(timeRange.value)
  }, refreshInterval.value)
}

const stopAutoRefresh = () => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

onMounted(() => {
  fetchAllData(timeRange.value)
  if (autoRefresh.value) {
    startAutoRefresh()
  }
})

onUnmounted(() => {
  stopAutoRefresh()
})
</script>

<style scoped lang="scss">
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-height: 100%;
  padding: 20px;
  background:
    radial-gradient(circle at top left, color-mix(in srgb, var(--macos-blue) 10%, transparent), transparent 30%),
    radial-gradient(circle at 88% 10%, color-mix(in srgb, #14b8a6 10%, transparent), transparent 26%),
    linear-gradient(180deg, color-mix(in srgb, var(--macos-bg-secondary) 94%, transparent), var(--macos-bg-primary));
}

.topbar {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid color-mix(in srgb, var(--macos-border) 76%, transparent);
  border-radius: 18px;
  background: color-mix(in srgb, var(--macos-card-bg) 90%, transparent);
  backdrop-filter: blur(14px);
}

.topbar-group,
.refresh-controls {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.time-selector {
  width: 160px;
}

.interval-selector {
  width: 112px;
}

.warning-stack {
  display: grid;
  gap: 10px;
}

@media (max-width: 960px) {
  .dashboard-page {
    padding: 16px;
  }

  .topbar {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
