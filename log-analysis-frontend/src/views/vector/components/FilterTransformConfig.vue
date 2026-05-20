<template>
  <div class="config-form">
    <el-form-item v-if="props.context?.upstreamSourceComponentIds?.length" label="上游 Source 组件 ID">
      <div class="upstream-list">
        <el-tag
          v-for="sourceId in props.context.upstreamSourceComponentIds"
          :key="sourceId"
          size="small"
          type="info"
        >
          {{ sourceId }}
        </el-tag>
      </div>
      <div class="hint">当前 PROCESSORS 可追溯到的上游 Source 组件 ID</div>
    </el-form-item>
    <el-form-item label="过滤条件 (VRL)">
      <el-input
        v-model="config.condition"
        type="textarea"
        :rows="4"
        placeholder='.level == "error" || .level == "warn"'
        @change="emit('change')"
      />
      <div class="hint">返回 true 的事件将被保留</div>
    </el-form-item>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'

const props = defineProps<{
  modelValue: Record<string, any>
  context?: {
    upstreamSourceComponentIds?: string[]
    upstreamSourceNames?: string[]
  }
}>()
const emit = defineEmits<{ (e: 'update:modelValue', v: any): void; (e: 'change'): void }>()

const config = reactive({ condition: '', ...props.modelValue })

watch(config, (v) => emit('update:modelValue', { ...v }), { deep: true })
</script>

<style scoped>
.hint { font-size: 12px; color: #999; margin-top: 4px; }
.upstream-list { display: flex; flex-wrap: wrap; gap: 6px; }
:deep(textarea) { font-family: 'Monaco', 'Consolas', monospace; font-size: 12px; }
</style>
