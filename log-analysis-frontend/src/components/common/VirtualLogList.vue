<template>
  <div
    ref="containerRef"
    class="virtual-log-list"
    :style="{ height: height + 'px' }"
    @scroll="onScroll"
  >
    <div class="virtual-spacer" :style="{ height: totalHeight + 'px' }"></div>
    <div class="virtual-viewport" :style="{ transform: `translateY(${offsetY}px)` }">
      <div
        v-for="item in visibleItems"
        :key="item._index"
        class="virtual-item"
        :style="{ height: itemHeight + 'px' }"
        @click="$emit('row-click', item)"
      >
        <slot :item="item" :index="item._index" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'

interface Props {
  items: any[]
  itemHeight?: number
  height?: number
  buffer?: number
}

const props = withDefaults(defineProps<Props>(), {
  itemHeight: 40,
  height: 600,
  buffer: 5
})

defineEmits<{ (e: 'row-click', item: any): void }>()

const containerRef = ref<HTMLElement>()
const scrollTop = ref(0)

const totalHeight = computed(() => props.items.length * props.itemHeight)

const startIndex = computed(() => {
  return Math.max(0, Math.floor(scrollTop.value / props.itemHeight) - props.buffer)
})

const endIndex = computed(() => {
  const visibleCount = Math.ceil(props.height / props.itemHeight)
  return Math.min(props.items.length, startIndex.value + visibleCount + props.buffer * 2)
})

const offsetY = computed(() => startIndex.value * props.itemHeight)

const visibleItems = computed(() => {
  return props.items.slice(startIndex.value, endIndex.value).map((item, i) => ({
    ...item,
    _index: startIndex.value + i
  }))
})

const onScroll = () => {
  if (containerRef.value) {
    scrollTop.value = containerRef.value.scrollTop
  }
}

// 当数据变化时重置滚动位置
watch(() => props.items.length, () => {
  if (containerRef.value) {
    containerRef.value.scrollTop = 0
    scrollTop.value = 0
  }
})
</script>

<style scoped lang="scss">
.virtual-log-list {
  overflow-y: auto;
  position: relative;
  border: 1px solid var(--macos-border);
  border-radius: var(--macos-radius-sm);
  background: var(--macos-bg-primary);

  &::-webkit-scrollbar {
    width: 6px;
  }
  &::-webkit-scrollbar-thumb {
    background: rgba(0, 0, 0, 0.15);
    border-radius: 3px;
  }
}

.virtual-spacer {
  width: 100%;
  position: absolute;
  top: 0;
  left: 0;
  pointer-events: none;
}

.virtual-viewport {
  position: relative;
}

.virtual-item {
  display: flex;
  align-items: center;
  padding: 0 12px;
  border-bottom: 1px solid var(--macos-border);
  cursor: pointer;
  transition: background 0.1s;
  overflow: hidden;

  &:hover {
    background: var(--macos-blue-light);
  }
}
</style>
