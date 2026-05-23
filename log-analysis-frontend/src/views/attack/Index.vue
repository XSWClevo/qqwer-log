<template>
  <AppLayout>
    <div class="attack-page">
      <div class="page-header">
        <div>
          <h1 class="page-title">攻击识别</h1>
          <p class="page-subtitle">基于动态日志数据集的攻击类型分类结果</p>
        </div>
        <div class="header-buttons">
          <el-button :icon="Setting" @click="openDatasetDrawer">
            数据集配置
          </el-button>
          <el-button type="primary" :icon="VideoPlay" :loading="running" @click="handleRun">
            执行分类
          </el-button>
        </div>
      </div>

      <div class="filter-bar">
        <el-select v-model="selectedDatasetId" placeholder="全部数据集" clearable class="dataset-select" @change="handleDatasetChange">
          <el-option
            v-for="dataset in enabledDatasets"
            :key="dataset.id"
            :label="dataset.name"
            :value="dataset.id"
          >
            <span>{{ dataset.name }}</span>
            <span class="dataset-target">{{ dataset.tableName || dataset.indexName }}</span>
          </el-option>
        </el-select>
        <el-select v-model="filters.attackType" placeholder="攻击类型" clearable class="type-select">
          <el-option v-for="item in attackTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="filters.severity" placeholder="风险等级" clearable class="severity-select">
          <el-option label="严重" value="critical" />
          <el-option label="高危" value="high" />
          <el-option label="中危" value="medium" />
          <el-option label="低危" value="low" />
        </el-select>
        <el-input
          v-model="filters.sourceIp"
          :disabled="sourceIpDisabled"
          placeholder="源 IP"
          clearable
          class="field-input"
        />
        <el-input
          v-model="filters.hostname"
          :disabled="hostnameDisabled"
          placeholder="主机名"
          clearable
          class="field-input"
        />
        <el-input
          v-model="filters.keyword"
          :prefix-icon="Search"
          placeholder="搜索日志、规则或原因"
          clearable
          class="keyword-input"
        />
        <el-button :icon="Refresh" @click="loadClassifications">刷新</el-button>
      </div>

      <div class="summary-row">
        <div class="summary-item">
          <span class="summary-label">结果总数</span>
          <span class="summary-value">{{ total }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">数据集</span>
          <span class="summary-value">{{ datasets.length }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">规则</span>
          <span class="summary-value">{{ rules.length }}</span>
        </div>
      </div>

      <div class="table-section">
        <el-table v-loading="loading" :data="records" height="calc(100vh - 326px)" stripe>
          <el-table-column label="日志时间" width="180">
            <template #default="{ row }">
              <span class="mono">{{ formatDateTime(row.logTimestamp) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="风险" width="92" align="center">
            <template #default="{ row }">
              <el-tag :type="severityTag(row.severity)" size="small">{{ severityLabel(row.severity) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="攻击类型" width="170">
            <template #default="{ row }">
              <div class="attack-type">{{ attackTypeLabel(row.attackType) }}</div>
              <div class="sub-type">{{ row.attackSubType || '-' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="来源" width="190">
            <template #default="{ row }">
              <div class="mono">{{ row.sourceIp || '-' }}</div>
              <div class="sub-type">{{ row.hostname || '-' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="数据集" width="170">
            <template #default="{ row }">
              <div>{{ row.tableName || row.indexName || '-' }}</div>
              <div class="sub-type">{{ row.datasourceType }}</div>
            </template>
          </el-table-column>
          <el-table-column label="命中规则" width="200">
            <template #default="{ row }">
              <div>{{ row.ruleName }}</div>
              <div class="sub-type mono">{{ row.ruleId }}</div>
            </template>
          </el-table-column>
          <el-table-column label="日志内容" min-width="360">
            <template #default="{ row }">
              <div class="message-cell">{{ row.message || row.raw || '-' }}</div>
              <div class="reason">{{ row.reason }}</div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="96" fixed="right" align="center">
            <template #default="{ row }">
              <el-button type="primary" text @click="openDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination">
          <el-pagination
            v-model:current-page="pagination.pageNum"
            v-model:page-size="pagination.pageSize"
            :total="total"
            :page-sizes="[20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @change="loadClassifications"
          />
        </div>
      </div>

      <el-drawer v-model="detailVisible" title="攻击识别详情" size="520px">
        <template v-if="selectedRecord">
          <dl class="detail-list">
            <dt>攻击类型</dt>
            <dd>{{ attackTypeLabel(selectedRecord.attackType) }} / {{ selectedRecord.attackSubType || '-' }}</dd>
            <dt>风险等级</dt>
            <dd>{{ severityLabel(selectedRecord.severity) }}，置信度 {{ selectedRecord.confidence }}</dd>
            <dt>来源</dt>
            <dd>{{ selectedRecord.sourceIp || '-' }} / {{ selectedRecord.hostname || '-' }}</dd>
            <dt>数据集</dt>
            <dd>{{ selectedRecord.datasourceType }} / {{ selectedRecord.databaseName || '-' }} / {{ selectedRecord.tableName || selectedRecord.indexName || '-' }}</dd>
            <dt>规则</dt>
            <dd>{{ selectedRecord.ruleName }}（{{ selectedRecord.ruleId }}）</dd>
            <dt>MITRE</dt>
            <dd>{{ selectedRecord.mitreTactic || '-' }} / {{ selectedRecord.mitreTechnique || '-' }}</dd>
            <dt>判断原因</dt>
            <dd>{{ selectedRecord.reason || '-' }}</dd>
            <dt>原始日志</dt>
            <dd class="raw-log">{{ selectedRecord.raw || selectedRecord.message || '-' }}</dd>
          </dl>
        </template>
      </el-drawer>

      <el-drawer v-model="datasetDrawerVisible" title="攻击分类数据集" size="860px">
        <div class="dataset-toolbar">
          <el-button type="primary" :icon="Plus" @click="openCreateDataset">新增数据集</el-button>
        </div>

        <el-table :data="datasets" height="calc(100vh - 188px)" stripe>
          <el-table-column label="名称" min-width="160">
            <template #default="{ row }">
              <div class="dataset-name">{{ row.name }}</div>
              <div class="sub-type">{{ datasourceTypeLabel(row.datasourceType) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="目标" min-width="220">
            <template #default="{ row }">
              <div class="mono">{{ row.tableName || row.indexName || '-' }}</div>
              <div class="sub-type">{{ row.databaseName || '-' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="字段能力" width="190">
            <template #default="{ row }">
              <el-tag v-if="row.capabilities?.hasSourceIp" size="small">source_ip</el-tag>
              <el-tag v-if="row.capabilities?.hasHostname" size="small" class="tag-gap">hostname</el-tag>
              <span v-if="!row.capabilities?.hasSourceIp && !row.capabilities?.hasHostname" class="sub-type">无 IP/主机字段</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="86" align="center">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
                {{ row.enabled ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="批量" prop="batchSize" width="86" align="right" />
          <el-table-column label="操作" width="150" fixed="right" align="center">
            <template #default="{ row }">
              <el-button type="primary" text :icon="Edit" @click="openEditDataset(row)">编辑</el-button>
              <el-button type="danger" text :icon="Delete" @click="handleDeleteDataset(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-drawer>

      <el-dialog v-model="datasetDialogVisible" :title="datasetDialogTitle" width="680px" @close="resetDatasetForm">
        <el-form ref="datasetFormRef" :model="datasetForm" :rules="datasetRules" label-width="120px">
          <el-form-item label="数据集名称" prop="name">
            <el-input v-model="datasetForm.name" placeholder="例如：syslog 登录日志" />
          </el-form-item>
          <el-form-item label="数据源类型" prop="datasourceType">
            <el-select v-model="datasetForm.datasourceType" style="width: 100%" @change="handleDatasetTypeChange">
              <el-option label="ClickHouse" value="clickhouse" />
              <el-option label="Elasticsearch" value="elasticsearch" />
            </el-select>
          </el-form-item>
          <el-form-item label="数据源">
            <el-select v-model="datasetForm.datasourceId" clearable filterable style="width: 100%" placeholder="默认应用 ClickHouse">
              <el-option
                v-for="item in selectableDatasources"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              >
                <span>{{ item.label }}</span>
                <span class="dataset-target">{{ item.source }}</span>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="数据库" v-if="datasetForm.datasourceType === 'clickhouse'">
            <el-input v-model="datasetForm.databaseName" placeholder="默认 default" />
          </el-form-item>
          <el-form-item label="表名" prop="tableName" v-if="datasetForm.datasourceType === 'clickhouse'">
            <el-input v-model="datasetForm.tableName" placeholder="ClickHouse 表名" />
          </el-form-item>
          <el-form-item label="索引名" prop="indexName" v-else>
            <el-input v-model="datasetForm.indexName" placeholder="Elasticsearch index 或 index pattern" />
          </el-form-item>

          <div class="mapping-grid">
            <el-form-item label="时间字段" prop="fieldMapping.timestamp">
              <el-input v-model="datasetForm.fieldMapping.timestamp" placeholder="timestamp / @timestamp" />
            </el-form-item>
            <el-form-item label="消息字段" prop="fieldMapping.message">
              <el-input v-model="datasetForm.fieldMapping.message" placeholder="message" />
            </el-form-item>
            <el-form-item label="原文字段">
              <el-input v-model="datasetForm.fieldMapping.raw" placeholder="raw，可选" />
            </el-form-item>
            <el-form-item label="等级字段">
              <el-input v-model="datasetForm.fieldMapping.severity" placeholder="severity / level，可选" />
            </el-form-item>
            <el-form-item label="源 IP 字段">
              <el-input v-model="datasetForm.fieldMapping.sourceIp" placeholder="source_ip，仅 syslog/socket 类需要" />
            </el-form-item>
            <el-form-item label="主机字段">
              <el-input v-model="datasetForm.fieldMapping.hostname" placeholder="hostname，可选" />
            </el-form-item>
          </div>

          <el-form-item label="批量大小">
            <el-input-number v-model="datasetForm.batchSize" :min="1" :max="5000" style="width: 180px" />
          </el-form-item>
          <el-form-item label="启用">
            <el-switch v-model="datasetForm.enabled" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="datasetDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="savingDataset" @click="saveDataset">保存</el-button>
        </template>
      </el-dialog>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus, Refresh, Search, Setting, VideoPlay } from '@element-plus/icons-vue'
import AppLayout from '@/components/layout/AppLayout.vue'
import {
  createAttackDataset,
  deleteAttackDataset,
  queryAttackClassifications,
  queryAttackDatasets,
  queryAttackRules,
  runAttackClassification,
  updateAttackDataset,
  type AttackDatasetPayload,
  type AttackClassificationRecord,
  type AttackDetectionRule,
  type AttackLogDataset
} from '@/api/attack'
import { configComponentApi, type ConfigComponent } from '@/api/vector'
import { listActiveDatasources, type Datasource } from '@/api/datasource'

const datasets = ref<AttackLogDataset[]>([])
const rules = ref<AttackDetectionRule[]>([])
const records = ref<AttackClassificationRecord[]>([])
const total = ref(0)
const loading = ref(false)
const running = ref(false)
const selectedDatasetId = ref<number | undefined>()
const detailVisible = ref(false)
const selectedRecord = ref<AttackClassificationRecord>()
const datasetDrawerVisible = ref(false)
const datasetDialogVisible = ref(false)
const savingDataset = ref(false)
const editingDatasetId = ref<number | undefined>()
const datasetFormRef = ref<FormInstance>()
const datasourceOptions = ref<Array<{ value: string; label: string; type: string; source: string }>>([])

const filters = reactive({
  attackType: '',
  severity: '',
  sourceIp: '',
  hostname: '',
  keyword: ''
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 20
})

const datasetForm = reactive<AttackDatasetPayload>({
  name: '',
  datasourceType: 'clickhouse',
  datasourceId: '',
  databaseName: 'default',
  tableName: '',
  indexName: '',
  fieldMapping: {
    timestamp: 'timestamp',
    message: 'message',
    raw: '',
    severity: '',
    sourceIp: '',
    hostname: ''
  },
  enabled: true,
  batchSize: 500
})

const datasetRules: FormRules = {
  name: [{ required: true, message: '请输入数据集名称', trigger: 'blur' }],
  datasourceType: [{ required: true, message: '请选择数据源类型', trigger: 'change' }],
  tableName: [{ required: true, message: '请输入 ClickHouse 表名', trigger: 'blur' }],
  indexName: [{ required: true, message: '请输入 Elasticsearch 索引名', trigger: 'blur' }],
  'fieldMapping.timestamp': [{ required: true, message: '请输入时间字段', trigger: 'blur' }],
  'fieldMapping.message': [{ required: true, message: '请输入消息字段', trigger: 'blur' }]
}

const selectedDataset = computed(() => datasets.value.find(item => item.id === selectedDatasetId.value))
const enabledDatasets = computed(() => datasets.value.filter(item => item.enabled))
const sourceIpDisabled = computed(() => Boolean(selectedDataset.value && !selectedDataset.value.capabilities?.hasSourceIp))
const hostnameDisabled = computed(() => Boolean(selectedDataset.value && !selectedDataset.value.capabilities?.hasHostname))
const datasetDialogTitle = computed(() => editingDatasetId.value ? '编辑攻击分类数据集' : '新增攻击分类数据集')
const selectableDatasources = computed(() => {
  const options = datasourceOptions.value.filter(item => item.type === datasetForm.datasourceType)
  if (datasetForm.datasourceType === 'clickhouse') {
    return [{ value: '', label: '默认应用 ClickHouse', type: 'clickhouse', source: 'default' }, ...options]
  }
  return options
})

const attackTypeOptions = computed(() => {
  const values = new Map<string, string>()
  rules.value.forEach(rule => values.set(rule.attackType, attackTypeLabel(rule.attackType)))
  return Array.from(values.entries()).map(([value, label]) => ({ value, label }))
})

const loadDatasets = async () => {
  const response: any = await queryAttackDatasets({ pageSize: 200 })
  datasets.value = response.data?.records || []
}

const loadDatasourceOptions = async () => {
  const [componentsResult, datasourcesResult] = await Promise.allSettled([
    configComponentApi.getQueryableDataSources(),
    listActiveDatasources()
  ])

  const options: Array<{ value: string; label: string; type: string; source: string }> = []
  if (componentsResult.status === 'fulfilled') {
    const components = normalizeResponseList<ConfigComponent>(componentsResult.value)
    components.forEach(component => {
      if (component.vectorType) {
        options.push({
          value: component.id,
          label: component.displayName || component.name,
          type: component.vectorType,
          source: 'Vector Sink'
        })
      }
    })
  }

  if (datasourcesResult.status === 'fulfilled') {
    const activeDatasources = normalizeResponseList<Datasource>(datasourcesResult.value)
    activeDatasources.forEach(datasource => {
      if (datasource.type) {
        options.push({
          value: datasource.id,
          label: datasource.name,
          type: datasource.type,
          source: '数据源管理'
        })
      }
    })
  }

  datasourceOptions.value = options
}

const loadRules = async () => {
  const response: any = await queryAttackRules({ enabled: true, pageSize: 200 })
  rules.value = response.data?.records || []
}

const normalizeResponseList = <T,>(response: any): T[] => {
  const data = response?.data || response
  return Array.isArray(data) ? data : []
}

const loadClassifications = async () => {
  loading.value = true
  try {
    const dataset = selectedDataset.value
    const response: any = await queryAttackClassifications({
      datasourceType: dataset?.datasourceType,
      datasourceId: dataset?.datasourceId,
      databaseName: dataset?.databaseName,
      tableName: dataset?.tableName,
      indexName: dataset?.indexName,
      attackType: filters.attackType || undefined,
      severity: filters.severity || undefined,
      sourceIp: filters.sourceIp || undefined,
      hostname: filters.hostname || undefined,
      keyword: filters.keyword || undefined,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    })
    records.value = response.data?.records || []
    total.value = response.data?.total || 0
  } finally {
    loading.value = false
  }
}

const handleRun = async () => {
  running.value = true
  try {
    const response: any = await runAttackClassification({
      datasetIds: selectedDatasetId.value ? [selectedDatasetId.value] : undefined,
      limit: 500
    })
    const data = response.data
    ElMessage.success(`分类完成：扫描 ${data.scannedCount} 条，写入 ${data.insertedCount} 条`)
    await loadClassifications()
  } finally {
    running.value = false
  }
}

const handleDatasetChange = () => {
  if (sourceIpDisabled.value) filters.sourceIp = ''
  if (hostnameDisabled.value) filters.hostname = ''
  pagination.pageNum = 1
  loadClassifications()
}

const openDatasetDrawer = async () => {
  datasetDrawerVisible.value = true
  await Promise.all([loadDatasets(), loadDatasourceOptions()])
}

const openCreateDataset = () => {
  resetDatasetForm()
  datasetDialogVisible.value = true
}

const openEditDataset = (dataset: AttackLogDataset) => {
  resetDatasetForm()
  editingDatasetId.value = dataset.id
  datasetForm.name = dataset.name
  datasetForm.datasourceType = dataset.datasourceType || 'clickhouse'
  datasetForm.datasourceId = dataset.datasourceId || ''
  datasetForm.databaseName = dataset.databaseName || 'default'
  datasetForm.tableName = dataset.tableName || ''
  datasetForm.indexName = dataset.indexName || ''
  datasetForm.fieldMapping = {
    timestamp: dataset.fieldMapping?.timestamp || 'timestamp',
    message: dataset.fieldMapping?.message || 'message',
    raw: dataset.fieldMapping?.raw || '',
    severity: dataset.fieldMapping?.severity || '',
    sourceIp: dataset.fieldMapping?.sourceIp || dataset.fieldMapping?.source_ip || '',
    hostname: dataset.fieldMapping?.hostname || ''
  }
  datasetForm.enabled = dataset.enabled
  datasetForm.batchSize = dataset.batchSize || 500
  datasetDialogVisible.value = true
}

const handleDatasetTypeChange = () => {
  datasetForm.datasourceId = ''
  datasetForm.tableName = ''
  datasetForm.indexName = ''
  datasetForm.databaseName = datasetForm.datasourceType === 'clickhouse' ? 'default' : ''
  datasetForm.fieldMapping.timestamp = datasetForm.datasourceType === 'elasticsearch' ? '@timestamp' : 'timestamp'
  datasetForm.fieldMapping.message = 'message'
}

const resetDatasetForm = () => {
  editingDatasetId.value = undefined
  datasetForm.name = ''
  datasetForm.datasourceType = 'clickhouse'
  datasetForm.datasourceId = ''
  datasetForm.databaseName = 'default'
  datasetForm.tableName = ''
  datasetForm.indexName = ''
  datasetForm.fieldMapping = {
    timestamp: 'timestamp',
    message: 'message',
    raw: '',
    severity: '',
    sourceIp: '',
    hostname: ''
  }
  datasetForm.enabled = true
  datasetForm.batchSize = 500
  datasetFormRef.value?.clearValidate()
}

const saveDataset = async () => {
  if (!datasetFormRef.value) return
  await datasetFormRef.value.validate()
  savingDataset.value = true
  try {
    const payload = buildDatasetPayload()
    if (editingDatasetId.value) {
      await updateAttackDataset(editingDatasetId.value, payload)
    } else {
      await createAttackDataset(payload)
    }
    ElMessage.success('数据集已保存')
    datasetDialogVisible.value = false
    await loadDatasets()
    await loadClassifications()
  } finally {
    savingDataset.value = false
  }
}

const buildDatasetPayload = (): AttackDatasetPayload => ({
  name: datasetForm.name,
  datasourceType: datasetForm.datasourceType,
  datasourceId: datasetForm.datasourceId || undefined,
  databaseName: datasetForm.datasourceType === 'clickhouse' ? datasetForm.databaseName : undefined,
  tableName: datasetForm.datasourceType === 'clickhouse' ? datasetForm.tableName : undefined,
  indexName: datasetForm.datasourceType === 'elasticsearch' ? datasetForm.indexName : undefined,
  fieldMapping: {
    timestamp: datasetForm.fieldMapping.timestamp || 'timestamp',
    message: datasetForm.fieldMapping.message || 'message',
    raw: datasetForm.fieldMapping.raw || '',
    severity: datasetForm.fieldMapping.severity || '',
    sourceIp: datasetForm.fieldMapping.sourceIp || '',
    hostname: datasetForm.fieldMapping.hostname || ''
  },
  enabled: datasetForm.enabled,
  batchSize: datasetForm.batchSize
})

const handleDeleteDataset = async (dataset: AttackLogDataset) => {
  try {
    await ElMessageBox.confirm(`确认删除数据集「${dataset.name}」？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  await deleteAttackDataset(dataset.id)
  ElMessage.success('数据集已删除')
  if (selectedDatasetId.value === dataset.id) {
    selectedDatasetId.value = undefined
  }
  await loadDatasets()
  await loadClassifications()
}

const openDetail = (record: AttackClassificationRecord) => {
  selectedRecord.value = record
  detailVisible.value = true
}

const formatDateTime = (value?: string) => value ? value.replace('T', ' ').slice(0, 23) : '-'

const severityTag = (severity: string) => {
  const map: Record<string, any> = {
    critical: 'danger',
    high: 'danger',
    medium: 'warning',
    low: 'info'
  }
  return map[severity] || 'info'
}

const severityLabel = (severity: string) => {
  const map: Record<string, string> = {
    critical: '严重',
    high: '高危',
    medium: '中危',
    low: '低危'
  }
  return map[severity] || severity || '-'
}

const attackTypeLabel = (type: string) => {
  const map: Record<string, string> = {
    authentication_attack: '认证攻击',
    web_attack: 'Web 攻击',
    command_execution: '命令执行',
    scan_probe: '扫描探测',
    privilege_abuse: '权限异常'
  }
  return map[type] || type || '-'
}

const datasourceTypeLabel = (type: string) => {
  const map: Record<string, string> = {
    clickhouse: 'ClickHouse',
    elasticsearch: 'Elasticsearch'
  }
  return map[type] || type || '-'
}

watch(
  () => [filters.attackType, filters.severity, filters.sourceIp, filters.hostname, filters.keyword],
  () => {
    pagination.pageNum = 1
    loadClassifications()
  }
)

onMounted(async () => {
  await Promise.all([loadDatasets(), loadRules()])
  await loadClassifications()
})
</script>

<style scoped lang="scss">
.attack-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.header-buttons {
  display: flex;
  align-items: center;
  gap: 10px;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 650;
  color: var(--macos-text-primary);
}

.page-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--macos-text-secondary);
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.dataset-select {
  width: 240px;
}

.type-select,
.severity-select,
.field-input {
  width: 150px;
}

.keyword-input {
  width: 260px;
}

.dataset-target {
  float: right;
  margin-left: 16px;
  color: var(--macos-text-tertiary);
  font-size: 12px;
}

.dataset-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.dataset-name {
  font-weight: 600;
  color: var(--macos-text-primary);
}

.tag-gap {
  margin-left: 6px;
}

.mapping-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: 12px;
}

.summary-row {
  display: flex;
  gap: 24px;
  padding: 10px 0;
  border-top: 1px solid var(--macos-border);
  border-bottom: 1px solid var(--macos-border);
}

.summary-item {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.summary-label {
  font-size: 12px;
  color: var(--macos-text-secondary);
}

.summary-value {
  font-size: 20px;
  font-weight: 650;
  color: var(--macos-text-primary);
}

.table-section {
  min-height: 0;
  flex: 1;
  border: 1px solid var(--macos-border);
  border-radius: 8px;
  overflow: hidden;
  background: var(--macos-bg-primary);
}

.pagination {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px;
  border-top: 1px solid var(--macos-border);
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

.attack-type {
  font-weight: 600;
  color: var(--macos-text-primary);
}

.sub-type,
.reason {
  margin-top: 4px;
  font-size: 12px;
  color: var(--macos-text-tertiary);
}

.message-cell {
  line-height: 1.45;
  color: var(--macos-text-primary);
  word-break: break-word;
}

.detail-list {
  display: grid;
  grid-template-columns: 96px 1fr;
  gap: 12px 16px;
  margin: 0;
}

.detail-list dt {
  color: var(--macos-text-secondary);
}

.detail-list dd {
  margin: 0;
  color: var(--macos-text-primary);
  word-break: break-word;
}

.raw-log {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  white-space: pre-wrap;
  line-height: 1.5;
}
</style>
