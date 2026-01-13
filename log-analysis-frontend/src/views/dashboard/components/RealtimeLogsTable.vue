<template>
  <el-card class="dashboard-card" v-loading="loading">
    <template #header>
      <div class="card-header">
        <span class="card-title">最新错误/警告日志</span>
        <el-tag type="danger" size="small" effect="light">
          <el-icon class="pulse"><Warning /></el-icon>
          实时
        </el-tag>
      </div>
    </template>
    <el-table :data="data" class="logs-table" @row-click="handleRowClick" highlight-current-row>
      <el-table-column label="时间" width="180">
        <template #default="{ row }">
          <span class="timestamp">{{ formatTime(row.timestamp) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="级别" width="90">
        <template #default="{ row }">
          <el-tag :type="getLevelType(row.severity)" size="small" effect="dark">{{ row.severity }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="hostname" label="主机" width="140" show-overflow-tooltip />
      <el-table-column prop="appname" label="应用" width="160" show-overflow-tooltip />
      <el-table-column prop="message" label="消息摘要" min-width="300" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="message-text">{{ row.message }}</span>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { Warning } from '@element-plus/icons-vue'
import type { LogRecord } from '../types'

defineProps<{ data: LogRecord[]; loading: boolean }>()
const emit = defineEmits<{ (e: 'row-click', log: LogRecord): void }>()

const formatTime = (timestamp: string) => {
  const date = new Date(timestamp)
  return date.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

const getLevelType = (level: string) => {
  const map: Record<string, string> = { INFO: 'primary', WARN: 'warning', ERROR: 'danger', FATAL: '' }
  return map[level] || 'info'
}

const handleRowClick = (row: LogRecord) => emit('row-click', row)
</script>

<style scoped lang="scss">
.dashboard-card {
  border-radius: var(--macos-radius-lg);
  border: 1px solid var(--macos-border);
  box-shadow: var(--macos-shadow-sm);
  background: var(--macos-glass-bg);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  transition: var(--macos-transition);

  &:hover {
    box-shadow: var(--macos-shadow-md);
    border-color: var(--macos-border-hover);
  }
}
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-size: 16px; font-weight: 600; color: var(--macos-text-primary); }
.pulse { animation: pulse 1.5s infinite; }
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }
.logs-table {
  background: transparent;
  :deep(.el-table__row) { 
    background: transparent;
    cursor: pointer; 
    transition: background-color 0.2s;
    &:hover {
      background-color: var(--macos-blue-light) !important;
    }
  }
  
  :deep(.el-table__header-wrapper) {
     th {
       background: var(--macos-bg-tertiary);
       color: var(--macos-text-secondary);
       font-weight: 600;
       border-bottom: 1px solid var(--macos-border);
     }
  }
  
  :deep(.el-table__cell) {
    border-bottom: 1px solid var(--macos-border);
  }
}
.timestamp { font-family: 'SF Mono', monospace; font-size: 12px; color: var(--macos-text-secondary); }
.message-text { font-family: 'SF Mono', monospace; font-size: 12px; color: var(--macos-text-primary); }
:deep(.el-tag--dark) { &.el-tag--danger { background: #FF4D4F; border-color: #FF4D4F; } }
</style>
