<template>
  <el-card class="dashboard-card" v-loading="loading">
    <template #header>
      <div class="card-header">
        <span class="card-title">日志管道</span>
        <el-tag type="primary" size="small">实时</el-tag>
      </div>
    </template>
    <div class="pipeline-content">
      <!-- 摄入速率趋势 -->
      <div class="trend-section">
        <div class="section-label">实时摄入速率</div>
        <div ref="trendChartRef" class="trend-chart"></div>
      </div>
      <!-- 处理延迟 -->
      <div class="delay-section">
        <div class="delay-label">处理延迟</div>
        <div class="delay-value" :class="getDelayClass(data.processingDelay)">
          {{ data.processingDelay }}<span class="delay-unit">ms</span>
        </div>
        <div class="delay-status">
          <el-icon v-if="data.processingDelay < 50" class="status-icon success"><CircleCheck /></el-icon>
          <el-icon v-else class="status-icon warning"><Warning /></el-icon>
          <span>{{ data.processingDelay < 50 ? '正常' : '偏高' }}</span>
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { CircleCheck, Warning } from '@element-plus/icons-vue'
import type { LogPipeline } from '../types'

const props = defineProps<{ data: LogPipeline; loading: boolean }>()

const trendChartRef = ref<HTMLElement>()
let trendChart: echarts.ECharts | null = null

const getDelayClass = (delay: number) => delay < 30 ? 'low' : delay < 50 ? 'medium' : 'high'

const initChart = () => {
  if (!trendChartRef.value) return
  trendChart = echarts.init(trendChartRef.value)
  updateChart()
}

const updateChart = () => {
  if (!trendChart) return
  trendChart.setOption({
    grid: { top: 5, right: 5, bottom: 20, left: 35 },
    xAxis: { type: 'category', data: props.data.ingestRateTimes, axisLine: { show: false }, axisTick: { show: false }, axisLabel: { fontSize: 10, color: '#909399' } },
    yAxis: { type: 'value', axisLine: { show: false }, axisTick: { show: false }, splitLine: { lineStyle: { color: '#F0F0F0' } }, axisLabel: { fontSize: 10, color: '#909399' } },
    series: [{
      type: 'line',
      data: props.data.ingestRate,
      smooth: true,
      symbol: 'none',
      lineStyle: { color: '#1890FF', width: 2 },
      areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(24, 144, 255, 0.3)' }, { offset: 1, color: 'rgba(24, 144, 255, 0.05)' }]) }
    }]
  })
}

watch(() => props.data, updateChart, { deep: true })
onMounted(initChart)
onUnmounted(() => trendChart?.dispose())
</script>

<style scoped lang="scss">
.dashboard-card {
  height: 100%;
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
    transform: translateY(-2px);
  }
}
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-size: 16px; font-weight: 600; color: var(--macos-text-primary); }
.pipeline-content { display: flex; flex-direction: column; gap: 16px; }
.trend-section { .section-label { font-size: 12px; color: var(--macos-text-secondary); margin-bottom: 8px; } }
.trend-chart { height: 80px; }
.delay-section {
  display: flex; flex-direction: column; align-items: center; padding: 12px;
  background: var(--macos-bg-secondary);
  border-radius: 8px;
  border: 1px solid transparent;
  transition: var(--macos-transition-fast);
  
  &:hover {
     background: var(--macos-bg-tertiary);
     border-color: var(--macos-border);
  }

  .delay-label { font-size: 12px; color: var(--macos-text-secondary); }
  .delay-value {
    font-size: 32px; font-weight: 700; font-family: 'SF Mono', monospace;
    &.low { color: #52C41A; }
    &.medium { color: #FAAD14; }
    &.high { color: #FF4D4F; }
    .delay-unit { font-size: 14px; font-weight: 400; margin-left: 4px; }
  }
  .delay-status {
    display: flex; align-items: center; gap: 4px; font-size: 12px; color: var(--macos-text-secondary);
    .status-icon { font-size: 14px; &.success { color: #52C41A; } &.warning { color: #FAAD14; } }
  }
}
</style>
