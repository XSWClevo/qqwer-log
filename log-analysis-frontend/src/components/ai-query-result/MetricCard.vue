<template>
  <div class="metric-card">
    <div class="metric-icon">
      <el-icon :size="48" :color="iconColor">
        <component :is="iconComponent" />
      </el-icon>
    </div>
    <div class="metric-value">
      {{ formattedValue }}
    </div>
    <div class="metric-label">
      {{ label }}
    </div>
    <div v-if="trend !== undefined" class="metric-trend" :class="trendClass">
      <el-icon>
        <CaretTop v-if="trend > 0" />
        <CaretBottom v-else-if="trend < 0" />
      </el-icon>
      {{ Math.abs(trend) }}% 较昨天
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { CaretTop, CaretBottom, TrendCharts, DataAnalysis, Odometer } from '@element-plus/icons-vue'

interface Props {
  value: number
  label: string
  trend?: number
  icon?: 'trend' | 'data' | 'odometer'
  color?: string
}

const props = withDefaults(defineProps<Props>(), {
  icon: 'trend',
  color: '#409EFF'
})

// 格式化数值
const formattedValue = computed(() => {
  const num = props.value
  if (num >= 1000000) {
    return (num / 1000000).toFixed(1) + 'M'
  } else if (num >= 1000) {
    return (num / 1000).toFixed(1) + 'K'
  }
  return num.toLocaleString()
})

// 图标组件
const iconComponent = computed(() => {
  switch (props.icon) {
    case 'data':
      return DataAnalysis
    case 'odometer':
      return Odometer
    default:
      return TrendCharts
  }
})

// 图标颜色
const iconColor = computed(() => props.color)

// 趋势样式
const trendClass = computed(() => {
  if (props.trend === undefined) return ''
  return props.trend > 0 ? 'trend-up' : 'trend-down'
})
</script>

<style scoped lang="scss">
.metric-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 16px;
  color: white;
  min-height: 200px;
  box-shadow: 0 8px 24px rgba(102, 126, 234, 0.3);
  transition: transform 0.3s ease, box-shadow 0.3s ease;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 12px 32px rgba(102, 126, 234, 0.4);
  }

  .metric-icon {
    margin-bottom: 16px;
    opacity: 0.9;
  }

  .metric-value {
    font-size: 48px;
    font-weight: 700;
    margin-bottom: 8px;
    text-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
  }

  .metric-label {
    font-size: 16px;
    opacity: 0.9;
    margin-bottom: 12px;
  }

  .metric-trend {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 14px;
    padding: 4px 12px;
    border-radius: 12px;
    background: rgba(255, 255, 255, 0.2);

    &.trend-up {
      color: var(--macos-success);
      background: var(--macos-success-light);
    }

    &.trend-down {
      color: var(--macos-danger);
      background: var(--macos-danger-light);
    }
  }
}
</style>
