<template>
  <el-dialog
    v-model="visible"
    title="智能向导 - 快速创建日志表"
    width="900px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-steps :active="currentStep" align-center class="wizard-steps">
      <el-step title="解析日志" description="上传样本并选择解析方式" />
      <el-step title="确认字段" description="确认字段类型" />
      <el-step title="配置表" description="选择数据源和表名" />
      <el-step title="完成" description="创建表并配置 Vector" />
    </el-steps>

    <div class="wizard-content">
      <!-- 步骤 1: 解析日志 -->
      <div v-if="currentStep === 0" class="step-content">
        <el-form :model="step1Form" label-width="120px">
          <el-form-item label="日志样本">
            <el-input
              v-model="step1Form.logSample"
              type="textarea"
              :rows="6"
              placeholder="粘贴一条日志样本，例如：
<134>1 2024-01-07T10:30:45.123Z server01 nginx 1234 - - GET /api/users 200 0.123
或
{&quot;timestamp&quot;:&quot;2024-01-07T10:30:45Z&quot;,&quot;level&quot;:&quot;INFO&quot;,&quot;message&quot;:&quot;Request processed&quot;}"
            />
          </el-form-item>

          <el-form-item label="解析方式">
            <el-select v-model="step1Form.parseMethod" placeholder="选择解析方式">
              <el-option label="JSON 格式" value="parse_json" />
              <el-option label="Syslog 格式" value="parse_syslog" />
              <el-option label="Key-Value 格式" value="parse_kv" />
              <el-option label="正则表达式" value="parse_regex" />
              <el-option label="自定义 VRL" value="custom" />
            </el-select>
          </el-form-item>

          <el-form-item v-if="step1Form.parseMethod === 'parse_regex'" label="正则表达式">
            <el-input
              v-model="step1Form.regexPattern"
              placeholder="例如: ^(?P<timestamp>\S+) (?P<level>\w+) (?P<message>.+)$"
            />
            <span class="form-tip">使用命名捕获组 (?P&lt;name&gt;pattern)</span>
          </el-form-item>

          <el-form-item v-if="step1Form.parseMethod === 'custom'" label="VRL 脚本">
            <el-input
              v-model="step1Form.customVrl"
              type="textarea"
              :rows="4"
              placeholder="输入自定义 VRL 脚本"
            />
          </el-form-item>

          <el-form-item>
            <el-button type="primary" @click="handleParseLog" :loading="parsing">
              <el-icon><MagicStick /></el-icon>
              解析日志
            </el-button>
            <el-button @click="testRegex" v-if="step1Form.parseMethod === 'parse_regex'">
              <el-icon><View /></el-icon>
              测试正则
            </el-button>
          </el-form-item>

          <el-alert v-if="parseError" type="error" :closable="false" class="parse-result">
            <template #title>解析失败</template>
            {{ parseError }}
          </el-alert>

          <el-alert v-if="parseSuccess" type="success" :closable="false" class="parse-result">
            <template #title>解析成功</template>
            成功提取 {{ parsedFields.length }} 个字段
          </el-alert>
        </el-form>
      </div>

      <!-- 步骤 2: 确认字段 -->
      <div v-if="currentStep === 1" class="step-content">
        <el-alert type="info" :closable="false" class="step-tip">
          <p>请确认字段类型，系统已自动推断。带有 🔍 标记的字段检测到特殊类型（如 IP 地址）。</p>
        </el-alert>

        <el-table :data="parsedFields" border class="fields-table">
          <el-table-column prop="name" label="字段名" width="200">
            <template #default="{ row }">
              <el-input v-model="row.name" size="small" />
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
                <el-option label="Date" value="Date" />
                <el-option label="UInt8 (Boolean)" value="UInt8" />
                <el-option label="IPv4" value="IPv4" />
                <el-option label="IPv6" value="IPv6" />
              </el-select>
            </template>
          </el-table-column>

          <el-table-column prop="value" label="示例值" min-width="200">
            <template #default="{ row }">
              <span class="sample-value">{{ formatValue(row.value) }}</span>
              <el-tag v-if="row.suggestion" type="warning" size="small" class="suggestion-tag">
                🔍 {{ row.suggestion }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column prop="comment" label="备注" width="200">
            <template #default="{ row }">
              <el-input v-model="row.comment" size="small" placeholder="可选" />
            </template>
          </el-table-column>

          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ $index }">
              <el-button
                type="danger"
                size="small"
                link
                @click="removeField($index)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-button type="primary" link class="add-field-btn" @click="addField">
          <el-icon><Plus /></el-icon>
          添加字段
        </el-button>
      </div>

      <!-- 步骤 3: 配置表 -->
      <div v-if="currentStep === 2" class="step-content">
        <el-form :model="step3Form" label-width="120px">
          <el-form-item label="数据源">
            <el-select
              v-model="step3Form.datasourceId"
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
            <el-input
              v-model="step3Form.tableName"
              placeholder="例如: nginx_logs"
              @blur="handleGenerateDDL"
            />
            <span class="form-tip">建议使用小写字母和下划线</span>
          </el-form-item>

          <el-form-item label="DDL 预览">
            <el-input
              v-model="generatedDDL"
              type="textarea"
              :rows="12"
              readonly
              class="ddl-preview"
            />
          </el-form-item>

          <el-form-item>
            <el-button @click="handleGenerateDDL" :loading="generating">
              <el-icon><Refresh /></el-icon>
              重新生成 DDL
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 步骤 4: 完成 -->
      <div v-if="currentStep === 3" class="step-content">
        <el-result
          :icon="createSuccess ? 'success' : 'info'"
          :title="createSuccess ? '表创建成功！' : '正在创建表...'"
        >
          <template #sub-title>
            <div v-if="createSuccess">
              <p>表 <strong>{{ step3Form.tableName }}</strong> 已成功创建</p>
              <p>接下来您可以：</p>
              <ul class="next-steps">
                <li>在可视化配置中添加 Sink 组件，将日志写入此表</li>
                <li>配置 Transform 组件，使用刚才的 VRL 脚本解析日志</li>
                <li>部署配置到 Vector Agent 开始采集日志</li>
              </ul>
            </div>
            <div v-else-if="createError">
              <el-alert type="error" :closable="false">
                <template #title>创建失败</template>
                {{ createError }}
              </el-alert>
            </div>
          </template>

          <template #extra>
            <el-button v-if="createSuccess" type="primary" @click="goToVisualConfig">
              前往可视化配置
            </el-button>
            <el-button @click="handleClose">关闭</el-button>
          </template>
        </el-result>
      </div>
    </div>

    <template #footer>
      <div class="wizard-footer">
        <el-button @click="handlePrev" :disabled="currentStep === 0 || creating">
          上一步
        </el-button>
        <el-button
          type="primary"
          @click="handleNext"
          :disabled="!canNext"
          :loading="creating"
        >
          {{ currentStep === 2 ? '创建表' : currentStep === 3 ? '完成' : '下一步' }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { MagicStick, View, Plus, Refresh } from '@element-plus/icons-vue'
import { parseLog, generateDDL, createTable, type ParsedField } from '@/api/wizard'
import { listDatasourcesByType, type Datasource } from '@/api/datasource'

const router = useRouter()

interface Props {
  modelValue: boolean
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

// 当前步骤
const currentStep = ref(0)

// 步骤 1: 解析日志
const step1Form = ref({
  logSample: '',
  parseMethod: 'parse_json' as any,
  regexPattern: '',
  customVrl: ''
})

const parsing = ref(false)
const parseSuccess = ref(false)
const parseError = ref('')
const parsedFields = ref<ParsedField[]>([])
const generatedVrlScript = ref('') // 保存生成的 VRL 脚本

// 步骤 3: 配置表
const step3Form = ref({
  datasourceId: '',
  tableName: ''
})

const datasources = ref<Datasource[]>([])
const generating = ref(false)
const generatedDDL = ref('')

// 步骤 4: 完成
const creating = ref(false)
const createSuccess = ref(false)
const createError = ref('')

/**
 * 是否可以进入下一步
 */
const canNext = computed(() => {
  if (currentStep.value === 0) {
    return parseSuccess.value && parsedFields.value.length > 0
  } else if (currentStep.value === 1) {
    return parsedFields.value.length > 0
  } else if (currentStep.value === 2) {
    return step3Form.value.datasourceId && step3Form.value.tableName && generatedDDL.value
  } else if (currentStep.value === 3) {
    // 步骤 4（完成步骤）始终允许点击"完成"按钮
    return true
  }
  return false
})

/**
 * 解析日志
 */
const handleParseLog = async () => {
  if (!step1Form.value.logSample) {
    ElMessage.warning('请输入日志样本')
    return
  }

  parsing.value = true
  parseError.value = ''
  parseSuccess.value = false

  try {
    const { data } = await parseLog({
      logSample: step1Form.value.logSample,
      parseMethod: step1Form.value.parseMethod,
      regexPattern: step1Form.value.regexPattern,
      customVrl: step1Form.value.customVrl
    })

    console.log('完整响应:', data)

    // data 本身就是后端返回的数据，包含 code, message, data
    if (data.code === 200 && data.data) {
      const response = data.data
      console.log('解析结果:', response)

      if (response.success && response.fields && response.fields.length > 0) {
        // 将 sampleValue 映射为 value
        parsedFields.value = response.fields.map((f: any) => ({
          name: f.name,
          value: f.sampleValue,
          type: f.type,
          suggestion: f.suggestion?.type || null,
          comment: ''
        }))
        // 保存生成的 VRL 脚本
        generatedVrlScript.value = response.vrlScript || ''
        parseSuccess.value = true
        ElMessage.success(`日志解析成功，提取了 ${parsedFields.value.length} 个字段`)
      } else {
        parseError.value = response.error || '解析失败'
      }
    } else if (data.success && data.fields) {
      // 如果 data 直接就是解析结果（没有包装）
      console.log('直接解析结果:', data)
      if (data.fields.length > 0) {
        parsedFields.value = data.fields.map((f: any) => ({
          name: f.name,
          value: f.sampleValue,
          type: f.type,
          suggestion: f.suggestion?.type || null,
          comment: ''
        }))
        // 保存生成的 VRL 脚本
        generatedVrlScript.value = data.vrlScript || ''
        parseSuccess.value = true
        ElMessage.success(`日志解析成功，提取了 ${parsedFields.value.length} 个字段`)
      } else {
        parseError.value = data.error || '解析失败'
      }
    } else {
      parseError.value = data.message || '解析失败'
    }
  } catch (error: any) {
    console.error('解析日志失败:', error)
    parseError.value = error.response?.data?.message || error.message || '解析失败'
  } finally {
    parsing.value = false
  }
}

/**
 * 测试正则表达式
 */
const testRegex = () => {
  // 复用现有的正则测试功能
  ElMessage.info('请在组件库中测试正则表达式')
}

/**
 * 格式化值
 */
const formatValue = (value: any) => {
  if (typeof value === 'object') {
    return JSON.stringify(value)
  }
  return String(value)
}

/**
 * 添加字段
 */
const addField = () => {
  parsedFields.value.push({
    name: 'new_field',
    value: '',
    type: 'String',
    comment: ''
  })
}

/**
 * 删除字段
 */
const removeField = (index: number) => {
  parsedFields.value.splice(index, 1)
}

/**
 * 加载数据源列表
 */
const loadDatasources = async () => {
  try {
    const { data } = await listDatasourcesByType('clickhouse')
    console.log('数据源响应:', data)

    let datasourceList = []

    // 处理不同的响应格式
    if (data.code === 200 && data.data) {
      datasourceList = data.data
    } else if (Array.isArray(data)) {
      datasourceList = data
    } else if (data.data && Array.isArray(data.data)) {
      datasourceList = data.data
    }

    console.log('数据源列表:', datasourceList)
    datasources.value = datasourceList

    // 默认选择第一个
    if (datasources.value.length > 0) {
      step3Form.value.datasourceId = datasources.value[0].id
    } else {
      ElMessage.warning('未找到 ClickHouse 数据源，请先添加数据源')
    }
  } catch (error) {
    console.error('加载数据源失败:', error)
    ElMessage.error('加载数据源失败')
  }
}

/**
 * 数据源变更
 */
const handleDatasourceChange = () => {
  if (step3Form.value.tableName) {
    handleGenerateDDL()
  }
}

/**
 * 生成 DDL
 */
const handleGenerateDDL = async () => {
  if (!step3Form.value.datasourceId || !step3Form.value.tableName) {
    return
  }

  generating.value = true
  try {
    const { data } = await generateDDL({
      datasourceId: step3Form.value.datasourceId,
      tableName: step3Form.value.tableName,
      fields: parsedFields.value.map(f => ({
        name: f.name,
        type: f.type,
        comment: f.comment
      }))
    })

    console.log('DDL 生成响应:', data)

    // 处理不同的响应格式
    if (data.code === 200 && data.data) {
      generatedDDL.value = data.data.ddl
    } else if (data.ddl) {
      generatedDDL.value = data.ddl
    } else if (data.data && data.data.ddl) {
      generatedDDL.value = data.data.ddl
    }

    if (!generatedDDL.value) {
      ElMessage.error('DDL 生成失败')
    }
  } catch (error) {
    console.error('生成 DDL 失败:', error)
    ElMessage.error('生成 DDL 失败')
  } finally {
    generating.value = false
  }
}

/**
 * 上一步
 */
const handlePrev = () => {
  if (currentStep.value > 0) {
    currentStep.value--
  }
}

/**
 * 下一步
 */
const handleNext = async () => {
  if (currentStep.value === 2) {
    // 创建表
    await handleCreateTable()
  } else if (currentStep.value === 3) {
    // 完成
    handleClose()
  } else {
    currentStep.value++
    
    // 进入步骤 3 时加载数据源
    if (currentStep.value === 2) {
      await loadDatasources()
    }
  }
}

/**
 * 创建表
 */
const handleCreateTable = async () => {
  creating.value = true
  createError.value = ''

  try {
    const response = await createTable({
      datasourceId: step3Form.value.datasourceId,
      ddl: generatedDDL.value,
      tableName: step3Form.value.tableName,
      vrlScript: generatedVrlScript.value,
      parseMethod: step1Form.value.parseMethod,
      autoCreateComponents: true
    }) as any

    console.log('创建表响应:', response)

    // response 是 {code: 200, message: 'success', data: {success: true, remapComponentId: 'xxx', sinkComponentId: 'xxx'}}
    if (response.code === 200 && response.data?.success) {
      createSuccess.value = true
      currentStep.value = 3
      ElMessage.success('表和组件创建成功')
    } else {
      createError.value = response.data?.error || response.message || '创建失败'
      currentStep.value = 3
    }
  } catch (error: any) {
    console.error('创建表失败:', error)
    createError.value = error.response?.data?.message || error.message || '创建失败'
    currentStep.value = 3
  } finally {
    creating.value = false
  }
}

/**
 * 前往可视化配置
 */
const goToVisualConfig = () => {
  router.push('/vector/visual-configs')
  handleClose()
}

/**
 * 关闭对话框
 */
const handleClose = () => {
  visible.value = false
  // 重置状态
  setTimeout(() => {
    currentStep.value = 0
    step1Form.value = {
      logSample: '',
      parseMethod: 'parse_json',
      regexPattern: '',
      customVrl: ''
    }
    step3Form.value = {
      datasourceId: '',
      tableName: ''
    }
    parsedFields.value = []
    parseSuccess.value = false
    parseError.value = ''
    generatedDDL.value = ''
    createSuccess.value = false
    createError.value = ''
  }, 300)
}
</script>

<style scoped lang="scss">
.wizard-steps {
  margin-bottom: 30px;
}

.wizard-content {
  min-height: 400px;
  padding: 20px 0;
}

.step-content {
  .form-tip {
    margin-left: 12px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .parse-result {
    margin-top: 16px;
  }

  .step-tip {
    margin-bottom: 16px;
  }
}

.fields-table {
  margin-bottom: 12px;

  .sample-value {
    font-family: 'Monaco', 'Consolas', monospace;
    font-size: 12px;
  }

  .suggestion-tag {
    margin-left: 8px;
  }
}

.add-field-btn {
  margin-top: 8px;
}

.ddl-preview {
  font-family: 'Monaco', 'Consolas', monospace;
  font-size: 12px;
}

.next-steps {
  text-align: left;
  margin: 16px auto;
  max-width: 500px;

  li {
    margin: 8px 0;
    line-height: 1.6;
  }
}

.wizard-footer {
  display: flex;
  justify-content: space-between;
}
</style>
