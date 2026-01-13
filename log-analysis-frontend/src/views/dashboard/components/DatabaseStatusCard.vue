<template>
  <el-card class="dashboard-card" v-loading="loading">
    <template #header>
      <div class="card-header">
        <span class="card-title">数据库状态</span>
        <el-tag :type="statusType" size="small">{{ statusText }}</el-tag>
      </div>
    </template>
    <div class="db-content">
      <!-- 集群状态 -->
      <div class="cluster-status">
        <div class="status-indicator" :class="data.clusterStatus"></div>
        <div class="status-text">
          <span class="status-label">ClickHouse 集群</span>
          <span class="status-value">{{ statusText }}</span>
        </div>
      </div>
      <!-- 存储占用 -->
      <div class="storage-info">
        <div class="storage-header">
          <span>存储占用</span>
          <span class="storage-value">{{ data.storageUsed }} / {{ data.storageTotal }} TB</span>
        </div>
        <el-progress :percentage="storagePercentage" :stroke-width="10" :color="storageColor" :show-text="false" />
      </div>
      <!-- 查询 TPS -->
      <div class="tps-info">
        <el-icon class="tps-icon"><DataLine /></el-icon>
        <div class="tps-text">
          <span class="tps-label">查询 TPS</span>
          <span class="tps-value">{{ data.queryTps }}</span>
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { DataLine } from '@element-plus/icons-vue'
import type { DatabaseStatus } from '../types'

const props = defineProps<{ data: DatabaseStatus; loading: boolean }>()

const statusType = computed(() => {
  const map = { healthy: 'success', warning: 'warning', error: 'danger' }
  return map[props.data.clusterStatus] || 'info'
})

const statusText = computed(() => {
  const map = { healthy: '健康', warning: '警告', error: '异常' }
  return map[props.data.clusterStatus] || '未知'
})

const storagePercentage = computed(() => {
  const used = parseFloat(props.data.storageUsed)
  const total = parseFloat(props.data.storageTotal)
  return Math.round((used / total) * 100)
})

const storageColor = computed(() => {
  if (storagePercentage.value < 60) return '#52C41A'
  if (storagePercentage.value < 80) return '#FAAD14'
  return '#FF4D4F'
})
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
.db-content { display: flex; flex-direction: column; gap: 16px; }
.cluster-status {
  display: flex; align-items: center; gap: 12px;
  .status-indicator { 
    width: 12px; height: 12px; border-radius: 50%; 
    &.healthy { background: #52C41A; box-shadow: 0 0 8px rgba(82, 196, 26, 0.5); } 
    &.warning { background: #FAAD14; } 
    &.error { background: #FF4D4F; } 
  }
  .status-text { 
    display: flex; flex-direction: column; 
    .status-label { font-size: 12px; color: var(--macos-text-secondary); } 
    .status-value { font-size: 14px; font-weight: 600; color: var(--macos-text-primary); } 
  }
}
.storage-info { 
  .storage-header { 
    display: flex; justify-content: space-between; margin-bottom: 8px; font-size: 13px; color: var(--macos-text-secondary); 
    .storage-value { font-weight: 600; color: var(--macos-text-primary); } 
  } 
}
.tps-info { 
  display: flex; align-items: center; gap: 12px; padding: 12px; 
  background: var(--macos-bg-secondary);
  border-radius: 8px; 
  border: 1px solid transparent;
  transition: var(--macos-transition-fast);

  &:hover {
     background: var(--macos-bg-tertiary);
     border-color: var(--macos-border);
  }

  .tps-icon { font-size: 24px; color: var(--macos-blue); } 
  .tps-text { 
    display: flex; flex-direction: column; 
    .tps-label { font-size: 12px; color: var(--macos-text-secondary); } 
    .tps-value { font-size: 18px; font-weight: 700; color: var(--macos-text-primary); font-family: 'SF Mono', monospace; } 
  } 
}
</style>
