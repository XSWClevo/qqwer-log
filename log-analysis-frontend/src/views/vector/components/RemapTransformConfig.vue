<template>
  <div class="config-form">
    <el-form-item label="VRL 脚本">
      <el-input
        v-model="config.source"
        type="textarea"
        :rows="8"
        placeholder=".timestamp = now()
.host = get_hostname!()
del(.raw)"
        @change="emit('change')"
      />
      <div class="hint">使用 Vector Remap Language (VRL) 进行数据转换</div>
    </el-form-item>
    <el-form-item label="丢弃失败事件">
      <el-switch v-model="config.drop_on_error" @change="emit('change')" />
    </el-form-item>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'

const props = defineProps<{ modelValue: Record<string, any> }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: any): void; (e: 'change'): void }>()

const config = reactive({ source: '', drop_on_error: false, ...props.modelValue })

watch(config, (v) => emit('update:modelValue', { ...v }), { deep: true })
</script>

<style scoped>
.hint { font-size: 12px; color: #999; margin-top: 4px; }
:deep(textarea) { font-family: 'Monaco', 'Consolas', monospace; font-size: 12px; }
</style>
