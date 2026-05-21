<template>
  <AppLayout>
    <div class="vector-log-monitor">
      <!-- 页面头部 -->
      <el-card shadow="never" class="page-header">
        <div class="header-content">
          <div>
            <h2>Vector 运行日志</h2>
            <p class="subtitle">实时查看 Vector 运行日志</p>
          </div>
          <div class="header-actions">
            <el-tag :type="connectionTagType">{{ connectionStatusText }}</el-tag>
            <el-button v-if="!isConnected" type="primary" @click="handleManualReconnect">
              <el-icon><Refresh /></el-icon>
              重新连接
            </el-button>
          </div>
        </div>
      </el-card>

      <!-- 筛选和控制 -->
      <el-card shadow="never" class="filter-card">
        <el-form :inline="true">
          <el-form-item label="机器">
            <el-select
              v-model="filters.machineId"
              placeholder="全部机器"
              clearable
              filterable
              style="width: 220px"
              @change="handleFilterChange"
            >
              <el-option
                v-for="machine in machines"
                :key="machine.id"
                :label="`${machine.name} (${machine.hostname})`"
                :value="machine.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="日志文件">
            <el-select
              v-model="filters.fileName"
              placeholder="全部文件"
              clearable
              filterable
              style="width: 220px"
              @change="handleFilterChange"
            >
              <el-option v-for="name in fileNames" :key="name" :label="name" :value="name" />
            </el-select>
          </el-form-item>
          <el-form-item label="关键词">
            <el-input
              v-model="filters.keyword"
              placeholder="搜索日志内容"
              clearable
              style="width: 250px"
              @keyup.enter="handleSearch"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleSearch">搜索</el-button>
          </el-form-item>
        </el-form>

        <div class="control-buttons">
          <el-button-group>
            <el-button :type="isPaused ? 'warning' : 'success'" @click="togglePause">
              <el-icon><component :is="isPaused ? 'VideoPlay' : 'VideoPause'" /></el-icon>
              {{ isPaused ? '继续' : '暂停' }}
            </el-button>
            <el-button @click="handleClear">
              <el-icon><Delete /></el-icon>
              清空
            </el-button>
            <el-button @click="scrollToTop">
              <el-icon><Top /></el-icon>
              顶部
            </el-button>
          </el-button-group>
          <el-tag type="info" style="margin-left: 12px;">共 {{ logs.length }} 条</el-tag>
        </div>
      </el-card>

      <!-- 日志显示区域 -->
      <el-card shadow="never" class="log-display-card">
        <div ref="logContainer" class="log-container log-container--reversed" @scroll="handleScroll">
          <div v-if="logs.length === 0" class="empty-logs">
            <el-empty description="暂无日志数据，请确认 Vector 已部署并运行" />
          </div>
          <div v-else class="log-list">
            <div
              v-for="(logItem, index) in logs"
              :key="index"
              class="log-item"
              :class="{ 'log-highlight': isHighlighted(logItem) }"
            >
              <span class="log-message">{{ logItem.message }}</span>
            </div>
          </div>
        </div>
      </el-card>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, VideoPlay, VideoPause, Delete, Top } from '@element-plus/icons-vue'
import axios from 'axios'
import AppLayout from '@/components/layout/AppLayout.vue'

interface VectorLog {
  machineId: string
  fileName: string
  message: string
  timestamp: string
}

interface VectorMachine {
  id: string
  name: string
  hostname: string
}

const loading = ref(false)
const logs = ref<VectorLog[]>([])
const machines = ref<VectorMachine[]>([])
const fileNames = ref<string[]>([])
const isPaused = ref(false)
const isConnected = ref(false)
const logContainer = ref<HTMLElement | null>(null)
const reconnectCount = ref(0)
const maxReconnectAttempts = 10

let eventSource: EventSource | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let currentEmitterId: string | null = null
let pendingLogs: VectorLog[] = []
let flushScheduled = false

const MAX_LOGS = 1000

/** 批量刷新：将 buffer 中的日志一次性写入响应式数组，减少重渲染次数 */
const flushPendingLogs = () => {
  flushScheduled = false
  if (pendingLogs.length === 0) return
  // push 到数组末尾（配合 CSS column-reverse 最新显示在顶部）
  logs.value.push(...pendingLogs)
  pendingLogs = []
  // 超出上限则裁剪旧数据（数组头部是最早的）
  if (logs.value.length > MAX_LOGS) {
    logs.value = logs.value.slice(-MAX_LOGS)
  }
}

const scheduleFlush = () => {
  if (flushScheduled) return
  flushScheduled = true
  requestAnimationFrame(flushPendingLogs)
}

const filters = reactive({
  machineId: '',
  fileName: '',
  keyword: ''
})

const connectionStatusText = computed(() => {
  if (isConnected.value) return '已连接'
  if (reconnectCount.value > 0 && reconnectCount.value < maxReconnectAttempts) {
    return `重连中 (${reconnectCount.value}/${maxReconnectAttempts})`
  }
  return '未连接'
})

const connectionTagType = computed(() => {
  if (isConnected.value) return 'success'
  if (reconnectCount.value > 0 && reconnectCount.value < maxReconnectAttempts) return 'warning'
  return 'info'
})

const handleManualReconnect = () => {
  reconnectCount.value = 0
  disconnectSSE()
  connectSSE()
  handleLoadHistory()
}

// 加载主机列表
const loadMachines = async () => {
  try {
    const { data } = await axios.get('/api/vector/machines/page', {
      params: { pageNum: 1, pageSize: 1000 }
    })
    if (data.code === 200 && data.data) {
      machines.value = data.data.records || []
    }
  } catch (error) {
    console.error('加载主机列表失败:', error)
  }
}

// 加载日志文件列表
const loadFileNames = async () => {
  try {
    const { data } = await axios.get('/api/vector/logs/files')
    if (data.code === 200 && data.data) {
      fileNames.value = data.data
    }
  } catch (error) {
    console.error('加载文件列表失败:', error)
  }
}

// 加载历史日志
const handleLoadHistory = async () => {
  loading.value = true
  try {
    const params: Record<string, string | number> = { pageNum: 1, pageSize: 200 }
    if (filters.machineId) params.machineId = filters.machineId
    if (filters.fileName) params.fileName = filters.fileName
    if (filters.keyword) params.keyword = filters.keyword

    const { data } = await axios.get('/api/vector/logs/query', { params })
    if (data.code === 200 && data.data) {
      const records: VectorLog[] = data.data.logs || []
      // 后端按时间倒序返回（最新在前），反转为升序存储
      // 配合 CSS column-reverse，数组末尾（最新）显示在顶部
      logs.value = records.reverse()
    }
  } catch (error) {
    console.error('加载历史日志失败:', error)
  } finally {
    loading.value = false
  }
}

const clearReconnectTimer = () => {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
}

// SSE 实时推送
const connectSSE = () => {
  if (eventSource) eventSource.close()
  clearReconnectTimer()

  const params = new URLSearchParams()
  if (filters.machineId) params.append('machineId', filters.machineId)
  if (filters.fileName) params.append('fileName', filters.fileName)
  const url = params.toString() ? `/api/vector/logs/stream?${params}` : '/api/vector/logs/stream'

  eventSource = new EventSource(url)

  eventSource.addEventListener('connected', (event: MessageEvent) => {
    isConnected.value = true
    reconnectCount.value = 0
    currentEmitterId = event.data
  })

  eventSource.addEventListener('log', (event) => {
    if (isPaused.value) return
    try {
      const logEntry: VectorLog = JSON.parse(event.data)
      if (filters.keyword && !logEntry.message?.includes(filters.keyword)) return
      // 批量 buffer：收集消息，用 rAF 合并渲染
      pendingLogs.push(logEntry)
      scheduleFlush()
    } catch (error) {
      console.error('解析日志失败:', error)
    }
  })

  eventSource.onerror = () => {
    isConnected.value = false
    if (eventSource) { eventSource.close(); eventSource = null }
    if (reconnectCount.value < maxReconnectAttempts) {
      const delay = Math.min(1000 * Math.pow(2, reconnectCount.value), 30000)
      reconnectCount.value++
      reconnectTimer = setTimeout(() => connectSSE(), delay)
    }
  }
}

const disconnectSSE = () => {
  clearReconnectTimer()
  reconnectCount.value = 0
  if (eventSource) { eventSource.close(); eventSource = null; isConnected.value = false }
  if (currentEmitterId) {
    // 通知后端关闭 polling task，使用 sendBeacon 确保页面关闭时也能发送
    try {
      navigator.sendBeacon(`/api/vector/logs/stream/${currentEmitterId}/close`)
    } catch {
      // fallback: 直接发请求
      axios.delete(`/api/vector/logs/stream/${currentEmitterId}`).catch(() => {})
    }
    currentEmitterId = null
  }
}

const handleFilterChange = () => {
  disconnectSSE()
  connectSSE()
  handleLoadHistory()
}

const handleSearch = () => handleLoadHistory()
const togglePause = () => { isPaused.value = !isPaused.value }
const handleClear = () => { logs.value = [] }

const scrollToTop = () => {
  if (logContainer.value) {
    logContainer.value.scrollTop = 0
  }
}

const handleScroll = () => {
  // 保留 scroll handler 以备将来扩展
}

const highlightKeyword = (text: string) => {
  if (!filters.keyword || !text) return text
  const keyword = filters.keyword.trim()
  if (!keyword) return text
  return text.replace(new RegExp(`(${keyword})`, 'gi'), '<span class="keyword-highlight">$1</span>')
}

const isHighlighted = (logItem: VectorLog) => {
  return filters.keyword && logItem.message?.includes(filters.keyword)
}

const handleBeforeUnload = () => {
  disconnectSSE()
}

onMounted(async () => {
  window.addEventListener('beforeunload', handleBeforeUnload)
  await Promise.all([loadMachines(), loadFileNames()])
  await handleLoadHistory()
  connectSSE()
})

onUnmounted(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
  disconnectSSE()
})
</script>

<style scoped lang="scss">
.vector-log-monitor {
  padding: 20px;
  height: calc(100vh - 40px);
  display: flex;
  flex-direction: column;
}

.page-header {
  margin-bottom: 20px;
  flex-shrink: 0;

  .header-content {
    display: flex;
    justify-content: space-between;
    align-items: center;

    h2 {
      margin: 0 0 8px 0;
      font-size: 24px;
    }

    .subtitle {
      margin: 0;
      color: var(--el-text-color-secondary);
      font-size: 14px;
    }

    .header-actions {
      display: flex;
      gap: 12px;
      align-items: center;
    }
  }
}

.filter-card {
  margin-bottom: 20px;
  flex-shrink: 0;

  .control-buttons {
    display: flex;
    align-items: center;
    margin-top: 12px;
    padding-top: 12px;
    border-top: 1px solid var(--el-border-color-lighter);
  }
}

.log-display-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;

  :deep(.el-card__body) {
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    padding: 0;
  }
}

.log-container {
  flex: 1;
  overflow-y: auto;
  background: #1e1e1e;
  font-family: 'Monaco', 'Consolas', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.6;
  padding: 12px;

  // 最新日志显示在顶部：数组末尾的元素（最新 push 进来的）渲染在容器顶部
  &.log-container--reversed {
    display: flex;
    flex-direction: column-reverse;
  }

  .empty-logs {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
  }
}

.log-list {
  .log-item {
    padding: 2px 0;
    border-bottom: 1px solid rgba(255, 255, 255, 0.05);
    color: #d4d4d4;

    &:hover {
      background: rgba(255, 255, 255, 0.05);
    }

    &.log-highlight {
      background: rgba(255, 255, 0, 0.1);
    }
  }

  .log-message {
    word-break: break-all;
    white-space: pre-wrap;

    :deep(.keyword-highlight) {
      background-color: yellow;
      color: black;
      font-weight: bold;
      padding: 0 2px;
    }
  }
}

// 滚动条样式
.log-container::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.log-container::-webkit-scrollbar-track {
  background: #2d2d2d;
}

.log-container::-webkit-scrollbar-thumb {
  background: #555;
  border-radius: 4px;

  &:hover {
    background: #666;
  }
}
</style>
