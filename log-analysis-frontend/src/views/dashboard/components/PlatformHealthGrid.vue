<template>
  <section class="metric-section">
    <header class="section-header">
      <div>
        <span class="section-eyebrow">Platform Health</span>
        <h2>平台健康</h2>
      </div>
    </header>

    <div class="metric-grid">
      <article
        v-for="metric in metrics"
        :key="metric.key"
        class="metric-card"
        :class="metric.tone || 'neutral'"
      >
        <div class="metric-header">
          <span>{{ metric.label }}</span>
          <em v-if="metric.badge">{{ metric.badge }}</em>
        </div>
        <strong>{{ metric.value }}</strong>
        <p v-if="metric.hint">{{ metric.hint }}</p>
        <small v-if="metric.footnote">{{ metric.footnote }}</small>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { DashboardMetricCard } from '../types'

defineProps<{ metrics: DashboardMetricCard[] }>()
</script>

<style scoped lang="scss">
.metric-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: end;

  h2 {
    margin: 4px 0 0;
    font-size: 17px;
    color: var(--macos-text-primary);
  }
}

.section-eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--macos-text-tertiary);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.metric-card {
  min-height: 132px;
  padding: 16px;
  border-radius: 18px;
  border: 1px solid color-mix(in srgb, var(--macos-border) 76%, transparent);
  background: linear-gradient(180deg, color-mix(in srgb, var(--macos-card-bg) 92%, transparent), color-mix(in srgb, var(--macos-bg-secondary) 94%, transparent));
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);

  strong {
    display: block;
    margin-top: 12px;
    font-size: clamp(22px, 2.4vw, 32px);
    line-height: 1.05;
    color: var(--macos-text-primary);
  }

  p,
  small {
    display: block;
    margin: 12px 0 0;
    line-height: 1.6;
  }

  p {
    font-size: 13px;
    color: var(--macos-text-secondary);
  }

  small {
    font-size: 12px;
    color: var(--macos-text-tertiary);
  }
}

.metric-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;

  span {
    font-size: 13px;
    color: var(--macos-text-secondary);
  }

  em {
    padding: 4px 8px;
    border-radius: 999px;
    background: var(--macos-bg-secondary);
    font-style: normal;
    font-size: 11px;
    color: var(--macos-text-primary);
  }
}

.metric-card.primary {
  background: linear-gradient(160deg, color-mix(in srgb, var(--macos-blue) 11%, var(--macos-card-bg)), var(--macos-card-bg));
}

.metric-card.success {
  background: linear-gradient(160deg, color-mix(in srgb, var(--macos-success) 11%, var(--macos-card-bg)), var(--macos-card-bg));
}

.metric-card.warning {
  background: linear-gradient(160deg, color-mix(in srgb, var(--macos-warning) 12%, var(--macos-card-bg)), var(--macos-card-bg));
}

.metric-card.danger {
  background: linear-gradient(160deg, color-mix(in srgb, var(--macos-danger) 12%, var(--macos-card-bg)), var(--macos-card-bg));
}

@media (max-width: 1280px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
