<template>
  <el-card class="dashboard-card" v-loading="loading">
    <template #header>
      <div class="card-header">
        <span class="card-title">Top 频繁异常</span>
      </div>
    </template>
    <div class="exception-list">
      <div v-for="(item, index) in data" :key="index" class="exception-item">
        <div class="exception-rank" :class="getRankClass(index)">{{ index + 1 }}</div>
        <div class="exception-info">
          <div class="exception-name">{{ item.className }}</div>
          <div class="exception-service">{{ item.service }}</div>
        </div>
        <div class="exception-count">{{ formatCount(item.count) }}</div>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import type { ExceptionItem } from '../types'

defineProps<{ data: ExceptionItem[]; loading: boolean }>()

const getRankClass = (index: number) => {
  if (index === 0) return 'first'
  if (index === 1) return 'second'
  if (index === 2) return 'third'
  return ''
}

const formatCount = (count: number) => {
  if (count >= 1000) return (count / 1000).toFixed(1) + 'K'
  return count.toString()
}
</script>

<style scoped lang="scss">
@use '@/assets/styles/macos.scss' as *;

.dashboard-card {
  height: 320px;
  border-radius: var(--macos-radius-lg);
  border: 1px solid var(--macos-border);
  box-shadow: var(--macos-shadow-sm);
  background: var(--macos-glass-bg);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  overflow: hidden;
  transition: var(--macos-transition);

  &:hover {
    box-shadow: var(--macos-shadow-md);
    border-color: var(--macos-border-hover);
  }
}
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-size: 16px; font-weight: 600; color: var(--macos-text-primary); }
.exception-list { 
  height: calc(100% - 60px); 
  overflow-y: auto; 
  display: flex; 
  flex-direction: column; 
  gap: 8px; 
  @include macos-scrollbar;
}
.exception-item {
  display: flex; align-items: center; gap: 12px; padding: 8px 12px; 
  background: var(--macos-bg-secondary);
  border-radius: 6px; 
  border: 1px solid transparent;
  transition: var(--macos-transition-fast);
  
  &:hover { 
    background: var(--macos-bg-tertiary); 
    border-color: var(--macos-border);
  }
}
.exception-rank {
  width: 24px; height: 24px; border-radius: 6px; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 600; 
  background: var(--macos-border);
  color: var(--macos-text-secondary);
  
  &.first { background: #FF4D4F; color: #FFF; }
  &.second { background: #FAAD14; color: #FFF; }
  &.third { background: #1890FF; color: #FFF; }
}
.exception-info { 
  flex: 1; min-width: 0; 
  .exception-name { font-size: 13px; font-weight: 500; color: var(--macos-text-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; font-family: 'SF Mono', monospace; } 
  .exception-service { font-size: 11px; color: var(--macos-text-secondary); } 
}
.exception-count { font-size: 14px; font-weight: 600; color: #FF4D4F; font-family: 'SF Mono', monospace; }
</style>
