<template>
  <el-dialog
    v-model="visible"
    width="980px"
    class="smart-wizard-dialog"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <template #header>
      <div class="dialog-header">
        <div>
          <div class="dialog-title">智能向导</div>
          <div class="dialog-subtitle">从一条日志样本，快速生成解析规则、建表 DDL 和可复用组件</div>
        </div>
        <el-tag effect="dark" type="success">自动识别优先</el-tag>
      </div>
    </template>

    <el-steps :active="currentStep" align-center class="wizard-steps">
      <el-step title="识别日志" description="粘贴样本，自动判断格式" />
      <el-step title="确认字段" description="复核字段和类型" />
      <el-step title="生成表结构" description="选择数据源并预览 DDL" />
      <el-step title="完成创建" description="创建表并生成组件" />
    </el-steps>

    <div class="wizard-content">
      <div v-if="currentStep === 0" class="step-content">
        <div class="hero-card">
          <div>
            <h3>输入一条代表性日志</h3>
            <p>默认使用自动识别，能处理 JSON、Syslog、Key-Value，也支持正则和自定义 VRL。</p>
          </div>
          <div class="preset-list">
            <span class="preset-label">快速填入示例</span>
            <el-button
              v-for="preset in samplePresets"
              :key="preset.label"
              size="small"
              plain
              @click="applyPreset(preset)"
            >
              {{ preset.label }}
            </el-button>
          </div>
        </div>

        <el-form :model="step1Form" label-width="110px" class="wizard-form">
          <el-form-item label="日志样本">
            <el-input
              v-model="step1Form.logSample"
              type="textarea"
              :rows="8"
              placeholder="粘贴一条完整日志。建议选择字段最全、最接近真实生产流量的样本。"
            />
          </el-form-item>

          <el-form-item label="解析方式">
            <el-select v-model="step1Form.parseMethod" placeholder="选择解析方式" class="full-width">
              <el-option label="自动识别（推荐）" value="auto" />
              <el-option label="JSON 格式" value="parse_json" />
              <el-option label="Syslog 格式" value="parse_syslog" />
              <el-option label="Key-Value 格式" value="parse_kv" />
              <el-option label="正则表达式" value="parse_regex" />
              <el-option label="自定义 VRL" value="custom" />
            </el-select>
          </el-form-item>

          <el-form-item v-if="step1Form.parseMethod === 'parse_regex'" label="正则表达式">
            <div class="full-width">
              <el-input
                v-model="step1Form.regexPattern"
                placeholder="例如: ^(?P<timestamp>\\S+) (?P<level>\\w+) (?P<message>.+)$"
              />
              <div class="form-tip">使用命名捕获组，例如 `(?P&lt;level&gt;...)`。</div>
            </div>
          </el-form-item>

          <el-form-item v-if="step1Form.parseMethod === 'custom'" label="VRL 脚本">
            <div class="full-width">
              <el-input
                v-model="step1Form.customVrl"
                type="textarea"
                :rows="6"
                placeholder="输入自定义 VRL 脚本"
              />
              <div class="form-tip">系统会保留原始日志到 `raw` 字段，便于后续排障和追溯。</div>
            </div>
          </el-form-item>

          <el-form-item>
            <div class="actions-row">
              <el-button type="primary" @click="handleParseLog" :loading="parsing">
                <el-icon><MagicStick /></el-icon>
                开始识别
              </el-button>
              <el-button v-if="step1Form.parseMethod === 'parse_regex'" @click="testRegex">
                <el-icon><View /></el-icon>
                正则提示
              </el-button>
            </div>
          </el-form-item>
        </el-form>

        <el-alert v-if="parseError" type="error" :closable="false" class="state-alert">
          <template #title>解析失败</template>
          {{ parseError }}
        </el-alert>

        <div v-if="parseSuccess" class="parse-summary">
          <div class="summary-main">
            <div class="summary-title">识别完成</div>
            <div class="summary-subtitle">已生成字段推断和可复用 VRL 脚本，可以继续确认字段并建表。</div>
          </div>
          <div class="summary-metrics">
            <div class="metric-card">
              <span class="metric-label">识别格式</span>
              <strong>{{ detectedFormat }}</strong>
            </div>
            <div class="metric-card">
              <span class="metric-label">字段数量</span>
              <strong>{{ parsedFields.length }}</strong>
            </div>
            <div class="metric-card">
              <span class="metric-label">VRL 脚本</span>
              <strong>{{ generatedVrlScript ? '已生成' : '无' }}</strong>
            </div>
          </div>
          <div class="preview-fields">
            <el-tag
              v-for="field in previewFields"
              :key="field.name"
              size="small"
              effect="plain"
            >
              {{ field.name }} : {{ field.type }}
            </el-tag>
          </div>
        </div>
      </div>

      <div v-else-if="currentStep === 1" class="step-content">
        <div class="section-header">
          <div>
            <h3>确认字段类型</h3>
            <p>系统已推断字段类型。这里主要做增删和少量修正，不建议大范围手改。</p>
          </div>
          <div class="section-tags">
            <el-tag>{{ detectedFormat }}</el-tag>
            <el-tag type="success">{{ parsedFields.length }} 个字段</el-tag>
          </div>
        </div>

        <el-alert type="info" :closable="false" class="state-alert">
          <template #title>建议</template>
          带有“建议”标记的字段说明检测到了更适合 ClickHouse 的类型，例如 IP 地址。
        </el-alert>

        <el-table :data="parsedFields" border class="fields-table">
          <el-table-column prop="name" label="字段名" min-width="180">
            <template #default="{ row }">
              <div class="field-name-cell">
                <el-input v-model="row.name" size="small" />
                <el-tag v-if="row.isSystemDefault" size="small" type="info" effect="plain">
                  系统默认
                </el-tag>
              </div>
            </template>
          </el-table-column>

          <el-table-column prop="type" label="字段类型" width="180">
            <template #default="{ row }">
              <el-select v-model="row.type" size="small">
                <el-option label="String" value="String" />
                <el-option label="Int32" value="Int32" />
                <el-option label="Int64" value="Int64" />
                <el-option label="Float32" value="Float32" />
                <el-option label="Float64" value="Float64" />
                <el-option label="DateTime" value="DateTime" />
                <el-option label="DateTime64" value="DateTime64" />
                <el-option label="Date" value="Date" />
                <el-option label="UInt8 (Boolean)" value="UInt8" />
                <el-option label="IPv4" value="IPv4" />
                <el-option label="IPv6" value="IPv6" />
              </el-select>
            </template>
          </el-table-column>

          <el-table-column prop="value" label="示例值" min-width="260">
            <template #default="{ row }">
              <div class="sample-cell">
                <span class="sample-value">{{ formatValue(row.value) }}</span>
                <el-tag v-if="row.suggestion" type="warning" size="small">
                  建议 {{ row.suggestion }}
                </el-tag>
              </div>
            </template>
          </el-table-column>

          <el-table-column prop="comment" label="备注" width="180">
            <template #default="{ row }">
              <el-input v-model="row.comment" size="small" placeholder="可选" />
            </template>
          </el-table-column>

          <el-table-column label="操作" width="88" fixed="right">
            <template #default="{ $index }">
              <el-button
                v-if="!parsedFields[$index]?.isSystemDefault"
                type="danger"
                size="small"
                link
                @click="removeField($index)"
              >
                删除
              </el-button>
              <span v-else class="system-field-text">保留</span>
            </template>
          </el-table-column>
        </el-table>

        <el-button type="primary" link class="add-field-btn" @click="addField">
          <el-icon><Plus /></el-icon>
          添加字段
        </el-button>
      </div>

      <div v-else-if="currentStep === 2" class="step-content">
        <div class="section-header">
          <div>
            <h3>生成表结构</h3>
            <p>选择 ClickHouse 数据源，确认表名后会自动生成 DDL。默认会保留 `raw` 原始日志字段。</p>
          </div>
          <div class="section-tags">
            <el-tag type="success">{{ datasources.length }} 个数据源</el-tag>
            <el-tag>{{ generatedVrlScript ? '将同时生成 Remap 组件' : '仅建表' }}</el-tag>
          </div>
        </div>

        <el-form :model="step3Form" label-width="110px" class="wizard-form">
          <el-form-item label="数据源">
            <el-select
              v-model="step3Form.datasourceId"
              class="full-width"
              placeholder="选择 ClickHouse 数据源"
              @change="handleDatasourceChange"
            >
              <el-option
                v-for="ds in datasources"
                :key="ds.id"
                :label="`${ds.name} (${ds.host}:${ds.port})`"
                :value="ds.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="表名">
            <div class="full-width">
              <el-input
                v-model="step3Form.tableName"
                placeholder="例如: nginx_logs"
                @input="tableNameTouched = true"
                @blur="handleGenerateDDL"
              />
              <div class="form-tip">建议使用小写字母和下划线。已根据识别结果预填一个可用表名。</div>
            </div>
          </el-form-item>

          <el-form-item label="DDL 预览">
            <el-input
              v-model="generatedDDL"
              type="textarea"
              :rows="13"
              readonly
              class="ddl-preview"
              placeholder="生成后会在这里显示 ClickHouse DDL"
            />
          </el-form-item>

          <el-form-item>
            <div class="actions-row">
              <el-button @click="handleGenerateDDL" :loading="generating">
                <el-icon><Refresh /></el-icon>
                重新生成 DDL
              </el-button>
            </div>
          </el-form-item>
        </el-form>

        <div v-if="generatedVrlScript" class="vrl-card">
          <div class="vrl-header">
            <span>将一并生成的 VRL 脚本</span>
            <el-tag size="small" type="info">可用于 Remap 组件</el-tag>
          </div>
          <pre>{{ vrlPreview }}</pre>
        </div>
      </div>

      <div v-else class="step-content">
        <el-result
          :icon="createSuccess ? 'success' : 'error'"
          :title="createSuccess ? '创建完成' : '创建失败'"
          :sub-title="createSuccess ? '表和相关组件已经准备就绪。' : createError"
        >
          <template #extra>
            <div v-if="createSuccess" class="result-meta">
              <el-tag v-if="createdRemapComponentId" type="success">Remap: {{ createdRemapComponentId }}</el-tag>
              <el-tag v-if="createdSinkComponentId" type="success">Sink: {{ createdSinkComponentId }}</el-tag>
              <el-tag>{{ step3Form.tableName }}</el-tag>
            </div>
            <div class="result-actions">
              <el-button v-if="createSuccess" @click="goToComponentLibrary">
                查看组件库
              </el-button>
              <el-button v-if="createSuccess" type="primary" @click="goToVisualConfig">
                前往可视化配置
              </el-button>
              <el-button @click="handleClose">关闭</el-button>
            </div>
          </template>
        </el-result>
      </div>
    </div>

    <template #footer>
      <div class="wizard-footer">
        <div class="footer-hint">
          <span v-if="currentStep < 3">当前建议: {{ footerHint }}</span>
        </div>
        <div class="footer-actions">
          <el-button @click="handlePrev" :disabled="currentStep === 0 || creating">
            上一步
          </el-button>
          <el-button
            type="primary"
            @click="handleNext"
            :disabled="!canNext"
            :loading="creating"
          >
            {{ currentStep === 2 ? '创建表和组件' : currentStep === 3 ? '完成' : '下一步' }}
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { MagicStick, View, Plus, Refresh } from '@element-plus/icons-vue'
import {
  createTable,
  generateDDL,
  parseLog,
  type GenerateDDLResponse,
  type ParseLogRequest,
  type ParseLogResponse,
  type ParsedField,
  type CreateTableResponse
} from '@/api/wizard'
import { listDatasourcesByType, type Datasource } from '@/api/datasource'

const router = useRouter()

interface Props {
  modelValue: boolean
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'created', payload: {
    tableName: string
    remapComponentId?: string
    sinkComponentId?: string
  }): void
}

interface ApiResult<T> {
  code?: number
  message?: string
  data?: T
}

interface SamplePreset {
  label: string
  parseMethod: ParseLogRequest['parseMethod']
  value: string
}

type WizardField = ParsedField & {
  isSystemDefault?: boolean
}

const samplePresets: SamplePreset[] = [
  {
    label: 'JSON',
    parseMethod: 'auto',
    value: '{"timestamp":"2024-01-07T10:30:45Z","level":"INFO","service":"api","duration_ms":123,"message":"Request processed"}'
  },
  {
    label: 'Syslog',
    parseMethod: 'auto',
    value: '<134>1 2024-01-07T10:30:45.123Z server01 nginx 1234 - - GET /api/users 200 0.123'
  },
  {
    label: 'Key-Value',
    parseMethod: 'auto',
    value: 'timestamp=2024-01-07T10:30:45Z level=ERROR service=payment host=node-01 latency_ms=231 message="payment failed"'
  }
]

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const currentStep = ref(0)
const parsing = ref(false)
const parseSuccess = ref(false)
const parseError = ref('')
const parsingFormat = ref('未识别')
const parsedFields = ref<WizardField[]>([])
const generatedVrlScript = ref('')

const datasources = ref<Datasource[]>([])
const generating = ref(false)
const generatedDDL = ref('')
const tableNameTouched = ref(false)

const creating = ref(false)
const createSuccess = ref(false)
const createError = ref('')
const createdRemapComponentId = ref('')
const createdSinkComponentId = ref('')

const step1Form = ref({
  logSample: '',
  parseMethod: 'auto' as ParseLogRequest['parseMethod'],
  regexPattern: '',
  customVrl: ''
})

const step3Form = ref({
  datasourceId: '',
  tableName: ''
})

const detectedFormat = computed(() => parsingFormat.value || '未识别')
const previewFields = computed(() => parsedFields.value.slice(0, 6))
const vrlPreview = computed(() => generatedVrlScript.value.split('\n').slice(0, 10).join('\n'))

const footerHint = computed(() => {
  if (currentStep.value === 0) return '先用一条代表性样本完成识别，后续字段和 DDL 会自动生成。'
  if (currentStep.value === 1) return '只修正真正有问题的字段，尽量保持自动推断结果。'
  return '确认数据源和表名后即可建表，并同步生成可复用组件。'
})

const canNext = computed(() => {
  if (currentStep.value === 0) {
    return parseSuccess.value && parsedFields.value.length > 0
  }
  if (currentStep.value === 1) {
    return parsedFields.value.length > 0
  }
  if (currentStep.value === 2) {
    return Boolean(step3Form.value.datasourceId && step3Form.value.tableName && generatedDDL.value)
  }
  return true
})

const unwrapResult = <T>(response: ApiResult<T> | T) => {
  if (response && typeof response === 'object' && 'data' in (response as ApiResult<T>)) {
    return (response as ApiResult<T>).data as T
  }
  return response as T
}

const sanitizeTableName = (value: string) => {
  const normalized = value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9_]+/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_+|_+$/g, '')

  if (!normalized) return 'log_events'
  if (/^\d/.test(normalized)) return `log_${normalized}`
  return normalized
}

const buildSuggestedTableName = () => {
  const formatPrefixMap: Record<string, string> = {
    'JSON': 'json_logs',
    'Syslog RFC 5424': 'syslog_logs',
    'Key-Value': 'kv_logs',
    '自定义正则': 'regex_logs',
    '自定义 VRL': 'custom_logs',
    '自动识别': 'app_logs'
  }

  return sanitizeTableName(formatPrefixMap[detectedFormat.value] || 'app_logs')
}

const ensureDefaultTimestampField = (fields: WizardField[]) => {
  const hasTimestamp = fields.some((field) => field.name.trim().toLowerCase() === 'timestamp')
  if (hasTimestamp) {
    return fields
  }

  return [
    {
      name: 'timestamp',
      value: '自动填充当前时间',
      type: 'DateTime',
      comment: '系统默认时间戳',
      isSystemDefault: true
    },
    ...fields
  ]
}

const markSystemFields = (fields: WizardField[]) => {
  return fields.map((field) => ({
    ...field,
    isSystemDefault: field.isSystemDefault || ['timestamp', 'raw'].includes(field.name.trim().toLowerCase())
  }))
}

const applyPreset = (preset: SamplePreset) => {
  step1Form.value.logSample = preset.value
  step1Form.value.parseMethod = preset.parseMethod
  step1Form.value.regexPattern = ''
  step1Form.value.customVrl = ''
}

const handleParseLog = async () => {
  if (!step1Form.value.logSample.trim()) {
    ElMessage.warning('请输入日志样本')
    return
  }

  parsing.value = true
  parseSuccess.value = false
  parseError.value = ''

  try {
    const response = unwrapResult<ParseLogResponse>(await parseLog({
      logSample: step1Form.value.logSample,
      parseMethod: step1Form.value.parseMethod,
      regexPattern: step1Form.value.regexPattern,
      customVrl: step1Form.value.customVrl
    }) as ApiResult<ParseLogResponse>)

    if (!response?.success || !response.fields?.length) {
      parseError.value = response?.error || '未解析出字段，请更换样本或调整解析方式'
      return
    }

    parsingFormat.value = response.format || '自动识别'
    generatedVrlScript.value = response.vrlScript || ''
    parsedFields.value = markSystemFields(ensureDefaultTimestampField(
      response.fields.map((field) => ({
        name: field.name,
        value: field.sampleValue,
        type: field.type,
        suggestion: field.suggestion?.type || undefined,
        comment: ''
      }))
    ))
    parseSuccess.value = true

    if (!tableNameTouched.value || !step3Form.value.tableName) {
      step3Form.value.tableName = buildSuggestedTableName()
    }

    ElMessage.success(`识别成功，已提取 ${parsedFields.value.length} 个字段`)
  } catch (error: any) {
    parseError.value = error?.message || '解析失败'
  } finally {
    parsing.value = false
  }
}

const testRegex = () => {
  ElMessage.info('建议先用“自动识别”试一次；如果不满足，再补充命名捕获组正则。')
}

const formatValue = (value: unknown) => {
  if (typeof value === 'object' && value !== null) {
    return JSON.stringify(value)
  }
  return String(value ?? '')
}

const addField = () => {
  parsedFields.value.push({
    name: `field_${parsedFields.value.length + 1}`,
    value: '',
    type: 'String',
    comment: ''
  })
}

const removeField = (index: number) => {
  parsedFields.value.splice(index, 1)
}

const loadDatasources = async () => {
  try {
    const result = unwrapResult<Datasource[]>(await listDatasourcesByType('clickhouse') as ApiResult<Datasource[]>)
    datasources.value = Array.isArray(result) ? result : []

    if (!datasources.value.length) {
      ElMessage.warning('未找到 ClickHouse 数据源，请先添加数据源')
      return
    }

    const [firstDatasource] = datasources.value
    if (!step3Form.value.datasourceId && firstDatasource) {
      step3Form.value.datasourceId = firstDatasource.id
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '加载数据源失败')
  }
}

const handleDatasourceChange = () => {
  if (step3Form.value.tableName) {
    void handleGenerateDDL()
  }
}

const handleGenerateDDL = async () => {
  if (!step3Form.value.datasourceId || !step3Form.value.tableName) {
    return
  }

  generating.value = true
  try {
    const response = unwrapResult<GenerateDDLResponse>(await generateDDL({
      datasourceId: step3Form.value.datasourceId,
      tableName: sanitizeTableName(step3Form.value.tableName),
      fields: parsedFields.value.map((field) => ({
        name: field.name,
        type: field.type,
        comment: field.comment
      }))
    }) as ApiResult<GenerateDDLResponse>)

    generatedDDL.value = response?.ddl || ''
    step3Form.value.tableName = sanitizeTableName(step3Form.value.tableName)

    if (!generatedDDL.value) {
      ElMessage.error('DDL 生成失败')
    }
  } catch (error: any) {
    ElMessage.error(error?.message || 'DDL 生成失败')
  } finally {
    generating.value = false
  }
}

const handlePrev = () => {
  if (currentStep.value > 0) {
    currentStep.value -= 1
  }
}

const handleNext = async () => {
  if (currentStep.value === 2) {
    await handleCreateTable()
    return
  }

  if (currentStep.value === 3) {
    handleClose()
    return
  }

  currentStep.value += 1

  if (currentStep.value === 2) {
    await loadDatasources()
    if (!step3Form.value.tableName) {
      step3Form.value.tableName = buildSuggestedTableName()
    }
    await handleGenerateDDL()
  }
}

const handleCreateTable = async () => {
  creating.value = true
  createError.value = ''

  try {
    const response = unwrapResult<CreateTableResponse>(await createTable({
      datasourceId: step3Form.value.datasourceId,
      ddl: generatedDDL.value,
      tableName: step3Form.value.tableName,
      vrlScript: generatedVrlScript.value,
      parseMethod: step1Form.value.parseMethod,
      autoCreateComponents: true
    }) as ApiResult<CreateTableResponse>)

    createSuccess.value = Boolean(response?.success)
    createdRemapComponentId.value = response?.remapComponentId || ''
    createdSinkComponentId.value = response?.sinkComponentId || ''
    createError.value = response?.error || ''
    currentStep.value = 3

    if (createSuccess.value) {
      emit('created', {
        tableName: response?.tableName || step3Form.value.tableName,
        remapComponentId: response?.remapComponentId || undefined,
        sinkComponentId: response?.sinkComponentId || undefined
      })
      ElMessage.success('表和组件创建成功')
    }
  } catch (error: any) {
    createSuccess.value = false
    createError.value = error?.message || '创建失败'
    currentStep.value = 3
  } finally {
    creating.value = false
  }
}

const goToVisualConfig = () => {
  router.push('/vector/visual-configs')
  handleClose()
}

const goToComponentLibrary = () => {
  if (router.currentRoute.value.path !== '/vector/components') {
    router.push('/vector/components')
  }
  handleClose()
}

const resetWizard = () => {
  currentStep.value = 0
  parsing.value = false
  parseSuccess.value = false
  parseError.value = ''
  parsingFormat.value = '未识别'
  parsedFields.value = []
  generatedVrlScript.value = ''
  datasources.value = []
  generating.value = false
  generatedDDL.value = ''
  tableNameTouched.value = false
  creating.value = false
  createSuccess.value = false
  createError.value = ''
  createdRemapComponentId.value = ''
  createdSinkComponentId.value = ''
  step1Form.value = {
    logSample: '',
    parseMethod: 'auto',
    regexPattern: '',
    customVrl: ''
  }
  step3Form.value = {
    datasourceId: '',
    tableName: ''
  }
}

const handleClose = () => {
  visible.value = false
  setTimeout(resetWizard, 200)
}
</script>

<style scoped lang="scss">
.wizard-steps {
  margin-bottom: 28px;
}

.wizard-content {
  min-height: 470px;
}

.step-content {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.dialog-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.dialog-title {
  font-size: 22px;
  font-weight: 700;
  color: #101828;
}

.dialog-subtitle {
  margin-top: 6px;
  color: #667085;
  font-size: 13px;
}

.hero-card,
.parse-summary,
.vrl-card {
  border: 1px solid #e4e7ec;
  border-radius: 18px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
  padding: 20px 22px;
}

.hero-card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;

  h3 {
    margin: 0 0 8px;
    font-size: 18px;
  }

  p {
    margin: 0;
    color: #667085;
    line-height: 1.6;
  }
}

.preset-list {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.preset-label {
  font-size: 12px;
  color: #667085;
}

.wizard-form {
  padding: 6px 4px 0;
}

.full-width {
  width: 100%;
}

.actions-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.form-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #667085;
  line-height: 1.5;
}

.state-alert {
  margin-top: 2px;
}

.summary-main {
  margin-bottom: 16px;
}

.summary-title {
  font-size: 16px;
  font-weight: 700;
  color: #101828;
}

.summary-subtitle {
  margin-top: 6px;
  font-size: 13px;
  color: #667085;
}

.summary-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.metric-card {
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.03);
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;

  strong {
    font-size: 18px;
    color: #111827;
  }
}

.metric-label {
  font-size: 12px;
  color: #667085;
}

.preview-fields {
  margin-top: 16px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;

  h3 {
    margin: 0 0 8px;
    font-size: 18px;
  }

  p {
    margin: 0;
    color: #667085;
    line-height: 1.6;
  }
}

.section-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.fields-table {
  border-radius: 14px;
  overflow: hidden;
}

.sample-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.field-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.system-field-text {
  color: #98a2b3;
  font-size: 12px;
}

.sample-value,
.ddl-preview :deep(textarea),
.vrl-card pre {
  font-family: 'SFMono-Regular', 'Monaco', 'Consolas', monospace;
}

.sample-value {
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}

.add-field-btn {
  align-self: flex-start;
}

.ddl-preview :deep(textarea) {
  font-size: 12px;
  line-height: 1.6;
}

.vrl-card {
  background: linear-gradient(180deg, #fbfdff 0%, #f4f8ff 100%);
}

.vrl-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  font-weight: 600;
  color: #344054;
}

.vrl-card pre {
  margin: 0;
  padding: 16px;
  border-radius: 12px;
  background: #101828;
  color: #e2e8f0;
  font-size: 12px;
  line-height: 1.6;
  overflow: auto;
}

.result-meta {
  display: flex;
  gap: 8px;
  justify-content: center;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.result-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
}

.wizard-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.footer-hint {
  color: #667085;
  font-size: 12px;
}

.footer-actions {
  display: flex;
  gap: 10px;
}

@media (max-width: 900px) {
  .hero-card,
  .section-header,
  .wizard-footer {
    flex-direction: column;
  }

  .summary-metrics {
    grid-template-columns: 1fr;
  }

  .footer-actions {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
