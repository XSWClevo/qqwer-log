<template>
  <AppLayout>
    <div class="alert-rules-page">
      <div class="page-header">
        <div>
          <h1 class="page-title">告警规则</h1>
          <p class="page-subtitle">定义日志命中条件、触发阈值和通知动作</p>
        </div>
        <div class="header-actions">
          <el-button :icon="Refresh" :loading="loading" @click="loadRules">刷新</el-button>
          <el-button type="primary" :icon="Plus" @click="openCreateDrawer">创建规则</el-button>
        </div>
      </div>

      <div class="toolbar">
        <el-input
          v-model="filters.keyword"
          :prefix-icon="Search"
          placeholder="搜索规则名称、描述或条件"
          clearable
          class="keyword-input"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select v-model="filters.status" placeholder="运行状态" clearable class="filter-select" @change="handleSearch">
          <el-option label="已启用" value="enabled" />
          <el-option label="已禁用" value="disabled" />
        </el-select>
        <el-select v-model="filters.severity" placeholder="风险等级" clearable class="filter-select" @change="handleSearch">
          <el-option label="严重" value="critical" />
          <el-option label="警告" value="warning" />
          <el-option label="信息" value="info" />
        </el-select>
        <el-select v-model="filters.type" placeholder="规则类型" clearable class="filter-select" @change="handleSearch">
          <el-option label="日志查询" value="log_query" />
          <el-option label="聚合阈值" value="aggregation" />
          <el-option label="指标阈值" value="metric_threshold" />
          <el-option label="异常检测" value="anomaly" />
        </el-select>
        <el-select v-model="filters.channel" placeholder="通知渠道" clearable class="filter-select" @change="handleSearch">
          <el-option label="页面展示" value="page" />
        </el-select>
        <el-button text @click="resetFilters">重置</el-button>
      </div>

      <div class="summary-grid">
        <div v-for="item in summaryItems" :key="item.label" class="summary-tile">
          <span class="summary-label">{{ item.label }}</span>
          <strong class="summary-value">{{ item.value }}</strong>
          <span class="summary-sub">{{ item.sub }}</span>
        </div>
      </div>

      <section class="panel rules-panel">
        <div class="panel-header">
          <div>
            <h2>规则清单</h2>
            <p>{{ pagination.total }} 条规则，当前显示 {{ rules.length }} 条</p>
          </div>
        </div>

        <el-table v-loading="loading" :data="rules" height="calc(100vh - 426px)" stripe>
          <el-table-column label="状态" width="96" align="center">
            <template #default="{ row }">
              <el-switch
                v-model="row.enabled"
                :loading="togglingRuleId === row.id"
                @change="handleToggleStatus(row)"
              />
            </template>
          </el-table-column>

          <el-table-column label="规则" min-width="280">
            <template #default="{ row }">
              <div class="rule-name-cell">
                <button class="rule-link" type="button" @click="openEditDrawer(row.id)">{{ row.name }}</button>
                <span class="rule-description">{{ row.description || '未填写描述' }}</span>
                <div class="monitor-meta">
                  <span v-if="monitorOptions(row).priority" class="meta-chip priority">
                    {{ monitorOptions(row).priority }}
                  </span>
                  <span v-if="monitorOptions(row).team" class="meta-chip">
                    {{ monitorOptions(row).team }}
                  </span>
                  <span class="meta-chip">{{ monitorModeLabel(row) }}</span>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="Monitor" width="152">
            <template #default="{ row }">
              <el-tag :type="stateTag(row.currentState?.state)" size="small">
                {{ stateLabel(row.currentState?.state) }}
              </el-tag>
              <div class="strategy-sub">{{ formatRelativeTime(row.currentState?.lastStateChangedAt) }}</div>
            </template>
          </el-table-column>

          <el-table-column label="风险" width="104" align="center">
            <template #default="{ row }">
              <el-tag :type="severityTag(row.severity)" size="small">{{ severityLabel(row.severity) }}</el-tag>
            </template>
          </el-table-column>

          <el-table-column label="触发逻辑" min-width="260">
            <template #default="{ row }">
              <div class="trigger-summary">{{ row.triggerConditionSummary || buildConditionSummary(row) }}</div>
              <div class="query-summary mono">{{ row.condition?.query || '结构化条件' }}</div>
            </template>
          </el-table-column>

          <el-table-column label="执行策略" width="170">
            <template #default="{ row }">
              <div class="strategy-line">每 {{ row.evalEvery || '1m' }} 评估</div>
              <div class="strategy-sub">连续 {{ row.consecutiveHits || 1 }} 次命中</div>
              <div class="strategy-sub">{{ monitorOptionSummary(row) }}</div>
            </template>
          </el-table-column>

          <el-table-column label="通知" width="150">
            <template #default="{ row }">
              <div class="channel-list">
                <el-tooltip v-for="channel in row.notificationChannels || []" :key="channel" :content="channelLabel(channel)">
                  <span class="channel-chip">{{ channelLabel(channel).slice(0, 1) }}</span>
                </el-tooltip>
                <span v-if="!row.notificationChannels?.length" class="muted">未配置</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="最近触发" width="170">
            <template #default="{ row }">
              <div class="last-triggered">{{ formatRelativeTime(row.lastEvaluation?.finishedAt || row.currentState?.lastEvaluatedAt || row.lastTriggeredAt) }}</div>
              <div class="strategy-sub">累计 {{ row.triggerCount || 0 }} 次</div>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="94" align="center" fixed="right">
            <template #default="{ row }">
              <el-dropdown trigger="click" @command="handleDropdownCommand($event, row)">
                <el-button text :icon="MoreFilled" />
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="edit">
                      <el-icon><Edit /></el-icon>
                      编辑
                    </el-dropdown-item>
                    <el-dropdown-item command="test">
                      <el-icon><VideoPlay /></el-icon>
                      测试
                    </el-dropdown-item>
                    <el-dropdown-item command="duplicate">
                      <el-icon><CopyDocument /></el-icon>
                      复制
                    </el-dropdown-item>
                    <el-dropdown-item command="delete" divided>
                      <el-icon><Delete /></el-icon>
                      删除
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination">
          <el-pagination
            v-model:current-page="pagination.pageNum"
            v-model:page-size="pagination.pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="pagination.total"
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="loadRules"
            @size-change="handlePageSizeChange"
          />
        </div>
      </section>

      <CreateRuleDrawer
        v-model="drawerVisible"
        :rule-id="editingRuleId"
        @success="handleDrawerSuccess"
      />
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  CopyDocument,
  Delete,
  Edit,
  MoreFilled,
  Plus,
  Refresh,
  Search,
  VideoPlay
} from '@element-plus/icons-vue'
import AppLayout from '@/components/layout/AppLayout.vue'
import CreateRuleDrawer from './components/CreateRuleDrawer.vue'
import {
  deleteAlertRule,
  duplicateAlertRule,
  queryAlertRules,
  testAlertRule,
  toggleAlertRuleStatus,
  type AlertRule
} from '@/api/alert'

type AlertRuleRow = AlertRule & {
  enabled: boolean
}

const loading = ref(false)
const drawerVisible = ref(false)
const editingRuleId = ref<number | null>(null)
const togglingRuleId = ref<number | null>(null)
const rules = ref<AlertRuleRow[]>([])

const filters = reactive({
  keyword: '',
  status: '',
  severity: '',
  type: '',
  channel: ''
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0
})

const summaryItems = computed(() => {
  const enabledCount = rules.value.filter(rule => rule.enabled).length
  const criticalCount = rules.value.filter(rule => normalizeSeverity(rule.severity) === 'critical').length
  const triggeredCount = rules.value.reduce((sum, rule) => sum + (rule.triggerCount || 0), 0)

  return [
    { label: '当前页规则', value: rules.value.length.toLocaleString(), sub: `共 ${pagination.total.toLocaleString()} 条` },
    { label: '已启用', value: enabledCount.toLocaleString(), sub: `${rules.value.length - enabledCount} 条停用` },
    { label: '严重规则', value: criticalCount.toLocaleString(), sub: '当前筛选范围' },
    { label: '累计触发', value: triggeredCount.toLocaleString(), sub: '当前页合计' }
  ]
})

const loadRules = async () => {
  loading.value = true
  try {
    const response = await queryAlertRules({
      keyword: filters.keyword || undefined,
      status: filters.status || undefined,
      severity: filters.severity || undefined,
      type: filters.type || undefined,
      channel: filters.channel || undefined,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    })

    const page = response.data
    rules.value = (page?.records || []).map(rule => ({
      ...rule,
      enabled: rule.enabled !== false,
      severity: normalizeSeverity(rule.severity)
    }))
    pagination.total = page?.total || 0
  } catch (error: any) {
    ElMessage.error('加载告警规则失败: ' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadRules()
}

const handlePageSizeChange = () => {
  pagination.pageNum = 1
  loadRules()
}

const resetFilters = () => {
  filters.keyword = ''
  filters.status = ''
  filters.severity = ''
  filters.type = ''
  filters.channel = ''
  handleSearch()
}

const openCreateDrawer = () => {
  editingRuleId.value = null
  drawerVisible.value = true
}

const openEditDrawer = (ruleId: number) => {
  editingRuleId.value = ruleId
  drawerVisible.value = true
}

const handleDrawerSuccess = () => {
  editingRuleId.value = null
  loadRules()
}

const handleToggleStatus = async (rule: AlertRuleRow) => {
  togglingRuleId.value = rule.id
  try {
    await toggleAlertRuleStatus(rule.id, rule.enabled)
    ElMessage.success(`规则已${rule.enabled ? '启用' : '停用'}`)
  } catch (error: any) {
    rule.enabled = !rule.enabled
    ElMessage.error('切换规则状态失败: ' + (error.message || '未知错误'))
  } finally {
    togglingRuleId.value = null
  }
}

const handleAction = async (command: string | number | object, rule: AlertRuleRow) => {
  const action = String(command)
  if (action === 'edit') {
    openEditDrawer(rule.id)
    return
  }

  if (action === 'test') {
    await handleTestRule(rule)
    return
  }

  if (action === 'duplicate') {
    await handleDuplicateRule(rule)
    return
  }

  if (action === 'delete') {
    await handleDeleteRule(rule)
  }
}

const handleDropdownCommand = (command: string | number | object, rule: AlertRuleRow) => {
  void handleAction(command, rule)
}

const handleTestRule = async (rule: AlertRuleRow) => {
  try {
    const response = await testAlertRule(rule.id)
    const result = response.data || {}
    const matchedCount = result.matchedCount ?? result.count ?? result.total
    ElMessage.success(matchedCount === undefined ? '测试完成' : `测试完成，命中 ${matchedCount} 条`)
  } catch (error: any) {
    ElMessage.error('测试规则失败: ' + (error.message || '未知错误'))
  }
}

const handleDuplicateRule = async (rule: AlertRuleRow) => {
  try {
    await duplicateAlertRule(rule.id)
    ElMessage.success('规则已复制')
    loadRules()
  } catch (error: any) {
    ElMessage.error('复制规则失败: ' + (error.message || '未知错误'))
  }
}

const handleDeleteRule = async (rule: AlertRuleRow) => {
  try {
    await ElMessageBox.confirm(`确定删除规则“${rule.name}”吗？删除后不会再产生新的告警事件。`, '删除规则', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await deleteAlertRule(rule.id)
    ElMessage.success('规则已删除')
    loadRules()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('删除规则失败: ' + (error.message || '未知错误'))
    }
  }
}

const buildConditionSummary = (rule?: AlertRule) => {
  if (!rule) return '未配置'
  const condition = rule.condition || {}
  const metric = condition.aggregate?.function || 'count'
  const operator = rule.thresholds?.critical?.operator || condition.trigger?.operator || 'gt'
  const threshold = rule.thresholds?.critical?.threshold ?? condition.trigger?.threshold ?? 0
  const warningThreshold = rule.thresholds?.warning?.threshold ?? condition.trigger?.warningThreshold
  const recoveryThreshold = rule.thresholds?.recovery?.threshold ?? condition.trigger?.recoveryThreshold
  const timeWindow = rule.thresholds?.critical?.timeWindow || condition.trigger?.timeWindow || '5m'
  const parts = [`${metric} ${operatorLabel(operator)} ${threshold} / ${timeWindow}`]
  if (warningThreshold !== undefined && warningThreshold !== null && warningThreshold !== '') {
    parts.push(`warning ${operatorLabel(operator)} ${warningThreshold}`)
  }
  if (recoveryThreshold !== undefined && recoveryThreshold !== null && recoveryThreshold !== '') {
    parts.push(`recovery ${recoveryThreshold}`)
  }
  return parts.join(' · ')
}

const monitorOptions = (rule?: AlertRule) => rule?.monitorOptions || rule?.condition?.options || {}

const monitorModeLabel = (rule?: AlertRule) => {
  const mode = monitorOptions(rule).alertMode || (Array.isArray(rule?.condition?.groupBy) && rule.condition.groupBy.length ? 'multi' : 'simple')
  return mode === 'multi' ? 'Multi Alert' : 'Simple Alert'
}

const monitorOptionSummary = (rule?: AlertRule) => {
  const options = monitorOptions(rule)
  const parts: string[] = []
  if (options.notifyNoData) parts.push(`No Data ${options.noDataTimeframe || ''}`.trim())
  if (options.evaluationDelaySeconds) parts.push(`延迟 ${options.evaluationDelaySeconds}s`)
  if (options.renotifyIntervalMinutes) parts.push(`重通知 ${options.renotifyIntervalMinutes}m`)
  return parts.length ? parts.join(' · ') : '默认 Monitor 选项'
}

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

const stateTag = (state?: string) => {
  const normalized = String(state || 'OK').toUpperCase()
  if (normalized === 'CRITICAL') return 'danger'
  if (normalized === 'WARNING' || normalized === 'NO_DATA') return 'warning'
  if (normalized === 'RECOVERED') return 'success'
  return 'info'
}

const stateLabel = (state?: string) => {
  const map: Record<string, string> = {
    OK: 'OK',
    WARNING: 'WARNING',
    CRITICAL: 'CRITICAL',
    NO_DATA: 'NO DATA',
    RECOVERED: 'RECOVERED'
  }
  return map[String(state || 'OK').toUpperCase()] || state || 'OK'
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

const channelLabel = (channel: string) => {
  const map: Record<string, string> = {
    page: '页面展示',
    email: '邮件',
    slack: 'Slack',
    webhook: 'Webhook'
  }
  return map[channel] || channel
}

const formatRelativeTime = (time?: string) => {
  if (!time) return '从未触发'
  const timestamp = new Date(time).getTime()
  if (Number.isNaN(timestamp)) return time.replace('T', ' ').slice(0, 19)

  const diff = Date.now() - timestamp
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  if (hours < 24) return `${hours} 小时前`
  if (days < 7) return `${days} 天前`
  return new Date(time).toLocaleString()
}

onMounted(() => {
  loadRules()
})
</script>

<style scoped lang="scss">
.alert-rules-page {
  min-height: 100vh;
  box-sizing: border-box;
  padding: 20px 24px 28px;
  background: var(--macos-fill-tertiary);
  color: var(--macos-text-primary);
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 650;
}

.page-subtitle {
  margin: 6px 0 0;
  color: var(--macos-text-secondary);
  font-size: 13px;
}

.header-actions,
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.toolbar {
  flex-wrap: wrap;
  padding: 12px;
  margin-bottom: 14px;
  background: var(--macos-card-bg);
  border: 1px solid var(--macos-border);
  border-radius: 8px;
}

.keyword-input {
  width: 300px;
}

.filter-select {
  width: 136px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(160px, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.summary-tile {
  min-height: 86px;
  padding: 14px 16px;
  background: var(--macos-card-bg);
  border: 1px solid var(--macos-border);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.summary-label,
.summary-sub,
.strategy-sub,
.muted {
  color: var(--macos-text-tertiary);
  font-size: 12px;
}

.summary-value {
  margin-top: 8px;
  font-size: 24px;
  line-height: 1.2;
}

.panel {
  background: var(--macos-card-bg);
  border: 1px solid var(--macos-border);
  border-radius: 8px;
  overflow: hidden;
}

.panel-header {
  min-height: 56px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--macos-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;

  h2 {
    margin: 0;
    font-size: 15px;
    font-weight: 650;
  }

  p {
    margin: 4px 0 0;
    color: var(--macos-text-secondary);
    font-size: 12px;
  }
}

.rule-name-cell {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.rule-link {
  width: fit-content;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--macos-blue);
  font: inherit;
  font-weight: 650;
  cursor: pointer;
}

.rule-description,
.query-summary,
.strategy-line,
.last-triggered {
  color: var(--macos-text-secondary);
  font-size: 12px;
}

.monitor-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.meta-chip {
  width: fit-content;
  max-width: 130px;
  padding: 2px 7px;
  border-radius: 999px;
  background: var(--macos-fill-secondary);
  color: var(--macos-text-secondary);
  font-size: 11px;
  line-height: 18px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  &.priority {
    color: var(--macos-danger);
    background: var(--macos-danger-bg);
    font-weight: 700;
  }
}

.trigger-summary {
  color: var(--macos-text-primary);
  font-size: 13px;
  font-weight: 600;
}

.mono {
  margin-top: 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.channel-list {
  display: flex;
  align-items: center;
  gap: 6px;
}

.channel-chip {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--macos-blue-light);
  color: var(--macos-blue);
  font-size: 12px;
  font-weight: 700;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px;
  border-top: 1px solid var(--macos-border);
}

@media (max-width: 960px) {
  .page-header {
    flex-direction: column;
  }

  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .keyword-input,
  .filter-select {
    width: 100%;
  }
}
</style>
