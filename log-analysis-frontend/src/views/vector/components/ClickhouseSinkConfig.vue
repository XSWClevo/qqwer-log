<template>
  <div class="config-form">
    <!-- 基础配置 -->
    <el-form-item label="Endpoint">
      <el-input v-model="config.endpoint" placeholder="http://localhost:8123" @change="emitChange" />
    </el-form-item>
    <el-form-item label="数据库">
      <el-input v-model="config.database" placeholder="default" @change="emitChange" />
    </el-form-item>
    <el-form-item label="表名">
      <el-input v-model="config.table" placeholder="logs" @change="emitChange" />
    </el-form-item>

    <!-- 数据格式 -->
    <el-divider content-position="left">数据格式</el-divider>
    <el-form-item label="格式">
      <el-select v-model="config.format" @change="emitChange">
        <el-option value="json_each_row" label="JSONEachRow" />
        <el-option value="csv" label="CSV" />
        <el-option value="tsv" label="TSV" />
      </el-select>
    </el-form-item>
    <el-form-item label="压缩">
      <el-select v-model="config.compression" @change="emitChange">
        <el-option value="none" label="无" />
        <el-option value="gzip" label="Gzip" />
        <el-option value="lz4" label="LZ4" />
        <el-option value="zstd" label="Zstd" />
      </el-select>
    </el-form-item>
    <el-form-item label="跳过未知字段">
      <el-switch v-model="config.skip_unknown_fields" @change="emitChange" />
    </el-form-item>

    <!-- 编码配置 -->
    <el-divider content-position="left">编码</el-divider>
    <el-form-item label="时间戳格式">
      <el-select v-model="encoding.timestamp_format" @change="emitChange">
        <el-option value="unix" label="Unix 时间戳" />
        <el-option value="rfc3339" label="RFC3339" />
      </el-select>
    </el-form-item>

    <!-- 批处理配置 -->
    <el-divider content-position="left">批处理</el-divider>
    <el-form-item label="最大字节数">
      <el-input v-model="batch.max_bytes" placeholder="10000000" @change="emitChange">
        <template #append>bytes</template>
      </el-input>
    </el-form-item>
    <el-form-item label="超时时间">
      <el-input v-model="batch.timeout_secs" placeholder="10" @change="emitChange">
        <template #append>秒</template>
      </el-input>
    </el-form-item>

    <!-- 缓冲配置 -->
    <el-divider content-position="left">缓冲</el-divider>
    <el-form-item label="缓冲类型">
      <el-select v-model="buffer.type" @change="emitChange">
        <el-option value="memory" label="内存" />
        <el-option value="disk" label="磁盘" />
      </el-select>
    </el-form-item>
    <el-form-item label="最大事件数">
      <el-input v-model="buffer.max_events" placeholder="500000" @change="emitChange" />
    </el-form-item>

    <!-- 认证配置 -->
    <el-divider content-position="left">认证</el-divider>
    <el-form-item label="认证方式">
      <el-select v-model="auth.strategy" @change="emitChange">
        <el-option value="none" label="无" />
        <el-option value="basic" label="Basic" />
      </el-select>
    </el-form-item>
    <template v-if="auth.strategy === 'basic'">
      <el-form-item label="用户名">
        <el-input v-model="auth.user" placeholder="default" @change="emitChange" />
      </el-form-item>
      <el-form-item label="密码">
        <el-input v-model="auth.password" type="password" show-password @change="emitChange" />
      </el-form-item>
    </template>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch, onMounted } from 'vue'

const props = defineProps<{ modelValue: Record<string, any> }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: any): void; (e: 'change'): void }>()

const config = reactive({
  endpoint: '',
  database: 'default',
  table: '',
  format: 'json_each_row',
  compression: 'gzip',
  skip_unknown_fields: true
})

const encoding = reactive({
  timestamp_format: 'unix'
})

const batch = reactive({
  max_bytes: '10000000',
  timeout_secs: '10'
})

const buffer = reactive({
  type: 'memory',
  max_events: '500000'
})

const auth = reactive({
  strategy: 'basic',
  user: '',
  password: ''
})

// 从 modelValue 初始化
onMounted(() => {
  if (props.modelValue) {
    Object.assign(config, {
      endpoint: props.modelValue.endpoint || '',
      database: props.modelValue.database || 'default',
      table: props.modelValue.table || '',
      format: props.modelValue.format || 'json_each_row',
      compression: props.modelValue.compression || 'gzip',
      skip_unknown_fields: props.modelValue.skip_unknown_fields !== false
    })
    if (props.modelValue.encoding) {
      Object.assign(encoding, props.modelValue.encoding)
    }
    if (props.modelValue.batch) {
      Object.assign(batch, {
        max_bytes: String(props.modelValue.batch.max_bytes || '10000000'),
        timeout_secs: String(props.modelValue.batch.timeout_secs || '10')
      })
    }
    if (props.modelValue.buffer) {
      Object.assign(buffer, {
        type: props.modelValue.buffer.type || 'memory',
        max_events: String(props.modelValue.buffer.max_events || '500000')
      })
    }
    if (props.modelValue.auth) {
      Object.assign(auth, props.modelValue.auth)
    }
  }
})

watch(() => props.modelValue, (newVal) => {
  if (newVal) {
    Object.assign(config, {
      endpoint: newVal.endpoint || config.endpoint,
      database: newVal.database || config.database,
      table: newVal.table || config.table,
      format: newVal.format || config.format,
      compression: newVal.compression || config.compression,
      skip_unknown_fields: newVal.skip_unknown_fields !== false
    })
    if (newVal.encoding) Object.assign(encoding, newVal.encoding)
    if (newVal.batch) {
      batch.max_bytes = String(newVal.batch.max_bytes || batch.max_bytes)
      batch.timeout_secs = String(newVal.batch.timeout_secs || batch.timeout_secs)
    }
    if (newVal.buffer) {
      buffer.type = newVal.buffer.type || buffer.type
      buffer.max_events = String(newVal.buffer.max_events || buffer.max_events)
    }
    if (newVal.auth) Object.assign(auth, newVal.auth)
  }
}, { deep: true })

const emitChange = () => {
  const result: Record<string, any> = {
    endpoint: config.endpoint,
    database: config.database,
    table: config.table,
    format: config.format,
    compression: config.compression,
    skip_unknown_fields: config.skip_unknown_fields,
    encoding: { ...encoding },
    batch: {
      max_bytes: parseInt(batch.max_bytes) || 10000000,
      timeout_secs: parseInt(batch.timeout_secs) || 10
    },
    buffer: {
      type: buffer.type,
      max_events: parseInt(buffer.max_events) || 500000
    }
  }
  
  if (auth.strategy !== 'none') {
    result.auth = {
      strategy: auth.strategy,
      user: auth.user,
      password: auth.password
    }
  }
  
  emit('update:modelValue', result)
  emit('change')
}
</script>

<style scoped>
.config-form :deep(.el-divider) {
  margin: 16px 0 12px;
}
.config-form :deep(.el-divider__text) {
  font-size: 12px;
  color: #909399;
}
</style>
