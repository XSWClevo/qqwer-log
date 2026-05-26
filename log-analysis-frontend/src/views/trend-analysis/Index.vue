<template>
  <AppLayout>
    <div class="trend-page">
      <div class="page-header">
        <div>
          <h1 class="page-title">趋势分析</h1>
          <p class="page-subtitle">按时间、风险、来源和主机观察日志量与攻击识别结果的变化</p>
        </div>
        <div class="header-actions">
          <el-button :icon="Refresh" :loading="loading" @click="loadTrendData">刷新</el-button>
        </div>
      </div>

      <div class="toolbar">
        <el-select v-model="selectedDatasource" placeholder="默认日志库" clearable class="datasource-select" @change="handleDatasourceChange">
          <el-option label="默认日志库" value="" />
          <el-option
            v-for="item in datasourceOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          >
            <span>{{ item.label }}</span>
            <span class="option-meta">{{ item.type }}</span>
          </el-option>
        </el-select>

        <el-select v-model="timeRange" class="time-select" @change="handleTimeRangeChange">
          <el-option label="最近 1 小时" value="1h" />
          <el-option label="最近 6 小时" value="6h" />
          <el-option label="最近 24 小时" value="24h" />
          <el-option label="最近 7 天" value="7d" />
          <el-option label="最近 30 天" value="30d" />
          <el-option label="自定义" value="custom" />
        </el-select>

        <el-date-picker
          v-if="timeRange === 'custom'"
          v-model="customTimeRange"
          type="datetimerange"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          range-separator="至"
          value-format="YYYY-MM-DD HH:mm:ss"
          class="custom-time"
          @change="loadTrendData"
        />

        <el-select v-model="granularity" class="granularity-select" @change="loadTrendData">
          <el-option label="自动粒度" value="auto" />
          <el-option label="1 分钟" value="1m" />
          <el-option label="5 分钟" value="5m" />
          <el-option label="1 小时" value="1h" />
          <el-option label="1 天" value="1d" />
        </el-select>

        <el-select v-model="chartMode" class="mode-select" @change="renderTrendChart">
          <el-option label="日志与攻击" value="combined" />
          <el-option label="仅日志量" value="logs" />
          <el-option label="仅攻击识别" value="attacks" />
        </el-select>

        <el-input
          v-model="keyword"
          :prefix-icon="Search"
          placeholder="攻击日志关键词"
          clearable
          class="keyword-input"
          @keyup.enter="loadTrendData"
          @clear="loadTrendData"
        />
      </div>

      <div class="metric-grid">
        <div v-for="metric in metrics" :key="metric.label" class="metric-tile">
          <span class="metric-label">{{ metric.label }}</span>
          <span class="metric-value">{{ metric.value }}</span>
          <span class="metric-sub" :class="metric.trendClass">{{ metric.sub }}</span>
        </div>
      </div>

      <div class="content-grid">
        <section class="panel trend-panel">
          <div class="panel-header">
            <div>
              <h2>综合趋势</h2>
              <p>{{ rangeLabel }}，粒度 {{ activeGranularityLabel }}</p>
            </div>
            <div class="chart-switch">
              <el-tooltip content="折线图" placement="top">
                <button :class="{ active: chartType === 'line' }" @click="setChartType('line')">
                  <el-icon><TrendCharts /></el-icon>
                </button>
              </el-tooltip>
              <el-tooltip content="柱状图" placement="top">
                <button :class="{ active: chartType === 'bar' }" @click="setChartType('bar')">
                  <el-icon><Histogram /></el-icon>
                </button>
              </el-tooltip>
            </div>
          </div>
          <div ref="trendChartRef" v-loading="loading" class="trend-chart"></div>
        </section>

        <section class="panel insight-panel">
          <div class="panel-header compact">
            <h2>趋势判断</h2>
          </div>
          <div v-if="insights.length" class="insight-list">
            <div v-for="item in insights" :key="item.title" class="insight-item" :class="item.level">
              <div class="insight-main">
                <el-icon v-if="item.direction === 'up'"><Top /></el-icon>
                <el-icon v-else-if="item.direction === 'down'"><Bottom /></el-icon>
                <el-icon v-else><WarningFilled /></el-icon>
                <span>{{ item.title }}</span>
              </div>
              <p>{{ item.description }}</p>
            </div>
          </div>
          <el-empty v-else description="暂无明显波动" :image-size="64" />
        </section>
      </div>

      <div class="bottom-grid">
        <section class="panel">
          <div class="panel-header compact">
            <h2>风险等级分布</h2>
          </div>
          <div ref="severityChartRef" class="small-chart"></div>
        </section>

        <section class="panel">
          <div class="panel-header compact">
            <h2>攻击类型 Top</h2>
          </div>
          <div ref="attackTypeChartRef" class="small-chart"></div>
        </section>

        <section class="panel">
          <div class="panel-header compact">
            <h2>日志字段 Top</h2>
            <el-select v-model="selectedDimension" class="dimension-select" size="small" @change="renderDimensionChart">
              <el-option
                v-for="item in dimensionOptions"
                :key="item"
                :label="fieldLabel(item)"
                :value="item"
              />
            </el-select>
          </div>
          <div ref="dimensionChartRef" class="small-chart"></div>
        </section>
      </div>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { Bottom, Histogram, Refresh, Search, Top, TrendCharts, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import AppLayout from '@/components/layout/AppLayout.vue'
import echarts from '@/utils/echarts'
import { getDatasourceSchema, queryFieldStats, queryTimeSeries, type FieldInfo } from '@/api/log'
import { queryAttackClassifications, type AttackClassificationRecord } from '@/api/attack'
import { configComponentApi, type ConfigComponent } from '@/api/vector'

type ChartType = 'line' | 'bar'
type ChartMode = 'combined' | 'logs' | 'attacks'

interface SeriesPoint {
  timestamp: string
  count: number
}

interface MetricTile {
  label: string
  value: string
  sub: string
  trendClass?: string
}

interface Insight {
  title: string
  description: string
  level: 'info' | 'warning' | 'danger' | 'success'
  direction?: 'up' | 'down'
}

const loading = ref(false)
const selectedDatasource = ref('')
const datasourceOptions = ref<Array<{ value: string; label: string; type: string }>>([])
const schemaFields = ref<FieldInfo[]>([])
const timeRange = ref('24h')
const customTimeRange = ref<[string, string]>()
const granularity = ref('auto')
const activeGranularity = ref('1h')
const chartMode = ref<ChartMode>('combined')
const chartType = ref<ChartType>('line')
const keyword = ref('')
const selectedDimension = ref('severity')

const logSeries = ref<SeriesPoint[]>([])
const attackRecords = ref<AttackClassificationRecord[]>([])
const fieldStats = ref<Record<string, Array<{ value: string; count: number }>>>({})
const insights = ref<Insight[]>([])

const trendChartRef = ref<HTMLElement>()
const severityChartRef = ref<HTMLElement>()
const attackTypeChartRef = ref<HTMLElement>()
const dimensionChartRef = ref<HTMLElement>()
let trendChart: any = null
let severityChart: any = null
let attackTypeChart: any = null
let dimensionChart: any = null

const dimensionOptions = computed(() => {
  const fromSchema = schemaFields.value
    .filter(item => item.isStatsDimension && !item.isContentField)
    .map(item => item.name)
    .filter(item => !['id', 'message', 'raw', 'timestamp'].includes(item))
  const defaults = ['severity', 'source_type', 'hostname', 'source_ip', 'appname']
  return Array.from(new Set(fromSchema.length ? fromSchema.slice(0, 8) : defaults))
})

const rangeLabel = computed(() => {
  const [startTime, endTime] = getTimeRange()
  return `${startTime} 至 ${endTime}`
})

const activeGranularityLabel = computed(() => {
  const map: Record<string, string> = {
    '1m': '1 分钟',
    '5m': '5 分钟',
    '1h': '1 小时',
    '1d': '1 天'
  }
  return map[activeGranularity.value] || activeGranularity.value
})

const attackBuckets = computed(() => bucketAttackRecords(attackRecords.value))

const metrics = computed<MetricTile[]>(() => {
  const totalLogs = sumSeries(logSeries.value)
  const totalAttacks = attackRecords.value.length
  const highRisk = attackRecords.value.filter(item => ['critical', 'high'].includes(item.severity)).length
  const sourceIps = new Set(attackRecords.value.map(item => item.sourceIp).filter(Boolean))
  const hostAgg = aggregateBy(attackRecords.value, item => item.hostname || '未知主机')
  const topHost = hostAgg[0]

  return [
    {
      label: '日志总量',
      value: formatNumber(totalLogs),
      sub: buildChangeText(logSeries.value),
      trendClass: compareHalves(logSeries.value) > 0 ? 'danger' : 'success'
    },
    {
      label: '攻击识别',
      value: formatNumber(totalAttacks),
      sub: totalAttacks ? `${highRisk} 条高危及以上` : '当前范围未命中'
    },
    {
      label: '来源 IP',
      value: formatNumber(sourceIps.size),
      sub: sourceIps.size ? '参与攻击链路聚合' : '无来源 IP'
    },
    {
      label: '最活跃主机',
      value: topHost?.name || '-',
      sub: topHost ? `${topHost.count} 条命中` : '暂无数据'
    }
  ]
})

const loadDatasourceOptions = async () => {
  try {
    const response: any = await configComponentApi.getQueryableDataSources()
    const list = normalizeResponseList<ConfigComponent>(response)
    datasourceOptions.value = list
      .filter(item => item.vectorType)
      .map(item => ({
        value: item.id,
        label: item.displayName || item.name,
        type: item.vectorType
      }))
  } catch (error) {
    console.warn('加载可查询数据源失败:', error)
  }
}

const handleDatasourceChange = async () => {
  await loadSchema()
  await loadTrendData()
}

const handleTimeRangeChange = () => {
  if (timeRange.value !== 'custom') {
    loadTrendData()
  }
}

const loadSchema = async () => {
  if (!selectedDatasource.value) {
    schemaFields.value = []
    selectedDimension.value = 'severity'
    return
  }
  try {
    const response: any = await getDatasourceSchema(selectedDatasource.value)
    schemaFields.value = normalizeResponseList<FieldInfo>(response)
    if (!dimensionOptions.value.includes(selectedDimension.value)) {
      selectedDimension.value = dimensionOptions.value[0] || 'severity'
    }
  } catch (error) {
    schemaFields.value = []
  }
}

const loadTrendData = async () => {
  const [startTime, endTime] = getTimeRange()
  const queryGranularity = resolveGranularity(startTime, endTime)
  activeGranularity.value = queryGranularity
  loading.value = true

  try {
    const [timeResult, statsResult, attackResult] = await Promise.allSettled([
      queryTimeSeries({
        datasourceId: selectedDatasource.value || undefined,
        startTime,
        endTime,
        granularity: queryGranularity,
        useMcp: false
      }),
      queryFieldStats({
        datasourceId: selectedDatasource.value || undefined,
        startTime,
        endTime,
        dimensions: dimensionOptions.value.slice(0, 6),
        metrics: ['count'],
        useMcp: false
      }),
      queryAttackClassifications({
        startTime,
        endTime,
        datasourceId: selectedDatasource.value || undefined,
        keyword: keyword.value || undefined,
        pageNum: 1,
        pageSize: 1000
      })
    ])

    if (timeResult.status === 'fulfilled') {
      logSeries.value = normalizeSeries(timeResult.value?.data?.series || [])
    } else {
      logSeries.value = []
      console.warn('加载日志趋势失败:', timeResult.reason)
    }

    if (statsResult.status === 'fulfilled') {
      fieldStats.value = statsResult.value?.data?.data || {}
    } else {
      fieldStats.value = {}
      console.warn('加载字段统计失败:', statsResult.reason)
    }

    if (attackResult.status === 'fulfilled') {
      attackRecords.value = attackResult.value?.data?.records || []
    } else {
      attackRecords.value = []
      console.warn('加载攻击趋势失败:', attackResult.reason)
    }

    insights.value = buildInsights()
    await nextTick()
    renderAllCharts()
  } catch (error) {
    ElMessage.error('趋势分析加载失败')
  } finally {
    loading.value = false
  }
}

const renderAllCharts = () => {
  renderTrendChart()
  renderSeverityChart()
  renderAttackTypeChart()
  renderDimensionChart()
}

const renderTrendChart = () => {
  if (!trendChartRef.value) return
  if (!trendChart) trendChart = echarts.init(trendChartRef.value)

  const bucketKeys = buildBucketKeys()
  const logMap = new Map(logSeries.value.map(item => [normalizeBucket(item.timestamp), item.count]))
  const attackMap = attackBuckets.value
  const criticalMap = bucketAttackRecords(attackRecords.value.filter(item => ['critical', 'high'].includes(item.severity)))

  const series: any[] = []
  if (chartMode.value !== 'attacks') {
    series.push({
      name: '日志量',
      type: chartType.value,
      smooth: chartType.value === 'line',
      data: bucketKeys.map(key => logMap.get(key) || 0),
      itemStyle: { color: '#2563EB' },
      areaStyle: chartType.value === 'line' ? { opacity: 0.08 } : undefined
    })
  }
  if (chartMode.value !== 'logs') {
    series.push({
      name: '攻击识别',
      type: 'line',
      smooth: true,
      data: bucketKeys.map(key => attackMap.get(key) || 0),
      itemStyle: { color: '#DC2626' },
      areaStyle: { opacity: 0.06 }
    })
    series.push({
      name: '高危及以上',
      type: 'bar',
      data: bucketKeys.map(key => criticalMap.get(key) || 0),
      itemStyle: { color: '#F59E0B' },
      barMaxWidth: 18
    })
  }

  trendChart.setOption({
    color: ['#2563EB', '#DC2626', '#F59E0B'],
    tooltip: { trigger: 'axis' },
    legend: { top: 4, right: 16 },
    grid: { left: 44, right: 24, top: 44, bottom: 42 },
    xAxis: {
      type: 'category',
      data: bucketKeys.map(formatAxisLabel),
      axisLabel: { color: '#6B7280' },
      axisLine: { lineStyle: { color: '#E5E7EB' } }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#EEF2F7' } },
      axisLabel: { color: '#6B7280' }
    },
    dataZoom: bucketKeys.length > 36 ? [{ type: 'inside' }, { type: 'slider', height: 18, bottom: 8 }] : undefined,
    series
  }, true)
}

const renderSeverityChart = () => {
  if (!severityChartRef.value) return
  if (!severityChart) severityChart = echarts.init(severityChartRef.value)
  const data = aggregateBy(attackRecords.value, item => severityLabel(item.severity)).map(item => ({
    name: item.name,
    value: item.count
  }))
  severityChart.setOption({
    color: ['#B91C1C', '#EF4444', '#F59E0B', '#64748B'],
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, left: 'center' },
    series: [{
      type: 'pie',
      radius: ['46%', '72%'],
      center: ['50%', '44%'],
      avoidLabelOverlap: true,
      label: { formatter: '{b}: {c}' },
      data
    }]
  }, true)
}

const renderAttackTypeChart = () => {
  if (!attackTypeChartRef.value) return
  if (!attackTypeChart) attackTypeChart = echarts.init(attackTypeChartRef.value)
  const data = aggregateBy(attackRecords.value, item => attackTypeLabel(item.attackType)).slice(0, 8).reverse()
  attackTypeChart.setOption({
    color: ['#0F766E'],
    tooltip: { trigger: 'axis' },
    grid: { left: 86, right: 20, top: 12, bottom: 24 },
    xAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#EEF2F7' } } },
    yAxis: {
      type: 'category',
      data: data.map(item => item.name),
      axisLabel: { color: '#4B5563' }
    },
    series: [{ type: 'bar', data: data.map(item => item.count), barMaxWidth: 16 }]
  }, true)
}

const renderDimensionChart = () => {
  if (!dimensionChartRef.value) return
  if (!dimensionChart) dimensionChart = echarts.init(dimensionChartRef.value)
  const data = (fieldStats.value[selectedDimension.value] || []).slice(0, 8).reverse()
  dimensionChart.setOption({
    color: ['#7C3AED'],
    tooltip: { trigger: 'axis' },
    grid: { left: 92, right: 20, top: 12, bottom: 24 },
    xAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#EEF2F7' } } },
    yAxis: {
      type: 'category',
      data: data.map(item => String(item.value || '空值')),
      axisLabel: { color: '#4B5563', overflow: 'truncate', width: 82 }
    },
    series: [{ type: 'bar', data: data.map(item => Number(item.count) || 0), barMaxWidth: 16 }]
  }, true)
}

const setChartType = (type: ChartType) => {
  chartType.value = type
  renderTrendChart()
}

const buildInsights = (): Insight[] => {
  const results: Insight[] = []
  const logChange = compareHalves(logSeries.value)
  if (Math.abs(logChange) >= 20) {
    results.push({
      title: logChange > 0 ? '日志量明显上升' : '日志量明显下降',
      description: `后半段相比前半段${logChange > 0 ? '增加' : '减少'} ${Math.abs(logChange).toFixed(1)}%，建议结合来源 IP 和主机 Top 排查变化来源。`,
      level: logChange > 0 ? 'warning' : 'success',
      direction: logChange > 0 ? 'up' : 'down'
    })
  }

  const attackPoints = Array.from(attackBuckets.value.entries()).map(([timestamp, count]) => ({ timestamp, count }))
  const attackChange = compareHalves(attackPoints)
  if (Math.abs(attackChange) >= 20 && attackRecords.value.length > 0) {
    results.push({
      title: attackChange > 0 ? '攻击识别升高' : '攻击识别回落',
      description: `攻击命中在后半段${attackChange > 0 ? '升高' : '回落'} ${Math.abs(attackChange).toFixed(1)}%，优先查看链路分析中的源 IP 与命中规则。`,
      level: attackChange > 0 ? 'danger' : 'info',
      direction: attackChange > 0 ? 'up' : 'down'
    })
  }

  const spikes = detectSpikes(logSeries.value)
  if (spikes.length) {
    const firstSpike = spikes[0]!
    results.push({
      title: '发现日志峰值',
      description: `${formatAxisLabel(firstSpike.timestamp)} 的日志量达到 ${firstSpike.count}，高于当前范围平均水平。`,
      level: 'warning'
    })
  }

  const critical = attackRecords.value.filter(item => item.severity === 'critical').length
  if (critical > 0) {
    results.push({
      title: '存在严重风险事件',
      description: `当前时间范围内有 ${critical} 条严重风险识别结果，应优先进入链路分析确认影响面。`,
      level: 'danger'
    })
  }

  return results
}

const detectSpikes = (series: SeriesPoint[]) => {
  if (series.length < 4) return []
  const avg = sumSeries(series) / series.length
  return series.filter(item => item.count > avg * 2 && item.count > 10)
}

const buildBucketKeys = () => {
  const keys = new Set<string>()
  logSeries.value.forEach(item => keys.add(normalizeBucket(item.timestamp)))
  attackBuckets.value.forEach((_count, timestamp) => keys.add(timestamp))
  if (!keys.size) {
    const [startTime, endTime] = getTimeRange()
    return generateBuckets(startTime, endTime)
  }
  return Array.from(keys).sort()
}

const bucketAttackRecords = (records: AttackClassificationRecord[]) => {
  const buckets = new Map<string, number>()
  records.forEach(record => {
    const timestamp = record.classifiedAt || record.logTimestamp
    if (!timestamp) return
    const key = normalizeBucket(timestamp)
    buckets.set(key, (buckets.get(key) || 0) + 1)
  })
  return buckets
}

const normalizeSeries = (rows: any[]): SeriesPoint[] => {
  return rows.map(row => ({
    timestamp: String(row.timestamp || row.time_bucket || ''),
    count: Number(row.count || 0)
  })).filter(item => item.timestamp)
}

const getTimeRange = (): [string, string] => {
  if (timeRange.value === 'custom' && customTimeRange.value?.length === 2) {
    return customTimeRange.value
  }

  const end = new Date()
  const start = new Date(end)
  if (timeRange.value === '1h') start.setHours(start.getHours() - 1)
  else if (timeRange.value === '6h') start.setHours(start.getHours() - 6)
  else if (timeRange.value === '7d') start.setDate(start.getDate() - 7)
  else if (timeRange.value === '30d') start.setDate(start.getDate() - 30)
  else start.setHours(start.getHours() - 24)
  return [formatDateTime(start), formatDateTime(end)]
}

const resolveGranularity = (startTime: string, endTime: string) => {
  if (granularity.value !== 'auto') return granularity.value
  const hours = (parseDateTime(endTime).getTime() - parseDateTime(startTime).getTime()) / 3600000
  if (hours <= 2) return '1m'
  if (hours <= 12) return '5m'
  if (hours <= 72) return '1h'
  return '1d'
}

const generateBuckets = (startTime: string, endTime: string) => {
  const result: string[] = []
  const current = parseDateTime(startTime)
  const end = parseDateTime(endTime)
  while (current <= end && result.length < 720) {
    result.push(formatDateTime(floorDate(current)))
    advanceDate(current)
  }
  return result
}

const normalizeBucket = (timestamp: string) => formatDateTime(floorDate(parseDateTime(timestamp)))

const floorDate = (date: Date) => {
  const value = new Date(date)
  value.setSeconds(0, 0)
  if (activeGranularity.value === '5m') value.setMinutes(Math.floor(value.getMinutes() / 5) * 5)
  if (activeGranularity.value === '1h') value.setMinutes(0, 0, 0)
  if (activeGranularity.value === '1d') value.setHours(0, 0, 0, 0)
  return value
}

const advanceDate = (date: Date) => {
  if (activeGranularity.value === '1m') date.setMinutes(date.getMinutes() + 1)
  else if (activeGranularity.value === '5m') date.setMinutes(date.getMinutes() + 5)
  else if (activeGranularity.value === '1d') date.setDate(date.getDate() + 1)
  else date.setHours(date.getHours() + 1)
}

const parseDateTime = (value: string) => new Date(String(value).replace(' ', 'T'))

const formatDateTime = (date: Date) => {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const formatAxisLabel = (timestamp: string) => {
  if (!timestamp) return ''
  if (activeGranularity.value === '1d') return timestamp.slice(5, 10)
  return timestamp.slice(5, 16)
}

const aggregateBy = <T,>(rows: T[], getter: (row: T) => string) => {
  const map = new Map<string, number>()
  rows.forEach(row => {
    const key = getter(row) || '未知'
    map.set(key, (map.get(key) || 0) + 1)
  })
  return Array.from(map.entries())
    .map(([name, count]) => ({ name, count }))
    .sort((a, b) => b.count - a.count)
}

const sumSeries = (series: SeriesPoint[]) => series.reduce((sum, item) => sum + item.count, 0)

const compareHalves = (series: SeriesPoint[]) => {
  if (series.length < 2) return 0
  const mid = Math.floor(series.length / 2)
  const first = sumSeries(series.slice(0, mid))
  const second = sumSeries(series.slice(mid))
  if (first === 0) return second > 0 ? 100 : 0
  return ((second - first) / first) * 100
}

const buildChangeText = (series: SeriesPoint[]) => {
  const change = compareHalves(series)
  if (!Number.isFinite(change) || change === 0) return '与前半段持平'
  return `较前半段${change > 0 ? '上升' : '下降'} ${Math.abs(change).toFixed(1)}%`
}

const formatNumber = (value: number) => value.toLocaleString()

const fieldLabel = (field: string) => {
  const map: Record<string, string> = {
    severity: '日志等级',
    source_type: '来源类型',
    hostname: '主机',
    source_ip: '来源 IP',
    appname: '应用'
  }
  return map[field] || field
}

const severityLabel = (severity?: string) => {
  const map: Record<string, string> = {
    critical: '严重',
    high: '高危',
    medium: '中危',
    low: '低危'
  }
  return map[severity || ''] || severity || '未知'
}

const attackTypeLabel = (type?: string) => {
  const map: Record<string, string> = {
    authentication_attack: '认证攻击',
    web_attack: 'Web 攻击',
    command_execution: '命令执行',
    scan_probe: '扫描探测',
    privilege_abuse: '权限异常'
  }
  return map[type || ''] || type || '未知'
}

const normalizeResponseList = <T,>(response: any): T[] => {
  const data = response?.data || response
  return Array.isArray(data) ? data : []
}

const handleResize = () => {
  trendChart?.resize()
  severityChart?.resize()
  attackTypeChart?.resize()
  dimensionChart?.resize()
}

onMounted(async () => {
  await loadDatasourceOptions()
  await loadTrendData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  severityChart?.dispose()
  attackTypeChart?.dispose()
  dimensionChart?.dispose()
})
</script>

<style scoped lang="scss">
.trend-page {
  height: 100%;
  min-height: 100vh;
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

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 12px;
  margin-bottom: 14px;
  background: var(--macos-card-bg);
  border: 1px solid var(--macos-border);
  border-radius: 8px;
}

.datasource-select {
  width: 220px;
}

.time-select,
.granularity-select,
.mode-select {
  width: 132px;
}

.custom-time {
  width: 360px;
}

.keyword-input {
  width: 240px;
}

.option-meta {
  float: right;
  margin-left: 16px;
  color: var(--macos-text-tertiary);
  font-size: 12px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(160px, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.metric-tile {
  min-height: 92px;
  padding: 14px 16px;
  background: var(--macos-card-bg);
  border: 1px solid var(--macos-border);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.metric-label {
  font-size: 12px;
  color: var(--macos-text-secondary);
}

.metric-value {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
  word-break: break-word;
}

.metric-sub {
  margin-top: 8px;
  font-size: 12px;
  color: var(--macos-text-tertiary);

  &.danger {
    color: var(--macos-danger);
  }

  &.success {
    color: var(--macos-success);
  }
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 14px;
  margin-bottom: 14px;
}

.bottom-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.panel {
  background: var(--macos-card-bg);
  border: 1px solid var(--macos-border);
  border-radius: 8px;
  overflow: hidden;
}

.panel-header {
  min-height: 56px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--macos-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;

  &.compact {
    min-height: 48px;
  }

  h2 {
    margin: 0;
    font-size: 15px;
    font-weight: 650;
  }

  p {
    margin: 4px 0 0;
    font-size: 12px;
    color: var(--macos-text-secondary);
  }
}

.chart-switch {
  display: flex;
  gap: 4px;
  padding: 3px;
  background: var(--macos-fill-secondary);
  border-radius: 8px;

  button {
    width: 32px;
    height: 28px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border: 0;
    border-radius: 6px;
    background: transparent;
    color: var(--macos-text-secondary);
    cursor: pointer;

    &.active {
      color: var(--macos-blue);
      background: var(--macos-card-bg);
      box-shadow: var(--macos-shadow-sm);
    }
  }
}

.trend-chart {
  height: 396px;
}

.small-chart {
  height: 286px;
}

.dimension-select {
  width: 132px;
}

.insight-panel {
  min-height: 454px;
}

.insight-list {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.insight-item {
  padding: 12px;
  border: 1px solid var(--macos-border);
  border-left-width: 4px;
  border-radius: 8px;
  background: var(--macos-fill-tertiary);

  &.danger {
    border-left-color: var(--macos-danger);
  }

  &.warning {
    border-left-color: var(--macos-warning);
  }

  &.success {
    border-left-color: var(--macos-success);
  }

  &.info {
    border-left-color: var(--macos-blue);
  }

  p {
    margin: 8px 0 0;
    color: var(--macos-text-secondary);
    font-size: 12px;
    line-height: 1.6;
  }
}

.insight-main {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 650;
  font-size: 13px;
}

@media (max-width: 1280px) {
  .metric-grid,
  .bottom-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .content-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .trend-page {
    padding: 14px;
  }

  .page-header,
  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .datasource-select,
  .time-select,
  .granularity-select,
  .mode-select,
  .keyword-input,
  .custom-time {
    width: 100%;
  }

  .metric-grid,
  .bottom-grid {
    grid-template-columns: 1fr;
  }
}
</style>
