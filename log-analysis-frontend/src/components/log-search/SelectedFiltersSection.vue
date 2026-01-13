<template>
  <div v-if="filters.length > 0" class="selected-filters-section">
    <!-- Section Header -->
    <div class="section-header" @click="toggleCollapse">
      <div class="header-left">
        <el-icon><Filter /></el-icon>
        <span class="section-title">已选筛选</span>
        <el-badge :value="filters.length" type="primary" class="filter-count" />
      </div>
      <div class="header-right">
        <el-button
          size="small"
          text
          type="danger"
          @click.stop="handleClearAll"
        >
          清除所有
        </el-button>
        <el-icon class="collapse-icon" :class="{ 'is-collapsed': isCollapsed }">
          <ArrowDown />
        </el-icon>
      </div>
    </div>

    <!-- Filter Tags -->
    <el-collapse-transition>
      <div v-show="!isCollapsed" class="section-content">
        <div class="filter-tags">
          <el-tag
            v-for="filter in filters"
            :key="filter.id"
            :type="filter.type === 'include' ? 'success' : 'danger'"
            closable
            @close="handleRemove(filter.id)"
            class="filter-tag"
          >
            <el-icon v-if="filter.type === 'include'" class="tag-icon"><ZoomIn /></el-icon>
            <el-icon v-else class="tag-icon"><Remove /></el-icon>
            <span class="tag-field">{{ filter.fieldLabel }}</span>
            <span class="tag-operator">{{ filter.type === 'include' ? '=' : '!=' }}</span>
            <span class="tag-value">{{ filter.value }}</span>
          </el-tag>
        </div>
      </div>
    </el-collapse-transition>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Filter, ArrowDown, ZoomIn, Remove } from '@element-plus/icons-vue'

interface ActiveFilter {
  id: string
  field: string
  fieldLabel: string
  value: string
  type: 'include' | 'exclude'
}

interface Props {
  filters: ActiveFilter[]
  collapsed?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  collapsed: false,
  filters: () => []
})

const emit = defineEmits<{
  remove: [filterId: string]
  'clear-all': []
  toggle: [collapsed: boolean]
}>()

const isCollapsed = ref(props.collapsed)

// Toggle collapse state
const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
  emit('toggle', isCollapsed.value)
}

// Remove single filter
const handleRemove = (filterId: string) => {
  emit('remove', filterId)
}

// Clear all filters
const handleClearAll = () => {
  emit('clear-all')
}
</script>

<style scoped lang="scss">
.selected-filters-section {
  border-bottom: 1px solid var(--macos-border);
  background: var(--macos-bg-secondary);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  cursor: pointer;
  transition: background 0.2s;

  &:hover {
    background: rgba(64, 158, 255, 0.08);
  }
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;

  .el-icon {
    color: #409EFF;
    font-size: 14px;
  }
}

.section-title {
  font-weight: 600;
  font-size: 13px;
  color: #409EFF;
}

.filter-count {
  :deep(.el-badge__content) {
    font-size: 10px;
    height: 16px;
    line-height: 16px;
    padding: 0 5px;
  }
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.collapse-icon {
  font-size: 12px;
  color: #909399;
  transition: transform 0.3s;

  &.is-collapsed {
    transform: rotate(-90deg);
  }
}

.section-content {
  padding: 8px 16px 12px;
}

.filter-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.filter-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  padding: 4px 8px;
  border-radius: 4px;

  .tag-icon {
    font-size: 12px;
  }

  .tag-field {
    font-weight: 500;
  }

  .tag-operator {
    color: inherit;
    opacity: 0.8;
  }

  .tag-value {
    max-width: 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}
</style>
