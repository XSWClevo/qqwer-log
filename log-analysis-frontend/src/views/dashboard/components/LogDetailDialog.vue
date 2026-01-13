<template>
  <el-dialog v-model="dialogVisible" title="日志详情" width="700px" :close-on-click-modal="false">
    <div v-if="log" class="log-detail">
      <div class="detail-header">
        <el-tag :type="getLevelType(log.severity)" size="large" effect="dark">{{ log.severity }}</el-tag>
        <span class="detail-time">{{ formatTime(log.timestamp) }}</span>
      </div>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="主机">{{ log.hostname }}</el-descriptions-item>
        <el-descriptions-item label="应用">{{ log.appname }}</el-descriptions-item>
        <el-descriptions-item label="消息" :span="2">{{ log.message }}</el-descriptions-item>
      </el-descriptions>
      <div class="raw-data-section">
        <div class="section-title">
          <span>原始数据</span>
          <el-button-group size="small">
            <el-button :type="viewMode === 'raw' ? 'primary' : ''" @click="viewMode = 'raw'">Raw</el-button>
            <el-button :type="viewMode === 'json' ? 'primary' : ''" @click="viewMode = 'json'">JSON</el-button>
          </el-button-group>
        </div>
        <pre class="raw-content">{{ viewMode === 'json' ? formatJson(log.rawData) : log.rawData }}</pre>
      </div>
    </div>
    <template #footer>
      <el-button @click="dialogVisible = false">关闭</el-button>
      <el-button type="primary" @click="copyToClipboard">复制</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import type { LogRecord } from '../types'

const props = defineProps<{ visible: boolean; log: LogRecord | null }>()
const emit = defineEmits<{ (e: 'update:visible', val: boolean): void }>()

const viewMode = ref<'raw' | 'json'>('json')

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

const formatTime = (timestamp: string) => new Date(timestamp).toLocaleString('zh-CN')

const getLevelType = (level: string) => {
  const map: Record<string, string> = { INFO: 'primary', WARN: 'warning', ERROR: 'danger', FATAL: '' }
  return map[level] || 'info'
}

const formatJson = (raw?: string) => {
  if (!raw) return ''
  try { return JSON.stringify(JSON.parse(raw), null, 2) } catch { return raw }
}

const copyToClipboard = () => {
  if (!props.log) return
  const text = viewMode.value === 'json' ? formatJson(props.log.rawData) : props.log.rawData
  navigator.clipboard.writeText(text || '').then(() => ElMessage.success('已复制到剪贴板'))
}
</script>

<style scoped lang="scss">
.log-detail { display: flex; flex-direction: column; gap: 16px; }
.detail-header { display: flex; align-items: center; gap: 12px; .detail-time { font-size: 14px; color: #909399; } }
.raw-data-section { .section-title { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; font-size: 14px; font-weight: 600; color: #1D1D1F; } }
.raw-content { background: #1E1E1E; color: #D4D4D4; padding: 16px; border-radius: 8px; font-family: 'SF Mono', Consolas, monospace; font-size: 12px; line-height: 1.6; overflow-x: auto; max-height: 300px; margin: 0; }
</style>
