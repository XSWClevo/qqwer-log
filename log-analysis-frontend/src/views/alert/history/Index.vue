<template>
  <AppLayout>
    <div class="alert-history-page">
      <div class="page-header">
        <div>
          <h1 class="page-title">告警历史</h1>
          <p class="page-subtitle">追踪触发记录、通知状态和处置进展</p>
        </div>
        <div class="header-actions">
          <el-button :icon="Refresh" :loading="loading" @click="refreshPage">刷新</el-button>
        </div>
      </div>

      <div class="toolbar">
        <el-select v-model="filters.timeRange" class="time-select" @change="handleFilterChange">
          <el-option label="最近 24 小时" value="24h" />
          <el-option label="最近 7 天" value="7d" />
          <el-option label="最近 30 天" value="30d" />
          <el-option label="自定义" value="custom" />
        </el-select>
        <el-date-picker
          v-if="filters.timeRange === 'custom'"
          v-model="customTimeRange"
          type="datetimerange"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          range-separator="至"
          value-format="YYYY-MM-DD HH:mm:ss"
          class="custom-time"
          @change="handleFilterChange"
        />
        <el-input
          v-model="filters.keyword"
          :prefix-icon="Search"
          placeholder="搜索规则名称或告警消息"
          clearable
          class="keyword-input"
          @keyup.enter="handleFilterChange"
          @clear="handleFilterChange"
        />
        <el-select v-model="filters.severity" placeholder="风险等级" clearable class="filter-select" @change="handleFilterChange">
          <el-option label="严重" value="critical" />
          <el-option label="警告" value="warning" />
          <el-option label="信息" value="info" />
        </el-select>
        <el-button text @click="resetFilters">重置</el-button>
      </div>

      <div class="summary-grid">
        <div v-for="item in summaryItems" :key="item.label" class="summary-tile">
          <span class="summary-label">{{ item.label }}</span>
          <strong class="summary-value">{{ item.value }}</strong>
          <span class="summary-sub">{{ item.sub }}</span>
        </div>
      </div>

      <section class="panel chart-panel">
        <div class="panel-header">
          <div>
            <h2>触发趋势</h2>
            <p>{{ rangeLabel }}，按风险等级堆叠展示</p>
          </div>
        </div>
        <div ref="chartRef" class="trend-chart"></div>
      </section>

      <section class="panel events-panel">
        <div class="panel-header">
          <div>
            <h2>事件列表</h2>
            <p>{{ pagination.total }} 条事件，当前显示 {{ events.length }} 条</p>
          </div>
        </div>

        <el-table v-loading="loading" :data="events" height="calc(100vh - 650px)" stripe>
          <el-table-column label="触发时间" width="180">
            <template #default="{ row }">
              <span class="mono">{{ formatDateTime(row.triggeredAt) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="风险" width="104" align="center">
            <template #default="{ row }">
              <el-tag :type="severityTag(row.severity)" size="small">{{ severityLabel(row.severity) }}</el-tag>
            </template>
          </el-table-column>

          <el-table-column label="规则与消息" min-width="300">
            <template #default="{ row }">
              <button class="rule-link" type="button" @click="openDetail(row)">{{ row.ruleName || '-' }}</button>
              <div class="event-message">{{ row.message || '无告警消息' }}</div>
            </template>
          </el-table-column>

          <el-table-column label="触发值" width="190">
            <template #default="{ row }">
              <div class="value-text">{{ row.triggeredValue || '-' }}</div>
            </template>
          </el-table-column>

          <el-table-column label="相关实体" width="180">
            <template #default="{ row }">
              <div class="value-text">{{ row.relatedEntity || '-' }}</div>
            </template>
          </el-table-column>

          <el-table-column label="通知" width="126" align="center">
            <template #default="{ row }">
              <span :class="['status-pill', notificationStatusClass(row.notificationStatus)]">
                {{ notificationStatusLabel(row.notificationStatus) }}
              </span>
            </template>
          </el-table-column>

          <el-table-column label="处置" width="126" align="center">
            <template #default="{ row }">
              <span :class="['status-pill', row.acknowledged ? 'muted' : 'warning']">
                {{ row.acknowledged ? '已确认' : '待确认' }}
              </span>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="152" align="center" fixed="right">
            <template #default="{ row }">
              <div class="row-actions">
                <el-button type="primary" text @click="openDetail(row)">详情</el-button>
                <el-button
                  v-if="!row.acknowledged"
                  text
                  :loading="acknowledgingId === row.id"
                  @click="handleAcknowledge(row)"
                >
                  确认
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination">
          <el-pagination
            v-model:current-page="pagination.pageNum"
            v-model:page-size="pagination.pageSize"
            :page-sizes="[20, 50, 100]"
            :total="pagination.total"
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="loadEvents"
            @size-change="handlePageSizeChange"
          />
        </div>
      </section>

      <AlertDetailDrawer
        v-model="detailVisible"
        :alert="selectedEvent"
        @acknowledged="handleDetailAcknowledged"
      />
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import AppLayout from '@/components/layout/AppLayout.vue'
import AlertDetailDrawer from './components/AlertDetailDrawer.vue'
import echarts from '@/utils/echarts'
import {
  acknowledgeAlertEvent,
  getAlertTrend,
  queryAlertEvents,
  type AlertEvent,
  type AlertTrend
} from '@/api/alert'

type EventRow = AlertEvent & {
  severity: string
  acknowledged: boolean
  notificationStatus: string
}

const loading = ref(false)
const chartRef = ref<HTMLElement>()
const customTimeRange = ref<[string, string] | null>(null)
const events = ref<EventRow[]>([])
const selectedEvent = ref<EventRow | null>(null)
const detailVisible = ref(false)
const acknowledgingId = ref<number | null>(null)
let trendChart: ReturnType<typeof echarts.init> | null = null

const filters = reactive({
  timeRange: '24h',
  keyword: '',
  severity: ''
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0
})

const rangeLabel = computed(() => {
  const map: Record<string, string> = {
    '24h': '最近 24 小时',
    '7d': '最近 7 天',
    '30d': '最近 30 天',
    custom: '自定义时间'
  }
  return map[filters.timeRange] || filters.timeRange
})

const summaryItems = computed(() => {
  const criticalCount = events.value.filter(event => normalizeSeverity(event.severity) === 'critical').length
  const openCount = events.value.filter(event => !event.acknowledged).length
  const failedCount = events.value.filter(event => event.notificationStatus === 'failed').length

  return [
    { label: '当前事件', value: events.value.length.toLocaleString(), sub: `共 ${pagination.total.toLocaleString()} 条` },
    { label: '严重事件', value: criticalCount.toLocaleString(), sub: '当前筛选范围' },
    { label: '待确认', value: openCount.toLocaleString(), sub: '需要继续处置' },
    { label: '通知失败', value: failedCount.toLocaleString(), sub: '当前页统计' }
  ]
})

const loadEvents = async () => {
  loading.value = true
  try {
    const [startTime, endTime] = customTimeRange.value || []
    const response = await queryAlertEvents({
      timeRange: filters.timeRange,
      startTime: filters.timeRange === 'custom' ? startTime : undefined,
      endTime: filters.timeRange === 'custom' ? endTime : undefined,
      keyword: filters.keyword || undefined,
      severity: filters.severity || undefined,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    })

    const page = response.data
    events.value = (page?.records || []).map(event => ({
      ...event,
      severity: normalizeSeverity(event.severity),
      acknowledged: event.acknowledged === true,
      notificationStatus: event.notificationStatus || 'pending'
    }))
    pagination.total = page?.total || 0
  } catch (error: any) {
    ElMessage.error('加载告警历史失败: ' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

const loadTrend = async () => {
  try {
    const response = await getAlertTrend(filters.timeRange)
    renderTrend(response.data || {})
  } catch {
    renderTrend({})
  }
}

const refreshPage = async () => {
  await Promise.all([loadEvents(), loadTrend()])
}

const handleFilterChange = () => {
  pagination.pageNum = 1
  refreshPage()
}

const handlePageSizeChange = () => {
  pagination.pageNum = 1
  loadEvents()
}

const resetFilters = () => {
  filters.timeRange = '24h'
  filters.keyword = ''
  filters.severity = ''
  customTimeRange.value = null
  handleFilterChange()
}

const openDetail = (event: EventRow) => {
  selectedEvent.value = event
  detailVisible.value = true
}

const handleAcknowledge = async (event: EventRow) => {
  acknowledgingId.value = event.id
  try {
    await acknowledgeAlertEvent(event.id)
    event.acknowledged = true
    event.acknowledgedAt = new Date().toISOString()
    ElMessage.success('告警已确认')
  } catch (error: any) {
    ElMessage.error('确认告警失败: ' + (error.message || '未知错误'))
  } finally {
    acknowledgingId.value = null
  }
}

const handleDetailAcknowledged = (eventId: number) => {
  const event = events.value.find(item => item.id === eventId)
  if (event) {
    event.acknowledged = true
    event.acknowledgedAt = new Date().toISOString()
  }
}

const renderTrend = (trend: AlertTrend) => {
  if (!chartRef.value) return
  if (!trendChart) trendChart = echarts.init(chartRef.value)

  const timestamps = trend.timestamps?.length ? trend.timestamps : ['00:00', '04:00', '08:00', '12:00', '16:00', '20:00', '24:00']
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: {
      right: 16,
      top: 8,
      data: ['严重', '警告', '信息']
    },
    grid: {
      left: 42,
      right: 24,
      top: 48,
      bottom: 28
    },
    xAxis: {
      type: 'category',
      data: timestamps,
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.2)' } }
    },
    series: [
      {
        name: '严重',
        type: 'bar',
        stack: 'alerts',
        data: trend.critical || [],
        itemStyle: { color: '#DC2626' }
      },
      {
        name: '警告',
        type: 'bar',
        stack: 'alerts',
        data: trend.warning || [],
        itemStyle: { color: '#D97706' }
      },
      {
        name: '信息',
        type: 'bar',
        stack: 'alerts',
        data: trend.info || [],
        itemStyle: { color: '#2563EB' }
      }
    ]
  })
}

const resizeTrendChart = () => {
  trendChart?.resize()
}

const normalizeSeverity = (severity?: string) => String(severity || 'info').toLowerCase()

const severityTag = (severity?: string) => {
  const map: Record<string, 'danger' | 'warning' | 'info'> = {
    critical: 'danger',
    warning: 'warning',
    info: 'info'
  }
  return map[normalizeSeverity(severity)] || 'info'
}

const severityLabel = (severity?: string) => {
  const map: Record<string, string> = {
    critical: '严重',
    warning: '警告',
    info: '信息'
  }
  return map[normalizeSeverity(severity)] || severity || '-'
}

const notificationStatusLabel = (status?: string) => {
  const map: Record<string, string> = {
    failed: '失败',
    pending: '待发送',
    sent: '已记录',
    skipped: '未配置'
  }
  return map[String(status || '').toLowerCase()] || status || '-'
}

const notificationStatusClass = (status?: string) => {
  const normalized = String(status || '').toLowerCase()
  if (normalized === 'failed') return 'danger'
  if (normalized === 'pending') return 'warning'
  return 'success'
}

const formatDateTime = (time?: string) => {
  if (!time) return '-'
  return time.replace('T', ' ').slice(0, 19)
}

watch(() => filters.timeRange, () => {
  if (filters.timeRange !== 'custom') customTimeRange.value = null
})

onMounted(async () => {
  await refreshPage()
  await nextTick()
  window.addEventListener('resize', resizeTrendChart)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeTrendChart)
  trendChart?.dispose()
})
</script>

<style scoped lang="scss">
.alert-history-page {
  min-height: 100vh;
  box-sizing: border-box;
  padding: 20px 24px 28px;
  background: var(--macos-fill-tertiary);
  color: var(--macos-text-primary);
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 650;
}

.page-subtitle {
  margin: 6px 0 0;
  color: var(--macos-text-secondary);
  font-size: 13px;
}

.header-actions,
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.toolbar {
  flex-wrap: wrap;
  padding: 12px;
  margin-bottom: 14px;
  background: var(--macos-card-bg);
  border: 1px solid var(--macos-border);
  border-radius: 8px;
}

.time-select,
.filter-select {
  width: 136px;
}

.custom-time {
  width: 360px;
}

.keyword-input {
  width: 300px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(160px, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.summary-tile {
  min-height: 86px;
  padding: 14px 16px;
  background: var(--macos-card-bg);
  border: 1px solid var(--macos-border);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.summary-label,
.summary-sub,
.event-message,
.value-text {
  color: var(--macos-text-secondary);
  font-size: 12px;
}

.summary-value {
  margin-top: 8px;
  font-size: 24px;
  line-height: 1.2;
}

.panel {
  background: var(--macos-card-bg);
  border: 1px solid var(--macos-border);
  border-radius: 8px;
  overflow: hidden;
}

.chart-panel {
  margin-bottom: 14px;
}

.panel-header {
  min-height: 56px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--macos-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;

  h2 {
    margin: 0;
    font-size: 15px;
    font-weight: 650;
  }

  p {
    margin: 4px 0 0;
    color: var(--macos-text-secondary);
    font-size: 12px;
  }
}

.trend-chart {
  height: 220px;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

.rule-link {
  width: fit-content;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--macos-blue);
  font: inherit;
  font-weight: 650;
  cursor: pointer;
}

.event-message {
  margin-top: 5px;
  line-height: 1.45;
  word-break: break-word;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 56px;
  height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;

  &.success {
    color: var(--macos-success);
    background: var(--macos-success-bg);
  }

  &.danger {
    color: var(--macos-danger);
    background: var(--macos-danger-bg);
  }

  &.warning {
    color: #B45309;
    background: rgba(245, 158, 11, 0.14);
  }

  &.muted {
    color: var(--macos-text-tertiary);
    background: var(--macos-fill-secondary);
  }
}

.row-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px;
  border-top: 1px solid var(--macos-border);
}

@media (max-width: 960px) {
  .page-header {
    flex-direction: column;
  }

  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .time-select,
  .filter-select,
  .custom-time,
  .keyword-input {
    width: 100%;
  }
}
</style>
