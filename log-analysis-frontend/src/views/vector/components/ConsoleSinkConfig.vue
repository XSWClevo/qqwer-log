<template>
  <div class="config-form">
    <el-form-item label="输出目标">
      <el-select v-model="config.target" @change="emit('change')">
        <el-option label="stdout" value="stdout" />
        <el-option label="stderr" value="stderr" />
      </el-select>
    </el-form-item>
    <el-form-item label="编码格式">
      <el-select v-model="config.encoding_codec" @change="emit('change')">
        <el-option label="JSON" value="json" />
        <el-option label="Text" value="text" />
      </el-select>
    </el-form-item>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'

const props = defineProps<{ modelValue: Record<string, any> }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: any): void; (e: 'change'): void }>()

const config = reactive({ target: 'stdout', encoding_codec: 'json', ...props.modelValue })

watch(config, (v) => emit('update:modelValue', { ...v }), { deep: true })
</script>
