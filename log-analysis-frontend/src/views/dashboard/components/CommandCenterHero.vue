<template>
  <section class="hero-shell">
    <div class="hero-copy">
      <div class="eyebrow">
        <span class="eyebrow-dot"></span>
        科技指挥中心
      </div>
      <h1>日志运营总览</h1>
      <p>
        以当前数据集为中心，把链路健康、风险级别、热点主机和异常模式集中到一个决策舞台。
      </p>

      <div class="hero-tags">
        <span class="hero-tag">
          数据集
          <strong>{{ datasetContext?.datasourceName || '未选择' }}</strong>
        </span>
        <span class="hero-tag">
          表
          <strong>{{ datasetContext?.tableName || '--' }}</strong>
        </span>
        <span class="hero-tag">
          更新时间
          <strong>{{ lastUpdatedLabel }}</strong>
        </span>
      </div>
    </div>

    <div class="hero-matrix">
      <article v-for="metric in spotlightMetrics" :key="metric.key" class="matrix-card">
        <span class="matrix-label">{{ metric.label }}</span>
        <strong>{{ metric.value }}</strong>
        <p>{{ metric.contextValue || metric.hint || '暂无说明' }}</p>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { DashboardDatasetContext, DashboardMetricCard } from '../types'

const props = defineProps<{
  datasetContext: DashboardDatasetContext | null
  platformMetrics: DashboardMetricCard[]
  lastUpdatedLabel: string
}>()

const spotlightMetrics = computed(() => props.platformMetrics.slice(0, 4))
</script>

<style scoped lang="scss">
.hero-shell {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(300px, 1fr);
  gap: 18px;
  padding: 18px;
  border: 1px solid color-mix(in srgb, var(--macos-border) 72%, transparent);
  border-radius: 8px;
  background:
    linear-gradient(90deg, color-mix(in srgb, var(--macos-blue) 16%, transparent), transparent 36%),
    linear-gradient(270deg, color-mix(in srgb, #14b8a6 12%, transparent), transparent 28%),
    linear-gradient(135deg, color-mix(in srgb, var(--macos-card-bg) 90%, transparent), color-mix(in srgb, var(--macos-bg-secondary) 96%, transparent));
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.10);
  overflow: hidden;
}

.hero-copy {
  display: flex;
  flex-direction: column;
  gap: 14px;

  h1 {
    margin: 0;
    font-size: 34px;
    line-height: 1.12;
    letter-spacing: 0;
    color: var(--macos-text-primary);
  }

  p {
    max-width: 720px;
    margin: 0;
    font-size: 14px;
    line-height: 1.75;
    color: var(--macos-text-secondary);
  }
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  letter-spacing: 0;
  text-transform: uppercase;
  color: color-mix(in srgb, var(--macos-text-secondary) 88%, transparent);
}

.eyebrow-dot {
  width: 10px;
  height: 10px;
  border-radius: 2px;
  background: linear-gradient(135deg, var(--macos-blue), #14b8a6);
  box-shadow: 0 0 18px color-mix(in srgb, var(--macos-blue) 56%, transparent);
}

.hero-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 10px;
}

.hero-tag {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  background: color-mix(in srgb, var(--macos-card-bg) 84%, transparent);
  color: var(--macos-text-secondary);
  font-size: 12px;

  strong {
    color: var(--macos-text-primary);
    font-weight: 600;
  }
}

.hero-matrix {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.matrix-card {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 8px;
  min-height: 120px;
  padding: 14px;
  border: 1px solid color-mix(in srgb, var(--macos-border) 66%, transparent);
  border-radius: 8px;
  background: color-mix(in srgb, var(--macos-card-bg) 88%, transparent);
  backdrop-filter: blur(16px);

  strong {
    font-size: 26px;
    line-height: 1;
    color: var(--macos-text-primary);
  }

  p {
    margin: 0;
    font-size: 12px;
    line-height: 1.6;
    color: var(--macos-text-secondary);
  }
}

.matrix-label {
  font-size: 12px;
  letter-spacing: 0;
  text-transform: uppercase;
  color: color-mix(in srgb, var(--macos-text-secondary) 92%, transparent);
}

@media (max-width: 960px) {
  .hero-shell {
    grid-template-columns: 1fr;
  }

  .hero-matrix {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 720px) {
  .hero-shell {
    padding: 18px;
    border-radius: 8px;
  }

  .hero-matrix {
    grid-template-columns: 1fr;
  }
}
</style>
