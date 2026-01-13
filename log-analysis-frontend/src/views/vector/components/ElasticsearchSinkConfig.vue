<template>
  <div class="config-form">
    <el-form-item label="Endpoints">
      <el-input v-model="config.endpoints" placeholder="http://localhost:9200" @change="emit('change')" />
      <div class="hint">多个地址用逗号分隔</div>
    </el-form-item>
    <el-form-item label="索引名称">
      <el-input v-model="config.index" placeholder="logs-%Y-%m-%d" @change="emit('change')" />
    </el-form-item>
    <el-form-item label="用户名">
      <el-input v-model="config.auth_user" placeholder="elastic" @change="emit('change')" />
    </el-form-item>
    <el-form-item label="密码">
      <el-input v-model="config.auth_password" type="password" show-password @change="emit('change')" />
    </el-form-item>
    <el-form-item label="批量大小">
      <el-input-number v-model="config.batch_max_events" :min="1" :max="10000" @change="emit('change')" />
    </el-form-item>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'

const props = defineProps<{ modelValue: Record<string, any> }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: any): void; (e: 'change'): void }>()

const config = reactive({
  endpoints: '',
  index: 'logs-%Y-%m-%d',
  auth_user: '',
  auth_password: '',
  batch_max_events: 1000,
  ...props.modelValue
})

watch(config, (v) => emit('update:modelValue', { ...v }), { deep: true })
</script>

<style scoped>
.hint { font-size: 12px; color: #999; margin-top: 4px; }
</style>
