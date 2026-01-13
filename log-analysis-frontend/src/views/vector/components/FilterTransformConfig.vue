<template>
  <div class="config-form">
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

const props = defineProps<{ modelValue: Record<string, any> }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: any): void; (e: 'change'): void }>()

const config = reactive({ condition: '', ...props.modelValue })

watch(config, (v) => emit('update:modelValue', { ...v }), { deep: true })
</script>

<style scoped>
.hint { font-size: 12px; color: #999; margin-top: 4px; }
:deep(textarea) { font-family: 'Monaco', 'Consolas', monospace; font-size: 12px; }
</style>
