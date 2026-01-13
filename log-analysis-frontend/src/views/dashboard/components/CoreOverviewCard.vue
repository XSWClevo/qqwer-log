<template>
  <el-card class="dashboard-card" v-loading="loading">
    <template #header>
      <div class="card-header">
        <span class="card-title">核心概览</span>
      </div>
    </template>
    <div class="overview-content">
      <!-- 今日总日志量 -->
      <div class="total-logs">
        <div class="total-label">今日总日志量</div>
        <div class="total-value">{{ formatNumber(data.todayTotal) }}</div>
      </div>
      <!-- 错误率分布 -->
      <div class="rate-distribution">
        <div class="rate-item">
          <div class="rate-bar info" :style="{ width: data.infoRate + '%' }"></div>
          <div class="rate-info">
            <span class="rate-label">INFO</span>
            <span class="rate-value">{{ data.infoRate.toFixed(1) }}%</span>
          </div>
        </div>
        <div class="rate-item">
          <div class="rate-bar warn" :style="{ width: data.warnRate * 10 + '%' }"></div>
          <div class="rate-info">
            <span class="rate-label">WARN</span>
            <span class="rate-value">{{ data.warnRate.toFixed(1) }}%</span>
          </div>
        </div>
        <div class="rate-item">
          <div class="rate-bar error" :style="{ width: data.errorRate * 20 + '%' }"></div>
          <div class="rate-info">
            <span class="rate-label">ERROR</span>
            <span class="rate-value">{{ data.errorRate.toFixed(2) }}%</span>
          </div>
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import type { CoreOverview } from '../types'

defineProps<{ data: CoreOverview; loading: boolean }>()

const formatNumber = (num: number) => {
  if (num >= 1000000) return (num / 1000000).toFixed(2) + 'M'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
  return num.toString()
}
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
.overview-content { display: flex; flex-direction: column; gap: 20px; }
.total-logs {
  text-align: center; padding: 16px; 
  background: linear-gradient(135deg, var(--macos-blue) 0%, #0051D5 100%); 
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(24, 144, 255, 0.3);
  
  .total-label { font-size: 12px; color: rgba(255, 255, 255, 0.9); margin-bottom: 4px; }
  .total-value { font-size: 28px; font-weight: 700; color: #FFFFFF; font-family: 'SF Mono', monospace; }
}
.rate-distribution { display: flex; flex-direction: column; gap: 10px; }
.rate-item {
  .rate-bar {
    height: 6px; border-radius: 3px; margin-bottom: 4px; transition: width 0.3s ease;
    &.info { background: #1890FF; }
    &.warn { background: #FAAD14; }
    &.error { background: #FF4D4F; }
  }
  .rate-info { 
    display: flex; justify-content: space-between; font-size: 12px; 
    .rate-label { color: var(--macos-text-secondary); } 
    .rate-value { font-weight: 600; color: var(--macos-text-primary); } 
  }
}
</style>
