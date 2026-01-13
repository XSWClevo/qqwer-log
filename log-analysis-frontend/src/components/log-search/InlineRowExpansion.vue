<template>
  <div class="inline-row-expansion">
    <el-tabs v-model="currentTab" class="expansion-tabs">
      <!-- Parsed Fields Tab -->
      <el-tab-pane label="解析字段" name="parsed">
        <div class="parsed-fields-list">
          <div
            v-for="field in parsedFields"
            :key="field.key"
            class="field-row"
          >
            <div class="field-key">{{ field.key }}</div>
            <div class="field-value">{{ field.value }}</div>
            <div class="field-actions">
              <el-button
                size="small"
                type="primary"
                @click="handleFilter(field.key, field.value, 'include')"
              >
                <el-icon><ZoomIn /></el-icon>
                筛选
              </el-button>
              <el-button
                size="small"
                type="danger"
                @click="handleFilter(field.key, field.value, 'exclude')"
              >
                <el-icon><Remove /></el-icon>
                排除
              </el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- Raw Log Tab -->
      <el-tab-pane label="原始日志" name="raw">
        <div class="raw-log-container">
          <div class="raw-log-header">
            <el-button size="small" @click="copyRawLog">
              <el-icon><CopyDocument /></el-icon>
              复制
            </el-button>
          </div>
          <pre class="raw-log-content">{{ rawContent }}</pre>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ZoomIn, Remove, CopyDocument } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

import type { LogEntry } from '@/types/log'

interface ParsedField {
  key: string
  value: string
}

interface Props {
  log: LogEntry
  activeTab?: 'parsed' | 'raw'
}

const props = withDefaults(defineProps<Props>(), {
  activeTab: 'parsed'
})

const emit = defineEmits<{
  filter: [field: string, value: string, type: 'include' | 'exclude']
  'update:activeTab': [tab: 'parsed' | 'raw']
}>()

const currentTab = ref(props.activeTab)

// Extract parsed fields from log object
const parsedFields = computed<ParsedField[]>(() => {
  const fields: ParsedField[] = []
  const excludeKeys = ['raw'] // Keys to exclude from parsed fields
  
  for (const [key, value] of Object.entries(props.log)) {
    if (excludeKeys.includes(key)) continue
    if (value === null || value === undefined) continue
    
    fields.push({
      key,
      value: typeof value === 'object' ? JSON.stringify(value) : String(value)
    })
  }
  
  return fields
})

// Get raw content
const rawContent = computed(() => {
  return props.log.raw || props.log.message || JSON.stringify(props.log, null, 2)
})

// Handle filter action
const handleFilter = (field: string, value: string, type: 'include' | 'exclude') => {
  emit('filter', field, value, type)
}

// Copy raw log to clipboard
const copyRawLog = () => {
  navigator.clipboard.writeText(rawContent.value).then(() => {
    ElMessage.success('已复制到剪贴板')
  }).catch(() => {
    ElMessage.error('复制失败')
  })
}
</script>

<style scoped lang="scss">
@use '@/assets/styles/macos.scss' as *;

.inline-row-expansion {
  padding: 16px 24px;
  background: linear-gradient(135deg, var(--macos-bg-secondary) 0%, var(--macos-bg-primary) 100%);
  border-top: 1px solid var(--macos-border);
}

.expansion-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 16px;
  }

  :deep(.el-tabs__item) {
    font-size: 13px;
    font-weight: 500;
  }
}

.parsed-fields-list {
  display: flex;
  flex-direction: column;
  gap: 0;
  background: var(--macos-bg-primary);
  border: 1px solid var(--macos-border);
  border-radius: var(--macos-radius-md);
  overflow: hidden;
}

.field-row {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--macos-border);
  transition: background 0.2s;

  &:last-child {
    border-bottom: none;
  }

  &:hover {
    background: var(--macos-blue-light);
  }
}

.field-key {
  width: 140px;
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--macos-blue);
  padding-right: 16px;
}

.field-value {
  flex: 1;
  font-family: 'JetBrains Mono', 'SF Mono', 'Monaco', 'Menlo', 'Consolas', monospace;
  font-size: 13px;
  color: var(--macos-text-primary);
  word-break: break-all;
  line-height: 1.5;
  padding-right: 16px;
}

.field-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
  
  .el-button {
    border-radius: var(--macos-radius-sm);
  }
}

.raw-log-container {
  background: #1e1e1e; // Keep dark terminal-like background for raw logs
  border-radius: var(--macos-radius-md);
  overflow: hidden;
}

.raw-log-header {
  display: flex;
  justify-content: flex-end;
  padding: 8px 12px;
  background: #2d2d2d;
  border-bottom: 1px solid #3d3d3d;
}

.raw-log-content {
  margin: 0;
  padding: 16px;
  font-family: 'JetBrains Mono', 'SF Mono', 'Monaco', 'Menlo', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.6;
  color: #d4d4d4;
  white-space: pre-wrap;
  word-wrap: break-word;
  max-height: 400px;
  overflow-y: auto;
  @include macos-scrollbar;

  &::-webkit-scrollbar {
    width: 8px;
  }

  &::-webkit-scrollbar-track {
    background: #2d2d2d;
  }

  &::-webkit-scrollbar-thumb {
    background: #4d4d4d;
    border-radius: 4px;
  }
}
</style>
