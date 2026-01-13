<template>
  <div class="config-form">
    <el-form-item label="文件路径">
      <el-input v-model="config.include" placeholder="/var/log/**/*.log" @change="emit('change')" />
      <div class="hint">支持 glob 模式，多个路径用逗号分隔</div>
    </el-form-item>
    <el-form-item label="排除路径">
      <el-input v-model="config.exclude" placeholder="/var/log/debug/**" @change="emit('change')" />
    </el-form-item>
    <el-form-item label="读取模式">
      <el-select v-model="config.read_from" @change="emit('change')">
        <el-option label="从头读取" value="beginning" />
        <el-option label="从末尾读取" value="end" />
      </el-select>
    </el-form-item>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'

const props = defineProps<{ modelValue: Record<string, any> }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: any): void; (e: 'change'): void }>()

const config = reactive({ include: '', exclude: '', read_from: 'beginning', ...props.modelValue })

watch(config, (v) => emit('update:modelValue', { ...v }), { deep: true })
</script>

<style scoped>
.hint { font-size: 12px; color: #999; margin-top: 4px; }
</style>
