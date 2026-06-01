<template>
  <el-dialog 
    v-model="visible" 
    title="数据源管理" 
    width="700px" 
    destroy-on-close
    @close="handleClose"
  >
    <div class="datasource-manage">
      <!-- 组件信息 -->
      <el-descriptions :column="2" border size="small" class="info-section">
        <el-descriptions-item label="组件名称">{{ component?.name }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ component?.vectorType }}</el-descriptions-item>
        <el-descriptions-item label="表名/索引">{{ tableName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="数据库">{{ database || '-' }}</el-descriptions-item>
      </el-descriptions>

      <!-- 操作按钮 -->
      <div class="action-buttons">
        <el-button type="primary" @click="testConnection" :loading="testing">
          <el-icon><Connection /></el-icon>
          测试连接
        </el-button>
        <el-button type="success" @click="checkTable" :loading="checking">
          <el-icon><Document /></el-icon>
          检查表
        </el-button>
        <el-button @click="loadTables" :loading="loadingTables">
          <el-icon><List /></el-icon>
          查看所有表
        </el-button>
      </div>

      <!-- 连接测试结果 -->
      <el-alert 
        v-if="connectionResult" 
        :type="connectionResult.success ? 'success' : 'error'"
        :closable="false"
        show-icon
        class="result-alert"
      >
        <template #title>
          {{ connectionResult.success ? '连接成功' : '连接失败' }}
        </template>
        <div v-if="connectionResult.success">
          <p>版本: {{ connectionResult.version }}</p>
          <p>响应时间: {{ connectionResult.responseTimeMs }}ms</p>
        </div>
        <div v-else>{{ connectionResult.message }}</div>
      </el-alert>

      <!-- 表检查结果 -->
      <el-card v-if="tableResult" class="table-result-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>
              <el-icon :color="tableResult.exists ? '#67c23a' : '#f56c6c'">
                <SuccessFilled v-if="tableResult.exists" />
                <CircleCloseFilled v-else />
              </el-icon>
              表 {{ tableResult.tableName }} {{ tableResult.exists ? '已存在' : '不存在' }}
            </span>
            <el-button 
              v-if="!tableResult.exists" 
              type="primary" 
              size="small"
              @click="showCreateTableDialog"
            >
              <el-icon><Plus /></el-icon>
              创建表
            </el-button>
          </div>
        </template>
        
        <template v-if="tableResult.exists">
          <p class="row-count">数据行数: {{ tableResult.rowCount?.toLocaleString() || 0 }}</p>
          <el-table :data="tableResult.fields" border size="small" max-height="300">
            <el-table-column prop="name" label="字段名" width="200" />
            <el-table-column prop="type" label="类型" width="150" />
            <el-table-column prop="label" label="说明" />
          </el-table>
        </template>
      </el-card>

      <!-- 表列表 -->
      <el-card v-if="tables.length > 0" class="tables-card" shadow="never">
        <template #header>
          <span>数据库中的表 ({{ tables.length }})</span>
        </template>
        <el-tag 
          v-for="t in tables" 
          :key="t" 
          size="small" 
          class="table-tag"
          :type="t === tableName ? 'success' : 'info'"
        >
          {{ t }}
          <el-icon v-if="t === tableName" style="margin-left: 4px"><Check /></el-icon>
        </el-tag>
      </el-card>
    </div>

    <!-- 建表对话框 -->
    <el-dialog 
      v-model="createTableVisible" 
      title="创建表" 
      width="900px" 
      append-to-body
      destroy-on-close
    >
      <el-form label-width="100px" class="create-table-form">
        <el-form-item label="表名">
          <el-input v-model="tableSchema.tableName" placeholder="logs" />
        </el-form-item>
        
        <!-- ClickHouse 特有配置 -->
        <template v-if="component?.vectorType === 'clickhouse'">
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="引擎">
                <el-select v-model="tableSchema.engine" placeholder="选择引擎">
                  <el-option label="MergeTree()" value="MergeTree()" />
                  <el-option label="ReplacingMergeTree()" value="ReplacingMergeTree()" />
                  <el-option label="SummingMergeTree()" value="SummingMergeTree()" />
                  <el-option label="AggregatingMergeTree()" value="AggregatingMergeTree()" />
                  <el-option label="CollapsingMergeTree(sign)" value="CollapsingMergeTree(sign)" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="TTL (天)">
                <el-input-number 
                  v-model="ttlDays" 
                  :min="0" 
                  :max="3650" 
                  placeholder="90"
                  style="width: 100%"
                />
                <div class="field-hint">数据保留天数，0 表示永久保留</div>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="分区键">
                <el-select v-model="tableSchema.partitionBy" placeholder="选择分区方式" allow-create filterable>
                  <el-option label="按月分区 toYYYYMM(timestamp)" value="toYYYYMM(timestamp)" />
                  <el-option label="按天分区 toYYYYMMDD(timestamp)" value="toYYYYMMDD(timestamp)" />
                  <el-option label="按周分区 toMonday(timestamp)" value="toMonday(timestamp)" />
                  <el-option label="不分区" value="" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="排序键">
                <el-select v-model="tableSchema.orderBy" placeholder="选择排序键" allow-create filterable>
                  <el-option label="(timestamp, hostname)" value="(timestamp, hostname)" />
                  <el-option label="(timestamp)" value="(timestamp)" />
                  <el-option label="(timestamp, severity, hostname)" value="(timestamp, severity, hostname)" />
                  <el-option label="(hostname, timestamp)" value="(hostname, timestamp)" />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
        </template>

        <!-- PostgreSQL 特有配置 -->
        <template v-if="component?.vectorType === 'postgresql'">
          <el-form-item label="主键">
            <el-select v-model="tableSchema.primaryKey" placeholder="选择主键" allow-create filterable>
              <el-option label="id" value="id" />
              <el-option label="id, timestamp" value="id, timestamp" />
            </el-select>
          </el-form-item>
        </template>

        <!-- 字段定义方式 -->
        <el-divider content-position="left">字段定义</el-divider>
        
        <el-form-item label="定义方式">
          <el-radio-group v-model="fieldDefineMode" @change="onFieldDefineModeChange">
            <el-radio-button value="default">默认模板</el-radio-button>
            <el-radio-button value="parse">日志解析</el-radio-button>
            <el-radio-button value="custom">自定义</el-radio-button>
          </el-radio-group>
          <div class="field-hint" style="margin-top: 8px">
            <template v-if="fieldDefineMode === 'default'">使用推荐的日志表结构模板</template>
            <template v-else-if="fieldDefineMode === 'parse'">通过解析日志样本自动生成字段</template>
            <template v-else>完全手动定义所有字段</template>
          </div>
        </el-form-item>

        <!-- 日志解析模式 -->
        <template v-if="fieldDefineMode === 'parse'">
          <el-card shadow="never" class="parse-section">
            <el-form-item label="日志样本">
              <el-input 
                v-model="parseConfig.logSample" 
                type="textarea" 
                :rows="3" 
                placeholder="粘贴一条日志样本，用于解析字段"
              />
            </el-form-item>
            <el-form-item label="解析方式">
              <el-select v-model="parseConfig.parseMethod" placeholder="选择解析方式">
                <el-option label="JSON 解析" value="parse_json" />
                <el-option label="Syslog 解析" value="parse_syslog" />
                <el-option label="正则解析" value="parse_regex" />
                <el-option label="Key-Value 解析" value="parse_key_value" />
                <el-option label="Grok 解析" value="parse_grok" />
              </el-select>
            </el-form-item>
            <template v-if="parseConfig.parseMethod === 'parse_regex'">
              <el-form-item label="正则表达式">
                <el-input v-model="parseConfig.regexPattern" placeholder="^(?P<timestamp>\S+) (?P<level>\S+) (?P<message>.*)$" />
              </el-form-item>
            </template>
            <template v-if="parseConfig.parseMethod === 'parse_grok'">
              <el-form-item label="Grok 模式">
                <el-input v-model="parseConfig.grokPattern" placeholder="SYSLOGBASE2 %{GREEDYDATA:message}" />
              </el-form-item>
            </template>
            <el-form-item>
              <el-button type="primary" @click="parseLogSample" :loading="parsing">
                <el-icon><Search /></el-icon>
                解析日志
              </el-button>
            </el-form-item>
            <el-alert v-if="parseError" type="error" :closable="false" show-icon>
              {{ parseError }}
            </el-alert>
          </el-card>
        </template>

        <!-- 字段列表 -->
        <el-form-item label="字段列表">
          <el-table :data="tableSchema.columns" border size="small" class="columns-table">
            <el-table-column label="字段名" width="140">
              <template #default="{ row }">
                <el-input v-model="row.name" size="small" placeholder="字段名" />
              </template>
            </el-table-column>
            <el-table-column label="类型" width="160">
              <template #default="{ row }">
                <el-select v-model="row.type" size="small" filterable allow-create placeholder="类型">
                  <el-option-group label="常用类型">
                    <el-option label="String" value="String" />
                    <el-option label="DateTime" value="DateTime" />
                    <el-option label="Int32" value="Int32" />
                    <el-option label="Int64" value="Int64" />
                    <el-option label="Float64" value="Float64" />
                    <el-option label="UUID" value="UUID" />
                  </el-option-group>
                  <el-option-group label="低基数类型">
                    <el-option label="LowCardinality(String)" value="LowCardinality(String)" />
                  </el-option-group>
                  <el-option-group label="PostgreSQL 类型" v-if="component?.vectorType === 'postgresql'">
                    <el-option label="TEXT" value="TEXT" />
                    <el-option label="VARCHAR(255)" value="VARCHAR(255)" />
                    <el-option label="TIMESTAMP WITH TIME ZONE" value="TIMESTAMP WITH TIME ZONE" />
                    <el-option label="INTEGER" value="INTEGER" />
                    <el-option label="BIGINT" value="BIGINT" />
                    <el-option label="BOOLEAN" value="BOOLEAN" />
                  </el-option-group>
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="可空" width="60" align="center">
              <template #default="{ row }">
                <el-checkbox v-model="row.nullable" />
              </template>
            </el-table-column>
            <el-table-column label="默认值" width="140">
              <template #default="{ row }">
                <el-input v-model="row.defaultValue" size="small" placeholder="默认值" />
              </template>
            </el-table-column>
            <el-table-column label="注释">
              <template #default="{ row }">
                <el-input v-model="row.comment" size="small" placeholder="字段说明" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70" align="center">
              <template #default="{ $index }">
                <el-button type="danger" text size="small" @click="removeColumn($index)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button size="small" @click="addColumn" style="margin-top: 8px">
            <el-icon><Plus /></el-icon>
            添加字段
          </el-button>
        </el-form-item>

        <!-- SQL 预览 -->
        <el-divider content-position="left">SQL 预览</el-divider>
        <el-form-item>
          <el-button type="info" @click="previewSQL" :loading="previewingSQL">
            <el-icon><View /></el-icon>
            生成预览
          </el-button>
        </el-form-item>
        
        <el-input 
          v-if="previewSQLContent" 
          v-model="previewSQLContent" 
          type="textarea" 
          :rows="12" 
          readonly
          class="sql-preview"
        />
      </el-form>

      <template #footer>
        <el-button @click="createTableVisible = false">取消</el-button>
        <el-button type="primary" @click="executeCreateTable" :loading="creating">
          执行建表
        </el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Connection, Document, List, Plus, Delete, Check, SuccessFilled, CircleCloseFilled, Search, View } from '@element-plus/icons-vue'
import * as yaml from 'js-yaml'
import { configComponentApi, vrlApi, type ConfigComponent, type TableSchema } from '@/api/vector'

const props = defineProps<{
  modelValue: boolean
  component: ConfigComponent | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

// 状态
const testing = ref(false)
const checking = ref(false)
const loadingTables = ref(false)
const creating = ref(false)
const previewingSQL = ref(false)
const parsing = ref(false)
const parseError = ref('')

const connectionResult = ref<{
  success: boolean
  message: string
  version?: string
  responseTimeMs?: number
} | null>(null)

const tableResult = ref<{
  exists: boolean
  message: string
  tableName?: string
  fields?: Array<{ name: string; type: string; label: string }>
  rowCount?: number
} | null>(null)

const tables = ref<string[]>([])

// 建表相关
const createTableVisible = ref(false)
const previewSQLContent = ref('')
const fieldDefineMode = ref<'default' | 'parse' | 'custom'>('default')
const ttlDays = ref(90)

const tableSchema = ref<TableSchema>({
  tableName: '',
  columns: [],
  engine: 'MergeTree()',
  partitionBy: 'toYYYYMM(timestamp)',
  orderBy: '(timestamp, hostname)',
  ttl: ''
})

// 日志解析配置
const parseConfig = reactive({
  logSample: '',
  parseMethod: 'parse_json',
  regexPattern: '',
  grokPattern: ''
})

// 从 configYaml 解析信息
const tableName = computed(() => {
  if (!props.component?.configYaml) return ''
  try {
    const parsed = yaml.load(props.component.configYaml) as Record<string, any>
    return parsed?.table || parsed?.index || parsed?.topic || ''
  } catch {
    return ''
  }
})

const database = computed(() => {
  if (!props.component?.configYaml) return ''
  try {
    const parsed = yaml.load(props.component.configYaml) as Record<string, any>
    return parsed?.database || 'default'
  } catch {
    return 'default'
  }
})

// 计算 TTL 表达式
const computedTtl = computed(() => {
  if (ttlDays.value <= 0) return ''
  return `timestamp + INTERVAL ${ttlDays.value} DAY`
})

// 监听 TTL 天数变化
watch(ttlDays, (days) => {
  tableSchema.value.ttl = days > 0 ? `timestamp + INTERVAL ${days} DAY` : ''
})

// 重置状态
const resetState = () => {
  connectionResult.value = null
  tableResult.value = null
  tables.value = []
  previewSQLContent.value = ''
}

watch(() => props.component, () => {
  resetState()
})

const handleClose = () => {
  resetState()
}

// 测试连接
const testConnection = async () => {
  if (!props.component?.id) return
  testing.value = true
  connectionResult.value = null
  try {
    const res = await configComponentApi.testConnection(props.component.id) as any
    connectionResult.value = res.data || res
  } catch (error: any) {
    connectionResult.value = { success: false, message: error.message || '连接测试失败' }
  } finally {
    testing.value = false
  }
}

// 检查表
const checkTable = async () => {
  if (!props.component?.id) return
  checking.value = true
  tableResult.value = null
  try {
    const res = await configComponentApi.checkTable(props.component.id) as any
    tableResult.value = res.data || res
  } catch (error: any) {
    ElMessage.error(error.message || '检查表失败')
  } finally {
    checking.value = false
  }
}

// 加载表列表
const loadTables = async () => {
  if (!props.component?.id) return
  loadingTables.value = true
  tables.value = []
  try {
    const res = await configComponentApi.listTables(props.component.id) as any
    tables.value = res.data || res || []
  } catch (error: any) {
    ElMessage.error(error.message || '获取表列表失败')
  } finally {
    loadingTables.value = false
  }
}

// 显示建表对话框
const showCreateTableDialog = async () => {
  if (!props.component?.id) return
  try {
    const res = await configComponentApi.getRecommendedSchema(props.component.id) as any
    const schema = res.data || res
    tableSchema.value = {
      tableName: tableName.value || schema.tableName || 'logs',
      columns: schema.columns || [],
      engine: schema.engine || 'MergeTree()',
      partitionBy: schema.partitionBy || 'toYYYYMM(timestamp)',
      orderBy: schema.orderBy || '(timestamp, hostname)',
      primaryKey: schema.primaryKey || '',
      ttl: schema.ttl || ''
    }
    // 解析 TTL 天数
    const ttlMatch = schema.ttl?.match(/INTERVAL\s+(\d+)\s+DAY/i)
    ttlDays.value = ttlMatch ? parseInt(ttlMatch[1]) : 90
    
    fieldDefineMode.value = 'default'
    previewSQLContent.value = ''
    parseError.value = ''
    parseConfig.logSample = ''
    createTableVisible.value = true
  } catch (error: any) {
    ElMessage.error(error.message || '获取推荐表结构失败')
  }
}

// 字段定义方式切换
const onFieldDefineModeChange = async (mode: string) => {
  if (mode === 'default') {
    // 重新加载默认模板
    if (!props.component?.id) return
    try {
      const res = await configComponentApi.getRecommendedSchema(props.component.id) as any
      const schema = res.data || res
      tableSchema.value.columns = schema.columns || []
    } catch (error: any) {
      ElMessage.error('加载默认模板失败')
    }
  } else if (mode === 'custom') {
    // 清空字段，让用户手动添加
    tableSchema.value.columns = []
  }
  // parse 模式保持当前字段，等用户解析后更新
}

// 解析日志样本
const parseLogSample = async () => {
  if (!parseConfig.logSample.trim()) {
    ElMessage.warning('请输入日志样本')
    return
  }
  parsing.value = true
  parseError.value = ''
  try {
    const res = await vrlApi.execute({
      logSample: parseConfig.logSample,
      parseMethod: parseConfig.parseMethod,
      regexPattern: parseConfig.regexPattern,
      grokPattern: parseConfig.grokPattern
    })
    if (res.success && res.fields) {
      // 将解析结果转换为列定义
      tableSchema.value.columns = res.fields.map((f: any) => ({
        name: f.name,
        type: mapFieldTypeToDbType(f.type, props.component?.vectorType || 'clickhouse'),
        nullable: true,
        defaultValue: '',
        comment: ''
      }))
      // 添加常用的系统字段
      addSystemFields()
      ElMessage.success(`解析成功，共 ${res.fields.length} 个字段`)
    } else {
      parseError.value = res.error || '解析失败'
    }
  } catch (e: any) {
    parseError.value = e.message || '请求失败'
  } finally {
    parsing.value = false
  }
}

// 映射字段类型到数据库类型
const mapFieldTypeToDbType = (fieldType: string, dbType: string): string => {
  const isClickHouse = dbType === 'clickhouse'
  const typeMap: Record<string, { ch: string; pg: string }> = {
    'string': { ch: 'String', pg: 'TEXT' },
    'integer': { ch: 'Int64', pg: 'BIGINT' },
    'float': { ch: 'Float64', pg: 'DOUBLE PRECISION' },
    'boolean': { ch: 'UInt8', pg: 'BOOLEAN' },
    'timestamp': { ch: 'DateTime', pg: 'TIMESTAMP WITH TIME ZONE' },
    'object': { ch: 'String', pg: 'JSONB' },
    'array': { ch: 'Array(String)', pg: 'TEXT[]' }
  }
  const mapped = typeMap[fieldType.toLowerCase()]
  return mapped ? (isClickHouse ? mapped.ch : mapped.pg) : (isClickHouse ? 'String' : 'TEXT')
}

// 添加系统字段
const addSystemFields = () => {
  const isClickHouse = props.component?.vectorType === 'clickhouse'
  const existingNames = tableSchema.value.columns.map(c => c.name)
  
  // 检查并添加 id 字段
  if (!existingNames.includes('id')) {
    tableSchema.value.columns.unshift({
      name: 'id',
      type: isClickHouse ? 'UUID' : 'UUID',
      nullable: false,
      defaultValue: isClickHouse ? 'generateUUIDv4()' : 'gen_random_uuid()',
      comment: '唯一标识'
    })
  }
  
  // 检查并添加 timestamp 字段
  if (!existingNames.includes('timestamp')) {
    tableSchema.value.columns.splice(1, 0, {
      name: 'timestamp',
      type: isClickHouse ? 'DateTime' : 'TIMESTAMP WITH TIME ZONE',
      nullable: false,
      defaultValue: isClickHouse ? 'now()' : 'NOW()',
      comment: '日志时间'
    })
  }
}

// 添加字段
const addColumn = () => {
  const isClickHouse = props.component?.vectorType === 'clickhouse'
  tableSchema.value.columns.push({
    name: '',
    type: isClickHouse ? 'String' : 'TEXT',
    nullable: true,
    defaultValue: '',
    comment: ''
  })
}

// 删除字段
const removeColumn = (index: number) => {
  tableSchema.value.columns.splice(index, 1)
}

// 预览 SQL
const previewSQL = async () => {
  if (!props.component?.id) return
  // 更新 TTL
  tableSchema.value.ttl = computedTtl.value
  previewingSQL.value = true
  try {
    const res = await configComponentApi.previewCreateTable(props.component.id, tableSchema.value) as any
    previewSQLContent.value = res.data || res || ''
  } catch (error: any) {
    ElMessage.error(error.message || '生成预览失败')
  } finally {
    previewingSQL.value = false
  }
}

// 执行建表
const executeCreateTable = async () => {
  if (!props.component?.id) return
  // 更新 TTL
  tableSchema.value.ttl = computedTtl.value
  creating.value = true
  try {
    const res = await configComponentApi.createTable(props.component.id, tableSchema.value) as any
    const result = res.data || res
    if (result.success) {
      ElMessage.success('建表成功')
      createTableVisible.value = false
      await checkTable()
    } else {
      ElMessage.error(result.message || '建表失败')
    }
  } catch (error: any) {
    ElMessage.error(error.message || '建表失败')
  } finally {
    creating.value = false
  }
}
</script>

<style scoped lang="scss">
.datasource-manage {
  .info-section {
    margin-bottom: 16px;
  }

  .action-buttons {
    display: flex;
    gap: 12px;
    margin-bottom: 16px;
  }

  .result-alert {
    margin-bottom: 16px;
    p { margin: 4px 0; }
  }

  .table-result-card {
    margin-bottom: 16px;
    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .row-count {
      margin: 0 0 12px;
      color: var(--el-text-color-secondary);
    }
  }

  .tables-card {
    .table-tag { margin: 4px; }
  }
}

.create-table-form {
  .field-hint {
    font-size: 12px;
    color: var(--el-text-color-placeholder);
    margin-top: 4px;
  }
  
  .parse-section {
    margin-bottom: 16px;
    background: var(--el-fill-color-lighter);
  }
  
  .columns-table {
    :deep(.el-table__cell) {
      padding: 8px 0;
    }
  }
}

.sql-preview {
  font-family: 'Monaco', 'Consolas', monospace;
  background: var(--el-fill-color-light);
}
</style>
