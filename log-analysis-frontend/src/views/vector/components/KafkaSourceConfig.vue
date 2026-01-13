<template>
  <div class="config-form">
    <el-form-item label="Bootstrap Servers">
      <el-input v-model="config.bootstrap_servers" placeholder="localhost:9092" @change="emit('change')" />
    </el-form-item>
    <el-form-item label="Topic">
      <el-input v-model="config.topics" placeholder="logs" @change="emit('change')" />
    </el-form-item>
    <el-form-item label="Group ID">
      <el-input v-model="config.group_id" placeholder="vector-consumer" @change="emit('change')" />
    </el-form-item>
    <el-form-item label="Auto Offset Reset">
      <el-select v-model="config.auto_offset_reset" @change="emit('change')">
        <el-option label="earliest" value="earliest" />
        <el-option label="latest" value="latest" />
      </el-select>
    </el-form-item>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'

const props = defineProps<{ modelValue: Record<string, any> }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: any): void; (e: 'change'): void }>()

const config = reactive({
  bootstrap_servers: '',
  topics: '',
  group_id: '',
  auto_offset_reset: 'earliest',
  ...props.modelValue
})

watch(config, (v) => emit('update:modelValue', { ...v }), { deep: true })
</script>
