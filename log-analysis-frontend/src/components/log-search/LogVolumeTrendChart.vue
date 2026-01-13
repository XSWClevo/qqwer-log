<template>
  <el-card class="chart-card" shadow="never">
    <template #header>
      <div class="card-header">
        <span class="card-title">日志数量趋势</span>
        <div class="header-actions">
          <el-switch
            v-model="autoRefreshEnabled"
            active-text="自动刷新"
            inactive-text=""
            @change="handleAutoRefreshChange"
          />
          <el-button
            size="small"
            @click="toggleCollapse"
          >
            <el-icon>
              <ArrowUp v-if="!isCollapsed" />
              <ArrowDown v-else />
            </el-icon>
            {{ isCollapsed ? '展开' : '收起' }}
          </el-button>
        </div>
      </div>
    </template>

    <el-collapse-transition>
      <div v-show="!isCollapsed" class="chart-wrapper">
        <div ref="chartRef" class="chart-container"></div>
        <div v-show="loading" class="chart-skeleton-overlay">
          <el-skeleton animated :rows="5" />
        </div>
      </div>
    </el-collapse-transition>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, nextTick, computed } from 'vue'
import { ArrowUp, ArrowDown } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { useDark } from '@vueuse/core'

interface TimeSeriesPoint {
  timestamp: string
  count: number
}

interface Props {
  series: TimeSeriesPoint[]
  loading?: boolean
  autoRefresh?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  series: () => [],
  loading: false,
  autoRefresh: false
})

const emit = defineEmits<{
  'update:autoRefresh': [value: boolean]
  refresh: []
}>()

const chartRef = ref<HTMLDivElement>()
const isCollapsed = ref(false)
const autoRefreshEnabled = ref(props.autoRefresh)
let chartInstance: echarts.ECharts | null = null
let refreshTimer: number | null = null
const isDark = useDark()

const axisLineColor = computed(() => isDark.value ? '#333333' : '#e0e0e0')
const axisLabelColor = computed(() => isDark.value ? '#6E6E73' : '#333')

// Format timestamp for display
const formatTimestamp = (timestamp: string, type: 'full' | 'short' = 'short'): string => {
  const date = new Date(timestamp)
  if (type === 'short') {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return date.toLocaleString('zh-CN')
}

// Initialize chart
const initChart = () => {
  if (!chartRef.value) return
  
  if (chartInstance) {
    chartInstance.dispose()
  }
  
  chartInstance = echarts.init(chartRef.value, isDark.value ? 'dark' : undefined)
  renderChart()
}

// Render chart with data
const renderChart = () => {
  if (!chartInstance || props.series.length === 0) return

  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross',
        crossStyle: {
          color: '#409EFF',
          width: 1,
          type: 'solid'
        },
        label: {
          backgroundColor: '#409EFF'
        }
      },
      backgroundColor: isDark.value ? 'rgba(30,30,30,0.9)' : 'rgba(255, 255, 255, 0.95)',
      borderColor: isDark.value ? '#4C4D4F' : '#409EFF',
      borderWidth: 1,
      textStyle: {
        color: isDark.value ? '#fff' : '#1D1D1F',
        fontSize: 12
      },
      formatter: (params: any) => {
        if (!params || params.length === 0) return ''
        const param = params[0]
        return `
          <div style="padding: 4px 0;">
            <div style="font-weight: 600; margin-bottom: 4px;">${param.name}</div>
            <div style="color: #409EFF; font-size: 16px; font-weight: 700;">${param.value} 条日志</div>
          </div>
        `
      }
    },
    dataZoom: [
      {
        type: 'inside',
        start: 0,
        end: 100
      },
      {
        type: 'slider',
        start: 0,
        end: 100,
        height: 20,
        bottom: 0,
        handleSize: '80%',
        handleStyle: {
          color: '#409EFF'
        },
        textStyle: {
          fontSize: 10,
          color: axisLabelColor.value
        },
        borderColor: isDark.value ? '#333' : '#E6E6E6'
      }
    ],
    grid: {
      left: '50px',
      right: '20px',
      top: '15px',
      bottom: '45px'
    },
    xAxis: {
      type: 'category',
      data: props.series.map(s => formatTimestamp(s.timestamp)),
      axisLabel: {
        rotate: 30,
        fontSize: 11,
        interval: 'auto',
        color: axisLabelColor.value
      },
      axisTick: {
        alignWithLabel: true,
        lineStyle: { color: axisLineColor.value }
      },
      axisLine: {
        lineStyle: { color: axisLineColor.value }
      }
    },
    yAxis: {
      type: 'value',
      name: '数量',
      nameTextStyle: {
        fontSize: 12,
        padding: [0, 0, 0, 0],
        color: axisLabelColor.value
      },
      axisLabel: {
        fontSize: 11,
        color: axisLabelColor.value
      },
      splitLine: {
        lineStyle: {
          type: 'dashed',
          color: axisLineColor.value
        }
      }
    },
    series: [
      {
        name: '日志数量',
        type: 'bar',
        barWidth: '60%',
        data: props.series.map(s => s.count),
        itemStyle: {
          color: '#409EFF',
          borderRadius: [2, 2, 0, 0]
        },
        emphasis: {
          itemStyle: {
            color: '#66b1ff'
          }
        }
      }
    ]
  }

  chartInstance.setOption(option)
}

// Toggle collapse
const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
  if (!isCollapsed.value) {
    nextTick(() => {
      chartInstance?.resize()
    })
  }
}

// Handle auto-refresh toggle
const handleAutoRefreshChange = (value: boolean) => {
  emit('update:autoRefresh', value)
  
  if (value) {
    startAutoRefresh()
  } else {
    stopAutoRefresh()
  }
}

// Start auto-refresh timer (10 seconds)
const startAutoRefresh = () => {
  stopAutoRefresh()
  refreshTimer = window.setInterval(() => {
    emit('refresh')
  }, 10000)
}

// Stop auto-refresh timer
const stopAutoRefresh = () => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

// Handle window resize
const handleResize = () => {
  chartInstance?.resize()
}

// Watch for series changes
watch(() => props.series, () => {
  renderChart()
}, { deep: true })

// Watch for autoRefresh prop changes
watch(() => props.autoRefresh, (value) => {
  autoRefreshEnabled.value = value
  if (value) {
    startAutoRefresh()
  } else {
    stopAutoRefresh()
  }
})

// Watch dark mode
watch(isDark, () => {
  initChart()
})

onMounted(() => {
  nextTick(() => {
    initChart()
  })
  window.addEventListener('resize', handleResize)
  
  if (props.autoRefresh) {
    startAutoRefresh()
  }
})

onUnmounted(() => {
  stopAutoRefresh()
  window.removeEventListener('resize', handleResize)
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
})

// Expose methods
defineExpose({
  refresh: () => emit('refresh'),
  resize: () => chartInstance?.resize()
})
</script>

<style scoped lang="scss">
@use '@/assets/styles/macos.scss' as *;

.chart-card {
  background: var(--macos-bg-primary);
  border: 1px solid var(--macos-border);
  
  :deep(.el-card__header) {
    padding: 12px 16px;
    border-bottom: 1px solid var(--macos-border);
    background: var(--macos-bg-secondary);
  }

  :deep(.el-card__body) {
    padding: 16px;
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--macos-text-primary);
}

.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;

  :deep(.el-switch) {
    --el-switch-on-color: #409EFF;
  }
}

.chart-wrapper {
  position: relative;
  min-height: 200px;
}

.chart-container {
  width: 100%;
  height: 200px;
}

.chart-skeleton-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: var(--macos-bg-primary);
  opacity: 0.9;
  z-index: 10;
  padding: 16px;
  border-radius: 8px;
}
</style>
