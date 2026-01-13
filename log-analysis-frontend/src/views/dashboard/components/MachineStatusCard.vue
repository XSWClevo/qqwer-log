<template>
  <el-card class="dashboard-card" v-loading="loading">
    <template #header>
      <div class="card-header">
        <span class="card-title">机器状态</span>
        <el-tag type="success" size="small">运行中</el-tag>
      </div>
    </template>
    <div class="machine-status">
      <!-- CPU 仪表盘 -->
      <div class="gauge-container">
        <div ref="cpuGaugeRef" class="gauge-chart"></div>
        <div class="gauge-label">CPU</div>
      </div>
      <!-- 内存进度条 -->
      <div class="progress-item">
        <div class="progress-header">
          <span>内存使用率</span>
          <span class="progress-value">{{ data.memoryUsage }}%</span>
        </div>
        <el-progress 
          :percentage="data.memoryUsage" 
          :stroke-width="8"
          :color="getProgressColor(data.memoryUsage)"
          :show-text="false"
        />
      </div>
      <!-- 磁盘空间 -->
      <div class="disk-info">
        <el-icon class="disk-icon"><Folder /></el-icon>
        <div class="disk-text">
          <span class="disk-label">磁盘剩余</span>
          <span class="disk-value">{{ data.diskFree }} / {{ data.diskTotal }} GB</span>
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, onUnmounted, computed } from 'vue'
import * as echarts from 'echarts'
import { useDark } from '@vueuse/core'
import { Folder } from '@element-plus/icons-vue'
import type { MachineStatus } from '../types'

const props = defineProps<{ data: MachineStatus; loading: boolean }>()

const cpuGaugeRef = ref<HTMLElement>()
let cpuChart: echarts.ECharts | null = null
const isDark = useDark()

const trackColor = computed(() => isDark.value ? '#333333' : '#E6E6E6')
const valueColor = computed(() => isDark.value ? '#F5F5F7' : '#1D1D1F')

const getProgressColor = (percentage: number) => {
  if (percentage < 60) return '#52C41A'
  if (percentage < 80) return '#FAAD14'
  return '#FF4D4F'
}

const initChart = () => {
  if (!cpuGaugeRef.value) return
  cpuChart = echarts.init(cpuGaugeRef.value, isDark.value ? 'dark' : undefined, {
    renderer: 'canvas'
  })
  updateChart()
}

const updateChart = () => {
  if (!cpuChart) return
  cpuChart.setOption({
    backgroundColor: 'transparent',
    series: [{
      type: 'gauge',
      startAngle: 200,
      endAngle: -20,
      min: 0,
      max: 100,
      splitNumber: 5,
      radius: '90%',
      itemStyle: { color: props.data.cpuUsage < 60 ? '#1890FF' : props.data.cpuUsage < 80 ? '#FAAD14' : '#FF4D4F' },
      progress: { show: true, width: 12 },
      pointer: { show: false },
      axisLine: { lineStyle: { width: 12, color: [[1, trackColor.value]] } },
      axisTick: { show: false },
      splitLine: { show: false },
      axisLabel: { show: false },
      title: { show: false },
      detail: { 
        valueAnimation: true, 
        fontSize: 20, 
        fontWeight: 'bold', 
        color: valueColor.value, 
        offsetCenter: [0, '10%'], 
        formatter: '{value}%' 
      },
      data: [{ value: props.data.cpuUsage }]
    }]
  })
}

watch(() => props.data.cpuUsage, updateChart)
watch(isDark, () => {
  if (cpuChart) {
    cpuChart.dispose()
    initChart()
  }
})

onMounted(initChart)
onUnmounted(() => cpuChart?.dispose())
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

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--macos-text-primary);
}

.machine-status {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.gauge-container {
  text-align: center;
}

.gauge-chart {
  height: 100px;
}

.gauge-label {
  font-size: 12px;
  color: var(--macos-text-secondary);
  margin-top: -10px;
}

.progress-item {
  .progress-header {
    display: flex;
    justify-content: space-between;
    margin-bottom: 6px;
    font-size: 13px;
    color: var(--macos-text-secondary);
  }
  .progress-value {
    font-weight: 600;
    color: var(--macos-text-primary);
  }
}

.disk-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: var(--macos-bg-secondary);
  border-radius: 8px;
  border: 1px solid transparent;
  transition: var(--macos-transition-fast);

  &:hover {
     background: var(--macos-bg-tertiary);
     border-color: var(--macos-border);
  }
}

.disk-icon {
  font-size: 24px;
  color: var(--macos-blue);
}

.disk-text {
  display: flex;
  flex-direction: column;
  .disk-label { font-size: 12px; color: var(--macos-text-secondary); }
  .disk-value { font-size: 14px; font-weight: 600; color: var(--macos-text-primary); }
}
</style>
