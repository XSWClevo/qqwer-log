<template>
  <el-card class="dashboard-card" v-loading="loading">
    <template #header>
      <div class="card-header">
        <span class="card-title">活跃主机 Top 10</span>
      </div>
    </template>
    <div ref="chartRef" class="bar-chart"></div>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, onUnmounted, computed } from 'vue'
import echarts from '@/utils/echarts'
import { useDark } from '@vueuse/core'
import type { TopItem } from '../types'

const props = defineProps<{ data: TopItem[]; loading: boolean }>()

const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null
const isDark = useDark()

const chartTextColor = computed(() => isDark.value ? '#98989D' : '#606266')
const labelColor = computed(() => isDark.value ? '#6E6E73' : '#909399')

const initChart = () => {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value, isDark.value ? 'dark' : undefined, {
    renderer: 'canvas'
  })
  updateChart()
  window.addEventListener('resize', handleResize)
}

const handleResize = () => chart?.resize()

const updateChart = () => {
  if (!chart || !props.data.length) return
  const sortedData = [...props.data].reverse()
  
  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: { 
      trigger: 'axis', 
      axisPointer: { type: 'shadow' },
      backgroundColor: isDark.value ? 'rgba(30,30,30,0.9)' : '#fff',
      borderColor: isDark.value ? '#4C4D4F' : '#e4e7ed',
      textStyle: {
        color: isDark.value ? '#fff' : '#606266'
      }
    },
    grid: { top: 10, right: 60, bottom: 10, left: 100, containLabel: false },
    xAxis: { type: 'value', axisLine: { show: false }, axisTick: { show: false }, axisLabel: { show: false }, splitLine: { show: false } },
    yAxis: { 
      type: 'category', 
      data: sortedData.map(d => d.name), 
      axisLine: { show: false }, 
      axisTick: { show: false }, 
      axisLabel: { color: chartTextColor.value, fontSize: 11 } 
    },
    series: [{
      type: 'bar', 
      data: sortedData.map(d => d.count), 
      barWidth: 12, 
      itemStyle: { 
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: '#1890FF' }, 
          { offset: 1, color: '#69C0FF' }
        ]), 
        borderRadius: [0, 6, 6, 0] 
      },
      label: { 
        show: true, 
        position: 'right', 
        formatter: (p: any) => (p.value / 1000).toFixed(0) + 'K', 
        fontSize: 11, 
        color: labelColor.value 
      }
    }]
  })
}

watch(() => props.data, updateChart, { deep: true })
watch(isDark, () => {
  if (chart) {
    chart.dispose()
    initChart()
  }
})

onMounted(initChart)
onUnmounted(() => { chart?.dispose(); window.removeEventListener('resize', handleResize) })
</script>

<style scoped lang="scss">
.dashboard-card {
  height: 320px;
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
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-size: 16px; font-weight: 600; color: var(--macos-text-primary); }
.bar-chart { height: calc(100% - 60px); }
</style>
