<template>
  <InsightPanel
    title="级别分布"
    description="高优先级日志占比与整体分布。"
    :status="status"
    empty-title="暂无级别分布"
    :empty-description="emptyText || '当前时间范围内没有可聚合的级别数据。'"
  >
    <div ref="chartRef" class="distribution-chart"></div>
  </InsightPanel>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useDark } from '@vueuse/core'
import echarts from '@/utils/echarts'
import InsightPanel from './InsightPanel.vue'
import type { DashboardPanelState, LevelDistribution } from '../types'

const props = defineProps<{
  data: DashboardPanelState<LevelDistribution>
}>()

const chartRef = ref<HTMLElement>()
const isDark = useDark()
const status = computed(() => props.data.status)
const emptyText = computed(() => props.data.emptyText)
let chart: echarts.ECharts | null = null

const renderChart = () => {
  if (!chart || props.data.status !== 'ready' || !props.data.items.length) {
    return
  }

  const total = props.data.items.reduce((sum, item) => sum + item.count, 0)
  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      backgroundColor: isDark.value ? 'rgba(30,30,30,0.9)' : '#fff',
      borderColor: isDark.value ? '#4C4D4F' : '#e4e7ed',
      textStyle: { color: isDark.value ? '#fff' : '#606266' }
    },
    legend: {
      bottom: 0,
      textStyle: { color: isDark.value ? '#98989D' : '#606266' }
    },
    series: [
      {
        type: 'pie',
        radius: ['54%', '76%'],
        center: ['50%', '44%'],
        label: {
          show: true,
          position: 'center',
          formatter: `总量\n${total.toLocaleString()}`,
          fontSize: 16,
          color: isDark.value ? '#F5F5F7' : '#1D1D1F',
          fontWeight: 'bold'
        },
        labelLine: { show: false },
        data: props.data.items.map(item => ({
          value: item.count,
          name: item.severity,
          itemStyle: { color: item.color }
        }))
      }
    ]
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
.distribution-chart {
  width: 100%;
  height: 320px;
}
</style>
