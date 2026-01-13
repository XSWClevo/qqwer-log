<template>
  <el-card class="dashboard-card chart-card" v-loading="loading">
    <template #header>
      <div class="card-header">
        <span class="card-title">级别分布</span>
      </div>
    </template>
    <div ref="chartRef" class="donut-chart"></div>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, onUnmounted, computed } from 'vue'
import * as echarts from 'echarts'
import { useDark } from '@vueuse/core'
import type { LevelDistribution } from '../types'

const props = defineProps<{ data: LevelDistribution[]; loading: boolean }>()

const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null
const isDark = useDark()

const legendColor = computed(() => isDark.value ? '#98989D' : '#606266')
const centerLabelColor = computed(() => isDark.value ? '#F5F5F7' : '#1D1D1F')

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
  const total = props.data.reduce((sum, d) => sum + d.count, 0)
  
  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: { 
      trigger: 'item', 
      formatter: '{b}: {c} ({d}%)',
      backgroundColor: isDark.value ? 'rgba(30,30,30,0.9)' : '#fff',
      borderColor: isDark.value ? '#4C4D4F' : '#e4e7ed',
      textStyle: {
        color: isDark.value ? '#fff' : '#606266'
      }
    },
    legend: { 
      orient: 'vertical', 
      right: 10, 
      top: 'center', 
      itemWidth: 10, 
      itemHeight: 10, 
      textStyle: { fontSize: 12, color: legendColor.value } 
    },
    series: [{
      type: 'pie', radius: ['50%', '70%'], center: ['35%', '50%'], avoidLabelOverlap: false,
      label: { 
        show: true, 
        position: 'center', 
        formatter: () => `总计\n${(total / 1000).toFixed(0)}K`, 
        fontSize: 14, 
        fontWeight: 'bold', 
        color: centerLabelColor.value 
      },
      emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
      labelLine: { show: false },
      data: props.data.map(d => ({ value: d.count, name: d.severity, itemStyle: { color: d.color } }))
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
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-size: 16px; font-weight: 600; color: var(--macos-text-primary); }
.donut-chart { height: calc(100% - 60px); }
</style>
