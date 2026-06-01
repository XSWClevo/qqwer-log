<template>
  <section class="metric-deck" data-testid="interactive-metric-deck">
    <button
      v-for="metric in metrics"
      :key="metric.key"
      type="button"
      class="metric-card"
      :class="[`tone-${metric.tone || 'neutral'}`, { active: metric.key === selectedMetricKey }]"
      @click="$emit('metric-select', metric.key)"
    >
      <span class="metric-label">{{ metric.label }}</span>
      <strong>{{ metric.value }}</strong>
      <p>{{ metric.hint || metric.contextValue || '点击查看下钻' }}</p>
      <span v-if="metric.delta || metric.badge" class="metric-chip">{{ metric.delta || metric.badge }}</span>
    </button>
  </section>
</template>

<script setup lang="ts">
import type { DashboardMetricCard } from '../types'

defineProps<{
  metrics: DashboardMetricCard[]
  selectedMetricKey: string
}>()

defineEmits<{
  (e: 'metric-select', metricKey: string): void
}>()
</script>

<style scoped lang="scss">
.metric-deck {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.metric-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 136px;
  padding: 14px;
  border: 1px solid color-mix(in srgb, var(--macos-border) 72%, transparent);
  border-radius: 8px;
  background: color-mix(in srgb, var(--macos-card-bg) 90%, transparent);
  text-align: left;
  cursor: pointer;
  transition: transform 0.22s ease, box-shadow 0.22s ease, border-color 0.22s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 12px 24px rgba(15, 23, 42, 0.10);
  }

  &:focus-visible {
    outline: 2px solid color-mix(in srgb, var(--macos-blue) 72%, transparent);
    outline-offset: 2px;
  }

  &.active {
    border-color: color-mix(in srgb, var(--macos-blue) 62%, transparent);
    box-shadow: inset 0 2px 0 color-mix(in srgb, var(--macos-blue) 48%, transparent), 0 12px 28px color-mix(in srgb, var(--macos-blue) 16%, transparent);
    transform: translateY(-2px);
  }

  strong {
    font-size: 22px;
    line-height: 1.1;
    color: var(--macos-text-primary);
  }

  p {
    margin: 0;
    font-size: 12px;
    line-height: 1.6;
    color: var(--macos-text-secondary);
  }
}

.metric-label {
  font-size: 12px;
  letter-spacing: 0;
  color: color-mix(in srgb, var(--macos-text-secondary) 88%, transparent);
}

.metric-chip {
  align-self: flex-start;
  margin-top: auto;
  padding: 5px 8px;
  border-radius: 6px;
  font-size: 11px;
  color: var(--macos-text-primary);
  background: color-mix(in srgb, var(--macos-bg-secondary) 92%, transparent);
}

.tone-primary.active { border-color: color-mix(in srgb, var(--macos-blue) 72%, transparent); }
.tone-success.active { border-color: color-mix(in srgb, #14b8a6 68%, transparent); }
.tone-warning.active { border-color: color-mix(in srgb, #f59e0b 68%, transparent); }
.tone-danger.active { border-color: color-mix(in srgb, #ef4444 68%, transparent); }

@media (max-width: 1280px) {
  .metric-deck {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .metric-deck {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .metric-card {
    transition: none;

    &:hover,
    &.active {
      transform: none;
    }
  }
}
</style>
