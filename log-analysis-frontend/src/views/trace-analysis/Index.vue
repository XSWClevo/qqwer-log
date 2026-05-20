<template>
  <AppLayout>
    <div class="trace-analysis-container">
      <!-- Page Header -->
      <div class="page-header">
        <div class="header-left">
          <el-button class="back-btn" @click="goBack">
            <el-icon><ArrowLeft /></el-icon>
            <span>Back to Trace List</span>
          </el-button>
          <div class="title-row">
            <h1 class="page-title">
              Trace Details: {{ shortTraceId }}
              <el-tooltip content="Copy Trace ID" placement="top">
                <el-button text size="small" class="copy-btn" @click="copyTraceId">
                  <el-icon><CopyDocument /></el-icon>
                </el-button>
              </el-tooltip>
            </h1>
          </div>
          <p class="trace-time">{{ traceTimestamp }}</p>
        </div>
      </div>

      <!-- Trace Summary Bar -->
      <div class="trace-summary-bar">
        <div class="summary-group primary">
          <div class="summary-item">
            <span class="summary-label">Root Service</span>
            <span class="summary-value service">{{ traceSummary.rootService }}</span>
          </div>
        </div>
        <div class="summary-divider"></div>
        <div class="summary-group highlight">
          <div class="summary-item">
            <span class="summary-label">Total Duration</span>
            <span class="summary-value duration" :class="{ slow: traceSummary.duration > 1000 }">
              {{ formatDuration(traceSummary.duration) }}
            </span>
          </div>
          <div class="summary-item">
            <span class="summary-label">Errors</span>
            <div class="error-badge" :class="{ 'has-error': traceSummary.errors > 0 }">
              <el-icon v-if="traceSummary.errors > 0"><CircleCloseFilled /></el-icon>
              <el-icon v-else><CircleCheckFilled /></el-icon>
              <span>{{ traceSummary.errors > 0 ? `${traceSummary.errors} Errors` : 'No Errors' }}</span>
            </div>
          </div>
        </div>
        <div class="summary-divider"></div>
        <div class="summary-group">
          <div class="summary-item">
            <span class="summary-label">Total Spans</span>
            <span class="summary-value">{{ traceSummary.totalSpans }} spans</span>
          </div>
        </div>
      </div>

      <!-- Split View Content -->
      <div class="split-content">
        <!-- Left Panel: Timeline -->
        <el-card class="timeline-card" shadow="never">
          <template #header>
            <div class="panel-header">
              <span class="panel-title">Timeline View</span>
              <el-input
                v-model="searchSpan"
                placeholder="Search spans..."
                :prefix-icon="Search"
                size="small"
                class="span-search"
                clearable
              />
            </div>
          </template>
          
          <div class="timeline-container">
            <!-- Timeline Header -->
            <div class="timeline-header">
              <div class="span-info-header">Service / Operation</div>
              <div class="span-timeline-header">
                <span v-for="tick in timelineTicks" :key="tick" class="tick">{{ tick }}</span>
              </div>
            </div>

            <!-- Span Rows -->
            <div class="spans-list">
              <div
                v-for="span in filteredSpans"
                :key="span.id"
                class="span-row"
                :class="{ selected: selectedSpan?.id === span.id, error: span.hasError }"
                @click="selectSpan(span)"
              >
                <!-- Tree guide lines -->
                <div class="tree-guides" :style="{ width: `${span.depth * 20 + 12}px` }">
                  <div v-for="i in span.depth" :key="i" class="guide-line" :style="{ left: `${(i - 1) * 20 + 10}px` }"></div>
                  <div v-if="span.depth > 0" class="guide-connector" :style="{ left: `${(span.depth - 1) * 20 + 10}px` }"></div>
                </div>
                
                <div class="span-info">
                  <!-- Error indicator -->
                  <el-icon v-if="span.hasError" class="error-indicator"><CircleCloseFilled /></el-icon>
                  <span class="service-name" :class="{ error: span.hasError }">
                    {{ span.service }}
                  </span>
                  <span class="operation-name">{{ span.operation }}</span>
                </div>
                <div class="span-timeline">
                  <div
                    class="span-bar"
                    :class="{ error: span.hasError, critical: span.isCriticalPath }"
                    :style="{
                      left: `${span.startPercent}%`,
                      width: `${Math.max(span.widthPercent, 3)}%`
                    }"
                  >
                    <span 
                      class="span-duration" 
                      :class="{ outside: span.widthPercent < 8 }"
                      :style="span.widthPercent < 8 ? { left: `calc(100% + 4px)`, color: '#606266' } : {}"
                    >
                      {{ formatDuration(span.duration) }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </el-card>

        <!-- Right Panel: Service Map & Details -->
        <div class="right-panel">
          <!-- Service Map -->
          <el-card class="service-map-card" shadow="never">
            <template #header>
              <span class="panel-title">Service Dependency</span>
            </template>
            <div ref="serviceMapRef" class="service-map"></div>
          </el-card>

          <!-- Span Details -->
          <el-card class="span-details-card" shadow="never">
            <template #header>
              <span class="panel-title">Span Details</span>
            </template>
            
            <div v-if="selectedSpan" class="span-details">
              <el-tabs v-model="activeTab">
                <el-tab-pane label="Summary" name="summary">
                  <div class="detail-grid">
                    <div class="detail-item">
                      <span class="detail-label">Service</span>
                      <span class="detail-value">{{ selectedSpan.service }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="detail-label">Operation</span>
                      <span class="detail-value">{{ selectedSpan.operation }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="detail-label">Duration</span>
                      <span class="detail-value mono">{{ formatDuration(selectedSpan.duration) }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="detail-label">Status</span>
                      <span class="status-text" :class="selectedSpan.hasError ? 'error' : 'success'">
                        <el-icon v-if="selectedSpan.hasError"><CircleCloseFilled /></el-icon>
                        <el-icon v-else><CircleCheckFilled /></el-icon>
                        {{ selectedSpan.hasError ? 'Error' : 'Success' }}
                      </span>
                    </div>
                  </div>
                </el-tab-pane>

                <el-tab-pane label="Tags" name="tags">
                  <div class="tags-list">
                    <div v-for="(value, key) in selectedSpan.tags" :key="key" class="tag-item">
                      <span class="tag-key">{{ key }}</span>
                      <span class="tag-value" :class="{ error: String(key).includes('error') }">{{ value }}</span>
                    </div>
                  </div>
                </el-tab-pane>

                <el-tab-pane label="Logs" name="logs">
                  <div v-if="selectedSpan.logs?.length" class="logs-list">
                    <div v-for="(log, index) in selectedSpan.logs" :key="index" class="log-item">
                      <span class="log-time">{{ log.timestamp }}</span>
                      <span class="log-message">{{ log.message }}</span>
                    </div>
                  </div>
                  <el-empty v-else description="No logs for this span" :image-size="60" />
                </el-tab-pane>

                <el-tab-pane label="Process" name="process">
                  <div class="process-info">
                    <div class="detail-item">
                      <span class="detail-label">Host</span>
                      <span class="detail-value">{{ selectedSpan.process?.hostname || 'N/A' }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="detail-label">IP</span>
                      <span class="detail-value mono">{{ selectedSpan.process?.ip || 'N/A' }}</span>
                    </div>
                    <div class="detail-item">
                      <span class="detail-label">Version</span>
                      <span class="detail-value">{{ selectedSpan.process?.version || 'N/A' }}</span>
                    </div>
                  </div>
                </el-tab-pane>
              </el-tabs>
            </div>
            <el-empty v-else description="Select a span to view details" :image-size="80" />
          </el-card>
        </div>
      </div>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, CopyDocument, CircleCloseFilled, CircleCheckFilled, Search } from '@element-plus/icons-vue'
import echarts from '@/utils/echarts'
import AppLayout from '@/components/layout/AppLayout.vue'
import { ElMessage } from 'element-plus'

const router = useRouter()

// State
const traceId = ref('3a5f2c8d-9e7b-4f1a-b2c3-d4e5f6a7b8c9')
const traceTimestamp = ref('Dec 20, 2025 14:30:45')
const searchSpan = ref('')
const activeTab = ref('summary')
const serviceMapRef = ref<HTMLElement>()
let serviceMapChart: echarts.ECharts | null = null

// Computed
const shortTraceId = computed(() => {
  const id = traceId.value
  if (id.length > 16) {
    return `${id.slice(0, 8)}...${id.slice(-4)}`
  }
  return id
})

// Trace Summary
const traceSummary = ref({
  rootService: 'frontend-gateway',
  duration: 1450,
  totalSpans: 45,
  errors: 2
})

// Timeline ticks
const timelineTicks = ['0ms', '250ms', '500ms', '750ms', '1000ms', '1250ms', '1450ms']

// Span data
interface Span {
  id: string
  service: string
  operation: string
  duration: number
  startPercent: number
  widthPercent: number
  depth: number
  hasError: boolean
  isCriticalPath: boolean
  children?: Span[]
  tags: Record<string, string>
  logs?: { timestamp: string; message: string }[]
  process?: { hostname: string; ip: string; version: string }
}

const spans = ref<Span[]>([
  {
    id: '1',
    service: 'frontend-gateway',
    operation: 'HTTP GET /api/orders',
    duration: 1450,
    startPercent: 0,
    widthPercent: 100,
    depth: 0,
    hasError: false,
    isCriticalPath: true,
    tags: { 'http.method': 'GET', 'http.url': '/api/orders', 'http.status_code': '200' },
    logs: [{ timestamp: '14:30:45.001', message: 'Request received' }],
    process: { hostname: 'gateway-01', ip: '10.0.1.10', version: '2.1.0' }
  },
  {
    id: '2',
    service: 'order-service',
    operation: 'getOrders',
    duration: 1200,
    startPercent: 5,
    widthPercent: 82,
    depth: 1,
    hasError: false,
    isCriticalPath: true,
    tags: { 'component': 'spring-boot', 'span.kind': 'server' },
    process: { hostname: 'order-01', ip: '10.0.2.20', version: '1.5.0' }
  },
  {
    id: '3',
    service: 'user-service',
    operation: 'getUserInfo',
    duration: 150,
    startPercent: 10,
    widthPercent: 10,
    depth: 2,
    hasError: false,
    isCriticalPath: false,
    tags: { 'user.id': '12345', 'cache.hit': 'true' },
    process: { hostname: 'user-01', ip: '10.0.3.30', version: '1.2.0' }
  },
  {
    id: '4',
    service: 'payment-service',
    operation: 'processPayment',
    duration: 800,
    startPercent: 25,
    widthPercent: 55,
    depth: 2,
    hasError: true,
    isCriticalPath: true,
    tags: {
      'http.status_code': '500',
      'error': 'true',
      'error.message': 'Connection timed out to payment gateway'
    },
    logs: [
      { timestamp: '14:30:45.350', message: 'Connecting to payment gateway...' },
      { timestamp: '14:30:46.150', message: 'ERROR: Connection timed out' }
    ],
    process: { hostname: 'payment-01', ip: '10.0.4.40', version: '2.0.1' }
  },
  {
    id: '5',
    service: 'payment-service',
    operation: 'DB: SELECT payment_methods',
    duration: 45,
    startPercent: 26,
    widthPercent: 3,
    depth: 3,
    hasError: false,
    isCriticalPath: false,
    tags: { 'db.type': 'postgresql', 'db.statement': 'SELECT * FROM payment_methods WHERE user_id = ?' },
    process: { hostname: 'payment-01', ip: '10.0.4.40', version: '2.0.1' }
  },
  {
    id: '6',
    service: 'notification-service',
    operation: 'sendNotification',
    duration: 200,
    startPercent: 85,
    widthPercent: 14,
    depth: 1,
    hasError: true,
    isCriticalPath: false,
    tags: {
      'notification.type': 'email',
      'error': 'true',
      'error.message': 'SMTP server unavailable'
    },
    process: { hostname: 'notify-01', ip: '10.0.5.50', version: '1.0.5' }
  }
])

const selectedSpan = ref<Span | null>(null)

// Computed
const filteredSpans = computed(() => {
  if (!searchSpan.value) return spans.value
  const search = searchSpan.value.toLowerCase()
  return spans.value.filter(
    span =>
      span.service.toLowerCase().includes(search) ||
      span.operation.toLowerCase().includes(search)
  )
})

// Methods
const goBack = () => {
  router.back()
}

const copyTraceId = () => {
  navigator.clipboard.writeText(traceId.value)
  ElMessage.success('Trace ID copied to clipboard')
}

const formatDuration = (ms: number): string => {
  if (ms < 1) return '<1ms'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(2)}s`
}

const selectSpan = (span: Span) => {
  selectedSpan.value = span
}

const renderServiceMap = () => {
  if (!serviceMapRef.value) return

  if (!serviceMapChart) {
    serviceMapChart = echarts.init(serviceMapRef.value)
  }

  // 根据错误状态设置节点样式
  const getNodeStyle = (serviceName: string, hasError: boolean) => {
    if (hasError) {
      return {
        color: '#F56C6C',
        borderColor: '#F56C6C',
        borderWidth: 3,
        shadowColor: 'rgba(245, 108, 108, 0.5)',
        shadowBlur: 10
      }
    }
    if (serviceName === 'frontend-gateway') {
      return { color: '#409EFF' }
    }
    return { color: '#67C23A' }
  }

  const nodes = [
    { name: 'frontend-gateway', x: 150, y: 30, itemStyle: getNodeStyle('frontend-gateway', false) },
    { name: 'order-service', x: 150, y: 100, itemStyle: getNodeStyle('order-service', false) },
    { name: 'user-service', x: 50, y: 170, itemStyle: getNodeStyle('user-service', false) },
    { name: 'payment-service', x: 150, y: 170, itemStyle: getNodeStyle('payment-service', true) },
    { name: 'notification-service', x: 250, y: 170, itemStyle: getNodeStyle('notification-service', true) }
  ]

  const links = [
    { source: 'frontend-gateway', target: 'order-service', lineStyle: { color: '#C0C4CC', width: 1.5 } },
    { source: 'order-service', target: 'user-service', lineStyle: { color: '#C0C4CC', width: 1.5 } },
    { source: 'order-service', target: 'payment-service', lineStyle: { color: '#F56C6C', width: 2.5 } },
    { source: 'order-service', target: 'notification-service', lineStyle: { color: '#F56C6C', width: 2.5 } }
  ]

  const option = {
    tooltip: { 
      trigger: 'item',
      formatter: (params: any) => {
        if (params.dataType === 'node') {
          const hasError = params.data.itemStyle?.color === '#F56C6C'
          return `<div style="font-weight:600;">${params.name}</div>
                  <div style="color:${hasError ? '#F56C6C' : '#67C23A'};">
                    ${hasError ? '❌ Has Errors' : '✅ Healthy'}
                  </div>`
        }
        return ''
      }
    },
    series: [
      {
        type: 'graph',
        layout: 'none',
        symbolSize: 45,
        roam: false,
        label: {
          show: true,
          position: 'bottom',
          fontSize: 10,
          color: '#606266',
          formatter: (params: any) => {
            const name = params.name
            return name.length > 12 ? name.slice(0, 12) + '...' : name
          }
        },
        edgeSymbol: ['none', 'arrow'],
        edgeSymbolSize: [4, 10],
        data: nodes,
        links,
        lineStyle: { curveness: 0.1 }
      }
    ]
  }

  serviceMapChart.setOption(option)
}

// Lifecycle
onMounted(() => {
  renderServiceMap()
  window.addEventListener('resize', () => serviceMapChart?.resize())
})

onUnmounted(() => {
  serviceMapChart?.dispose()
})
</script>


<style scoped lang="scss">
.trace-analysis-container {
  padding: 24px;
  background: var(--macos-fill-tertiary);
  min-height: 100vh;
}

.page-header {
  margin-bottom: 20px;

  .back-btn {
    color: #409EFF !important;
    font-weight: 500;
    font-size: 14px;
    padding: 8px 12px;
    margin-bottom: 12px;
    border: 1px solid var(--macos-blue) !important;
    background: transparent !important;

    &:hover {
      background: rgba(64, 158, 255, 0.1) !important;
    }

    span {
      margin-left: 4px;
    }
  }

  .title-row {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .page-title {
    font-size: 22px;
    font-weight: 600;
    color: var(--macos-text-primary);
    margin: 0 0 4px 0;
    font-family: 'SF Mono', 'Consolas', monospace;
    display: flex;
    align-items: center;
    gap: 8px;

    .copy-btn {
      color: var(--macos-text-tertiary);
      padding: 4px;

      &:hover {
        color: var(--macos-blue);
        background: rgba(64, 158, 255, 0.1);
      }
    }
  }

  .trace-time {
    font-size: 14px;
    color: var(--macos-text-tertiary);
    margin: 0;
  }
}

.trace-summary-bar {
  display: flex;
  align-items: stretch;
  padding: 0;
  background: var(--macos-card-bg);
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  margin-bottom: 24px;
  overflow: hidden;

  .summary-group {
    display: flex;
    gap: 32px;
    padding: 20px 28px;

    &.primary {
      background: linear-gradient(135deg, #f0f7ff 0%, #e8f4ff 100%);
    }

    &.highlight {
      background: linear-gradient(135deg, #fff8f0 0%, #fff5f5 100%);
    }
  }

  .summary-divider {
    width: 1px;
    background: var(--macos-bg-tertiary);
  }

  .summary-item {
    display: flex;
    flex-direction: column;
    gap: 6px;

    .summary-label {
      font-size: 11px;
      color: var(--macos-text-tertiary);
      text-transform: uppercase;
      letter-spacing: 0.8px;
      font-weight: 500;
    }

    .summary-value {
      font-size: 18px;
      font-weight: 700;
      color: var(--macos-text-primary);

      &.service {
        color: var(--macos-blue);
      }

      &.duration {
        font-family: 'SF Mono', monospace;

        &.slow {
          color: var(--macos-danger);
        }
      }
    }

    .error-badge {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 15px;
      font-weight: 600;
      color: var(--macos-success);

      .el-icon {
        font-size: 18px;
      }

      &.has-error {
        color: var(--macos-danger);
      }
    }
  }
}

.split-content {
  display: grid;
  grid-template-columns: 1fr 400px;
  gap: 24px;
}

.timeline-card {
  :deep(.el-card__header) {
    padding: 14px 20px;
    border-bottom: 1px solid var(--macos-border);
    background: var(--macos-fill-tertiary);
  }

  .panel-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .span-search {
      width: 220px;
    }
  }
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--macos-text-primary);
}

.timeline-container {
  .timeline-header {
    display: flex;
    border-bottom: 2px solid #E4E7ED;
    padding: 10px 0;
    font-size: 12px;
    color: var(--macos-text-secondary);
    font-weight: 600;
    background: var(--macos-fill-tertiary);

    .span-info-header {
      width: 300px;
      padding-left: 16px;
    }

    .span-timeline-header {
      flex: 1;
      display: flex;
      justify-content: space-between;
      padding: 0 12px;
    }
  }

  .spans-list {
    max-height: 520px;
    overflow-y: auto;
  }

  .span-row {
    display: flex;
    align-items: center;
    padding: 10px 0;
    border-bottom: 1px solid var(--macos-border);
    cursor: pointer;
    transition: all 0.2s;
    position: relative;

    &:hover {
      background: var(--macos-fill-secondary);
    }

    &.selected {
      background: var(--macos-info-bg);
      border-left: 3px solid #409EFF;
    }

    &.error {
      background: var(--macos-danger-bg);
      border-left: 3px solid #F56C6C;

      &:hover {
        background: var(--macos-danger-bg);
      }

      &.selected {
        background: var(--macos-danger-bg);
      }
    }

    .tree-guides {
      position: relative;
      height: 100%;
      flex-shrink: 0;

      .guide-line {
        position: absolute;
        top: 0;
        bottom: 0;
        width: 2px;
        background: var(--macos-bg-tertiary);
      }

      .guide-connector {
        position: absolute;
        top: 50%;
        width: 12px;
        height: 2px;
        background: var(--macos-bg-tertiary);
      }
    }

    .span-info {
      width: 280px;
      display: flex;
      align-items: center;
      gap: 8px;
      min-width: 0;
      padding-left: 8px;

      .error-indicator {
        color: var(--macos-danger);
        font-size: 16px;
        flex-shrink: 0;
      }

      .service-name {
        font-size: 13px;
        font-weight: 600;
        color: var(--macos-blue);
        white-space: nowrap;

        &.error {
          color: var(--macos-danger);
        }
      }

      .operation-name {
        font-size: 12px;
        color: var(--macos-text-secondary);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }

    .span-timeline {
      flex: 1;
      height: 26px;
      position: relative;
      background: var(--macos-bg-secondary);
      border-radius: 4px;
      margin-right: 12px;

      .span-bar {
        position: absolute;
        height: 100%;
        background: linear-gradient(90deg, #67C23A 0%, #85CE61 100%);
        border-radius: 4px;
        display: flex;
        align-items: center;
        justify-content: flex-end;
        padding-right: 8px;
        min-width: 30px;
        box-shadow: 0 2px 4px rgba(103, 194, 58, 0.3);

        &.error {
          background: linear-gradient(90deg, #F56C6C 0%, #F78989 100%);
          box-shadow: 0 2px 4px rgba(245, 108, 108, 0.3);
        }

        &.critical {
          border-left: 4px solid #E6A23C;
        }

        .span-duration {
          font-size: 11px;
          color: #FFFFFF;
          font-weight: 600;
          white-space: nowrap;
          text-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);

          &.outside {
            position: absolute;
            color: #606266 !important;
            text-shadow: none;
          }
        }
      }
    }
  }
}

.right-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.service-map-card {
  :deep(.el-card__header) {
    padding: 14px 20px;
    border-bottom: 1px solid var(--macos-border);
    background: var(--macos-fill-tertiary);
  }

  .service-map {
    height: 220px;
  }
}

.span-details-card {
  flex: 1;

  :deep(.el-card__header) {
    padding: 14px 20px;
    border-bottom: 1px solid var(--macos-border);
    background: var(--macos-fill-tertiary);
  }

  :deep(.el-card__body) {
    padding: 0;
  }

  :deep(.el-tabs__header) {
    margin: 0;
    padding: 0 20px;
    background: var(--macos-fill-tertiary);
  }

  :deep(.el-tabs__content) {
    padding: 20px;
  }
}

.span-details {
  .detail-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 20px;
  }

  .detail-item {
    display: flex;
    flex-direction: column;
    gap: 6px;

    .detail-label {
      font-size: 12px;
      color: var(--macos-text-tertiary);
      font-weight: 500;
    }

    .detail-value {
      font-size: 14px;
      color: var(--macos-text-primary);
      font-weight: 500;

      &.mono {
        font-family: 'SF Mono', monospace;
      }
    }

    .status-text {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 14px;
      font-weight: 600;

      &.success {
        color: var(--macos-success);
      }

      &.error {
        color: var(--macos-danger);
      }

      .el-icon {
        font-size: 16px;
      }
    }
  }

  .tags-list {
    .tag-item {
      display: flex;
      padding: 10px 0;
      border-bottom: 1px solid var(--macos-border);

      &:last-child {
        border-bottom: none;
      }

      .tag-key {
        width: 150px;
        font-size: 13px;
        color: var(--macos-text-secondary);
        font-family: 'SF Mono', monospace;
      }

      .tag-value {
        flex: 1;
        font-size: 13px;
        color: var(--macos-text-primary);
        font-family: 'SF Mono', monospace;
        word-break: break-all;

        &.error {
          color: var(--macos-danger);
          font-weight: 500;
        }
      }
    }
  }

  .logs-list {
    .log-item {
      display: flex;
      gap: 16px;
      padding: 10px 0;
      border-bottom: 1px solid var(--macos-border);

      .log-time {
        font-size: 12px;
        color: var(--macos-text-tertiary);
        font-family: 'SF Mono', monospace;
        white-space: nowrap;
      }

      .log-message {
        font-size: 13px;
        color: var(--macos-text-secondary);
      }
    }
  }

  .process-info {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }
}
</style>
