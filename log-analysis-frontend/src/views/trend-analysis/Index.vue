<template>
  <AppLayout>
    <div class="trend-analysis-container">
      <!-- Page Header -->
      <div class="page-header">
        <div class="header-left">
          <h1 class="page-title">Trend Analysis</h1>
          <p class="page-subtitle">Explore system metrics, identify long-term trends, and detect anomalies.</p>
        </div>
        <div class="header-actions">
          <el-button @click="handleSaveToDashboard">
            <el-icon><Star /></el-icon>
            Save to Dashboard
          </el-button>
          <el-button @click="handleShare">
            <el-icon><Share /></el-icon>
            Share View
          </el-button>
        </div>
      </div>

      <!-- Query Builder & Controls -->
      <el-card class="query-builder-card" shadow="never">
        <div class="query-controls">
          <!-- Time Range Selector -->
          <div class="time-range-section">
            <span class="section-label">Time Range</span>
            <el-select v-model="timeRange" class="time-select" @change="handleTimeRangeChange">
              <el-option label="Last 1 Hour" value="1h" />
              <el-option label="Last 6 Hours" value="6h" />
              <el-option label="Last 24 Hours" value="24h" />
              <el-option label="Last 7 Days" value="7d" />
              <el-option label="Last 30 Days" value="30d" />
              <el-option label="Custom" value="custom" />
            </el-select>
            <el-date-picker
              v-if="timeRange === 'custom'"
              v-model="customTimeRange"
              type="datetimerange"
              range-separator="to"
              start-placeholder="Start"
              end-placeholder="End"
              value-format="YYYY-MM-DD HH:mm:ss"
              class="custom-picker"
            />
          </div>

          <!-- Query A Card -->
          <div class="query-card">
            <div class="query-card-header">
              <span class="query-badge">A</span>
              <span class="query-title">Primary Query</span>
            </div>
            <div class="query-card-body">
              <div class="query-field-group">
                <label class="field-label">Metric</label>
                <el-select v-model="queryA.metric" placeholder="Select Metric" filterable class="metric-select">
                  <el-option-group label="System Metrics">
                    <el-option label="system.cpu.usage" value="system.cpu.usage" />
                    <el-option label="system.memory.usage" value="system.memory.usage" />
                    <el-option label="system.disk.usage" value="system.disk.usage" />
                    <el-option label="system.network.in" value="system.network.in" />
                    <el-option label="system.network.out" value="system.network.out" />
                  </el-option-group>
                  <el-option-group label="Application Metrics">
                    <el-option label="http.server.requests" value="http.server.requests" />
                    <el-option label="http.server.latency" value="http.server.latency" />
                    <el-option label="http.server.errors" value="http.server.errors" />
                    <el-option label="log.count" value="log.count" />
                    <el-option label="log.error.count" value="log.error.count" />
                  </el-option-group>
                </el-select>
              </div>
              <div class="query-field-group">
                <label class="field-label">Aggregation</label>
                <el-select v-model="queryA.aggregation" placeholder="Aggregation" class="agg-select">
                  <el-option label="Average" value="avg" />
                  <el-option label="Max" value="max" />
                  <el-option label="Min" value="min" />
                  <el-option label="Sum" value="sum" />
                  <el-option label="P95" value="p95" />
                  <el-option label="P99" value="p99" />
                </el-select>
              </div>
              <div class="query-field-group">
                <label class="field-label">Group By</label>
                <el-select v-model="queryA.groupBy" placeholder="Group By" clearable class="group-select">
                  <el-option label="hostname" value="hostname" />
                  <el-option label="appname" value="appname" />
                  <el-option label="severity" value="severity" />
                  <el-option label="source_type" value="source_type" />
                </el-select>
              </div>
              <div class="query-field-group filter-group">
                <label class="field-label">Filters</label>
                <div class="filter-tags-container">
                  <el-tag
                    v-for="(filter, index) in queryA.filters"
                    :key="index"
                    closable
                    type="info"
                    class="filter-tag"
                    @close="removeFilter('A', index)"
                  >
                    {{ filter }}
                  </el-tag>
                  <el-input
                    v-model="queryA.filterInput"
                    placeholder="e.g. env=production"
                    class="filter-input-inline"
                    size="small"
                    @keyup.enter="addFilter('A')"
                  >
                    <template #suffix>
                      <el-icon class="add-filter-icon" @click="addFilter('A')"><Plus /></el-icon>
                    </template>
                  </el-input>
                </div>
              </div>
            </div>
          </div>

          <!-- Query B Card (Optional) -->
          <div v-if="showQueryB" class="query-card query-card-secondary">
            <div class="query-card-header">
              <span class="query-badge secondary">B</span>
              <span class="query-title">Compare Query</span>
              <el-button type="danger" text size="small" class="remove-query-btn" @click="removeQueryB">
                <el-icon><Close /></el-icon>
              </el-button>
            </div>
            <div class="query-card-body">
              <div class="query-field-group">
                <label class="field-label">Metric</label>
                <el-select v-model="queryB.metric" placeholder="Select Metric" filterable class="metric-select">
                  <el-option-group label="System Metrics">
                    <el-option label="system.cpu.usage" value="system.cpu.usage" />
                    <el-option label="system.memory.usage" value="system.memory.usage" />
                    <el-option label="system.disk.usage" value="system.disk.usage" />
                  </el-option-group>
                  <el-option-group label="Application Metrics">
                    <el-option label="http.server.requests" value="http.server.requests" />
                    <el-option label="http.server.latency" value="http.server.latency" />
                    <el-option label="log.count" value="log.count" />
                  </el-option-group>
                </el-select>
              </div>
              <div class="query-field-group">
                <label class="field-label">Aggregation</label>
                <el-select v-model="queryB.aggregation" placeholder="Aggregation" class="agg-select">
                  <el-option label="Average" value="avg" />
                  <el-option label="Max" value="max" />
                  <el-option label="Sum" value="sum" />
                </el-select>
              </div>
              <div class="query-field-group">
                <label class="field-label">Group By</label>
                <el-select v-model="queryB.groupBy" placeholder="Group By" clearable class="group-select">
                  <el-option label="hostname" value="hostname" />
                  <el-option label="appname" value="appname" />
                </el-select>
              </div>
              <div class="query-field-group filter-group">
                <label class="field-label">Filters</label>
                <div class="filter-tags-container">
                  <el-tag
                    v-for="(filter, index) in queryB.filters"
                    :key="index"
                    closable
                    type="info"
                    class="filter-tag"
                    @close="removeFilter('B', index)"
                  >
                    {{ filter }}
                  </el-tag>
                  <el-input
                    v-model="queryB.filterInput"
                    placeholder="e.g. env=staging"
                    class="filter-input-inline"
                    size="small"
                    @keyup.enter="addFilter('B')"
                  >
                    <template #suffix>
                      <el-icon class="add-filter-icon" @click="addFilter('B')"><Plus /></el-icon>
                    </template>
                  </el-input>
                </div>
              </div>
            </div>
          </div>

          <div class="query-actions">
            <el-button v-if="!showQueryB" class="add-query-btn" @click="showQueryB = true">
              <el-icon><Plus /></el-icon>
              Add Query
            </el-button>
            <el-button type="primary" @click="executeQuery" :loading="loading">
              <el-icon><Search /></el-icon>
              Run Query
            </el-button>
          </div>
        </div>
      </el-card>

      <!-- Main Content Area -->
      <div class="main-content">
        <!-- Chart Area -->
        <el-card class="chart-card" shadow="never">
          <template #header>
            <div class="chart-header">
              <div class="chart-type-switcher">
                <el-tooltip content="Line Chart" placement="top">
                  <button 
                    class="chart-type-btn" 
                    :class="{ active: chartType === 'line' }"
                    @click="chartType = 'line'"
                  >
                    <el-icon><TrendCharts /></el-icon>
                  </button>
                </el-tooltip>
                <el-tooltip content="Area Chart" placement="top">
                  <button 
                    class="chart-type-btn" 
                    :class="{ active: chartType === 'area' }"
                    @click="chartType = 'area'"
                  >
                    <el-icon><DataLine /></el-icon>
                  </button>
                </el-tooltip>
                <el-tooltip content="Bar Chart" placement="top">
                  <button 
                    class="chart-type-btn" 
                    :class="{ active: chartType === 'bar' }"
                    @click="chartType = 'bar'"
                  >
                    <el-icon><Histogram /></el-icon>
                  </button>
                </el-tooltip>
              </div>
            </div>
          </template>
          <div ref="chartRef" class="main-chart" v-loading="loading"></div>
          <!-- Legend -->
          <div class="chart-legend">
            <div
              v-for="series in chartSeries"
              :key="series.name"
              class="legend-item"
              :class="{ disabled: !series.visible, highlighted: highlightedSeries === series.name }"
              @click="toggleSeries(series.name)"
              @mouseenter="highlightSeries(series.name)"
              @mouseleave="highlightSeries(null)"
            >
              <span class="legend-color" :style="{ backgroundColor: series.color }"></span>
              <span class="legend-name">{{ series.name }}</span>
              <span class="legend-value">{{ series.currentValue }}</span>
            </div>
          </div>
        </el-card>

        <!-- Insights Panel -->
        <el-card class="insights-card" shadow="never">
          <template #header>
            <span class="section-title">Insights & Summary</span>
          </template>
          
          <!-- Statistics Table -->
          <div class="stats-section">
            <h4>Statistics</h4>
            <el-table :data="statsData" size="small" class="stats-table">
              <el-table-column prop="metric" label="Metric" />
              <el-table-column prop="min" label="Min" />
              <el-table-column prop="max" label="Max" />
              <el-table-column prop="avg" label="Average" />
              <el-table-column prop="current" label="Current" />
            </el-table>
          </div>

          <!-- Detected Shifts -->
          <div class="shifts-section">
            <h4>Detected Shifts</h4>
            <div v-if="detectedShifts.length > 0" class="shifts-list">
              <div v-for="(shift, index) in detectedShifts" :key="index" class="shift-item" :class="shift.type">
                <div class="shift-indicator"></div>
                <div class="shift-content">
                  <el-icon v-if="shift.type === 'increase'" class="shift-icon"><Top /></el-icon>
                  <el-icon v-else class="shift-icon"><Bottom /></el-icon>
                  <span class="shift-message">{{ shift.message }}</span>
                </div>
              </div>
            </div>
            <el-empty v-else description="No significant shifts detected" :image-size="60" />
          </div>

          <!-- Anomalies -->
          <div class="anomalies-section">
            <h4>Anomalies</h4>
            <div v-if="anomalies.length > 0" class="anomalies-list">
              <div v-for="(anomaly, index) in anomalies" :key="index" class="anomaly-item">
                <div class="anomaly-indicator"></div>
                <div class="anomaly-content">
                  <span class="anomaly-time">{{ anomaly.time }}</span>
                  <span class="anomaly-desc">{{ anomaly.description }}</span>
                </div>
              </div>
            </div>
            <el-empty v-else description="No anomalies detected" :image-size="60" />
          </div>
        </el-card>
      </div>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, watch } from 'vue'
import { Star, Share, Plus, Close, Search, TrendCharts, WarningFilled, Top, Bottom, DataLine, Histogram } from '@element-plus/icons-vue'
import echarts from '@/utils/echarts'
import AppLayout from '@/components/layout/AppLayout.vue'
import { ElMessage } from 'element-plus'

// State
const loading = ref(false)
const timeRange = ref('7d')
const customTimeRange = ref<[string, string]>()
const showQueryB = ref(false)
const chartType = ref<'line' | 'area' | 'bar'>('line')
const chartRef = ref<HTMLElement>()
const highlightedSeries = ref<string | null>(null)
let chartInstance: echarts.ECharts | null = null

// Query configurations
const queryA = reactive({
  metric: 'log.count',
  aggregation: 'avg',
  groupBy: 'hostname',
  filters: [] as string[],
  filterInput: ''
})

const queryB = reactive({
  metric: '',
  aggregation: 'avg',
  groupBy: '',
  filters: [] as string[],
  filterInput: ''
})

// Chart data
interface ChartSeriesItem {
  name: string
  color: string
  visible: boolean
  currentValue: string
}

const chartSeries = ref<ChartSeriesItem[]>([])

// Statistics
const statsData = ref([
  { metric: 'log.count', min: '1,234', max: '8,567', avg: '4,521', current: '5,123' }
])

// Detected shifts
const detectedShifts = ref([
  { type: 'increase', message: 'Log volume increased by 23% compared to last week' },
  { type: 'decrease', message: 'Error rate decreased by 8% in the last 24 hours' }
])

// Anomalies
const anomalies = ref([
  { time: '2025-12-18 14:30', description: 'Unusual spike in error logs detected' },
  { time: '2025-12-17 03:15', description: 'Log volume dropped below baseline' }
])

// Methods
const handleTimeRangeChange = () => {
  if (timeRange.value !== 'custom') {
    executeQuery()
  }
}

const addFilter = (query: 'A' | 'B') => {
  const q = query === 'A' ? queryA : queryB
  if (q.filterInput.trim()) {
    q.filters.push(q.filterInput.trim())
    q.filterInput = ''
  }
}

const removeFilter = (query: 'A' | 'B', index: number) => {
  const q = query === 'A' ? queryA : queryB
  q.filters.splice(index, 1)
}

const removeQueryB = () => {
  showQueryB.value = false
  queryB.metric = ''
  queryB.aggregation = 'avg'
  queryB.groupBy = ''
  queryB.filters = []
  queryB.filterInput = ''
}

const highlightSeries = (name: string | null) => {
  highlightedSeries.value = name
  if (chartInstance) {
    if (name) {
      chartInstance.dispatchAction({
        type: 'highlight',
        seriesName: name
      })
    } else {
      chartInstance.dispatchAction({
        type: 'downplay'
      })
    }
  }
}

const executeQuery = async () => {
  if (!queryA.metric) {
    ElMessage.warning('Please select a metric')
    return
  }
  
  loading.value = true
  try {
    // Simulate API call - replace with actual API
    await new Promise(resolve => setTimeout(resolve, 1000))
    generateMockData()
    renderChart()
  } catch (error) {
    ElMessage.error('Failed to execute query')
  } finally {
    loading.value = false
  }
}

const generateMockData = () => {
  const colors = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399']
  const hosts = ['host-01', 'host-02', 'host-03']
  
  chartSeries.value = hosts.map((host, index) => ({
    name: host,
    color: colors[index % colors.length],
    visible: true,
    currentValue: Math.floor(Math.random() * 5000 + 1000).toLocaleString()
  }))
}

const toggleSeries = (name: string) => {
  const series = chartSeries.value.find(s => s.name === name)
  if (series) {
    series.visible = !series.visible
    renderChart()
  }
}

const renderChart = () => {
  if (!chartRef.value) return
  
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
  }
  
  // Generate time points
  const now = new Date()
  const timePoints: string[] = []
  const dataPoints = 24
  
  for (let i = dataPoints - 1; i >= 0; i--) {
    const time = new Date(now.getTime() - i * 60 * 60 * 1000)
    timePoints.push(time.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }))
  }
  
  // Generate series data
  const series = chartSeries.value
    .filter(s => s.visible)
    .map(s => {
      const data = timePoints.map(() => Math.floor(Math.random() * 5000 + 1000))
      return {
        name: s.name,
        type: chartType.value === 'area' ? 'line' : chartType.value,
        smooth: true,
        areaStyle: chartType.value === 'area' ? { opacity: 0.3 } : undefined,
        data,
        itemStyle: { color: s.color },
        markArea: {
          silent: true,
          data: [
            [
              { xAxis: timePoints[8], itemStyle: { color: 'rgba(255, 77, 79, 0.1)' } },
              { xAxis: timePoints[10] }
            ]
          ]
        }
      }
    })
  
  const option = {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: '#E4E7ED',
      borderWidth: 1,
      textStyle: { color: '#303133' },
      formatter: (params: any) => {
        let html = `<div style="font-weight:600;margin-bottom:8px;">${params[0]?.axisValue}</div>`
        params.forEach((p: any) => {
          html += `<div style="display:flex;align-items:center;gap:8px;margin:4px 0;">
            <span style="width:10px;height:10px;border-radius:50%;background:${p.color};"></span>
            <span>${p.seriesName}:</span>
            <strong>${p.value.toLocaleString()}</strong>
          </div>`
        })
        return html
      }
    },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '10%', containLabel: true },
    xAxis: {
      type: 'category',
      data: timePoints,
      axisLine: { lineStyle: { color: '#E4E7ED' } },
      axisLabel: { color: '#909399' }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#F2F6FC' } },
      axisLabel: { color: '#909399' }
    },
    series
  }
  
  chartInstance.setOption(option, true)
}

const handleSaveToDashboard = () => {
  ElMessage.success('View saved to dashboard')
}

const handleShare = () => {
  navigator.clipboard.writeText(window.location.href)
  ElMessage.success('Link copied to clipboard')
}

// Lifecycle
onMounted(() => {
  executeQuery()
  window.addEventListener('resize', () => chartInstance?.resize())
})

onUnmounted(() => {
  chartInstance?.dispose()
  window.removeEventListener('resize', () => chartInstance?.resize())
})

watch(chartType, () => {
  renderChart()
})
</script>


<style scoped lang="scss">
.trend-analysis-container {
  padding: 24px;
  background: var(--macos-fill-tertiary);
  min-height: 100vh;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;

  .header-left {
    .page-title {
      font-size: 24px;
      font-weight: 600;
      color: var(--macos-text-primary);
      margin: 0 0 4px 0;
    }

    .page-subtitle {
      font-size: 14px;
      color: #6E6E73;
      margin: 0;
    }
  }

  .header-actions {
    display: flex;
    gap: 12px;
  }
}

.query-builder-card {
  margin-bottom: 24px;

  :deep(.el-card__body) {
    padding: 20px;
  }
}

.query-controls {
  .time-range-section {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 20px;
    padding-bottom: 16px;
    border-bottom: 1px solid var(--macos-border);

    .section-label {
      font-weight: 600;
      color: var(--macos-text-primary);
      min-width: 80px;
    }

    .time-select {
      width: 160px;
    }

    .custom-picker {
      width: 360px;
    }
  }

  .query-card {
    background: #F8FAFC;
    border: 1px solid #E4E7ED;
    border-radius: 8px;
    margin-bottom: 16px;
    overflow: hidden;

    &.query-card-secondary {
      background: #FAFBFF;
      border-color: #D4E5FF;
    }

    .query-card-header {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 12px 16px;
      background: var(--macos-card-bg);
      border-bottom: 1px solid #E4E7ED;

      .query-badge {
        width: 24px;
        height: 24px;
        border-radius: 6px;
        background: linear-gradient(135deg, #409EFF 0%, #66B1FF 100%);
        color: #FFFFFF;
        font-size: 13px;
        font-weight: 700;
        display: flex;
        align-items: center;
        justify-content: center;

        &.secondary {
          background: linear-gradient(135deg, #67C23A 0%, #85CE61 100%);
        }
      }

      .query-title {
        font-size: 14px;
        font-weight: 600;
        color: var(--macos-text-primary);
      }

      .remove-query-btn {
        margin-left: auto;
      }
    }

    .query-card-body {
      padding: 16px;
      display: flex;
      flex-wrap: wrap;
      gap: 16px;

      .query-field-group {
        display: flex;
        flex-direction: column;
        gap: 6px;

        .field-label {
          font-size: 12px;
          color: var(--macos-text-tertiary);
          font-weight: 500;
        }

        .metric-select {
          width: 200px;
        }

        .agg-select {
          width: 120px;
        }

        .group-select {
          width: 140px;
        }

        &.filter-group {
          flex: 1;
          min-width: 280px;
        }
      }
    }
  }

  .filter-tags-container {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
    padding: 8px 12px;
    background: var(--macos-card-bg);
    border: 1px solid var(--macos-border);
    border-radius: 6px;
    min-height: 40px;

    .filter-tag {
      border-radius: 4px;
      font-size: 12px;
    }

    .filter-input-inline {
      flex: 1;
      min-width: 140px;

      :deep(.el-input__wrapper) {
        box-shadow: none !important;
        background: transparent;
        padding: 0;
      }

      .add-filter-icon {
        cursor: pointer;
        color: var(--macos-text-tertiary);
        transition: color 0.2s;

        &:hover {
          color: var(--macos-blue);
        }
      }
    }
  }

  .query-actions {
    display: flex;
    gap: 12px;
    margin-top: 8px;
    padding-top: 16px;
    border-top: 1px solid #F0F0F0;

    .add-query-btn {
      color: #409EFF !important;
      border-color: #409EFF !important;
      background: transparent !important;

      &:hover {
        color: #66b1ff !important;
        border-color: #66b1ff !important;
        background: rgba(64, 158, 255, 0.1) !important;
      }
    }
  }
}

.main-content {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 24px;
}

.chart-card {
  :deep(.el-card__header) {
    padding: 12px 20px;
    border-bottom: 1px solid var(--macos-border);
    background: var(--macos-fill-tertiary);
  }

  .chart-header {
    display: flex;
    justify-content: flex-end;
    align-items: center;
  }

  .chart-type-switcher {
    display: flex;
    gap: 4px;
    background: var(--macos-bg-secondary);
    padding: 4px;
    border-radius: 8px;

    .chart-type-btn {
      width: 36px;
      height: 32px;
      border: none;
      background: transparent;
      border-radius: 6px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      color: var(--macos-text-tertiary);
      transition: all 0.2s;

      &:hover {
        color: var(--macos-text-secondary);
        background: rgba(255, 255, 255, 0.5);
      }

      &.active {
        background: var(--macos-card-bg);
        color: var(--macos-blue);
        box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
      }

      .el-icon {
        font-size: 18px;
      }
    }
  }

  .main-chart {
    height: 400px;
  }

  .chart-legend {
    display: flex;
    flex-wrap: wrap;
    gap: 16px;
    padding: 16px 0;
    border-top: 1px solid #F0F0F0;
    margin-top: 16px;

    .legend-item {
      display: flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;
      padding: 6px 12px;
      border-radius: 6px;
      transition: all 0.2s;
      border: 1px solid transparent;

      &:hover {
        background: var(--macos-fill-secondary);
        border-color: #E4E7ED;
      }

      &.highlighted {
        background: var(--macos-info-bg);
        border-color: #B3D8FF;
      }

      &.disabled {
        opacity: 0.4;

        .legend-color {
          background: #C0C4CC !important;
        }
      }

      .legend-color {
        width: 12px;
        height: 12px;
        border-radius: 3px;
      }

      .legend-name {
        font-size: 13px;
        color: var(--macos-text-secondary);
      }

      .legend-value {
        font-size: 13px;
        font-weight: 600;
        color: var(--macos-text-primary);
        font-family: 'SF Mono', monospace;
      }
    }
  }
}

.insights-card {
  :deep(.el-card__header) {
    padding: 16px 20px;
    border-bottom: 1px solid var(--macos-border);
    background: var(--macos-fill-tertiary);
  }

  .section-title {
    font-size: 16px;
    font-weight: 600;
    color: var(--macos-text-primary);
  }

  h4 {
    font-size: 13px;
    font-weight: 600;
    color: var(--macos-text-secondary);
    margin: 0 0 12px 0;
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }

  .stats-section {
    margin-bottom: 24px;

    .stats-table {
      :deep(.el-table__cell) {
        padding: 14px 12px;
      }

      :deep(.el-table__header-wrapper th) {
        background: var(--macos-fill-tertiary);
        font-weight: 600;
        color: var(--macos-text-secondary);
      }
    }
  }

  .shifts-section {
    margin-bottom: 24px;

    .shifts-list {
      .shift-item {
        display: flex;
        align-items: stretch;
        background: var(--macos-card-bg);
        border: 1px solid #E4E7ED;
        border-radius: 8px;
        margin-bottom: 10px;
        overflow: hidden;

        .shift-indicator {
          width: 4px;
          flex-shrink: 0;
        }

        .shift-content {
          display: flex;
          align-items: center;
          gap: 10px;
          padding: 12px 16px;
          flex: 1;

          .shift-icon {
            font-size: 16px;
            flex-shrink: 0;
          }

          .shift-message {
            font-size: 13px;
            color: var(--macos-text-primary);
            line-height: 1.5;
          }
        }

        &.increase {
          .shift-indicator {
            background: #F56C6C;
          }

          .shift-icon {
            color: var(--macos-danger);
          }
        }

        &.decrease {
          .shift-indicator {
            background: #67C23A;
          }

          .shift-icon {
            color: var(--macos-success);
          }
        }
      }
    }
  }

  .anomalies-section {
    .anomalies-list {
      .anomaly-item {
        display: flex;
        align-items: stretch;
        background: var(--macos-card-bg);
        border: 1px solid #E4E7ED;
        border-radius: 8px;
        margin-bottom: 10px;
        overflow: hidden;

        .anomaly-indicator {
          width: 4px;
          background: #E6A23C;
          flex-shrink: 0;
        }

        .anomaly-content {
          display: flex;
          flex-direction: column;
          gap: 4px;
          padding: 12px 16px;

          .anomaly-time {
            font-size: 11px;
            color: var(--macos-text-tertiary);
            font-family: 'SF Mono', monospace;
          }

          .anomaly-desc {
            font-size: 13px;
            color: var(--macos-text-primary);
            line-height: 1.5;
          }
        }
      }
    }
  }
}
</style>
