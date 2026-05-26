<template>
  <el-drawer
    v-model="visible"
    title="告警详情"
    size="760px"
    v-loading="loading"
  >
    <div v-if="activeAlert" class="alert-detail">
      <div class="detail-header">
        <div>
          <div class="header-row">
            <h2 class="rule-name">{{ activeAlert.ruleName || '-' }}</h2>
            <el-tag :type="severityTag(activeAlert.severity)" size="small">{{ severityLabel(activeAlert.severity) }}</el-tag>
          </div>
          <p class="trigger-time">{{ formatDateTime(activeAlert.triggeredAt) }}</p>
        </div>
        <el-button
          v-if="!activeAlert.acknowledged"
          type="primary"
          :loading="acknowledging"
          @click="handleAcknowledge"
        >
          确认告警
        </el-button>
      </div>

      <section class="detail-section">
        <div class="section-title">触发摘要</div>
        <div class="summary-box">
          <p class="alert-message">{{ activeAlert.message || '无告警消息' }}</p>
          <dl class="meta-grid">
            <dt>事件状态</dt>
            <dd>{{ eventLevelText }}</dd>
            <dt>原因</dt>
            <dd>{{ logData.reason || '-' }}</dd>
            <dt>触发值</dt>
            <dd>{{ activeAlert.triggeredValue || actualValueText }}</dd>
            <dt>相关实体</dt>
            <dd>{{ activeAlert.relatedEntity || relatedEntityText }}</dd>
            <dt>优先级</dt>
            <dd>{{ monitorOptions.priority || '-' }}</dd>
            <dt>负责团队</dt>
            <dd>{{ monitorOptions.team || '-' }}</dd>
            <dt>处置状态</dt>
            <dd>{{ activeAlert.acknowledged ? `已确认 ${formatDateTime(activeAlert.acknowledgedAt)}` : '待确认' }}</dd>
            <dt>通知状态</dt>
            <dd>{{ notificationStatusLabel(activeAlert.notificationStatus) }}</dd>
          </dl>
          <div v-if="monitorTags.length" class="tag-list">
            <span v-for="tag in monitorTags" :key="tag" class="meta-tag">{{ tag }}</span>
          </div>
        </div>
      </section>

      <section class="detail-section">
        <div class="section-title">规则条件</div>
        <div class="condition-grid">
          <div class="condition-item">
            <span>查询</span>
            <code>{{ conditionText }}</code>
          </div>
          <div class="condition-item">
            <span>阈值</span>
            <code>{{ thresholdText }}</code>
          </div>
          <div class="condition-item">
            <span>恢复</span>
            <code>{{ recoveryText }}</code>
          </div>
          <div class="condition-item">
            <span>窗口</span>
            <code>{{ conditionTimeWindow }}</code>
          </div>
        </div>
        <div class="option-grid">
          <div class="option-item">
            <span>No Data</span>
            <strong>{{ monitorOptions.notifyNoData ? monitorOptions.noDataTimeframe || '开启' : '关闭' }}</strong>
          </div>
          <div class="option-item">
            <span>评估延迟</span>
            <strong>{{ monitorOptions.evaluationDelaySeconds || 0 }}s</strong>
          </div>
          <div class="option-item">
            <span>完整窗口</span>
            <strong>{{ monitorOptions.requireFullWindow ? '开启' : '关闭' }}</strong>
          </div>
          <div class="option-item">
            <span>重复通知</span>
            <strong>{{ renotifyText }}</strong>
          </div>
        </div>
      </section>

      <section class="detail-section">
        <div class="section-title">通知结果</div>
        <div v-if="notificationResults.length" class="notification-results">
          <div
            v-for="(result, index) in notificationResults"
            :key="`${result.channel || 'channel'}-${index}`"
            :class="['result-item', result.status === 'success' ? 'success' : 'failed']"
          >
            <el-icon :size="18">
              <SuccessFilled v-if="result.status === 'success'" />
              <CircleCloseFilled v-else />
            </el-icon>
            <div class="result-content">
              <div class="result-channel">{{ channelLabel(result.channel) }}</div>
              <div class="result-status">{{ result.message || (result.status === 'success' ? '发送成功' : '发送失败') }}</div>
            </div>
            <span class="result-time">{{ formatDateTime(result.sentAt) }}</span>
          </div>
        </div>
        <el-empty v-else description="暂无通知结果" :image-size="72" />
      </section>

      <section class="detail-section">
        <div class="section-title">上下文日志</div>
        <div v-if="contextLogs.length" class="contextual-logs">
          <div v-for="(log, index) in contextLogs" :key="index" class="log-entry">
            <span class="log-time">{{ formatDateTime(log.timestamp || log.triggeredAt) }}</span>
            <span class="log-level" :class="`level-${String(log.severity || 'info').toLowerCase()}`">
              {{ log.severity || 'INFO' }}
            </span>
            <span class="log-message">{{ log.message || JSON.stringify(log) }}</span>
          </div>
        </div>
        <el-empty v-else description="暂无上下文日志" :image-size="72" />
      </section>

      <section v-if="logDataText" class="detail-section">
        <div class="section-title">原始事件数据</div>
        <pre class="raw-json">{{ logDataText }}</pre>
      </section>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleCloseFilled, SuccessFilled } from '@element-plus/icons-vue'
import {
  acknowledgeAlertEvent,
  getAlertEventById,
  type AlertEvent,
  type AlertNotificationResult
} from '@/api/alert'

const props = defineProps<{
  modelValue: boolean
  alert: AlertEvent | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  acknowledged: [eventId: number]
}>()

const visible = ref(false)
const loading = ref(false)
const acknowledging = ref(false)
const detailData = ref<AlertEvent | null>(null)

const activeAlert = computed(() => detailData.value || props.alert)
const logData = computed(() => activeAlert.value?.logData || {})
const condition = computed(() => logData.value.condition || {})
const thresholds = computed(() => logData.value.thresholds || {})
const monitorOptions = computed(() => logData.value.options || condition.value.options || {})
const monitorTags = computed<string[]>(() => {
  const tags = logData.value.tags || monitorOptions.value.tags
  return Array.isArray(tags) ? tags.filter(Boolean) : []
})

const notificationResults = computed<AlertNotificationResult[]>(() => activeAlert.value?.notificationResults || [])
const contextLogs = computed<Record<string, any>[]>(() => {
  if (activeAlert.value?.contextLogs?.length) return activeAlert.value.contextLogs
  const samples = logData.value.results
  return Array.isArray(samples) ? samples : []
})

const conditionText = computed(() => {
  if (condition.value.query) return condition.value.query
  if (condition.value.filters) return '结构化筛选条件'
  return '未记录查询条件'
})

const thresholdText = computed(() => {
  const actual = logData.value.actualValue ?? logData.value.count
  const threshold = logData.value.threshold ?? thresholds.value.critical?.threshold ?? condition.value.trigger?.threshold
  const operator = thresholds.value.critical?.operator || condition.value.trigger?.operator || 'gt'
  const warningThreshold = thresholds.value.warning?.threshold ?? condition.value.trigger?.warningThreshold
  if (threshold === undefined) return '-'
  const parts = [`${actual ?? '-'} ${operatorLabel(operator)} ${threshold}`]
  if (warningThreshold !== undefined && warningThreshold !== null) {
    parts.push(`warning ${operatorLabel(operator)} ${warningThreshold}`)
  }
  return parts.join(' · ')
})

const recoveryText = computed(() => {
  const recoveryThreshold = logData.value.recoveryThreshold
    ?? thresholds.value.recovery?.threshold
    ?? condition.value.trigger?.recoveryThreshold
  return recoveryThreshold === undefined || recoveryThreshold === null ? '-' : String(recoveryThreshold)
})

const conditionTimeWindow = computed(() => thresholds.value.critical?.timeWindow || condition.value.trigger?.timeWindow || '-')
const actualValueText = computed(() => {
  const actual = logData.value.actualValue ?? logData.value.count
  const threshold = logData.value.threshold
  if (logData.value.level === 'no_data') return 'No Data'
  if (actual === undefined) return '-'
  return threshold === undefined ? String(actual) : `${actual} / 阈值 ${threshold}`
})

const relatedEntityText = computed(() => {
  const groupValues = logData.value.groupValues
  if (groupValues && typeof groupValues === 'object' && Object.keys(groupValues).length) {
    return Object.entries(groupValues).map(([key, value]) => `${key}: ${value}`).join(', ')
  }
  return logData.value.tableName || logData.value.datasourceId || '-'
})

const logDataText = computed(() => {
  if (!Object.keys(logData.value).length) return ''
  return JSON.stringify(logData.value, null, 2)
})

const eventLevelText = computed(() => {
  const map: Record<string, string> = {
    critical: '严重阈值',
    warning: '警告阈值',
    no_data: 'No Data',
    recovered: 'Recovered',
    ok: 'OK'
  }
  const level = String(activeAlert.value?.state || logData.value.level || '').toLowerCase()
  return map[level] || severityLabel(activeAlert.value?.severity)
})

const renotifyText = computed(() => {
  if (!monitorOptions.value.renotifyIntervalMinutes) return '关闭'
  const occurrences = monitorOptions.value.renotifyOccurrences
  return occurrences ? `${monitorOptions.value.renotifyIntervalMinutes}m / ${occurrences} 次` : `${monitorOptions.value.renotifyIntervalMinutes}m`
})

const loadDetail = async () => {
  if (!props.alert?.id) return

  loading.value = true
  try {
    const response = await getAlertEventById(props.alert.id)
    detailData.value = response.data
  } catch (error: any) {
    ElMessage.error('加载告警详情失败: ' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

const handleAcknowledge = async () => {
  if (!activeAlert.value?.id) return

  acknowledging.value = true
  try {
    await acknowledgeAlertEvent(activeAlert.value.id)
    if (detailData.value) {
      detailData.value.acknowledged = true
      detailData.value.acknowledgedAt = new Date().toISOString()
    }
    emit('acknowledged', activeAlert.value.id)
    ElMessage.success('告警已确认')
  } catch (error: any) {
    ElMessage.error('确认告警失败: ' + (error.message || '未知错误'))
  } finally {
    acknowledging.value = false
  }
}

watch(() => props.modelValue, (value) => {
  visible.value = value
  if (value) {
    detailData.value = null
    loadDetail()
  }
})

watch(visible, (value) => {
  emit('update:modelValue', value)
})

const normalizeSeverity = (severity?: string) => String(severity || 'info').toLowerCase()

const severityTag = (severity?: string) => {
  const map: Record<string, 'danger' | 'warning' | 'info'> = {
    critical: 'danger',
    warning: 'warning',
    info: 'info'
  }
  return map[normalizeSeverity(severity)] || 'info'
}

const severityLabel = (severity?: string) => {
  const map: Record<string, string> = {
    critical: '严重',
    warning: '警告',
    info: '信息'
  }
  return map[normalizeSeverity(severity)] || severity || '-'
}

const operatorLabel = (operator?: string) => {
  const map: Record<string, string> = {
    gt: '>',
    gte: '>=',
    lt: '<',
    lte: '<=',
    eq: '=',
    ne: '!='
  }
  return map[String(operator || '').toLowerCase()] || operator || '>'
}

const channelLabel = (channel?: string) => {
  const map: Record<string, string> = {
    page: '页面展示',
    email: '邮件',
    slack: 'Slack',
    webhook: 'Webhook'
  }
  return map[channel || ''] || channel || '通知渠道'
}

const notificationStatusLabel = (status?: string) => {
  const map: Record<string, string> = {
    failed: '发送失败',
    sent: '已记录',
    pending: '待发送',
    skipped: '未配置'
  }
  return map[String(status || '').toLowerCase()] || status || '-'
}

const formatDateTime = (time?: string) => {
  if (!time) return '-'
  return String(time).replace('T', ' ').slice(0, 19)
}
</script>

<style scoped lang="scss">
.alert-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--macos-border);
}

.header-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.rule-name {
  margin: 0;
  font-size: 20px;
  font-weight: 650;
  color: var(--macos-text-primary);
}

.trigger-time {
  margin: 6px 0 0;
  color: var(--macos-text-secondary);
  font-size: 13px;
}

.detail-section {
  padding: 14px 16px;
  background: var(--macos-card-bg);
  border: 1px solid var(--macos-border);
  border-radius: 8px;
}

.section-title {
  margin-bottom: 12px;
  color: var(--macos-text-primary);
  font-size: 15px;
  font-weight: 650;
}

.summary-box {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.alert-message {
  margin: 0;
  color: var(--macos-text-primary);
  line-height: 1.55;
}

.meta-grid {
  display: grid;
  grid-template-columns: 92px 1fr;
  gap: 10px 14px;
  margin: 0;

  dt {
    color: var(--macos-text-secondary);
  }

  dd {
    margin: 0;
    color: var(--macos-text-primary);
    word-break: break-word;
  }
}

.condition-grid {
  display: grid;
  grid-template-columns: 1fr 190px 120px 110px;
  gap: 10px;
}

.condition-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;

  span {
    color: var(--macos-text-secondary);
    font-size: 12px;
  }

  code {
    min-height: 34px;
    padding: 8px 10px;
    border-radius: 6px;
    background: var(--macos-fill-secondary);
    color: var(--macos-text-primary);
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 12px;
    white-space: pre-wrap;
    word-break: break-word;
  }
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 2px;
}

.meta-tag {
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--macos-blue-light);
  color: var(--macos-blue);
  font-size: 12px;
  line-height: 20px;
}

.option-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-top: 10px;
}

.option-item {
  padding: 10px;
  border-radius: 8px;
  background: var(--macos-fill-secondary);

  span,
  strong {
    display: block;
  }

  span {
    color: var(--macos-text-tertiary);
    font-size: 12px;
  }

  strong {
    margin-top: 4px;
    color: var(--macos-text-primary);
    font-size: 13px;
  }
}

.notification-results {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.result-item {
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;

  &.success {
    background: var(--macos-success-bg);
    color: var(--macos-success);
  }

  &.failed {
    background: var(--macos-danger-bg);
    color: var(--macos-danger);
  }
}

.result-channel {
  color: var(--macos-text-primary);
  font-weight: 650;
}

.result-status,
.result-time {
  color: var(--macos-text-secondary);
  font-size: 12px;
}

.contextual-logs {
  max-height: 320px;
  overflow-y: auto;
}

.log-entry {
  display: grid;
  grid-template-columns: 150px 72px minmax(0, 1fr);
  gap: 10px;
  padding: 9px 0;
  border-bottom: 1px solid var(--macos-border);
  font-size: 12px;

  &:last-child {
    border-bottom: 0;
  }
}

.log-time {
  color: var(--macos-text-tertiary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.log-level {
  width: fit-content;
  height: 22px;
  padding: 2px 8px;
  border-radius: 999px;
  font-weight: 700;

  &.level-error,
  &.level-critical {
    color: var(--macos-danger);
    background: var(--macos-danger-bg);
  }

  &.level-warn,
  &.level-warning {
    color: #B45309;
    background: rgba(245, 158, 11, 0.14);
  }

  &.level-info {
    color: var(--macos-blue);
    background: var(--macos-blue-light);
  }
}

.log-message {
  color: var(--macos-text-primary);
  line-height: 1.45;
  word-break: break-word;
}

.raw-json {
  max-height: 260px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  border-radius: 6px;
  background: var(--macos-fill-secondary);
  color: var(--macos-text-primary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 760px) {
  .detail-header,
  .header-row {
    flex-direction: column;
  }

  .condition-grid,
  .option-grid,
  .log-entry,
  .result-item {
    grid-template-columns: 1fr;
  }
}
</style>
