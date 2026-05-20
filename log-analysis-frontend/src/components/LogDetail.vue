<template>
  <el-dialog
    v-model="visible"
    title="日志详情"
    width="90%"
    :close-on-click-modal="false"
  >
    <el-descriptions :column="2" border size="default">
      <!-- 时间戳 -->
      <el-descriptions-item label="时间戳" :span="2">
        <el-text>{{ log?.timestamp }}</el-text>
      </el-descriptions-item>

      <!-- 可筛选字段 -->
      <el-descriptions-item label="级别">
        <div class="field-row">
          <el-tag :type="getLevelType(log?.severity)">{{ log?.severity }}</el-tag>
          <el-button-group size="small" class="ml-2">
            <el-tooltip content="筛选此值">
              <el-button :icon="Filter" size="small" @click="addFilter('severity', log?.severity)" />
            </el-tooltip>
            <el-tooltip content="排除此值">
              <el-button :icon="Remove" size="small" @click="excludeFilter('severity', log?.severity)" />
            </el-tooltip>
          </el-button-group>
        </div>
      </el-descriptions-item>

      <el-descriptions-item label="来源">
        <div class="field-row">
          <el-text>{{ log?.source_type }}</el-text>
          <el-button-group size="small" class="ml-2">
            <el-button :icon="Filter" size="small" @click="addFilter('source_type', log?.source_type)" />
            <el-button :icon="Remove" size="small" @click="excludeFilter('source_type', log?.source_type)" />
          </el-button-group>
        </div>
      </el-descriptions-item>

      <el-descriptions-item label="主机">
        <div class="field-row">
          <el-text>{{ log?.hostname }}</el-text>
          <el-button-group size="small" class="ml-2">
            <el-button :icon="Filter" size="small" @click="addFilter('hostname', log?.hostname)" />
            <el-button :icon="Remove" size="small" @click="excludeFilter('hostname', log?.hostname)" />
          </el-button-group>
        </div>
      </el-descriptions-item>

      <el-descriptions-item label="服务">
        <div class="field-row">
          <el-text>{{ log?.appname }}</el-text>
          <el-button-group size="small" class="ml-2">
            <el-button :icon="Filter" size="small" @click="addFilter('appname', log?.appname)" />
            <el-button :icon="Remove" size="small" @click="excludeFilter('appname', log?.appname)" />
          </el-button-group>
        </div>
      </el-descriptions-item>

      <el-descriptions-item v-if="log?.facility" label="Facility">
        <div class="field-row">
          <el-text>{{ log?.facility }}</el-text>
          <el-button-group size="small" class="ml-2">
            <el-button :icon="Filter" size="small" @click="addFilter('facility', log?.facility)" />
            <el-button :icon="Remove" size="small" @click="excludeFilter('facility', log?.facility)" />
          </el-button-group>
        </div>
      </el-descriptions-item>

      <el-descriptions-item v-if="log?.procid" label="进程ID">
        <div class="field-row">
          <el-text>{{ log?.procid }}</el-text>
          <el-button-group size="small" class="ml-2">
            <el-button :icon="Filter" size="small" @click="addFilter('procid', log?.procid)" />
            <el-button :icon="Remove" size="small" @click="excludeFilter('procid', log?.procid)" />
          </el-button-group>
        </div>
      </el-descriptions-item>

      <el-descriptions-item v-if="log?.source_ip" label="来源IP" :span="2">
        <div class="field-row">
          <el-text>{{ log?.source_ip }}</el-text>
          <el-button-group size="small" class="ml-2">
            <el-button :icon="Filter" size="small" @click="addFilter('source_ip', log?.source_ip)" />
            <el-button :icon="Remove" size="small" @click="excludeFilter('source_ip', log?.source_ip)" />
          </el-button-group>
        </div>
      </el-descriptions-item>

      <!-- 只读字段 -->
      <el-descriptions-item label="日志ID" :span="2">
        <el-text class="text-sm text-gray-500">{{ log?.id }}</el-text>
      </el-descriptions-item>

      <el-descriptions-item label="消息内容" :span="2">
        <div class="message-box">
          <pre>{{ log?.message }}</pre>
        </div>
      </el-descriptions-item>

      <el-descriptions-item v-if="log?.raw" label="原始日志" :span="2">
        <div class="message-box">
          <pre>{{ log?.raw }}</pre>
        </div>
      </el-descriptions-item>
    </el-descriptions>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button type="primary" @click="viewContext">
        <el-icon class="mr-1"><Timer /></el-icon>
        查看上下文
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Filter, Remove, Timer } from '@element-plus/icons-vue'
import type { LogItem } from '@/types/log'
import { ElMessage } from 'element-plus'

interface Props {
  modelValue: boolean
}

const emit = defineEmits(['update:modelValue', 'addFilter', 'excludeFilter', 'viewContext'])

const visible = ref(false)
const log = ref<LogItem | null>(null)

// 打开详情
const open = (logItem: LogItem) => {
  log.value = logItem
  visible.value = true
}

// 获取级别标签类型
const getLevelType = (level?: string) => {
  if (!level) return ''
  const levelUpper = level.toUpperCase()
  if (levelUpper === 'ERROR' || levelUpper === 'CRITICAL') return 'danger'
  if (levelUpper === 'WARN') return 'warning'
  if (levelUpper === 'INFO') return 'success'
  return 'info'
}

// 添加筛选
const addFilter = (field: string, value?: string) => {
  if (!value) {
    ElMessage.warning('字段值为空，无法添加筛选')
    return
  }
  emit('addFilter', { field, value })
  ElMessage.success(`已添加筛选: ${field} = ${value}`)
}

// 添加排除
const excludeFilter = (field: string, value?: string) => {
  if (!value) {
    ElMessage.warning('字段值为空，无法添加排除')
    return
  }
  emit('excludeFilter', { field, value })
  ElMessage.success(`已添加排除: ${field} != ${value}`)
}

// 查看上下文
const viewContext = () => {
  if (log.value) {
    emit('viewContext', log.value)
    visible.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.field-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ml-2 {
  margin-left: 8px;
}

.mr-1 {
  margin-right: 4px;
}

.message-box {
  max-height: 400px;
  overflow-y: auto;
  background-color: var(--macos-fill-secondary);
  padding: 12px;
  border-radius: 4px;
}

.message-box pre {
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
  font-family: 'Courier New', Courier, monospace;
  font-size: 13px;
  line-height: 1.5;
}

.text-sm {
  font-size: 12px;
}

.text-gray-500 {
  color: var(--macos-text-tertiary);
}
</style>
