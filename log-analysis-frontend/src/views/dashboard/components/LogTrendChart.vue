<template>
  <InsightPanel
    title="日志趋势"
    description="按级别查看所选时间范围内的日志分布。"
    :status="status"
    empty-title="暂无趋势数据"
    :empty-description="emptyText || '当前数据集在所选时间范围内没有日志趋势。'"
  >
    <div ref="chartRef" class="trend-chart"></div>
  </InsightPanel>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useDark } from '@vueuse/core'
import echarts from '@/utils/echarts'
import InsightPanel from './InsightPanel.vue'
import type { DashboardPanelState, LogTrendItem } from '../types'

const props = defineProps<{
  data: DashboardPanelState<LogTrendItem>
}>()

const chartRef = ref<HTMLElement>()
const isDark = useDark()
const status = computed(() => props.data.status)
const emptyText = computed(() => props.data.emptyText)
let chart: echarts.ECharts | null = null

const levelConfig = {
  INFO: { key: 'info', color: '#1890FF' },
  WARN: { key: 'warn', color: '#FAAD14' },
  ERROR: { key: 'error', color: '#FF4D4F' },
  FATAL: { key: 'fatal', color: '#722ED1' }
} as const

const renderChart = () => {
  if (!chart || props.data.status !== 'ready' || !props.data.items.length) {
    return
  }

  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: isDark.value ? 'rgba(30,30,30,0.9)' : '#fff',
      borderColor: isDark.value ? '#4C4D4F' : '#e4e7ed',
      textStyle: { color: isDark.value ? '#fff' : '#606266' }
    },
    legend: {
      top: 0,
      textStyle: { color: isDark.value ? '#98989D' : '#606266' }
    },
    grid: { top: 42, right: 20, bottom: 20, left: 20, containLabel: true },
    xAxis: {
      type: 'category',
      data: props.data.items.map(item => item.time),
      boundaryGap: false,
      axisLine: { lineStyle: { color: isDark.value ? '#333333' : '#E0E0E0' } },
      axisLabel: { color: isDark.value ? '#98989D' : '#909399' }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      splitLine: { lineStyle: { color: isDark.value ? '#333333' : '#F0F0F0' } },
      axisLabel: { color: isDark.value ? '#98989D' : '#909399' }
    },
    series: Object.entries(levelConfig).map(([name, config]) => ({
      name,
      type: 'line',
      smooth: true,
      symbol: 'none',
      lineStyle: { width: 2, color: config.color },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: `${config.color}55` },
          { offset: 1, color: `${config.color}05` }
        ])
      },
      data: props.data.items.map(item => item[config.key as keyof LogTrendItem] as number)
    }))
  })
}

const initChart = async () => {
  await nextTick()
  if (!chartRef.value) {
    return
  }
  chart = echarts.init(chartRef.value, isDark.value ? 'dark' : undefined, { renderer: 'canvas' })
  renderChart()
  window.addEventListener('resize', handleResize)
}

const handleResize = () => chart?.resize()

watch(() => props.data, () => {
  nextTick(() => {
    if (!chart && props.data.status === 'ready') {
      initChart()
      return
    }
    renderChart()
  })
}, { deep: true, immediate: true })
watch(isDark, () => {
  chart?.dispose()
  chart = null
  initChart()
})

onMounted(initChart)
onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
})
</script>

<style scoped lang="scss">
.trend-chart {
  width: 100%;
  height: 320px;
}
</style>
