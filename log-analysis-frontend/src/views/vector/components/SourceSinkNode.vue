<template>
  <div class="ss-node" :class="[category, { shared: isShared, selected: selected }]">
    <Handle v-if="category === 'sink'" id="input" type="target" :position="Position.Left" />
    <div class="ss-stripe" :style="{ background: color }"></div>
    <div class="ss-content">
      <div class="ss-type">{{ typeText }}</div>
      <div class="ss-name">{{ data.name }}</div>
    </div>
    <div v-if="isShared" class="ss-badge">{{ refCount > 9 ? '9+' : refCount }}</div>
    <Handle v-if="category === 'source'" id="output" type="source" :position="Position.Right" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'

const props = defineProps<{
  data: {
    category: string
    componentType: string
    name: string
    config: any
    isShared?: boolean
    referenceCount?: number
    [key: string]: any
  }
  selected?: boolean
}>()

const category = computed(() => props.data.category || 'source')
const compType = computed(() => (props.data.componentType || '').replace(`_${category.value}`, ''))
const isShared = computed(() => props.data.isShared || false)
const refCount = computed(() => props.data.referenceCount || 0)

const color = computed(() => category.value === 'source' ? '#22c55e' : '#8b5cf6')

const typeText = computed(() => {
  const label = category.value === 'source' ? 'SOURCE' : 'DESTINATION'
  return `${label} · ${compType.value}`
})
</script>

<style scoped>
.ss-node {
  width: 220px;
  height: 64px;
  display: flex;
  align-items: stretch;
  background: var(--macos-bg-primary, #fff);
  border: 1px solid var(--macos-border, #e5e7eb);
  border-radius: 10px;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  transition: box-shadow 0.15s ease, border-color 0.15s ease;
  position: relative;
}
.ss-node:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}
.ss-node.selected {
  border-color: currentColor;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.2);
}
.ss-node.source.selected { border-color: #22c55e; }
.ss-node.sink.selected { border-color: #8b5cf6; }
.ss-stripe {
  width: 4px;
  flex-shrink: 0;
}
.ss-content {
  flex: 1;
  padding: 10px 12px;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
}
.ss-type {
  font-size: 10px;
  font-weight: 700;
  color: var(--macos-text-secondary, #888);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.ss-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--macos-text-primary, #333);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ss-badge {
  position: absolute;
  top: 4px;
  right: 8px;
  background: #f59e0b;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  border-radius: 8px;
  padding: 1px 5px;
  line-height: 1.4;
}
</style>
