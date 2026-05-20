<template>
  <Teleport to="body">
    <Transition name="palette-fade">
      <div v-if="visible" class="palette-overlay" @click.self="close">
        <div class="palette-container">
          <div class="palette-input-wrap">
            <el-icon class="search-icon"><Search /></el-icon>
            <input
              ref="inputRef"
              v-model="query"
              class="palette-input"
              placeholder="搜索页面、功能..."
              @keydown.esc="close"
              @keydown.up.prevent="moveUp"
              @keydown.down.prevent="moveDown"
              @keydown.enter="selectCurrent"
            />
            <kbd class="kbd-hint">ESC</kbd>
          </div>
          <div v-if="filteredItems.length" class="palette-list">
            <div
              v-for="(item, index) in filteredItems"
              :key="item.path"
              class="palette-item"
              :class="{ active: index === activeIndex }"
              @click="navigateTo(item)"
              @mouseenter="activeIndex = index"
            >
              <el-icon class="item-icon"><component :is="item.icon" /></el-icon>
              <div class="item-info">
                <span class="item-title">{{ item.title }}</span>
                <span v-if="item.group" class="item-group">{{ item.group }}</span>
              </div>
            </div>
          </div>
          <div v-else class="palette-empty">
            <span>无匹配结果</span>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  Search, DataBoard, MagicStick, TrendCharts, Bell,
  Setting, Connection, DataLine, Monitor
} from '@element-plus/icons-vue'

interface PaletteItem {
  title: string
  path: string
  icon: any
  group?: string
  keywords?: string[]
}

const router = useRouter()
const visible = ref(false)
const query = ref('')
const activeIndex = ref(0)
const inputRef = ref<HTMLInputElement>()

const items: PaletteItem[] = [
  { title: '监控大屏', path: '/', icon: DataBoard, group: '监控' },
  { title: '日志搜索', path: '/log-search', icon: Search, group: '日志', keywords: ['search', 'log'] },
  { title: '智能助手', path: '/agent', icon: MagicStick, group: 'AI', keywords: ['ai', 'chat', 'agent'] },
  { title: '趋势分析', path: '/trend-analysis', icon: TrendCharts, group: '分析' },
  { title: '链路分析', path: '/trace-analysis', icon: TrendCharts, group: '分析', keywords: ['trace'] },
  { title: '告警规则', path: '/alert/rules', icon: Bell, group: '告警' },
  { title: '告警历史', path: '/alert/history', icon: Bell, group: '告警' },
  { title: '主机管理', path: '/vector/machines', icon: Monitor, group: 'Vector' },
  { title: '配置管理', path: '/vector/configs', icon: Connection, group: 'Vector' },
  { title: '组件库', path: '/vector/components', icon: Connection, group: 'Vector' },
  { title: '可视化配置', path: '/vector/visual-configs', icon: Connection, group: 'Vector' },
  { title: '安装包管理', path: '/vector/packages', icon: Connection, group: 'Vector' },
  { title: '日志监控', path: '/vector/logs', icon: Connection, group: 'Vector' },
  { title: '数据源管理', path: '/datasources', icon: DataLine, group: '系统' },
  { title: '日志源管理', path: '/log-source', icon: Connection, group: '系统' },
  { title: '系统设置', path: '/settings', icon: Setting, group: '系统' },
]

const filteredItems = computed(() => {
  if (!query.value.trim()) return items
  const q = query.value.toLowerCase()
  return items.filter(item =>
    item.title.toLowerCase().includes(q) ||
    (item.group && item.group.toLowerCase().includes(q)) ||
    (item.keywords && item.keywords.some(k => k.includes(q)))
  )
})

watch(query, () => { activeIndex.value = 0 })

const open = () => {
  visible.value = true
  query.value = ''
  activeIndex.value = 0
  nextTick(() => inputRef.value?.focus())
}

const close = () => { visible.value = false }

const moveUp = () => {
  activeIndex.value = Math.max(0, activeIndex.value - 1)
}

const moveDown = () => {
  activeIndex.value = Math.min(filteredItems.value.length - 1, activeIndex.value + 1)
}

const selectCurrent = () => {
  const item = filteredItems.value[activeIndex.value]
  if (item) navigateTo(item)
}

const navigateTo = (item: PaletteItem) => {
  router.push(item.path)
  close()
}

const handleKeydown = (e: KeyboardEvent) => {
  if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
    e.preventDefault()
    visible.value ? close() : open()
  }
}

onMounted(() => document.addEventListener('keydown', handleKeydown))
onUnmounted(() => document.removeEventListener('keydown', handleKeydown))

defineExpose({ open })
</script>

<style scoped lang="scss">
.palette-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 20vh;
}

.palette-container {
  width: 560px;
  max-height: 420px;
  background: var(--macos-bg-primary);
  border: 1px solid var(--macos-border);
  border-radius: var(--macos-radius-lg);
  box-shadow: var(--macos-shadow-xl);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.palette-input-wrap {
  display: flex;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid var(--macos-border);
  gap: 10px;

  .search-icon {
    font-size: 20px;
    color: var(--macos-text-tertiary);
  }
}

.palette-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 16px;
  background: transparent;
  color: var(--macos-text-primary);

  &::placeholder {
    color: var(--macos-text-tertiary);
  }
}

.kbd-hint {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--macos-bg-tertiary);
  color: var(--macos-text-tertiary);
  border: 1px solid var(--macos-border);
}

.palette-list {
  overflow-y: auto;
  padding: 8px;
}

.palette-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: var(--macos-radius-sm);
  cursor: pointer;
  transition: background 0.1s;

  &.active {
    background: var(--macos-blue-light);
  }

  .item-icon {
    font-size: 18px;
    color: var(--macos-text-secondary);
  }

  .item-info {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .item-title {
    font-size: 14px;
    color: var(--macos-text-primary);
    font-weight: 500;
  }

  .item-group {
    font-size: 12px;
    color: var(--macos-text-tertiary);
  }
}

.palette-empty {
  padding: 32px;
  text-align: center;
  color: var(--macos-text-tertiary);
  font-size: 14px;
}

// Transition
.palette-fade-enter-active,
.palette-fade-leave-active {
  transition: opacity 0.15s ease;
}
.palette-fade-enter-from,
.palette-fade-leave-to {
  opacity: 0;
}
</style>
