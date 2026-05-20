<template>
  <div class="proc-node" :class="{ empty: steps.length === 0, selected: selected }">
    <Handle type="target" :position="Position.Left" class="handle-in" />
    <div class="proc-stripe"></div>
    <div class="proc-content">
      <div class="proc-title">PROCESSORS ({{ steps.length || '空' }})</div>
      <div class="proc-steps" v-if="steps.length > 0">
        <span v-for="(s, i) in steps" :key="s.id" class="proc-step-chip">
          {{ s.label }}<span v-if="i < steps.length - 1" class="proc-arrow"> → </span>
        </span>
      </div>
      <div class="proc-hint" v-else>
        选中后在右侧面板添加步骤
      </div>
    </div>
    <Handle type="source" :position="Position.Right" class="handle-out" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Handle, Position } from '@vue-flow/core'

const props = defineProps<{
  data: {
    category: string
    name: string
    isProcessorsContainer: boolean
    steps: Array<{ id: string; type: string; label: string; config: any; componentId?: string }>
    [key: string]: any
  }
  selected?: boolean
}>()

const steps = computed(() => props.data.steps || [])
</script>

<style scoped>
.proc-node {
  width: 220px;
  min-height: 64px;
  display: flex;
  align-items: stretch;
  background: var(--macos-bg-primary, #fff);
  border: 1.5px dashed var(--macos-border, #d1d5db);
  border-radius: 10px;
  overflow: hidden;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  cursor: pointer;
  transition: box-shadow 0.15s ease, border-color 0.15s ease;
}
.proc-node:hover {
  box-shadow: 0 2px 12px rgba(59, 130, 246, 0.1);
  border-color: #93c5fd;
}
.proc-node.selected {
  border-color: #3b82f6;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.2);
}
.proc-node.empty {
  opacity: 0.7;
}
.proc-stripe {
  width: 4px;
  flex-shrink: 0;
  background: #3b82f6;
}
.proc-content {
  flex: 1;
  padding: 10px 12px;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
}
.proc-title {
  font-size: 10px;
  font-weight: 700;
  color: var(--macos-text-secondary, #888);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.proc-steps {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0;
  font-size: 11px;
  font-weight: 500;
  color: var(--macos-text-primary, #333);
  line-height: 1.4;
}
.proc-step-chip {
  white-space: nowrap;
}
.proc-arrow {
  color: var(--macos-text-tertiary, #aaa);
  margin: 0 1px;
}
.proc-hint {
  font-size: 11px;
  color: var(--macos-text-tertiary, #aaa);
  font-style: italic;
}
.handle-in {
  background: #3b82f6 !important;
  width: 10px !important;
  height: 10px !important;
  border: 2px solid #fff !important;
}
.handle-out {
  background: #3b82f6 !important;
  width: 10px !important;
  height: 10px !important;
  border: 2px solid #fff !important;
}
</style>
