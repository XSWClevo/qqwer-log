<template>
  <div class="empty-state">
    <div class="empty-illustration">
      <svg width="120" height="100" viewBox="0 0 120 100" fill="none" xmlns="http://www.w3.org/2000/svg">
        <rect x="20" y="20" width="80" height="60" rx="8" stroke="var(--macos-border)" stroke-width="2" fill="var(--macos-bg-tertiary)"/>
        <path d="M40 50h40M40 62h24" stroke="var(--macos-text-tertiary)" stroke-width="2" stroke-linecap="round"/>
        <circle cx="60" cy="38" r="6" stroke="var(--macos-text-tertiary)" stroke-width="2"/>
      </svg>
    </div>
    <h3 class="empty-title">{{ title || '暂无数据' }}</h3>
    <p v-if="description" class="empty-desc">{{ description }}</p>
    <div v-if="$slots.action" class="empty-action">
      <slot name="action" />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title?: string
  description?: string
}>()
</script>

<style scoped lang="scss">
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
}

.empty-illustration {
  margin-bottom: 16px;
  opacity: 0.6;
}

.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--macos-text-primary);
  margin: 0 0 8px;
}

.empty-desc {
  font-size: 14px;
  color: var(--macos-text-secondary);
  margin: 0 0 20px;
  max-width: 300px;
}

.empty-action {
  margin-top: 4px;
}
</style>
