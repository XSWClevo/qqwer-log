<template>
  <el-drawer
    v-model="visible"
    :title="isEditMode ? '编辑告警规则' : '创建告警规则'"
    size="760px"
    :before-close="handleClose"
  >
    <div class="rule-drawer" v-loading="loading">
      <el-form :model="formData" label-position="top" class="rule-form">
        <section class="quick-hero">
          <div>
            <span class="eyebrow">快速创建</span>
            <h3>先让告警跑起来，细节以后再调</h3>
            <p>
              默认会每 1 分钟统计最近 5 分钟日志数量，超过阈值就触发告警。
              通常只需要填名称、选择日志条件和设置阈值。
            </p>
          </div>
          <div class="default-stack">
            <span>默认 count 日志数量</span>
            <span>Simple Alert</span>
            <span>页面展示</span>
          </div>
        </section>

        <section class="form-section primary-section">
          <div class="section-heading compact">
            <span class="section-index">1</span>
            <div>
              <h3>这条告警关注什么？</h3>
              <p>先选一个接近的场景，系统会自动填好名称、条件和建议阈值。</p>
            </div>
          </div>

          <div class="scenario-grid">
            <button
              v-for="scenario in scenarios"
              :key="scenario.key"
              type="button"
              class="scenario-card"
              :class="{ active: activeScenarioKey === scenario.key }"
              @click="applyScenario(scenario)"
            >
              <span class="scenario-icon">{{ scenario.icon }}</span>
              <strong>{{ scenario.title }}</strong>
              <span>{{ scenario.description }}</span>
              <small>{{ scenario.summary }}</small>
            </button>
          </div>

          <el-form-item label="规则名称" required>
            <el-input v-model="formData.name" placeholder="例如: SSH 登录失败突然变多" />
          </el-form-item>

          <el-form-item label="日志筛选条件" required>
            <QueryBuilder
              v-model="formData.query"
              v-model:filters="formData.filters"
            />
          </el-form-item>
        </section>

        <section class="form-section primary-section">
          <div class="section-heading compact">
            <span class="section-index">2</span>
            <div>
              <h3>什么时候触发？</h3>
              <p>默认统计日志条数，超过阈值就告警。</p>
            </div>
          </div>

          <div class="simple-trigger-card">
            <div class="simple-trigger-copy">
              <span>默认判断</span>
              <strong>最近 5 分钟内，查询命中的日志数量超过这个值</strong>
              <p>大多数日志告警都可以先这样创建，后续观察误报再调细节。</p>
            </div>
            <el-form-item label="触发阈值" required class="threshold-item">
              <el-input-number v-model="formData.threshold" :min="0" style="width: 100%" />
            </el-form-item>
          </div>

          <div class="default-summary">
            <span>统计方式：{{ metricLabel(formData.metric) }}</span>
            <span>比较方式：{{ operatorText(formData.operator) }}</span>
            <span>时间窗口：{{ timeWindowLabel(formData.timeWindow) }}</span>
            <button type="button" @click="openAdvancedSettings">修改这些默认值</button>
          </div>

          <el-alert
            class="plain-alert"
            type="info"
            :closable="false"
            show-icon
            :title="`示例：符合上面日志条件的数据，在 ${timeWindowLabel(formData.timeWindow)} 内 ${operatorText(formData.operator)} ${formData.threshold} 条，就创建 ${formData.severity.toUpperCase()} 告警。`"
          />
        </section>

        <section class="form-section primary-section">
          <div class="section-heading compact">
            <span class="section-index">3</span>
            <div>
              <h3>告警发给谁？</h3>
              <p>默认只写入告警历史页面，不依赖邮件或外部通知客户端。</p>
            </div>
          </div>

          <div class="inline-grid notify-simple-grid">
            <el-form-item label="风险等级" required>
              <el-radio-group v-model="formData.severity">
                <el-radio-button label="critical">严重</el-radio-button>
                <el-radio-button label="warning">警告</el-radio-button>
                <el-radio-button label="info">信息</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="通知渠道">
              <el-checkbox-group v-model="formData.channels" class="channel-group">
                <el-checkbox label="page">
                  <el-icon><Message /></el-icon>
                  页面展示
                </el-checkbox>
              </el-checkbox-group>
            </el-form-item>
          </div>
        </section>

        <el-collapse v-model="expandedPanels" class="advanced-collapse">
          <el-collapse-item name="advanced">
            <template #title>
              <div class="advanced-title">
                <strong>高级设置</strong>
                <span>分组、恢复阈值、No Data、重复通知、消息模板</span>
              </div>
            </template>

            <section class="form-section subtle-section">
              <div class="section-heading">
                <span class="section-index muted">A</span>
                <div>
                  <h3>范围与分组</h3>
                  <p>只有需要 Multi Alert 或指定数据范围时再改。</p>
                </div>
              </div>

              <div class="inline-grid">
                <el-form-item label="规则类型">
                  <el-select v-model="formData.ruleType" style="width: 100%">
                    <el-option label="聚合阈值" value="aggregation" />
                    <el-option label="日志查询" value="log_query" />
                    <el-option label="异常检测" value="anomaly" />
                  </el-select>
                </el-form-item>
                <el-form-item label="作用范围">
                  <el-select v-model="formData.scopeType" style="width: 100%">
                    <el-option label="全部日志数据集" value="all" />
                    <el-option label="按分类" value="category" />
                    <el-option label="按数据源" value="datasource" />
                    <el-option label="按表" value="table" />
                  </el-select>
                </el-form-item>
                <el-form-item label="聚合字段">
                  <el-input v-model="formData.metricField" placeholder="count 可留空，其他统计函数填写字段" />
                </el-form-item>
              </div>

              <div class="inline-grid">
                <el-form-item label="分组字段">
                  <el-input v-model="formData.groupBy" placeholder="例如: hostname, appname" />
                  <div class="form-tip">填写后自动切换为 Multi Alert，每个分组独立判断。</div>
                </el-form-item>
                <el-form-item label="Monitor 模式">
                  <el-select v-model="formData.alertMode" :disabled="!!formData.groupBy.trim()" style="width: 100%">
                    <el-option label="Simple Alert" value="simple" />
                    <el-option label="Multi Alert" value="multi" />
                  </el-select>
                </el-form-item>
                <el-form-item label="描述">
                  <el-input v-model="formData.description" placeholder="可选，记录处置建议或影响范围" />
                </el-form-item>
              </div>
            </section>

            <section class="form-section subtle-section">
              <div class="section-heading">
                <span class="section-index muted">B</span>
                <div>
                  <h3>恢复与评估策略</h3>
                  <p>需要更稳的状态机时再配置。</p>
                </div>
              </div>

              <div class="inline-grid">
                <el-form-item label="统计方式">
                  <el-select v-model="formData.metric" style="width: 100%">
                    <el-option label="日志数量 count" value="count" />
                    <el-option label="唯一值数量 unique" value="unique" />
                    <el-option label="平均值 avg" value="avg" />
                    <el-option label="最大值 max" value="max" />
                    <el-option label="最小值 min" value="min" />
                    <el-option label="求和 sum" value="sum" />
                  </el-select>
                </el-form-item>
                <el-form-item label="比较方式">
                  <el-select v-model="formData.operator" style="width: 100%">
                    <el-option label="大于" value="gt" />
                    <el-option label="大于等于" value="gte" />
                    <el-option label="小于" value="lt" />
                    <el-option label="小于等于" value="lte" />
                    <el-option label="等于" value="eq" />
                  </el-select>
                </el-form-item>
                <el-form-item label="时间窗口">
                  <el-select v-model="formData.timeWindow" style="width: 100%">
                    <el-option label="最近 1 分钟" value="1m" />
                    <el-option label="最近 5 分钟" value="5m" />
                    <el-option label="最近 10 分钟" value="10m" />
                    <el-option label="最近 30 分钟" value="30m" />
                    <el-option label="最近 1 小时" value="1h" />
                  </el-select>
                </el-form-item>
              </div>

              <div class="inline-grid">
                <el-form-item label="警告阈值">
                  <el-input-number v-model="formData.warningThreshold" :min="0" style="width: 100%" />
                  <div class="form-tip">命中后生成 WARNING；留空则只判断触发阈值。</div>
                </el-form-item>
                <el-form-item label="恢复阈值">
                  <el-input-number v-model="formData.recoveryThreshold" :min="0" style="width: 100%" />
                  <div class="form-tip">低于恢复阈值后回到 OK/RECOVERED。</div>
                </el-form-item>
                <el-form-item label="评估频率">
                  <el-select v-model="formData.evalEvery" style="width: 100%">
                    <el-option label="每 1 分钟" value="1m" />
                    <el-option label="每 5 分钟" value="5m" />
                    <el-option label="每 10 分钟" value="10m" />
                    <el-option label="每 30 分钟" value="30m" />
                    <el-option label="每 1 小时" value="1h" />
                  </el-select>
                </el-form-item>
              </div>

              <div class="inline-grid">
                <el-form-item label="连续命中">
                  <el-input-number v-model="formData.consecutiveHits" :min="1" style="width: 100%" />
                </el-form-item>
                <el-form-item label="评估延迟（秒）">
                  <el-input-number v-model="formData.evaluationDelaySeconds" :min="0" style="width: 100%" />
                  <div class="form-tip">Vector/后端写入有延迟时使用。</div>
                </el-form-item>
                <el-form-item label="新分组延迟（秒）">
                  <el-input-number v-model="formData.newGroupDelaySeconds" :min="0" style="width: 100%" />
                </el-form-item>
              </div>
            </section>

            <section class="form-section subtle-section">
              <div class="section-heading">
                <span class="section-index muted">C</span>
                <div>
                  <h3>No Data 与重复通知</h3>
                  <p>适合采集断流、值班重复提醒等场景。</p>
                </div>
              </div>

              <div class="switch-grid">
                <div class="switch-card">
                  <div>
                    <strong>No Data 通知</strong>
                    <span>评估窗口没有数据时生成 WARNING 事件</span>
                  </div>
                  <el-switch v-model="formData.notifyNoData" />
                </div>
                <div class="switch-card">
                  <div>
                    <strong>Require Full Window</strong>
                    <span>避免稀疏数据误触发</span>
                  </div>
                  <el-switch v-model="formData.requireFullWindow" />
                </div>
                <div class="switch-card">
                  <div>
                    <strong>通知中包含 Tags</strong>
                    <span>用于团队路由和排障上下文</span>
                  </div>
                  <el-switch v-model="formData.includeTags" />
                </div>
              </div>

              <div class="inline-grid">
                <el-form-item label="No Data 时间">
                  <el-select v-model="formData.noDataTimeframe" :disabled="!formData.notifyNoData" style="width: 100%">
                    <el-option label="2 分钟" value="2m" />
                    <el-option label="5 分钟" value="5m" />
                    <el-option label="10 分钟" value="10m" />
                    <el-option label="30 分钟" value="30m" />
                    <el-option label="1 小时" value="1h" />
                  </el-select>
                </el-form-item>
                <el-form-item label="重复通知间隔（分钟）">
                  <el-input-number v-model="formData.renotifyIntervalMinutes" :min="0" style="width: 100%" />
                </el-form-item>
                <el-form-item label="重复通知次数">
                  <el-input-number v-model="formData.renotifyOccurrences" :min="0" style="width: 100%" />
                </el-form-item>
              </div>
            </section>

            <section class="form-section subtle-section">
              <div class="section-heading">
                <span class="section-index muted">D</span>
                <div>
                  <h3>通知内容</h3>
                  <p>默认模板可直接使用，需要团队协作时再补充。</p>
                </div>
              </div>

              <div class="inline-grid">
                <el-form-item label="启用规则">
                  <div class="switch-line">
                    <span>创建后立即开始评估</span>
                    <el-switch v-model="formData.enabled" />
                  </div>
                </el-form-item>
                <el-form-item label="负责团队">
                  <el-input v-model="formData.team" placeholder="例如: security / platform / sre" />
                </el-form-item>
                <el-form-item label="优先级">
                  <el-select v-model="formData.priority" clearable style="width: 100%">
                    <el-option label="P1 紧急" value="P1" />
                    <el-option label="P2 高" value="P2" />
                    <el-option label="P3 中" value="P3" />
                    <el-option label="P4 低" value="P4" />
                    <el-option label="P5 提示" value="P5" />
                  </el-select>
                </el-form-item>
              </div>

              <div class="inline-grid">
                <el-form-item label="Tags">
                  <el-input v-model="formData.tags" placeholder="例如: source:vector, env:prod" />
                  <div class="form-tip">多个标签用英文逗号分隔。</div>
                </el-form-item>
                <el-form-item label="静默期">
                  <el-input-number v-model="formData.silenceMinutes" :min="0" style="width: 100%" />
                  <div class="form-tip">单位：分钟，同一规则在静默期内不会重复触发。</div>
                </el-form-item>
              </div>

              <el-form-item label="升级消息">
                <el-input
                  v-model="formData.escalationMessage"
                  type="textarea"
                  :rows="2"
                  placeholder="重复通知或升级时给值班同学的补充说明"
                />
              </el-form-item>

              <el-form-item label="消息模板">
                <el-input
                  v-model="formData.messageTemplate"
                  type="textarea"
                  :rows="3"
                  placeholder="告警: {{rule_name}} 触发&#10;实际值: {{actual_value}}&#10;阈值: {{threshold}}"
                />
              </el-form-item>
            </section>
          </el-collapse-item>
        </el-collapse>
      </el-form>

      <div class="drawer-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ isEditMode ? '保存修改' : '创建规则' }}
        </el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Message } from '@element-plus/icons-vue'
import QueryBuilder from './QueryBuilder.vue'
import {
  createAlertRule,
  getAlertRuleById,
  updateAlertRule,
  type AlertCondition,
  type AlertMonitorOptions,
  type AlertQueryFilters,
  type AlertRulePayload,
  type AlertThresholds
} from '@/api/alert'

interface RuleForm {
  name: string
  description: string
  ruleType: string
  scopeType: string
  enabled: boolean
  query: string
  filters: AlertQueryFilters
  metric: string
  metricField: string
  operator: string
  threshold: number
  warningThreshold?: number | null
  recoveryThreshold?: number | null
  timeWindow: string
  evalEvery: string
  consecutiveHits: number
  groupBy: string
  notifyNoData: boolean
  noDataTimeframe: string
  requireFullWindow: boolean
  evaluationDelaySeconds: number
  newGroupDelaySeconds: number
  renotifyIntervalMinutes: number
  renotifyOccurrences: number
  includeTags: boolean
  priority: string
  team: string
  tags: string
  alertMode: string
  escalationMessage: string
  severity: string
  silenceMinutes: number
  channels: string[]
  messageTemplate: string
}

interface AlertScenario {
  key: string
  icon: string
  title: string
  description: string
  summary: string
  name: string
  query: string
  filters: AlertQueryFilters
  threshold: number
  warningThreshold?: number
  recoveryThreshold?: number
  severity: string
  priority: string
  team: string
  tags: string
  timeWindow: string
  evalEvery: string
  groupBy?: string
  descriptionText: string
}

const props = defineProps<{
  modelValue: boolean
  ruleId?: number | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const visible = ref(false)
const loading = ref(false)
const submitting = ref(false)
const isEditMode = ref(false)
const expandedPanels = ref<string[]>([])

const defaultMessageTemplate = '告警: {{rule_name}} 触发\n实际值: {{actual_value}}\n阈值: {{threshold}}\n时间: {{timestamp}}'

const scenarios: AlertScenario[] = [
  {
    key: 'error_spike',
    icon: 'ERR',
    title: '错误日志突增',
    description: '监控 ERROR / CRITICAL 日志突然变多',
    summary: '5 分钟 > 10 条',
    name: '错误日志突增',
    query: "severity IN ('ERROR', 'CRITICAL')",
    filters: {
      fieldFilters: [{ field: 'severity', type: 'include', values: ['ERROR', 'CRITICAL'] }]
    },
    threshold: 10,
    warningThreshold: 5,
    recoveryThreshold: 3,
    severity: 'critical',
    priority: 'P2',
    team: 'platform',
    tags: 'source:vector, scenario:error-spike',
    timeWindow: '5m',
    evalEvery: '1m',
    descriptionText: '监控 ERROR/CRITICAL 日志数量，适合作为通用兜底告警。'
  },
  {
    key: 'ssh_failed',
    icon: 'SSH',
    title: 'SSH 登录失败',
    description: '监控 sshd 登录失败、暴力破解迹象',
    summary: '10 分钟 > 20 条',
    name: 'SSH 登录失败增多',
    query: "appname = 'sshd' AND message LIKE '%failed%'",
    filters: {
      fieldFilters: [{ field: 'appname', type: 'include', values: ['sshd'] }],
      messageConditions: [{ operator: 'contains', value: 'failed' }]
    },
    threshold: 20,
    warningThreshold: 10,
    recoveryThreshold: 5,
    severity: 'critical',
    priority: 'P1',
    team: 'security',
    tags: 'source:vector, scenario:ssh-failed, security',
    timeWindow: '10m',
    evalEvery: '1m',
    groupBy: 'hostname',
    descriptionText: '按主机独立监控 SSH 登录失败，适合识别暴力破解和凭证攻击。'
  },
  {
    key: 'timeout',
    icon: 'SLOW',
    title: '超时异常',
    description: '监控 timeout / timed out 类业务异常',
    summary: '5 分钟 > 15 条',
    name: '超时异常增多',
    query: "message LIKE '%timeout%'",
    filters: {
      messageConditions: [{ operator: 'contains', value: 'timeout' }]
    },
    threshold: 15,
    warningThreshold: 8,
    recoveryThreshold: 4,
    severity: 'warning',
    priority: 'P3',
    team: 'sre',
    tags: 'source:vector, scenario:timeout',
    timeWindow: '5m',
    evalEvery: '1m',
    descriptionText: '监控日志中的超时异常，适合发现接口慢、下游不可用等问题。'
  },
  {
    key: 'vector_pipeline',
    icon: 'VEC',
    title: 'Vector 管道异常',
    description: '监控 Vector 组件错误或丢弃日志',
    summary: '5 分钟 > 5 条',
    name: 'Vector 管道异常',
    query: "source_type = 'vector' AND message LIKE '%error%'",
    filters: {
      fieldFilters: [{ field: 'source_type', type: 'include', values: ['vector'] }],
      messageConditions: [{ operator: 'contains', value: 'error' }]
    },
    threshold: 5,
    warningThreshold: 2,
    recoveryThreshold: 1,
    severity: 'warning',
    priority: 'P2',
    team: 'platform',
    tags: 'source:vector, scenario:pipeline',
    timeWindow: '5m',
    evalEvery: '1m',
    descriptionText: '监控 Vector 自身错误，适合发现采集链路、下发配置或 sink 写入异常。'
  }
]

const defaultScenario: AlertScenario = scenarios[0]!
const activeScenarioKey = ref(defaultScenario.key)

const formData = reactive<RuleForm>({
  name: defaultScenario.name,
  description: defaultScenario.descriptionText,
  ruleType: 'aggregation',
  scopeType: 'all',
  enabled: true,
  query: defaultScenario.query,
  filters: cloneFilters(defaultScenario.filters),
  metric: 'count',
  metricField: '',
  operator: 'gt',
  threshold: defaultScenario.threshold,
  warningThreshold: defaultScenario.warningThreshold,
  recoveryThreshold: defaultScenario.recoveryThreshold,
  timeWindow: defaultScenario.timeWindow,
  evalEvery: defaultScenario.evalEvery,
  consecutiveHits: 1,
  groupBy: '',
  notifyNoData: false,
  noDataTimeframe: '5m',
  requireFullWindow: false,
  evaluationDelaySeconds: 0,
  newGroupDelaySeconds: 0,
  renotifyIntervalMinutes: 0,
  renotifyOccurrences: 0,
  includeTags: true,
  priority: defaultScenario.priority,
  team: defaultScenario.team,
  tags: defaultScenario.tags,
  alertMode: 'simple',
  escalationMessage: '',
  severity: defaultScenario.severity,
  silenceMinutes: 5,
  channels: ['page'],
  messageTemplate: defaultMessageTemplate
})

watch(() => props.modelValue, async (value) => {
  visible.value = value
  if (!value) return

  isEditMode.value = !!props.ruleId
  if (isEditMode.value && props.ruleId) {
    await loadRuleData(props.ruleId)
  } else {
    resetForm()
    expandedPanels.value = []
  }
})

watch(visible, (value) => {
  emit('update:modelValue', value)
})

watch(() => formData.groupBy, (value) => {
  if (parseGroupBy(value).length) {
    formData.alertMode = 'multi'
  } else if (formData.alertMode === 'multi') {
    formData.alertMode = 'simple'
  }
})

const loadRuleData = async (ruleId: number) => {
  loading.value = true
  try {
    activeScenarioKey.value = ''
    const response = await getAlertRuleById(ruleId)
    const rule = response.data
    const condition = rule.condition || {}
    const thresholds = rule.thresholds || {}
    const aggregate = condition.aggregate || {}
    const criticalThreshold = thresholds.critical || {}
    const trigger = condition.trigger || {}
    const warningThreshold = thresholds.warning?.threshold ?? trigger.warningThreshold
    const recoveryThreshold = thresholds.recovery?.threshold ?? trigger.recoveryThreshold
    const options = rule.monitorOptions || condition.options || {}

    Object.assign(formData, {
      name: rule.name || '',
      description: rule.description || '',
      ruleType: rule.ruleType || rule.type || 'aggregation',
      scopeType: rule.scopeType || 'all',
      enabled: rule.enabled !== false,
      query: condition.query || '',
      filters: normalizeFilters(condition.filters),
      metric: aggregate.function || 'count',
      metricField: aggregate.field === '*' ? '' : aggregate.field || '',
      operator: criticalThreshold.operator || trigger.operator || 'gt',
      threshold: Number(criticalThreshold.threshold ?? trigger.threshold ?? 10),
      warningThreshold: normalizeOptionalNumber(warningThreshold),
      recoveryThreshold: normalizeOptionalNumber(recoveryThreshold),
      timeWindow: criticalThreshold.timeWindow || trigger.timeWindow || '5m',
      evalEvery: rule.evalEvery || '1m',
      consecutiveHits: rule.consecutiveHits || 1,
      groupBy: normalizeGroupBy(condition.groupBy),
      notifyNoData: options.notifyNoData ?? false,
      noDataTimeframe: options.noDataTimeframe || '5m',
      requireFullWindow: options.requireFullWindow ?? false,
      evaluationDelaySeconds: Number(options.evaluationDelaySeconds ?? 0),
      newGroupDelaySeconds: Number(options.newGroupDelaySeconds ?? 0),
      renotifyIntervalMinutes: Number(options.renotifyIntervalMinutes ?? 0),
      renotifyOccurrences: Number(options.renotifyOccurrences ?? 0),
      includeTags: options.includeTags ?? true,
      priority: options.priority || 'P3',
      team: options.team || '',
      tags: normalizeTags(options.tags || condition.tags),
      alertMode: options.alertMode || (normalizeGroupBy(condition.groupBy) ? 'multi' : 'simple'),
      escalationMessage: options.escalationMessage || '',
      severity: String(rule.severity || 'warning').toLowerCase(),
      silenceMinutes: Math.floor((rule.silencePeriod || 300) / 60),
      channels: rule.notificationChannels?.length ? rule.notificationChannels : ['page'],
      messageTemplate: rule.messageTemplate || defaultMessageTemplate
    })
    expandedPanels.value = ['advanced']
  } catch (error: any) {
    ElMessage.error('加载规则数据失败: ' + (error.message || '未知错误'))
    visible.value = false
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  activeScenarioKey.value = defaultScenario.key
  const scenario = defaultScenario
  Object.assign(formData, {
    name: scenario.name,
    description: scenario.descriptionText,
    ruleType: 'aggregation',
    scopeType: 'all',
    enabled: true,
    query: scenario.query,
    filters: cloneFilters(scenario.filters),
    metric: 'count',
    metricField: '',
    operator: 'gt',
    threshold: scenario.threshold,
    warningThreshold: scenario.warningThreshold,
    recoveryThreshold: scenario.recoveryThreshold,
    timeWindow: scenario.timeWindow,
    evalEvery: scenario.evalEvery,
    consecutiveHits: 1,
    groupBy: scenario.groupBy || '',
    notifyNoData: false,
    noDataTimeframe: '5m',
    requireFullWindow: false,
    evaluationDelaySeconds: 0,
    newGroupDelaySeconds: 0,
    renotifyIntervalMinutes: 0,
    renotifyOccurrences: 0,
    includeTags: true,
    priority: scenario.priority,
    team: scenario.team,
    tags: scenario.tags,
    alertMode: scenario.groupBy ? 'multi' : 'simple',
    escalationMessage: '',
    severity: scenario.severity,
    silenceMinutes: 5,
    channels: ['page'],
    messageTemplate: defaultMessageTemplate
  })
  expandedPanels.value = []
}

const applyScenario = (scenario: AlertScenario) => {
  activeScenarioKey.value = scenario.key
  Object.assign(formData, {
    name: scenario.name,
    description: scenario.descriptionText,
    query: scenario.query,
    filters: cloneFilters(scenario.filters),
    threshold: scenario.threshold,
    warningThreshold: scenario.warningThreshold,
    recoveryThreshold: scenario.recoveryThreshold,
    timeWindow: scenario.timeWindow,
    evalEvery: scenario.evalEvery,
    groupBy: scenario.groupBy || '',
    alertMode: scenario.groupBy ? 'multi' : 'simple',
    severity: scenario.severity,
    priority: scenario.priority,
    team: scenario.team,
    tags: scenario.tags
  })
}

const openAdvancedSettings = () => {
  expandedPanels.value = ['advanced']
}

const handleSubmit = async () => {
  if (!formData.name.trim()) {
    ElMessage.warning('请填写规则名称')
    return
  }

  if (!formData.query.trim() && !hasFilters(formData.filters)) {
    ElMessage.warning('请配置日志筛选条件')
    return
  }

  if (!formData.channels.length) {
    ElMessage.warning('请至少选择一个通知渠道')
    return
  }

  submitting.value = true
  try {
    const payload = buildPayload()
    if (isEditMode.value && props.ruleId) {
      await updateAlertRule(props.ruleId, payload)
      ElMessage.success('规则已更新')
    } else {
      await createAlertRule(payload)
      ElMessage.success('规则已创建')
    }

    emit('success')
    visible.value = false
  } catch (error: any) {
    ElMessage.error(`${isEditMode.value ? '更新' : '创建'}规则失败: ` + (error.message || '未知错误'))
  } finally {
    submitting.value = false
  }
}

const buildPayload = (): AlertRulePayload => {
  const groupBy = parseGroupBy(formData.groupBy)
  const aggregateField = formData.metric === 'count' ? '*' : formData.metricField.trim() || '*'
  const warningThreshold = normalizeOptionalNumber(formData.warningThreshold)
  const recoveryThreshold = normalizeOptionalNumber(formData.recoveryThreshold)
  const tags = parseTags(formData.tags)
  const alertMode = groupBy.length ? 'multi' : formData.alertMode
  const condition: AlertCondition = {
    query: hasFilters(formData.filters) ? '' : formData.query.trim(),
    filters: formData.filters,
    aggregate: {
      function: formData.metric,
      field: aggregateField
    }
  }
  const thresholds: AlertThresholds = {
    critical: {
      level: 'critical',
      operator: formData.operator,
      threshold: formData.threshold,
      timeWindow: formData.timeWindow
    }
  }
  if (warningThreshold !== undefined) {
    thresholds.warning = {
      level: 'warning',
      operator: formData.operator,
      threshold: warningThreshold,
      timeWindow: formData.timeWindow
    }
  }
  if (recoveryThreshold !== undefined) {
    thresholds.recovery = {
      level: 'recovery',
      operator: recoveryOperator(formData.operator),
      threshold: recoveryThreshold,
      timeWindow: formData.timeWindow
    }
  }
  const monitorOptions: AlertMonitorOptions = {
    notifyNoData: formData.notifyNoData,
    noDataTimeframe: formData.noDataTimeframe,
    requireFullWindow: formData.requireFullWindow,
    evaluationDelaySeconds: Math.max(0, formData.evaluationDelaySeconds || 0),
    newGroupDelaySeconds: Math.max(0, formData.newGroupDelaySeconds || 0),
    renotifyIntervalMinutes: Math.max(0, formData.renotifyIntervalMinutes || 0),
    renotifyOccurrences: Math.max(0, formData.renotifyOccurrences || 0),
    includeTags: formData.includeTags,
    priority: formData.priority,
    team: formData.team.trim(),
    tags,
    alertMode,
    escalationMessage: formData.escalationMessage.trim()
  }

  if (groupBy.length) {
    condition.groupBy = groupBy
  }

  return {
    name: formData.name.trim(),
    description: formData.description.trim(),
    ruleType: formData.ruleType,
    scopeType: formData.scopeType,
    condition,
    thresholds,
    monitorOptions,
    evalEvery: formData.evalEvery,
    consecutiveHits: formData.consecutiveHits,
    severity: formData.severity.toUpperCase(),
    notificationChannels: formData.channels,
    messageTemplate: formData.messageTemplate,
    silencePeriod: Math.max(0, formData.silenceMinutes) * 60,
    enabled: formData.enabled
  }
}

const recoveryOperator = (operator: string) => {
  const reverseMap: Record<string, string> = {
    gt: 'lte',
    gte: 'lt',
    lt: 'gte',
    lte: 'gt',
    eq: 'ne',
    ne: 'eq'
  }
  return reverseMap[operator] || 'lte'
}

const metricLabel = (metric: string) => {
  const map: Record<string, string> = {
    count: 'count 日志数量',
    unique: 'unique 唯一值数量',
    avg: 'avg 平均值',
    max: 'max 最大值',
    min: 'min 最小值',
    sum: 'sum 求和'
  }
  return map[metric] || metric
}

const operatorText = (operator: string) => {
  const map: Record<string, string> = {
    gt: '大于',
    gte: '大于等于',
    lt: '小于',
    lte: '小于等于',
    eq: '等于',
    ne: '不等于'
  }
  return map[operator] || operator
}

const timeWindowLabel = (timeWindow: string) => {
  const map: Record<string, string> = {
    '1m': '最近 1 分钟',
    '5m': '最近 5 分钟',
    '10m': '最近 10 分钟',
    '30m': '最近 30 分钟',
    '1h': '最近 1 小时'
  }
  return map[timeWindow] || timeWindow
}

const parseGroupBy = (value: string) => value
  .split(',')
  .map(item => item.trim())
  .filter(Boolean)

const parseTags = (value: string) => value
  .split(',')
  .map(item => item.trim())
  .filter(Boolean)

const normalizeGroupBy = (value: unknown) => {
  if (Array.isArray(value)) return value.join(', ')
  return typeof value === 'string' ? value : ''
}

const normalizeTags = (value: unknown) => {
  if (Array.isArray(value)) return value.filter(Boolean).join(', ')
  return typeof value === 'string' ? value : ''
}

function cloneFilters(filters: AlertQueryFilters): AlertQueryFilters {
  return JSON.parse(JSON.stringify(filters || {}))
}

const normalizeFilters = (value: unknown): AlertQueryFilters => {
  if (!value || typeof value !== 'object') return {}
  const filters = value as AlertQueryFilters
  return {
    fieldFilters: Array.isArray(filters.fieldFilters) ? filters.fieldFilters : undefined,
    messageConditions: Array.isArray(filters.messageConditions) ? filters.messageConditions : undefined,
    rawConditions: Array.isArray(filters.rawConditions) ? filters.rawConditions : undefined
  }
}

const hasFilters = (filters?: AlertQueryFilters) => Boolean(
  filters?.fieldFilters?.length
  || filters?.messageConditions?.length
  || filters?.rawConditions?.length
)

const normalizeOptionalNumber = (value: unknown) => {
  if (value === undefined || value === null || value === '') return undefined
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : undefined
}

const handleClose = () => {
  visible.value = false
}
</script>

<style scoped lang="scss">
.rule-drawer {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

.rule-form {
  flex: 1;
  padding: 0 4px 72px;
}

.quick-hero {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 20px;
  margin-bottom: 16px;
  overflow: hidden;
  border: 1px solid rgba(64, 158, 255, 0.18);
  border-radius: 14px;
  background:
    radial-gradient(circle at top right, rgba(64, 158, 255, 0.18), transparent 34%),
    linear-gradient(135deg, rgba(64, 158, 255, 0.1), rgba(103, 194, 58, 0.08));

  h3 {
    margin: 6px 0 8px;
    color: var(--macos-text-primary);
    font-size: 20px;
    font-weight: 750;
  }

  p {
    max-width: 460px;
    margin: 0;
    color: var(--macos-text-secondary);
    font-size: 13px;
    line-height: 1.7;
  }
}

.eyebrow {
  color: var(--macos-blue);
  font-size: 12px;
  font-weight: 750;
  letter-spacing: 0.08em;
}

.default-stack {
  min-width: 128px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-self: center;

  span {
    padding: 7px 10px;
    border: 1px solid rgba(64, 158, 255, 0.2);
    border-radius: 999px;
    background: rgba(255, 255, 255, 0.62);
    color: var(--macos-text-secondary);
    font-size: 12px;
    font-weight: 600;
    text-align: center;
    white-space: nowrap;
  }
}

.form-section {
  padding: 16px;
  margin-bottom: 14px;
  background: var(--macos-card-bg);
  border: 1px solid var(--macos-border);
  border-radius: 8px;
}

.primary-section {
  border-radius: 12px;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.05);
}

.subtle-section {
  background: var(--macos-fill-secondary);
}

.section-heading {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;

  h3 {
    margin: 0;
    color: var(--macos-text-primary);
    font-size: 16px;
    font-weight: 650;
  }

  p {
    margin: 4px 0 0;
    color: var(--macos-text-secondary);
    font-size: 12px;
  }
}

.section-heading.compact {
  margin-bottom: 14px;
}

.section-index {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--macos-blue-light);
  color: var(--macos-blue);
  font-size: 13px;
  font-weight: 700;
}

.section-index.muted {
  background: var(--macos-fill-secondary);
  color: var(--macos-text-secondary);
}

.scenario-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.scenario-card {
  position: relative;
  min-height: 138px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 7px;
  padding: 14px;
  overflow: hidden;
  border: 1px solid var(--macos-border);
  border-radius: 14px;
  background:
    radial-gradient(circle at top right, rgba(64, 158, 255, 0.12), transparent 42%),
    var(--macos-card-bg);
  color: var(--macos-text-primary);
  cursor: pointer;
  text-align: left;
  transition: all 0.18s ease;

  &::after {
    content: "";
    position: absolute;
    inset: auto 12px 10px auto;
    width: 28px;
    height: 28px;
    border-radius: 999px;
    background: rgba(64, 158, 255, 0.08);
  }

  &:hover,
  &.active {
    border-color: rgba(64, 158, 255, 0.52);
    box-shadow: 0 12px 28px rgba(15, 23, 42, 0.08);
    transform: translateY(-1px);
  }

  &.active {
    background:
      radial-gradient(circle at top right, rgba(64, 158, 255, 0.2), transparent 44%),
      linear-gradient(135deg, rgba(64, 158, 255, 0.08), rgba(103, 194, 58, 0.06));
  }

  strong {
    color: var(--macos-text-primary);
    font-size: 14px;
    font-weight: 750;
  }

  span:not(.scenario-icon),
  small {
    color: var(--macos-text-secondary);
    font-size: 12px;
    line-height: 1.45;
  }

  small {
    margin-top: auto;
    padding: 4px 8px;
    border-radius: 999px;
    background: var(--macos-fill-secondary);
    color: var(--macos-blue);
    font-weight: 700;
  }
}

.scenario-icon {
  height: 26px;
  display: inline-flex;
  align-items: center;
  padding: 0 8px;
  border-radius: 999px;
  background: var(--macos-blue-light);
  color: var(--macos-blue);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.06em;
}

.inline-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.simple-trigger-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 180px;
  gap: 18px;
  align-items: center;
  padding: 16px;
  border: 1px solid rgba(64, 158, 255, 0.18);
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(64, 158, 255, 0.08), rgba(255, 255, 255, 0.82));
}

.simple-trigger-copy {
  span,
  strong,
  p {
    display: block;
  }

  span {
    color: var(--macos-blue);
    font-size: 12px;
    font-weight: 700;
  }

  strong {
    margin-top: 5px;
    color: var(--macos-text-primary);
    font-size: 15px;
  }

  p {
    margin: 6px 0 0;
    color: var(--macos-text-tertiary);
    font-size: 12px;
    line-height: 1.5;
  }
}

.threshold-item {
  margin-bottom: 0;
}

.default-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0;

  span {
    padding: 5px 9px;
    border-radius: 999px;
    background: var(--macos-fill-secondary);
    color: var(--macos-text-secondary);
    font-size: 12px;
  }

  button {
    border: none;
    background: transparent;
    color: var(--macos-blue);
    cursor: pointer;
    font-size: 12px;
    font-weight: 650;
  }
}

.notify-simple-grid {
  grid-template-columns: minmax(260px, 0.8fr) minmax(300px, 1fr);
}

.two-columns {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.switch-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.switch-card {
  min-height: 82px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--macos-border);
  border-radius: 8px;
  background: var(--macos-fill-secondary);

  strong,
  span {
    display: block;
  }

  strong {
    color: var(--macos-text-primary);
    font-size: 13px;
  }

  span {
    margin-top: 4px;
    color: var(--macos-text-tertiary);
    font-size: 12px;
    line-height: 1.4;
  }
}

.query-input {
  :deep(textarea) {
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 13px;
  }
}

.example-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin: -6px 0 0;
}

.example-label {
  color: var(--macos-text-tertiary);
  font-size: 12px;
}

.example-chip {
  height: 28px;
  padding: 0 10px;
  border: 1px solid var(--macos-border);
  border-radius: 999px;
  background: var(--macos-fill-secondary);
  color: var(--macos-text-secondary);
  cursor: pointer;
  font-size: 12px;

  &:hover {
    color: var(--macos-blue);
    border-color: var(--macos-blue);
  }
}

.plain-alert {
  margin-top: 4px;
}

.advanced-collapse {
  margin-top: 18px;
  border: none;

  :deep(.el-collapse-item__wrap) {
    border-bottom: none;
    background: transparent;
  }

  :deep(.el-collapse-item__header) {
    height: auto;
    min-height: 56px;
    padding: 0 14px;
    border: 1px dashed var(--macos-border);
    border-radius: 12px;
    background: var(--macos-card-bg);
  }

  :deep(.el-collapse-item__content) {
    padding: 14px 0 0;
  }
}

.advanced-title {
  display: flex;
  flex-direction: column;
  gap: 3px;
  line-height: 1.4;

  strong {
    color: var(--macos-text-primary);
    font-size: 14px;
  }

  span {
    color: var(--macos-text-tertiary);
    font-size: 12px;
  }
}

.channel-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;

  :deep(.el-checkbox) {
    margin-right: 0;
  }

  .el-icon {
    margin-right: 4px;
  }
}

.switch-line {
  min-height: 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 2px;
  color: var(--macos-text-secondary);
  font-size: 13px;
}

.form-tip {
  margin-top: 6px;
  color: var(--macos-text-tertiary);
  font-size: 12px;
}

.drawer-footer {
  position: sticky;
  bottom: 0;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 14px 4px 2px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.74), var(--macos-card-bg) 32%);
  border-top: 1px solid var(--macos-border);
  backdrop-filter: blur(8px);
}

@media (max-width: 760px) {
  .quick-hero {
    flex-direction: column;
  }

  .default-stack {
    width: 100%;
    min-width: 0;
    flex-direction: row;
    flex-wrap: wrap;
    align-self: stretch;
  }

  .inline-grid,
  .simple-trigger-card,
  .notify-simple-grid,
  .scenario-grid,
  .switch-grid,
  .two-columns {
    grid-template-columns: 1fr;
  }
}
</style>
