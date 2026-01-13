<template>
  <AppLayout>
    <div class="vector-log-monitor">
      <!-- 页面头部 -->
      <el-card shadow="never" class="page-header">
        <div class="header-content">
          <div>
            <h2>Vector 运行日志</h2>
            <p class="subtitle">实时查看 Vector 日志收集器的运行日志（类似 K8s 日志滚动）</p>
          </div>
          <div class="header-actions">
            <el-tag :type="isConnected ? 'success' : 'info'">
              {{ isConnected ? '已连接' : '未连接' }}
            </el-tag>
            <el-button @click="handleLoadHistory" :loading="loading">
              <el-icon><Refresh /></el-icon>
              加载历史
            </el-button>
          </div>
        </div>
      </el-card>

      <!-- 筛选和控制 -->
      <el-card shadow="never" class="filter-card">
        <el-form :inline="true">
          <el-form-item label="主机">
            <el-select
              v-model="filters.machineId"
              placeholder="选择主机"
              clearable
              filterable
              style="width: 250px"
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
          <el-form-item label="日志级别">
            <el-select
              v-model="filters.logLevel"
              placeholder="选择级别"
              clearable
              style="width: 150px"
              @change="handleFilterChange"
            >
              <el-option label="ERROR" value="error" />
              <el-option label="WARN" value="warn" />
              <el-option label="INFO" value="info" />
              <el-option label="DEBUG" value="debug" />
              <el-option label="TRACE" value="trace" />
            </el-select>
          </el-form-item>
          <el-form-item label="关键词">
            <el-input
              v-model="filters.keyword"
              placeholder="搜索日志内容"
              clearable
              style="width: 300px"
              @keyup.enter="handleSearch"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleSearch">
              <el-icon><Search /></el-icon>
              搜索
            </el-button>
          </el-form-item>
        </el-form>

        <!-- 控制按钮 -->
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
            <el-button @click="scrollToBottom">
              <el-icon><Bottom /></el-icon>
              跳到底部
            </el-button>
          </el-button-group>
          <el-tag type="info" style="margin-left: 12px;">
            共 {{ logs.length }} 条日志
          </el-tag>
        </div>
      </el-card>

      <!-- 日志显示区域 -->
      <el-card shadow="never" class="log-display-card">
        <div ref="logContainer" class="log-container" @scroll="handleScroll">
          <div v-if="logs.length === 0" class="empty-logs">
            <el-empty description="暂无日志数据">
              <el-button type="primary" @click="handleLoadHistory">加载历史日志</el-button>
            </el-empty>
          </div>
          <div v-else class="log-list">
            <div
              v-for="(log, index) in logs"
              :key="log.id || index"
              class="log-item"
              :class="[`log-level-${log.logLevel}`, { 'log-highlight': isHighlighted(log) }]"
            >
              <span class="log-timestamp">{{ formatTimestamp(log.timestamp) }}</span>
              <el-tag :type="getLevelType(log.logLevel)" size="small" class="log-level-tag">
                {{ log.logLevel?.toUpperCase() }}
              </el-tag>
              <span class="log-hostname">{{ log.hostname }}</span>
              <span class="log-ip">{{ log.ipAddress }}</span>
              <span class="log-message" v-html="highlightKeyword(log.message)"></span>
            </div>
          </div>
        </div>
      </el-card>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, VideoPlay, VideoPause, Delete, Bottom } from '@element-plus/icons-vue'
import axios from 'axios'
import dayjs from 'dayjs'
import AppLayout from '@/components/layout/AppLayout.vue'

interface VectorLog {
  id: string
  machineId: string
  hostname: string
  ipAddress: string
  logLevel: string
  message: string
  timestamp: string
  rawLog: string
}

interface VectorMachine {
  id: string
  name: string
  hostname: string
  ipAddress: string
}

const loading = ref(false)
const logs = ref<VectorLog[]>([])
const machines = ref<VectorMachine[]>([])
const isPaused = ref(false)
const isConnected = ref(false)
const logContainer = ref<HTMLElement | null>(null)
const autoScroll = ref(true)

let eventSource: EventSource | null = null

const filters = reactive({
  machineId: '',
  logLevel: '',
  keyword: ''
})

// 加载主机列表
const loadMachines = async () => {
  try {
    const { data } = await axios.get('/api/vector/machines/page', {
      params: {
        pageNum: 1,
        pageSize: 1000
      }
    })
    if (data.code === 200 && data.data) {
      machines.value = data.data.records || []
    }
  } catch (error) {
    console.error('加载主机列表失败:', error)
  }
}

// 加载历史日志
const handleLoadHistory = async () => {
  loading.value = true
  try {
    const params: any = {
      pageNum: 1,
      pageSize: 100
    }

    if (filters.machineId) {
      params.machineId = filters.machineId
    }
    if (filters.logLevel) {
      params.logLevel = filters.logLevel
    }
    if (filters.keyword) {
      params.keyword = filters.keyword
    }

    const { data } = await axios.get('/api/vector/logs/query', { params })

    if (data.code === 200 && data.data) {
      logs.value = data.data.logs || []
      ElMessage.success(`加载了 ${logs.value.length} 条历史日志`)
      await nextTick()
      scrollToBottom()
    } else {
      ElMessage.error(data.message || '加载失败')
    }
  } catch (error: any) {
    console.error('加载历史日志失败:', error)
    ElMessage.error(error.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

// 连接 SSE 实时推送
const connectSSE = () => {
  if (eventSource) {
    eventSource.close()
  }

  let url = '/api/vector/logs/stream'
  const params = new URLSearchParams()

  if (filters.machineId) {
    params.append('machineId', filters.machineId)
  }
  if (filters.logLevel) {
    params.append('logLevel', filters.logLevel)
  }

  if (params.toString()) {
    url += '?' + params.toString()
  }

  eventSource = new EventSource(url)

  eventSource.addEventListener('connected', () => {
    isConnected.value = true
    console.log('SSE 连接成功')
  })

  eventSource.addEventListener('log', (event) => {
    if (isPaused.value) return

    try {
      const log: VectorLog = JSON.parse(event.data)

      // 关键词过滤
      if (filters.keyword && !log.message.includes(filters.keyword)) {
        return
      }

      logs.value.push(log)

      // 限制日志数量，最多保留 5000 条
      if (logs.value.length > 5000) {
        logs.value.shift()
      }

      // 自动滚动到底部
      if (autoScroll.value) {
        nextTick(() => {
          scrollToBottom()
        })
      }
    } catch (error) {
      console.error('解析日志失败:', error)
    }
  })

  eventSource.onerror = (error) => {
    console.error('SSE 连接错误:', error)
    isConnected.value = false
    ElMessage.error('实时连接断开，请刷新页面')
  }
}

// 断开 SSE 连接
const disconnectSSE = () => {
  if (eventSource) {
    eventSource.close()
    eventSource = null
    isConnected.value = false
  }
}

// 筛选条件变化
const handleFilterChange = () => {
  // 重新连接 SSE
  disconnectSSE()
  connectSSE()

  // 重新加载历史日志
  handleLoadHistory()
}

// 搜索
const handleSearch = () => {
  handleLoadHistory()
}

// 暂停/继续
const togglePause = () => {
  isPaused.value = !isPaused.value
  ElMessage.info(isPaused.value ? '已暂停滚动' : '已继续滚动')
}

// 清空日志
const handleClear = () => {
  logs.value = []
  ElMessage.success('已清空日志')
}

// 滚动到底部
const scrollToBottom = () => {
  if (logContainer.value) {
    logContainer.value.scrollTop = logContainer.value.scrollHeight
  }
}

// 处理滚动事件
const handleScroll = () => {
  if (!logContainer.value) return

  const { scrollTop, scrollHeight, clientHeight } = logContainer.value
  const isAtBottom = scrollHeight - scrollTop - clientHeight < 50

  // 如果用户滚动到底部，启用自动滚动；否则禁用
  autoScroll.value = isAtBottom
}

// 格式化时间
const formatTimestamp = (timestamp: string) => {
  return dayjs(timestamp).format('YYYY-MM-DD HH:mm:ss.SSS')
}

// 获取日志级别类型
const getLevelType = (level: string) => {
  const map: Record<string, any> = {
    error: 'danger',
    warn: 'warning',
    info: 'info',
    debug: 'info',
    trace: 'info'
  }
  return map[level?.toLowerCase()] || 'info'
}

// 高亮关键词
const highlightKeyword = (text: string) => {
  if (!filters.keyword || !text) return text

  const keyword = filters.keyword.trim()
  if (!keyword) return text

  const regex = new RegExp(`(${keyword})`, 'gi')
  return text.replace(regex, '<span class="keyword-highlight">$1</span>')
}

// 判断是否高亮显示
const isHighlighted = (log: VectorLog) => {
  return filters.keyword && log.message.includes(filters.keyword)
}

onMounted(async () => {
  await loadMachines()
  await handleLoadHistory()
  connectSSE()
})

onUnmounted(() => {
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

  .empty-logs {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
  }
}

.log-list {
  .log-item {
    display: flex;
    align-items: baseline;
    padding: 4px 0;
    border-bottom: 1px solid rgba(255, 255, 255, 0.05);
    color: #d4d4d4;

    &:hover {
      background: rgba(255, 255, 255, 0.05);
    }

    &.log-highlight {
      background: rgba(255, 255, 0, 0.1);
    }

    &.log-level-error {
      .log-message {
        color: #f56c6c;
      }
    }

    &.log-level-warn {
      .log-message {
        color: #e6a23c;
      }
    }
  }

  .log-timestamp {
    color: #858585;
    margin-right: 12px;
    flex-shrink: 0;
    width: 180px;
  }

  .log-level-tag {
    margin-right: 12px;
    flex-shrink: 0;
    width: 60px;
  }

  .log-hostname {
    color: #4ec9b0;
    margin-right: 12px;
    flex-shrink: 0;
    width: 150px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .log-ip {
    color: #9cdcfe;
    margin-right: 12px;
    flex-shrink: 0;
    width: 120px;
  }

  .log-message {
    flex: 1;
    word-break: break-all;

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
