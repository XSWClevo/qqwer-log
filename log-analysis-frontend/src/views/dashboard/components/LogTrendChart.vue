<template>
  <el-card class="dashboard-card chart-card" v-loading="loading">
    <template #header>
      <div class="card-header">
        <span class="card-title">日志趋势</span>
        <div class="level-selector">
          <el-checkbox-group v-model="selectedLevels" size="small" @change="updateChart">
            <el-checkbox-button value="INFO">
              <span class="level-btn info">INFO</span>
            </el-checkbox-button>
            <el-checkbox-button value="WARN">
              <span class="level-btn warn">WARN</span>
            </el-checkbox-button>
            <el-checkbox-button value="ERROR">
              <span class="level-btn error">ERROR</span>
            </el-checkbox-button>
            <el-checkbox-button value="FATAL">
              <span class="level-btn fatal">FATAL</span>
            </el-checkbox-button>
          </el-checkbox-group>
        </div>
      </div>
    </template>
    <div ref="chartRef" class="trend-chart"></div>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, onUnmounted, computed } from 'vue'
import * as echarts from 'echarts'
import { useDark } from '@vueuse/core'
import type { LogTrendItem } from '../types'

const props = defineProps<{ data: LogTrendItem[]; loading: boolean }>()
const emit = defineEmits<{ (e: 'drill-down', params: { time: string; level: string }): void }>()

const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null
const isDark = useDark()

// Computed colors for dark/light mode
const axisLineColor = computed(() => isDark.value ? '#333333' : '#E0E0E0')
const axisLabelColor = computed(() => isDark.value ? '#6E6E73' : '#909399')
const splitLineColor = computed(() => isDark.value ? '#333333' : '#F0F0F0')

// 选中的日志级别
const selectedLevels = ref<string[]>(['INFO', 'WARN', 'ERROR', 'FATAL'])

const levelConfig = {
  INFO: { color: '#1890FF', key: 'info' },
  WARN: { color: '#FAAD14', key: 'warn' },
  ERROR: { color: '#FF4D4F', key: 'error' },
  FATAL: { color: '#722ED1', key: 'fatal' }
}

const initChart = () => {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value, isDark.value ? 'dark' : undefined, {
    renderer: 'canvas'
  })
  chart.on('click', (params: any) => {
    if (params.componentType === 'series') {
      emit('drill-down', { time: params.name, level: params.seriesName })
    }
  })
  updateChart()
  window.addEventListener('resize', handleResize)
}

const handleResize = () => chart?.resize()

const updateChart = () => {
  if (!chart) return
  
  // Set transparent background explicitly
  chart.setOption({ backgroundColor: 'transparent' })

  if (!props.data || props.data.length === 0) {
    console.log('LogTrendChart: No data to display')
    return
  }
  
  console.log('LogTrendChart data:', props.data)
  
  // 根据选中的级别生成 series
  const series = selectedLevels.value.map((level, index) => {
    const config = levelConfig[level as keyof typeof levelConfig]
    const seriesData = props.data.map(d => {
      const val = d[config.key as keyof LogTrendItem]
      return typeof val === 'number' ? val : 0
    })
    console.log(`Series ${level}:`, seriesData)
    return {
      name: level,
      type: 'bar',
      stack: 'total',
      data: seriesData,
      itemStyle: { color: config.color },
      barWidth: index === 0 ? '60%' : undefined
    }
  })
  
  const xAxisData = props.data.map(d => d.time)
  console.log('X-Axis data:', xAxisData)
  
  chart.setOption({
    tooltip: { 
      trigger: 'axis', 
      axisPointer: { type: 'shadow' },
      backgroundColor: isDark.value ? 'rgba(30,30,30,0.9)' : '#fff',
      borderColor: isDark.value ? '#4C4D4F' : '#e4e7ed',
      textStyle: {
        color: isDark.value ? '#fff' : '#606266'
      }
    },
    grid: { top: 20, right: 20, bottom: 60, left: 60 },
    xAxis: { 
      type: 'category', 
      data: xAxisData, 
      axisLine: { lineStyle: { color: axisLineColor.value } }, 
      axisLabel: { color: axisLabelColor.value, fontSize: 11 } 
    },
    yAxis: { 
      type: 'value', 
      axisLine: { show: false }, 
      axisTick: { show: false }, 
      splitLine: { lineStyle: { color: splitLineColor.value } }, 
      axisLabel: { color: axisLabelColor.value, fontSize: 11 } 
    },
    dataZoom: [{ type: 'inside', start: 0, end: 100 }, { type: 'slider', start: 0, end: 100, height: 20, bottom: 10 }],
    series
  }, true)
}

watch(() => props.data, updateChart, { deep: true })
watch(isDark, () => {
  if (chart) {
    chart.dispose()
    initChart()
    // Re-apply options after re-init since init wipes them
    updateChart()
  }
})

onMounted(initChart)
onUnmounted(() => { chart?.dispose(); window.removeEventListener('resize', handleResize) })
</script>

<style scoped lang="scss">
.dashboard-card {
  border-radius: var(--macos-radius-lg);
  border: 1px solid var(--macos-border);
  box-shadow: var(--macos-shadow-sm);
  background: var(--macos-glass-bg);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  transition: var(--macos-transition);

  &:hover {
    box-shadow: var(--macos-shadow-md);
    border-color: var(--macos-border-hover);
  }
}
.chart-card { height: 400px; }
.card-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; }
.card-title { font-size: 16px; font-weight: 600; color: var(--macos-text-primary); }

.level-selector {
  :deep(.el-checkbox-group) {
    display: flex;
    gap: 0;
  }
  
  :deep(.el-checkbox-button) {
    .el-checkbox-button__inner {
      padding: 5px 12px;
      border-radius: 0;
      border-color: var(--macos-border);
      background: var(--macos-bg-primary);
      color: var(--macos-text-secondary);
      transition: var(--macos-transition-fast);
      
      &:hover {
        color: var(--macos-blue);
        background: var(--macos-bg-secondary);
      }
    }
    
    &:first-child .el-checkbox-button__inner {
      border-radius: 4px 0 0 4px;
    }
    
    &:last-child .el-checkbox-button__inner {
      border-radius: 0 4px 4px 0;
    }
    
    &.is-checked .el-checkbox-button__inner {
      background: var(--macos-blue-light);
      border-color: var(--macos-blue);
      color: var(--macos-blue);
      box-shadow: none;
    }
  }
}

.level-btn {
  font-size: 12px;
  font-weight: 500;
  
  &.info { color: #1890FF; }
  &.warn { color: #FAAD14; }
  &.error { color: #FF4D4F; }
  &.fatal { color: #722ED1; }
}

.trend-chart { height: calc(100% - 60px); }
</style>
