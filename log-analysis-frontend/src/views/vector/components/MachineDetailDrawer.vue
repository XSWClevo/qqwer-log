<template>
  <el-drawer
    v-model="visible"
    :title="machine?.name || '机器详情'"
    size="720px"
    @close="handleClose"
  >
    <template v-if="loading">
      <el-skeleton :rows="10" animated />
    </template>
    
    <template v-else-if="detail">
      <!-- 基本信息 -->
      <el-card shadow="never" class="info-card">
        <template #header>
          <div class="card-header">
            <span>基本信息</span>
            <el-tag :type="getStatusType(detail.status)" size="small">
              {{ getStatusText(detail.status) }}
            </el-tag>
          </div>
        </template>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="主机名">{{ detail.hostname }}</el-descriptions-item>
          <el-descriptions-item label="IP 地址">{{ detail.ipAddress }}</el-descriptions-item>
          <el-descriptions-item label="操作系统">{{ detail.osType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="Vector 版本">{{ detail.vectorVersion || '-' }}</el-descriptions-item>
          <el-descriptions-item label="Agent 版本">{{ detail.agentVersion || '-' }}</el-descriptions-item>
          <el-descriptions-item label="最后心跳">
            <span :class="{ 'text-warning': isHeartbeatStale(detail.lastHeartbeat) }">
              {{ formatTime(detail.lastHeartbeat) }}
            </span>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 实时指标 -->
      <el-card shadow="never" class="info-card">
        <template #header>
          <div class="card-header">
            <span>实时指标</span>
            <el-tag v-if="latestMetrics" type="success" size="small">
              {{ formatTime(latestMetrics.timestamp) }}
            </el-tag>
            <el-tag v-else type="info" size="small">暂无数据</el-tag>
          </div>
        </template>
        
        <div v-if="latestMetrics" class="metrics-grid">
          <div class="metric-item">
            <div class="metric-label">CPU 使用率</div>
            <el-progress
              :percentage="latestMetrics.cpuUsagePercent"
              :color="getProgressColor(latestMetrics.cpuUsagePercent)"
              :stroke-width="12"
            />
            <div class="metric-value">{{ latestMetrics.cpuUsagePercent?.toFixed(2) }}%</div>
          </div>
          
          <div class="metric-item">
            <div class="metric-label">内存使用率</div>
            <el-progress
              :percentage="latestMetrics.memoryUsagePercent"
              :color="getProgressColor(latestMetrics.memoryUsagePercent)"
              :stroke-width="12"
            />
            <div class="metric-value">
              {{ latestMetrics.memoryUsagePercent?.toFixed(2) }}%
              <span class="metric-detail">({{ formatMemory(latestMetrics.memoryUsedMb) }})</span>
            </div>
          </div>
          
          <div class="metric-item">
            <div class="metric-label">磁盘使用率</div>
            <el-progress
              :percentage="latestMetrics.diskUsagePercent"
              :color="getProgressColor(latestMetrics.diskUsagePercent)"
              :stroke-width="12"
            />
            <div class="metric-value">
              {{ latestMetrics.diskUsagePercent?.toFixed(2) }}%
              <span class="metric-detail">({{ latestMetrics.diskUsedGb }} GB)</span>
            </div>
          </div>
          
          <div class="metric-item">
            <div class="metric-label">Vector 状态</div>
            <div class="vector-status">
              <el-tag :type="latestMetrics.vectorRunning ? 'success' : 'danger'" size="large">
                {{ latestMetrics.vectorRunning ? '运行中' : '已停止' }}
              </el-tag>
            </div>
          </div>
        </div>
        
        <el-empty v-else description="暂无指标数据" :image-size="60" />
      </el-card>

      <!-- 网卡流量 -->
      <el-card v-if="latestMetrics?.networkInterfaces?.length" shadow="never" class="info-card network-card">
        <template #header>
          <div class="card-header">
            <div class="header-left">
              <span>网卡流量</span>
              <el-tag type="info" size="small" style="margin-left: 8px">
                {{ latestMetrics.networkInterfaces.length }} 个接口
              </el-tag>
            </div>
            <el-radio-group v-model="networkViewMode" size="small">
              <el-radio-button value="chart">
                <el-icon><TrendCharts /></el-icon>
              </el-radio-button>
              <el-radio-button value="table">
                <el-icon><Grid /></el-icon>
              </el-radio-button>
            </el-radio-group>
          </div>
        </template>
        
        <!-- 图表视图 - 时间趋势 -->
        <div v-show="networkViewMode === 'chart'" class="network-chart-view">
          <div class="network-charts">
            <div ref="networkChartRef" class="network-chart"></div>
          </div>
          <div class="network-summary">
            <div class="summary-item">
              <div class="summary-icon recv">
                <el-icon><Download /></el-icon>
              </div>
              <div class="summary-content">
                <div class="summary-label">总接收</div>
                <div class="summary-value">{{ formatBytes(getTotalRecv()) }}</div>
              </div>
            </div>
            <div class="summary-item">
              <div class="summary-icon send">
                <el-icon><Upload /></el-icon>
              </div>
              <div class="summary-content">
                <div class="summary-label">总发送</div>
                <div class="summary-value">{{ formatBytes(getTotalSent()) }}</div>
              </div>
            </div>
            <div class="summary-item">
              <div class="summary-icon error" :class="{ 'has-error': getTotalErrors() > 0 }">
                <el-icon><Warning /></el-icon>
              </div>
              <div class="summary-content">
                <div class="summary-label">总错误</div>
                <div class="summary-value" :class="{ 'text-danger': getTotalErrors() > 0 }">
                  {{ getTotalErrors() }}
                </div>
              </div>
            </div>
          </div>
        </div>
        
        <!-- 表格视图 -->
        <el-table 
          v-show="networkViewMode === 'table'" 
          :data="sortedNetworkInterfaces" 
          size="small" 
          stripe
          :max-height="200"
        >
          <el-table-column prop="name" label="接口" width="90">
            <template #default="{ row }">
              <el-tag size="small" type="info">{{ row.name }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="接收" align="right">
            <template #default="{ row }">
              <div class="cell-traffic">
                <span class="traffic-bytes">{{ formatBytes(row.bytesRecv) }}</span>
                <span class="traffic-packets">{{ formatNumber(row.packetsRecv) }} 包</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="发送" align="right">
            <template #default="{ row }">
              <div class="cell-traffic">
                <span class="traffic-bytes">{{ formatBytes(row.bytesSent) }}</span>
                <span class="traffic-packets">{{ formatNumber(row.packetsSent) }} 包</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="错误" align="center" width="70">
            <template #default="{ row }">
              <el-tag 
                :type="(row.errin + row.errout) > 0 ? 'danger' : 'success'" 
                size="small"
              >
                {{ row.errin + row.errout }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 资源趋势图 -->
      <el-card shadow="never" class="info-card">
        <template #header>
          <div class="card-header">
            <span>资源趋势</span>
            <el-radio-group v-model="chartMinutes" size="small" @change="loadMetricsHistory">
              <el-radio-button :value="10">10分钟</el-radio-button>
              <el-radio-button :value="30">30分钟</el-radio-button>
              <el-radio-button :value="60">1小时</el-radio-button>
            </el-radio-group>
          </div>
        </template>
        
        <div v-if="metricsHistory.length > 0" class="chart-container">
          <div ref="chartRef" class="chart"></div>
        </div>
        <el-empty v-else description="暂无历史数据" :image-size="60" />
      </el-card>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch, onUnmounted, nextTick, computed } from 'vue'
import { vectorMachineApi, type MachineDetail, type MetricsPoint, type NetworkInterfaceInfo } from '@/api/vector'
import { Download, Upload, Warning, TrendCharts, Grid } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'
import * as echarts from 'echarts'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

const props = defineProps<{
  modelValue: boolean
  machine: { id: string; name: string } | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const visible = ref(props.modelValue)
const loading = ref(false)
const detail = ref<MachineDetail | null>(null)
const latestMetrics = ref<MetricsPoint | null>(null)
const metricsHistory = ref<MetricsPoint[]>([])
const chartMinutes = ref(10)
const chartRef = ref<HTMLElement>()
const networkChartRef = ref<HTMLElement>()
const networkViewMode = ref<'chart' | 'table'>('chart')

// 网卡流量历史数据（用于时间趋势图）
interface NetworkHistoryPoint {
  timestamp: string
  totalRecv: number
  totalSent: number
}
const networkHistory = ref<NetworkHistoryPoint[]>([])

let chart: echarts.ECharts | null = null
let networkChart: echarts.ECharts | null = null
let refreshTimer: ReturnType<typeof setInterval> | null = null

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val && props.machine) {
    // 重置状态
    networkHistory.value = []
    loadDetail()
    startAutoRefresh()
  } else {
    stopAutoRefresh()
  }
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

// 监听网卡视图模式切换 - 切换到图表时重新渲染
watch(networkViewMode, (val) => {
  if (val === 'chart') {
    nextTick(() => {
      // 销毁旧图表，重新创建
      if (networkChart) {
        networkChart.dispose()
        networkChart = null
      }
      setTimeout(renderNetworkChart, 100)
    })
  }
})

const loadDetail = async () => {
  if (!props.machine) return
  
  loading.value = true
  try {
    const res = await vectorMachineApi.getDetail(props.machine.id) as any
    detail.value = res.data || res
    latestMetrics.value = detail.value?.latestMetrics || null
    
    // 初始化网卡历史数据
    if (latestMetrics.value?.networkInterfaces?.length) {
      addNetworkHistoryPoint(latestMetrics.value)
    }
    
    await loadMetricsHistory()
    
    // 渲染网卡图表
    nextTick(() => {
      setTimeout(renderNetworkChart, 200)
    })
  } catch (e) {
    console.error('加载机器详情失败:', e)
  } finally {
    loading.value = false
  }
}

const loadMetricsHistory = async () => {
  if (!props.machine) return
  
  try {
    const res = await vectorMachineApi.getMetrics(props.machine.id, chartMinutes.value) as any
    const data = res.data || res
    metricsHistory.value = data.history || []
    
    // 同时更新最新指标
    if (data.latest) {
      latestMetrics.value = data.latest
    }
    
    // 渲染图表 - 先销毁再重建，避免切换时间范围后空白
    nextTick(() => {
      if (chart) {
        chart.dispose()
        chart = null
      }
      setTimeout(() => {
        if (chartRef.value && metricsHistory.value.length > 0) {
          renderChart()
        }
      }, 100)
    })
  } catch (e) {
    console.error('加载指标历史失败:', e)
  }
}

// 添加网卡历史数据点
const addNetworkHistoryPoint = (metrics: MetricsPoint) => {
  if (!metrics.networkInterfaces?.length) return
  
  const totalRecv = metrics.networkInterfaces.reduce((sum, i) => sum + (i.bytesRecv || 0), 0)
  const totalSent = metrics.networkInterfaces.reduce((sum, i) => sum + (i.bytesSent || 0), 0)
  
  networkHistory.value.push({
    timestamp: metrics.timestamp || dayjs().format('YYYY-MM-DD HH:mm:ss'),
    totalRecv,
    totalSent
  })
  
  // 保留最近 20 个数据点
  if (networkHistory.value.length > 20) {
    networkHistory.value.shift()
  }
}

const refreshLatestMetrics = async () => {
  if (!props.machine) return
  
  try {
    const res = await vectorMachineApi.getLatestMetrics(props.machine.id) as any
    const data = res.data || res
    if (data) {
      latestMetrics.value = data
      
      // 添加网卡历史数据点
      addNetworkHistoryPoint(data)
      
      // 更新网卡图表
      if (networkViewMode.value === 'chart') {
        renderNetworkChart()
      }
      
      // 更新资源趋势图
      if (metricsHistory.value.length > 0) {
        const lastTimestamp = metricsHistory.value[metricsHistory.value.length - 1]?.timestamp
        if (data.timestamp !== lastTimestamp) {
          metricsHistory.value.push(data)
          const maxPoints = chartMinutes.value * 2
          if (metricsHistory.value.length > maxPoints) {
            metricsHistory.value.shift()
          }
          renderChart()
        }
      }
    }
  } catch (e) {
    console.error('刷新指标失败:', e)
  }
}

const startAutoRefresh = () => {
  stopAutoRefresh()
  // 每 10 秒刷新一次（更频繁以展示网卡流量变化）
  refreshTimer = setInterval(refreshLatestMetrics, 10000)
}

const stopAutoRefresh = () => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

const renderChart = () => {
  if (!chartRef.value || metricsHistory.value.length === 0) return
  
  // 确保图表实例存在
  if (!chart) {
    chart = echarts.init(chartRef.value)
  }
  
  const times = metricsHistory.value.map(p => dayjs(p.timestamp).format('HH:mm:ss'))
  const cpuData = metricsHistory.value.map(p => Number(p.cpuUsagePercent?.toFixed(2)) || 0)
  const memData = metricsHistory.value.map(p => Number(p.memoryUsagePercent?.toFixed(2)) || 0)
  const diskData = metricsHistory.value.map(p => Number(p.diskUsagePercent?.toFixed(2)) || 0)
  
  const option: echarts.EChartsOption = {
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        let result = params[0].axisValue + '<br/>'
        params.forEach((item: any) => {
          result += `${item.marker} ${item.seriesName}: ${item.value}%<br/>`
        })
        return result
      }
    },
    legend: { data: ['CPU', '内存', '磁盘'], bottom: 0 },
    grid: { left: '3%', right: '4%', bottom: '15%', top: '10%', containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: times,
      axisLabel: { rotate: 45, fontSize: 10 }
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLabel: { formatter: '{value}%' }
    },
    series: [
      { name: 'CPU', type: 'line', smooth: true, data: cpuData, lineStyle: { color: '#409EFF' }, itemStyle: { color: '#409EFF' }, areaStyle: { color: 'rgba(64, 158, 255, 0.1)' } },
      { name: '内存', type: 'line', smooth: true, data: memData, lineStyle: { color: '#67C23A' }, itemStyle: { color: '#67C23A' }, areaStyle: { color: 'rgba(103, 194, 58, 0.1)' } },
      { name: '磁盘', type: 'line', smooth: true, data: diskData, lineStyle: { color: '#E6A23C' }, itemStyle: { color: '#E6A23C' }, areaStyle: { color: 'rgba(230, 162, 60, 0.1)' } }
    ]
  }
  chart.setOption(option, true) // true 表示不合并，完全替换
}

// 渲染网卡流量时间趋势图
const renderNetworkChart = () => {
  if (!networkChartRef.value) return
  
  // 确保图表实例存在
  if (!networkChart) {
    networkChart = echarts.init(networkChartRef.value)
  }
  
  // 如果历史数据不足，用当前数据填充
  if (networkHistory.value.length < 2 && latestMetrics.value?.networkInterfaces?.length) {
    const totalRecv = latestMetrics.value.networkInterfaces.reduce((sum, i) => sum + (i.bytesRecv || 0), 0)
    const totalSent = latestMetrics.value.networkInterfaces.reduce((sum, i) => sum + (i.bytesSent || 0), 0)
    
    // 生成模拟历史数据点（展示当前值）
    const now = dayjs()
    for (let i = 5; i >= 0; i--) {
      if (networkHistory.value.length >= 6) break
      networkHistory.value.unshift({
        timestamp: now.subtract(i * 10, 'second').format('YYYY-MM-DD HH:mm:ss'),
        totalRecv: totalRecv,
        totalSent: totalSent
      })
    }
  }
  
  const times = networkHistory.value.map(p => dayjs(p.timestamp).format('HH:mm:ss'))
  const recvData = networkHistory.value.map(p => Number((p.totalRecv / 1024 / 1024 / 1024).toFixed(2))) // GB
  const sentData = networkHistory.value.map(p => Number((p.totalSent / 1024 / 1024 / 1024).toFixed(2))) // GB
  
  const option: echarts.EChartsOption = {
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        let result = `<strong>${params[0].axisValue}</strong><br/>`
        params.forEach((item: any) => {
          const gb = parseFloat(item.value)
          result += `${item.marker} ${item.seriesName}: ${gb.toFixed(2)} GB<br/>`
        })
        return result
      }
    },
    legend: { data: ['接收', '发送'], bottom: 0 },
    grid: { left: '3%', right: '4%', bottom: '18%', top: '8%', containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: times,
      axisLabel: { fontSize: 10, rotate: 30 }
    },
    yAxis: {
      type: 'value',
      axisLabel: { 
        formatter: (v: number) => v.toFixed(1) + ' GB'
      }
    },
    series: [
      {
        name: '接收',
        type: 'line',
        smooth: true,
        data: recvData,
        lineStyle: { color: '#67C23A', width: 2 },
        itemStyle: { color: '#67C23A' },
        areaStyle: { 
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(103, 194, 58, 0.3)' },
            { offset: 1, color: 'rgba(103, 194, 58, 0.05)' }
          ])
        }
      },
      {
        name: '发送',
        type: 'line',
        smooth: true,
        data: sentData,
        lineStyle: { color: '#409EFF', width: 2 },
        itemStyle: { color: '#409EFF' },
        areaStyle: { 
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64, 158, 255, 0.3)' },
            { offset: 1, color: 'rgba(64, 158, 255, 0.05)' }
          ])
        }
      }
    ]
  }
  networkChart.setOption(option, true) // true 表示不合并，完全替换
}

// 网卡列表按总流量倒序排序
const sortedNetworkInterfaces = computed(() => {
  if (!latestMetrics.value?.networkInterfaces) return []
  return [...latestMetrics.value.networkInterfaces].sort((a, b) => {
    const totalA = (a.bytesRecv || 0) + (a.bytesSent || 0)
    const totalB = (b.bytesRecv || 0) + (b.bytesSent || 0)
    return totalB - totalA // 倒序
  })
})

// 计算总流量
const getTotalRecv = () => {
  if (!latestMetrics.value?.networkInterfaces) return 0
  return latestMetrics.value.networkInterfaces.reduce((sum, i) => sum + (i.bytesRecv || 0), 0)
}

const getTotalSent = () => {
  if (!latestMetrics.value?.networkInterfaces) return 0
  return latestMetrics.value.networkInterfaces.reduce((sum, i) => sum + (i.bytesSent || 0), 0)
}

const getTotalErrors = () => {
  if (!latestMetrics.value?.networkInterfaces) return 0
  return latestMetrics.value.networkInterfaces.reduce((sum, i) => sum + (i.errin || 0) + (i.errout || 0), 0)
}

const handleClose = () => {
  stopAutoRefresh()
  if (chart) { chart.dispose(); chart = null }
  if (networkChart) { networkChart.dispose(); networkChart = null }
  networkHistory.value = []
}

const getStatusType = (status: string) => {
  const map: Record<string, any> = { online: 'success', offline: 'info', error: 'danger' }
  return map[status] || 'info'
}

const getStatusText = (status: string) => {
  const map: Record<string, string> = { online: '在线', offline: '离线', error: '错误' }
  return map[status] || status
}

const getProgressColor = (percent: number) => {
  if (percent >= 90) return '#F56C6C'
  if (percent >= 70) return '#E6A23C'
  return '#67C23A'
}

const formatTime = (time: string) => {
  if (!time) return '-'
  return dayjs(time).fromNow()
}

const formatMemory = (mb: number) => {
  if (!mb) return '-'
  return mb >= 1024 ? (mb / 1024).toFixed(1) + ' GB' : mb + ' MB'
}

const formatBytes = (bytes: number) => {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let i = 0
  while (bytes >= 1024 && i < units.length - 1) { bytes /= 1024; i++ }
  return bytes.toFixed(2) + ' ' + units[i]
}

const formatNumber = (num: number) => {
  if (!num) return '0'
  if (num >= 1000000) return (num / 1000000).toFixed(2) + 'M'
  if (num >= 1000) return (num / 1000).toFixed(2) + 'K'
  return num.toString()
}

const isHeartbeatStale = (time: string) => {
  if (!time) return true
  return dayjs().diff(dayjs(time), 'minute') > 2
}

onUnmounted(() => { handleClose() })
</script>

<style scoped lang="scss">
.info-card {
  margin-bottom: 16px;
  
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    
    .header-left {
      display: flex;
      align-items: center;
    }
  }
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.metric-item {
  .metric-label {
    font-size: 13px;
    color: #606266;
    margin-bottom: 8px;
  }
  
  .metric-value {
    margin-top: 8px;
    font-size: 14px;
    font-weight: 600;
    
    .metric-detail {
      font-weight: normal;
      color: #909399;
      font-size: 12px;
    }
  }
  
  .vector-status {
    margin-top: 8px;
  }
}

// 网卡信息样式
.network-card {
  :deep(.el-card__body) {
    padding: 12px 16px;
  }
}

.network-chart-view {
  display: flex;
  gap: 16px;
  
  .network-charts {
    flex: 1;
    min-width: 0;
    
    .network-chart {
      width: 100%;
      height: 160px;
    }
  }
  
  .network-summary {
    width: 130px;
    display: flex;
    flex-direction: column;
    gap: 10px;
    
    .summary-item {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px;
      background: #f5f7fa;
      border-radius: 8px;
      
      .summary-icon {
        width: 28px;
        height: 28px;
        border-radius: 6px;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 14px;
        
        &.recv {
          background: linear-gradient(135deg, #67C23A 0%, #95d475 100%);
          color: white;
        }
        
        &.send {
          background: linear-gradient(135deg, #409EFF 0%, #79bbff 100%);
          color: white;
        }
        
        &.error {
          background: #e4e7ed;
          color: #909399;
          
          &.has-error {
            background: linear-gradient(135deg, #F56C6C 0%, #fab6b6 100%);
            color: white;
          }
        }
      }
      
      .summary-content {
        flex: 1;
        min-width: 0;
        
        .summary-label {
          font-size: 11px;
          color: #909399;
        }
        
        .summary-value {
          font-size: 12px;
          font-weight: 600;
          color: #303133;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
      }
    }
  }
}

// 表格样式
.cell-traffic {
  .traffic-bytes {
    display: block;
    font-weight: 500;
  }
  
  .traffic-packets {
    display: block;
    font-size: 11px;
    color: #909399;
  }
}

.chart-container {
  .chart {
    width: 100%;
    height: 280px;
  }
}

.text-warning { color: #E6A23C; }
.text-danger { color: #F56C6C; }
.text-muted { color: #909399; font-size: 12px; }
</style>
