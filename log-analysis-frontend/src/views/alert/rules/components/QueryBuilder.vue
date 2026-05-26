<template>
  <div class="query-builder">
    <div class="mode-panel">
      <div>
        <span class="mode-kicker">条件设计器</span>
        <h4>不用写表达式，点选日志字段就能生成条件</h4>
        <p>默认使用可视化模式；熟悉 SQL 风格表达式时，也可以切到高级手写。</p>
      </div>
      <el-radio-group v-model="mode" size="small">
        <el-radio-button label="visual">可视化</el-radio-button>
        <el-radio-button label="manual">高级手写</el-radio-button>
      </el-radio-group>
    </div>

    <template v-if="mode === 'visual'">
      <div class="template-strip">
        <button
          v-for="template in templates"
          :key="template.name"
          type="button"
          class="template-card"
          @click="applyTemplate(template)"
        >
          <strong>{{ template.name }}</strong>
          <span>{{ template.description }}</span>
        </button>
      </div>

      <div class="visual-builder">
        <div class="builder-toolbar">
          <div>
            <strong>满足条件</strong>
            <span>{{ joiner === 'AND' ? '全部条件都满足时命中' : '任一条件满足就命中' }}</span>
          </div>
          <div class="toolbar-actions">
            <el-select v-model="joiner" size="small" style="width: 132px">
              <el-option label="全部满足" value="AND" />
              <el-option label="任一满足" value="OR" />
            </el-select>
            <el-button size="small" type="primary" plain @click="addCondition">
              <el-icon><Plus /></el-icon>
              添加条件
            </el-button>
          </div>
        </div>

        <div class="condition-list">
          <div
            v-for="(condition, index) in conditions"
            :key="condition.id"
            class="condition-row"
          >
            <span class="row-index">{{ index + 1 }}</span>
            <el-select
              v-model="condition.field"
              filterable
              placeholder="选择字段"
              class="field-select"
              @change="handleFieldChange(condition)"
            >
              <el-option
                v-for="field in availableFields"
                :key="field.value"
                :label="field.label"
                :value="field.value"
              >
                <div class="field-option">
                  <span>{{ field.label }}</span>
                  <small>{{ field.value }}</small>
                </div>
              </el-option>
            </el-select>

            <el-select v-model="condition.operator" class="operator-select">
              <el-option
                v-for="operator in operatorOptions(condition.field)"
                :key="operator.value"
                :label="operator.label"
                :value="operator.value"
              />
            </el-select>

            <el-select
              v-if="condition.field === 'severity'"
              v-model="condition.value"
              filterable
              allow-create
              default-first-option
              placeholder="选择级别"
              class="value-control"
            >
              <el-option label="ERROR" value="ERROR" />
              <el-option label="CRITICAL" value="CRITICAL" />
              <el-option label="WARN" value="WARN" />
              <el-option label="INFO" value="INFO" />
              <el-option label="DEBUG" value="DEBUG" />
            </el-select>
            <el-input
              v-else
              v-model="condition.value"
              :placeholder="valuePlaceholder(condition.field)"
              class="value-control"
            />

            <el-button
              text
              type="danger"
              :disabled="conditions.length === 1"
              @click="removeCondition(condition.id)"
            >
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
    </template>

    <template v-else>
      <div class="manual-panel">
        <el-input
          v-model="manualQuery"
          type="textarea"
          :rows="4"
          placeholder="例如: severity = 'ERROR' AND message LIKE '%timeout%'"
          class="manual-input"
        />
        <div class="manual-examples">
          <span>示例：</span>
          <button
            v-for="example in manualExamples"
            :key="example.label"
            type="button"
            @click="manualQuery = example.query"
          >
            {{ example.label }}
          </button>
        </div>
      </div>
    </template>

    <div class="query-preview">
      <div class="preview-heading">
        <div>
          <strong>实时预览</strong>
          <span>{{ previewHint }}</span>
        </div>
        <el-button size="small" text @click="copyQuery">
          <el-icon><CopyDocument /></el-icon>
          复制
        </el-button>
      </div>
      <code>{{ previewQuery || '全部日志' }}</code>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument, Delete, Plus } from '@element-plus/icons-vue'
import type { AlertFieldFilter, AlertQueryFilters, AlertTextCondition } from '@/api/alert'

type QueryMode = 'visual' | 'manual'
type Joiner = 'AND' | 'OR'
type ConditionOperator = 'equals' | 'notEquals' | 'contains' | 'notContains' | 'gt' | 'gte' | 'lt' | 'lte'

interface FieldOption {
  label: string
  value: string
  kind: 'field' | 'message' | 'raw'
  placeholder: string
}

interface VisualCondition {
  id: number
  field: string
  operator: ConditionOperator
  value: string
}

interface QueryTemplate {
  name: string
  description: string
  joiner: Joiner
  conditions: Array<Omit<VisualCondition, 'id'>>
}

const props = withDefaults(defineProps<{
  modelValue: string
  filters?: AlertQueryFilters
}>(), {
  filters: () => ({})
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'update:filters': [value: AlertQueryFilters]
}>()

const availableFields: FieldOption[] = [
  { label: '日志级别', value: 'severity', kind: 'field', placeholder: 'ERROR / CRITICAL / WARN' },
  { label: '消息内容', value: 'message', kind: 'message', placeholder: 'timeout / failed / exception' },
  { label: '主机名', value: 'hostname', kind: 'field', placeholder: 'web-01 / db-01' },
  { label: '应用名', value: 'appname', kind: 'field', placeholder: 'nginx / sshd / payment' },
  { label: '来源类型', value: 'source_type', kind: 'field', placeholder: 'syslog / file / vector' },
  { label: '源 IP', value: 'source_ip', kind: 'field', placeholder: '192.168.1.10' },
  { label: '设施类型', value: 'facility', kind: 'field', placeholder: 'auth / daemon / local0' },
  { label: '进程 ID', value: 'procid', kind: 'field', placeholder: '1234' },
  { label: '原始日志', value: 'raw', kind: 'raw', placeholder: '原始日志片段' }
]

const templates: QueryTemplate[] = [
  {
    name: '错误日志',
    description: 'severity 是 ERROR',
    joiner: 'AND',
    conditions: [{ field: 'severity', operator: 'equals', value: 'ERROR' }]
  },
  {
    name: '严重或错误',
    description: 'severity 是 ERROR / CRITICAL',
    joiner: 'AND',
    conditions: [{ field: 'severity', operator: 'equals', value: 'ERROR, CRITICAL' }]
  },
  {
    name: '超时异常',
    description: '消息包含 timeout',
    joiner: 'AND',
    conditions: [{ field: 'message', operator: 'contains', value: 'timeout' }]
  },
  {
    name: 'SSH 登录失败',
    description: 'sshd 且消息包含 failed',
    joiner: 'AND',
    conditions: [
      { field: 'appname', operator: 'equals', value: 'sshd' },
      { field: 'message', operator: 'contains', value: 'failed' }
    ]
  }
]

const manualExamples = [
  { label: '错误日志', query: "severity = 'ERROR'" },
  { label: '超时', query: "message LIKE '%timeout%'" },
  { label: '认证失败', query: "appname = 'sshd' AND message LIKE '%failed%'" }
]

let nextConditionId = 1
let isSyncingToParent = false
const mode = ref<QueryMode>(inferInitialMode())
const joiner = ref<Joiner>('AND')
const manualQuery = ref(props.modelValue || '')
const conditions = ref<VisualCondition[]>(buildInitialConditions())

const previewQuery = computed(() => mode.value === 'manual' ? manualQuery.value.trim() : generatedQuery.value)
const previewHint = computed(() => {
  if (mode.value === 'manual') {
    return '高级表达式会直接作为告警查询条件'
  }
  return joiner.value === 'AND' ? '这些条件会同时满足' : '任一条件满足即可命中'
})

const generatedQuery = computed(() => {
  const validConditions = conditions.value.filter(item => item.field && item.operator && item.value.trim())
  if (!validConditions.length) {
    return ''
  }
  return validConditions.map(conditionToQuery).join(` ${joiner.value} `)
})

const structuredFilters = computed<AlertQueryFilters>(() => {
  if (mode.value !== 'visual' || joiner.value !== 'AND') {
    return {}
  }

  const validConditions = conditions.value.filter(item => item.field && item.operator && item.value.trim())
  if (validConditions.some(condition => !canRepresentAsStructuredFilter(condition))) {
    return {}
  }

  const fieldFilterMap = new Map<string, AlertFieldFilter>()
  const messageConditions: AlertTextCondition[] = []
  const rawConditions: AlertTextCondition[] = []

  for (const condition of validConditions) {
    const value = condition.value.trim()
    if (!value) {
      continue
    }

    const field = fieldMeta(condition.field)
    if (field?.kind === 'message' || field?.kind === 'raw') {
      const textCondition = toTextCondition(condition.operator, value)
      if (field.kind === 'message') {
        messageConditions.push(textCondition)
      } else {
        rawConditions.push(textCondition)
      }
      continue
    }

    if (condition.operator === 'equals' || condition.operator === 'notEquals') {
      const type = condition.operator === 'equals' ? 'include' : 'exclude'
      const key = `${condition.field}:${type}`
      const existing = fieldFilterMap.get(key)
      const values = splitValues(value)
      if (existing) {
        existing.values = Array.from(new Set([...existing.values, ...values]))
      } else {
        fieldFilterMap.set(key, { field: condition.field, type, values })
      }
    }
  }

  const fieldFilters = Array.from(fieldFilterMap.values()).filter(item => item.values.length)

  return {
    ...(fieldFilters.length ? { fieldFilters } : {}),
    ...(messageConditions.length ? { messageConditions } : {}),
    ...(rawConditions.length ? { rawConditions } : {})
  }
})

watch(mode, (value) => {
  if (value === 'manual') {
    manualQuery.value = manualQuery.value || generatedQuery.value || props.modelValue
  }
  syncToParent()
})

watch(manualQuery, () => {
  if (mode.value === 'manual') {
    syncToParent()
  }
})

watch([conditions, joiner], () => {
  if (mode.value === 'visual') {
    syncToParent()
  }
}, { deep: true, immediate: true })

watch(() => props.modelValue, (value) => {
  if (isSyncingToParent) {
    return
  }
  if (!hasStructuredFilters(props.filters) && value && value !== generatedQuery.value) {
    mode.value = 'manual'
    manualQuery.value = value
    return
  }
  if (mode.value === 'manual' && value !== manualQuery.value) {
    manualQuery.value = value || ''
  }
})

watch(() => props.filters, (filters) => {
  if (isSyncingToParent) {
    return
  }
  if (hasStructuredFilters(filters)) {
    mode.value = 'visual'
    hydrateFromFilters(filters)
  }
}, { deep: true, immediate: true })

function inferInitialMode(): QueryMode {
  if (hasStructuredFilters(props.filters)) {
    return 'visual'
  }
  return props.modelValue ? 'manual' : 'visual'
}

function createCondition(input?: Partial<VisualCondition>): VisualCondition {
  return {
    id: nextConditionId++,
    field: input?.field || 'severity',
    operator: input?.operator || 'equals',
    value: input?.value || ''
  }
}

function buildInitialConditions() {
  if (hasStructuredFilters(props.filters)) {
    return conditionsFromFilters(props.filters)
  }
  return [createCondition({ field: 'severity', operator: 'equals', value: 'ERROR' })]
}

function addCondition() {
  conditions.value.push(createCondition({ field: 'message', operator: 'contains' }))
}

function removeCondition(id: number) {
  if (conditions.value.length === 1) {
    return
  }
  conditions.value = conditions.value.filter(item => item.id !== id)
}

function handleFieldChange(condition: VisualCondition) {
  const field = fieldMeta(condition.field)
  condition.operator = field?.kind === 'field' ? 'equals' : 'contains'
  condition.value = ''
}

function applyTemplate(template: QueryTemplate) {
  joiner.value = template.joiner
  conditions.value = template.conditions.map(item => createCondition(item))
  mode.value = 'visual'
}

function syncToParent() {
  isSyncingToParent = true
  if (mode.value === 'manual') {
    emit('update:modelValue', manualQuery.value.trim())
    emit('update:filters', {})
    queueMicrotask(() => {
      isSyncingToParent = false
    })
    return
  }

  emit('update:modelValue', generatedQuery.value)
  emit('update:filters', structuredFilters.value)
  queueMicrotask(() => {
    isSyncingToParent = false
  })
}

function operatorOptions(fieldName: string) {
  const field = fieldMeta(fieldName)
  const textOperators = [
    { label: '包含', value: 'contains' },
    { label: '不包含', value: 'notContains' },
    { label: '等于', value: 'equals' },
    { label: '不等于', value: 'notEquals' }
  ]
  if (field?.kind === 'message' || field?.kind === 'raw') {
    return textOperators
  }
  return [
    { label: '等于', value: 'equals' },
    { label: '不等于', value: 'notEquals' },
    { label: '包含', value: 'contains' },
    { label: '不包含', value: 'notContains' },
    { label: '大于', value: 'gt' },
    { label: '大于等于', value: 'gte' },
    { label: '小于', value: 'lt' },
    { label: '小于等于', value: 'lte' }
  ]
}

function valuePlaceholder(fieldName: string) {
  return fieldMeta(fieldName)?.placeholder || '输入匹配值'
}

function fieldMeta(fieldName: string) {
  return availableFields.find(item => item.value === fieldName)
}

function conditionToQuery(condition: VisualCondition) {
  const field = condition.field
  const values = splitValues(condition.value)
  const escapedValues = values.map(escapeQueryValue)
  const value = escapeQueryValue(condition.value.trim())
  const quotedList = escapedValues.map(item => `'${item}'`).join(', ')

  switch (condition.operator) {
    case 'notEquals':
      if (escapedValues.length > 1) {
        return `${field} NOT IN (${quotedList})`
      }
      return `${field} != '${value}'`
    case 'contains':
      if (escapedValues.length > 1) {
        return `(${escapedValues.map(item => `${field} LIKE '%${item}%'`).join(' OR ')})`
      }
      return `${field} LIKE '%${value}%'`
    case 'notContains':
      if (escapedValues.length > 1) {
        return `(${escapedValues.map(item => `${field} NOT LIKE '%${item}%'`).join(' AND ')})`
      }
      return `${field} NOT LIKE '%${value}%'`
    case 'gt':
      return `${field} > ${formatComparableValue(value)}`
    case 'gte':
      return `${field} >= ${formatComparableValue(value)}`
    case 'lt':
      return `${field} < ${formatComparableValue(value)}`
    case 'lte':
      return `${field} <= ${formatComparableValue(value)}`
    default:
      if (escapedValues.length > 1) {
        return `${field} IN (${quotedList})`
      }
      return `${field} = '${value}'`
  }
}

function canRepresentAsStructuredFilter(condition: VisualCondition) {
  const field = fieldMeta(condition.field)
  if (field?.kind === 'message' || field?.kind === 'raw') {
    return ['contains', 'notContains', 'equals', 'notEquals'].includes(condition.operator)
  }
  return condition.operator === 'equals' || condition.operator === 'notEquals'
}

function toTextCondition(operator: ConditionOperator, value: string): AlertTextCondition {
  const map: Record<string, AlertTextCondition['operator']> = {
    notContains: 'notContains',
    equals: 'equals',
    notEquals: 'notEquals'
  }
  return {
    operator: map[operator] || 'contains',
    value
  }
}

function splitValues(value: string) {
  return value
    .split(',')
    .map(item => item.trim())
    .filter(Boolean)
}

function formatComparableValue(value: string) {
  return Number.isFinite(Number(value)) ? value : `'${value}'`
}

function escapeQueryValue(value: string) {
  return value.replace(/'/g, "''")
}

function hasStructuredFilters(filters?: AlertQueryFilters) {
  return Boolean(
    filters?.fieldFilters?.length
    || filters?.messageConditions?.length
    || filters?.rawConditions?.length
  )
}

function hydrateFromFilters(filters?: AlertQueryFilters) {
  const nextConditions = conditionsFromFilters(filters)

  if (nextConditions.length) {
    conditions.value = nextConditions
    joiner.value = 'AND'
  }
}

function conditionsFromFilters(filters?: AlertQueryFilters) {
  const nextConditions: VisualCondition[] = []

  for (const filter of filters?.fieldFilters || []) {
    const values = filter.values?.filter(Boolean) || []
    if (!values.length) {
      continue
    }
    nextConditions.push(createCondition({
      field: filter.field,
      operator: filter.type === 'exclude' ? 'notEquals' : 'equals',
      value: values.join(', ')
    }))
  }

  for (const condition of filters?.messageConditions || []) {
    nextConditions.push(createCondition({
      field: 'message',
      operator: fromTextOperator(condition.operator),
      value: condition.value
    }))
  }

  for (const condition of filters?.rawConditions || []) {
    nextConditions.push(createCondition({
      field: 'raw',
      operator: fromTextOperator(condition.operator),
      value: condition.value
    }))
  }

  return nextConditions
}

function fromTextOperator(operator: AlertTextCondition['operator']): ConditionOperator {
  const map: Record<AlertTextCondition['operator'], ConditionOperator> = {
    contains: 'contains',
    notContains: 'notContains',
    equals: 'equals',
    notEquals: 'notEquals'
  }
  return map[operator]
}

function copyQuery() {
  if (!previewQuery.value) {
    ElMessage.warning('查询条件为空')
    return
  }
  navigator.clipboard.writeText(previewQuery.value)
    .then(() => ElMessage.success('已复制查询条件'))
    .catch(() => ElMessage.error('复制失败'))
}
</script>

<style scoped lang="scss">
.query-builder {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.mode-panel {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  border: 1px solid rgba(64, 158, 255, 0.16);
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(64, 158, 255, 0.08), rgba(255, 255, 255, 0.8));

  h4 {
    margin: 4px 0 6px;
    color: var(--macos-text-primary);
    font-size: 15px;
    font-weight: 750;
  }

  p {
    margin: 0;
    color: var(--macos-text-secondary);
    font-size: 12px;
    line-height: 1.6;
  }
}

.mode-kicker {
  color: var(--macos-blue);
  font-size: 12px;
  font-weight: 750;
}

.template-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.template-card {
  min-height: 72px;
  padding: 12px;
  border: 1px solid var(--macos-border);
  border-radius: 12px;
  background: var(--macos-card-bg);
  color: var(--macos-text-primary);
  cursor: pointer;
  text-align: left;
  transition: all 0.18s ease;

  strong,
  span {
    display: block;
  }

  strong {
    font-size: 13px;
    font-weight: 750;
  }

  span {
    margin-top: 6px;
    color: var(--macos-text-tertiary);
    font-size: 12px;
    line-height: 1.4;
  }

  &:hover {
    border-color: rgba(64, 158, 255, 0.45);
    box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
    transform: translateY(-1px);
  }
}

.visual-builder {
  padding: 14px;
  border: 1px solid var(--macos-border);
  border-radius: 14px;
  background: var(--macos-card-bg);
}

.builder-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 12px;

  strong,
  span {
    display: block;
  }

  strong {
    color: var(--macos-text-primary);
    font-size: 14px;
  }

  span {
    margin-top: 3px;
    color: var(--macos-text-tertiary);
    font-size: 12px;
  }
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.condition-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.condition-row {
  display: grid;
  grid-template-columns: 28px minmax(130px, 0.9fr) minmax(112px, 0.72fr) minmax(160px, 1.2fr) 36px;
  gap: 8px;
  align-items: center;
  padding: 10px;
  border-radius: 12px;
  background: var(--macos-fill-secondary);
}

.row-index {
  width: 26px;
  height: 26px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: var(--macos-card-bg);
  color: var(--macos-blue);
  font-size: 12px;
  font-weight: 750;
}

.field-select,
.operator-select,
.value-control {
  width: 100%;
}

.field-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;

  small {
    color: var(--macos-text-tertiary);
    font-size: 11px;
  }
}

.manual-panel {
  padding: 14px;
  border: 1px solid var(--macos-border);
  border-radius: 14px;
  background: var(--macos-card-bg);
}

.manual-input {
  :deep(textarea) {
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 13px;
  }
}

.manual-examples {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
  color: var(--macos-text-tertiary);
  font-size: 12px;

  button {
    height: 26px;
    padding: 0 9px;
    border: 1px solid var(--macos-border);
    border-radius: 999px;
    background: var(--macos-fill-secondary);
    color: var(--macos-text-secondary);
    cursor: pointer;
  }
}

.query-preview {
  padding: 12px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 12px;
  background: #101827;

  code {
    display: block;
    margin-top: 10px;
    padding: 10px 12px;
    border-radius: 8px;
    background: rgba(255, 255, 255, 0.08);
    color: #dbeafe;
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    font-size: 12px;
    line-height: 1.6;
    white-space: pre-wrap;
    word-break: break-word;
  }
}

.preview-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;

  strong,
  span {
    display: block;
  }

  strong {
    color: #ffffff;
    font-size: 13px;
  }

  span {
    margin-top: 3px;
    color: rgba(255, 255, 255, 0.62);
    font-size: 12px;
  }

  :deep(.el-button) {
    color: #bfdbfe;
  }
}

@media (max-width: 760px) {
  .mode-panel,
  .builder-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .template-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .condition-row {
    grid-template-columns: 28px 1fr 36px;

    .field-select,
    .operator-select,
    .value-control {
      grid-column: 2 / span 1;
    }
  }
}
</style>
