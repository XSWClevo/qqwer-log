<template>
  <div class="field-facet-panel">
    <!-- Panel Header -->
    <div class="panel-header">
      <span class="panel-title" @click="toggleCollapse">{{ fieldLabel }}</span>
      <div class="panel-actions">
        <el-tooltip content="查看时序图表" placement="top">
          <el-icon class="chart-icon" @click.stop="showChart">
            <DataAnalysis />
          </el-icon>
        </el-tooltip>
        <el-tooltip :content="isPinned ? '已固定' : '固定到统计视图'" placement="top">
          <el-icon 
            class="pin-icon" 
            :class="{ 'is-pinned': isPinned }"
            @click.stop="!isPinned && pinChart()"
          >
            <StarFilled v-if="isPinned" />
            <Star v-else />
          </el-icon>
        </el-tooltip>
        <el-icon class="collapse-icon" :class="{ 'is-collapsed': isCollapsed }" @click="toggleCollapse">
          <ArrowDown />
        </el-icon>
      </div>
    </div>

    <!-- Panel Content -->
    <el-collapse-transition>
      <div v-show="!isCollapsed" class="panel-content">
        <el-scrollbar max-height="240px">
          <div
            v-for="(item, index) in sortedValues"
            :key="item.value"
            class="field-value-row"
            :class="{ 'field-value-row-divider': index < sortedValues.length - 1 }"
            @click="handleFilter(item.value, 'include')"
          >
            <div class="value-info">
              <span class="value-text" :title="item.value">{{ item.value }}</span>
              <div class="value-stats">
                <span class="value-count">{{ formatCount(item.count) }}</span>
                <span class="value-percent">{{ calculatePercent(item.count) }}%</span>
              </div>
            </div>
            <div class="value-bar">
              <div
                class="value-bar-fill"
                :style="{ width: calculatePercent(item.count) + '%' }"
              ></div>
            </div>
            <!-- Hover Actions -->
            <div class="value-actions">
              <el-tooltip content="筛选此值" placement="top">
                <el-button
                  size="small"
                  text
                  type="primary"
                  @click.stop="handleFilter(item.value, 'include')"
                  class="field-select-btn"
                >
                  筛选
                </el-button>
              </el-tooltip>
              <el-tooltip content="排除此值" placement="top">
                <el-button
                  size="small"
                  text
                  type="danger"
                  class="field-select-btn"
                  @click.stop="handleFilter(item.value, 'exclude')"
                >
                  排除
                </el-button>
              </el-tooltip>
            </div>
          </div>
          <div v-if="sortedValues.length === 0" class="empty-state">
            暂无数据
          </div>
        </el-scrollbar>
      </div>
    </el-collapse-transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ArrowDown, DataAnalysis, Star, StarFilled } from '@element-plus/icons-vue'

interface FieldValue {
  value: string
  count: number
}

interface Props {
  fieldName: string
  fieldLabel: string
  values: FieldValue[]
  totalCount: number
  collapsed?: boolean
  isPinned?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  collapsed: false,
  values: () => [],
  totalCount: 0,
  isPinned: false
})

const emit = defineEmits<{
  filter: [field: string, value: string, type: 'include' | 'exclude']
  toggle: [collapsed: boolean]
  'show-chart': [fieldName: string, fieldLabel: string]
  'pin-chart': [fieldName: string, fieldLabel: string]
}>()

const isCollapsed = ref(props.collapsed)

// Sort values by count descending
const sortedValues = computed(() => {
  return [...props.values].sort((a, b) => b.count - a.count)
})

// Calculate percentage
const calculatePercent = (count: number): number => {
  if (props.totalCount === 0) return 0
  return Math.round((count / props.totalCount) * 100)
}

// Format count with thousands separator
const formatCount = (count: number): string => {
  return count.toLocaleString()
}

// Toggle collapse state
const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
  emit('toggle', isCollapsed.value)
}

// Handle filter action
const handleFilter = (value: string, type: 'include' | 'exclude') => {
  emit('filter', props.fieldName, value, type)
}

// Show chart
const showChart = () => {
  emit('show-chart', props.fieldName, props.fieldLabel)
}

// Pin chart
const pinChart = () => {
  emit('pin-chart', props.fieldName, props.fieldLabel)
}
</script>

<style scoped lang="scss">
.field-facet-panel {
  border-bottom: 1px solid var(--macos-border);

  &:last-child {
    border-bottom: none;
  }
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  padding: 12px 16px;
  background: var(--macos-bg-secondary);
  transition: background 0.2s;
  &:hover {
    background: var(--macos-bg-tertiary);
  }
}

.panel-title {
  font-weight: 600;
  font-size: 13px;
  color: var(--macos-text-primary);
  cursor: pointer;
  flex: 1;
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chart-icon, .pin-icon {
  font-size: 14px;
  color: var(--macos-text-tertiary);
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    color: var(--macos-blue);
    transform: scale(1.1);
  }
}

.pin-icon:hover {
  color: var(--macos-warning);
}

.pin-icon.is-pinned {
  color: var(--macos-warning);
  cursor: default;
  
  &:hover {
    transform: none;
  }
}

.collapse-icon {
  font-size: 12px;
  color: var(--macos-text-tertiary);
  cursor: pointer;
  transition: transform 0.3s;

  &.is-collapsed {
    transform: rotate(-90deg);
  }
}

.panel-content {
  padding: 8px 0;
}

.field-value-row {
  display: flex;
  flex-direction: column;
  padding: 8px 16px;
  cursor: pointer;
  transition: background 0.15s;
  position: relative;

  &:hover {
    background: rgba(64, 158, 255, 0.06);

    .value-actions {
      opacity: 1;
    }
  }

  &.field-value-row-divider {
    border-bottom: 1px solid var(--macos-border);
  }
}

.value-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.value-text {
  font-size: 13px;
  color: var(--macos-text-primary);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding-right: 8px;
}

.value-stats {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.value-count {
  font-size: 13px;
  font-weight: 600;
  color: var(--macos-blue);
}

.value-percent {
  font-size: 11px;
  font-weight: 500;
  color: var(--macos-text-secondary);
  background: rgba(64, 158, 255, 0.1);
  padding: 2px 6px;
  border-radius: 10px;
  min-width: 36px;
  text-align: center;
}

.value-bar {
  height: 4px;
  background: rgba(64, 158, 255, 0.1);
  border-radius: 2px;
  overflow: hidden;
}

.value-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #409EFF 0%, #66b1ff 100%);
  border-radius: 2px;
  transition: width 0.4s ease;
}

.value-actions {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s;
  background: var(--macos-bg-secondary);
  padding: 4px 6px;
  border-radius: 4px;
  box-shadow: var(--macos-shadow-sm);

  :deep(.el-button) {
    padding: 4px 12px;
    font-size: 12px;
    
    
    &.el-button--danger {
      color: #F56C6C !important;
      
      &:hover {
        background: rgba(245, 108, 108, 0.1);
        color: #f78989 !important;
      }
    }
  }
}

.empty-state {
  padding: 20px;
  text-align: center;
  color: var(--macos-text-tertiary);
  font-size: 13px;
}

.field-select-btn {
  color: #ffffff;
  font-size: 12px;
  padding: 4px 8px;
}

</style>
