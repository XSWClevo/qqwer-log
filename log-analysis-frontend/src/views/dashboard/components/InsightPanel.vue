<template>
  <article class="insight-panel">
    <header class="panel-header">
      <div>
        <h3>{{ title }}</h3>
        <p v-if="description">{{ description }}</p>
      </div>
      <slot name="meta" />
    </header>

    <slot v-if="status === 'ready'" />

    <div v-else-if="status === 'loading'" class="panel-placeholder">
      <el-skeleton :rows="4" animated />
    </div>

    <EmptyState
      v-else
      :title="emptyTitle || '暂无数据'"
      :description="emptyDescription || '当前卡片没有可展示结果。'"
    />
  </article>
</template>

<script setup lang="ts">
import EmptyState from '@/components/common/EmptyState.vue'
import type { DashboardViewStatus } from '../types'

defineProps<{
  title: string
  description?: string
  status: DashboardViewStatus
  emptyTitle?: string
  emptyDescription?: string
}>()
</script>

<style scoped lang="scss">
.insight-panel {
  min-height: 300px;
  padding: 18px;
  border: 1px solid color-mix(in srgb, var(--macos-border) 76%, transparent);
  border-radius: 18px;
  background: linear-gradient(180deg, color-mix(in srgb, var(--macos-card-bg) 92%, transparent), color-mix(in srgb, var(--macos-bg-secondary) 95%, transparent));
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.05);
}

.panel-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;

  h3 {
    margin: 0;
    font-size: 16px;
    color: var(--macos-text-primary);
  }

  p {
    margin: 6px 0 0;
    font-size: 12px;
    line-height: 1.5;
    color: var(--macos-text-secondary);
  }
}

.panel-placeholder {
  padding-top: 16px;
}
</style>
