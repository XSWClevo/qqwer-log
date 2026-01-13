<template>
  <AppLayout>
    <div class="vector-config-container">
    <el-card shadow="never" class="page-header">
      <div class="header-content">
        <div>
          <h2>配置管理</h2>
          <p class="subtitle">管理 Vector 配置文件和模板</p>
        </div>
        <el-button type="primary" @click="showAddDialog = true">
          <el-icon><Plus /></el-icon>
          添加配置
        </el-button>
      </div>
    </el-card>

    <!-- 筛选器 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true">
        <el-form-item label="关键词">
          <el-input
            v-model="filters.keyword"
            placeholder="搜索配置名称"
            clearable
            @clear="fetchConfigs"
          />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="filters.isTemplate" placeholder="全部" clearable>
            <el-option label="模板" :value="true" />
            <el-option label="普通配置" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchConfigs">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 配置列表 -->
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="5" animated />
    </div>
    <div v-else-if="configs.length === 0" class="empty-container">
      <el-empty description="暂无配置">
        <el-button type="primary" @click="showAddDialog = true">创建第一个配置</el-button>
      </el-empty>
    </div>
    <div v-else class="config-list">
      <el-card
        v-for="config in configs"
        :key="config.id"
        shadow="hover"
        class="config-card"
      >
        <div class="config-header">
          <div class="config-info">
            <el-icon class="config-icon" :size="20"><Document /></el-icon>
            <div>
              <div class="config-title">
                <span class="config-name">{{ config.name }}</span>
                <el-tag v-if="config.isTemplate" type="warning" size="small">模板</el-tag>
                <el-tag type="info" size="small">v{{ config.version }}</el-tag>
              </div>
              <div class="config-desc">{{ config.description || '无描述' }}</div>
            </div>
          </div>
          <div class="config-time">{{ formatTime(config.createdAt) }}</div>
        </div>

        <el-divider />

        <div class="config-content">
          <pre class="code-preview">{{ getPreview(config.content) }}</pre>
        </div>

        <el-divider />

        <div class="config-actions">
          <el-button size="small" @click="viewConfig(config)">
            <el-icon><View /></el-icon>
            查看
          </el-button>
          <el-button size="small" type="primary" @click="editConfig(config)">
            <el-icon><Edit /></el-icon>
            编辑
          </el-button>
          <el-button size="small" @click="copyConfig(config)">
            <el-icon><DocumentCopy /></el-icon>
            复制
          </el-button>
          <el-button size="small" type="danger" @click="deleteConfig(config)">
            <el-icon><Delete /></el-icon>
            删除
          </el-button>
        </div>
      </el-card>
    </div>

    <!-- 分页 -->
    <div v-if="total > 0" class="pagination-container">
      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="fetchConfigs"
        @current-change="fetchConfigs"
      />
    </div>

    <!-- 添加/编辑对话框 -->
    <el-dialog
      v-model="showAddDialog"
      :title="editingConfig ? '编辑配置' : '添加配置'"
      width="1200px"
      destroy-on-close
    >
      <el-form
        ref="configFormRef"
        :model="configForm"
        :rules="configRules"
        label-width="80px"
      >
        <el-form-item label="配置名称" prop="name">
          <el-input v-model="configForm.name" placeholder="my-vector-config" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="configForm.description"
            type="textarea"
            :rows="2"
            placeholder="配置描述（可选）"
          />
        </el-form-item>
        <el-form-item label="配置内容" prop="content">
          <div ref="editorRef" class="codemirror-wrapper"></div>
          <div class="yaml-actions">
            <el-button size="small" @click="formatYaml">
              <el-icon><Tools /></el-icon>
              格式化 YAML
            </el-button>
            <span v-if="yamlError" class="yaml-error-text">
              <el-icon><WarningFilled /></el-icon>
              {{ yamlError }}
            </span>
            <span v-else-if="yamlValid && configForm.content" class="yaml-success-text">
              <el-icon><SuccessFilled /></el-icon>
              YAML 格式正确
            </span>
          </div>
        </el-form-item>
        <el-form-item label="配置类型">
          <el-checkbox v-model="configForm.isTemplate">设置为模板</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitConfig">
          {{ editingConfig ? '更新' : '添加' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 查看配置对话框 -->
    <el-dialog
      v-model="showViewDialog"
      title="查看配置"
      width="800px"
      destroy-on-close
    >
      <div v-if="viewingConfig">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="配置名称">{{ viewingConfig.name }}</el-descriptions-item>
          <el-descriptions-item label="版本">v{{ viewingConfig.version }}</el-descriptions-item>
          <el-descriptions-item label="类型">
            <el-tag v-if="viewingConfig.isTemplate" type="warning" size="small">模板</el-tag>
            <el-tag v-else type="info" size="small">普通配置</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">
            {{ formatTime(viewingConfig.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="描述" :span="2">
            {{ viewingConfig.description || '无' }}
          </el-descriptions-item>
        </el-descriptions>
        <el-divider />
        <h4>配置内容</h4>
        <pre class="code-view">{{ viewingConfig.content }}</pre>
      </div>
      <template #footer>
        <el-button @click="showViewDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  Plus, Document, View, Edit, Delete, DocumentCopy,
  WarningFilled, Tools, SuccessFilled
} from '@element-plus/icons-vue'
import axios from 'axios'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'
import AppLayout from '@/components/layout/AppLayout.vue'
import * as yaml from 'js-yaml'

// CodeMirror 导入
import { EditorView, basicSetup } from 'codemirror'
import { EditorState } from '@codemirror/state'
import { yaml as yamlLang } from '@codemirror/lang-yaml'
import { oneDark } from '@codemirror/theme-one-dark'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

interface VectorConfig {
  id: string
  name: string
  description?: string
  content: string
  version: number
  isTemplate: boolean
  createdAt: string
  updatedAt: string
}

const loading = ref(false)
const configs = ref<VectorConfig[]>([])
const total = ref(0)
const showAddDialog = ref(false)
const showViewDialog = ref(false)
const submitting = ref(false)
const editingConfig = ref<VectorConfig | null>(null)
const viewingConfig = ref<VectorConfig | null>(null)
const configFormRef = ref<FormInstance>()
const editorRef = ref<HTMLElement>()

const yamlError = ref('')
const yamlValid = ref(true)

let editorView: EditorView | null = null

const filters = reactive({
  keyword: '',
  isTemplate: undefined as boolean | undefined
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10
})

const configForm = reactive({
  name: '',
  description: '',
  content: '',
  isTemplate: false
})

const configRules: FormRules = {
  name: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  content: [{ required: true, message: '请输入配置内容', trigger: 'blur' }]
}

/**
 * 初始化 CodeMirror 编辑器
 */
const initEditor = () => {
  if (!editorRef.value || editorView) return

  const state = EditorState.create({
    doc: configForm.content,
    extensions: [
      basicSetup,
      yamlLang(),
      oneDark,
      EditorView.updateListener.of((update) => {
        if (update.docChanged) {
          configForm.content = update.state.doc.toString()
          validateYamlSyntax()
        }
      }),
      EditorView.theme({
        '&': { height: '500px' },
        '.cm-scroller': { overflow: 'auto' }
      })
    ]
  })

  editorView = new EditorView({
    state,
    parent: editorRef.value
  })
}

/**
 * 销毁编辑器
 */
const destroyEditor = () => {
  if (editorView) {
    editorView.destroy()
    editorView = null
  }
}

/**
 * 验证 YAML 语法（不格式化）
 */
const validateYamlSyntax = () => {
  const content = configForm.content.trim()
  if (!content) {
    yamlError.value = ''
    yamlValid.value = true
    return
  }

  try {
    yaml.load(content)
    yamlError.value = ''
    yamlValid.value = true
  } catch (e: any) {
    yamlValid.value = false
    const match = e.message?.match(/at line (\d+)/)
    const line = match ? match[1] : ''
    yamlError.value = line
      ? `第 ${line} 行: ${e.reason || e.message}`
      : e.reason || e.message
  }
}

/**
 * 格式化 YAML 内容
 * - 验证 YAML 语法
 * - 自动格式化为标准格式（数组换行、缩进对齐）
 */
const formatYaml = () => {
  const content = configForm.content.trim()
  if (!content) {
    yamlError.value = ''
    yamlValid.value = true
    return
  }

  try {
    // 解析 YAML
    const parsed = yaml.load(content)

    // 重新序列化为标准格式
    const formatted = yaml.dump(parsed, {
      indent: 2,           // 缩进2空格
      lineWidth: 120,      // 单行最大宽度
      noArrayIndent: false, // 数组项换行
      quotingType: '"',    // 使用双引号
      forceQuotes: false   // 非必要不加引号
    })

    configForm.content = formatted

    // 更新编辑器内容
    if (editorView) {
      editorView.dispatch({
        changes: {
          from: 0,
          to: editorView.state.doc.length,
          insert: formatted
        }
      })
    }

    yamlError.value = ''
    yamlValid.value = true
    ElMessage.success('YAML 格式化成功')
  } catch (e: any) {
    yamlValid.value = false
    const match = e.message?.match(/at line (\d+)/)
    const line = match ? match[1] : ''
    yamlError.value = line
      ? `第 ${line} 行: ${e.reason || e.message}`
      : e.reason || e.message
  }
}

// 监听对话框打开，初始化编辑器
watch(showAddDialog, async (newVal) => {
  if (newVal) {
    await nextTick()
    initEditor()
  } else {
    destroyEditor()
  }
})

// 监听编辑内容变化，更新编辑器
watch(() => configForm.content, (newVal) => {
  if (editorView && editorView.state.doc.toString() !== newVal) {
    editorView.dispatch({
      changes: {
        from: 0,
        to: editorView.state.doc.length,
        insert: newVal
      }
    })
  }
})

const formatTime = (time: string) => {
  return dayjs(time).fromNow()
}

const getPreview = (content: string) => {
  const lines = content.split('\n')
  if (lines.length <= 10) return content
  return lines.slice(0, 10).join('\n') + '\n...'
}

const fetchConfigs = async () => {
  loading.value = true
  try {
    const { data } = await axios.get('/api/vector/configs/page', {
      params: {
        pageNum: pagination.pageNum,
        pageSize: pagination.pageSize,
        keyword: filters.keyword || undefined,
        isTemplate: filters.isTemplate
      }
    })
    if (data.code === 200 && data.data) {
      configs.value = data.data.records || []
      total.value = data.data.total || 0
    }
  } catch (error) {
    ElMessage.error('加载配置列表失败')
  } finally {
    loading.value = false
  }
}

const resetFilters = () => {
  filters.keyword = ''
  filters.isTemplate = undefined
  pagination.pageNum = 1
  fetchConfigs()
}

const viewConfig = (config: VectorConfig) => {
  viewingConfig.value = config
  showViewDialog.value = true
}

const editConfig = (config: VectorConfig) => {
  editingConfig.value = config
  Object.assign(configForm, config)
  showAddDialog.value = true
}

const submitConfig = async () => {
  await configFormRef.value?.validate(async (valid) => {
    if (!valid) return

    // 检查 YAML 格式
    if (!yamlValid.value) {
      ElMessage.error('请修正 YAML 格式错误后再提交')
      return
    }

    submitting.value = true
    try {
      if (editingConfig.value) {
        await axios.put(`/api/vector/configs/${editingConfig.value.id}`, configForm)
        ElMessage.success('更新配置成功')
      } else {
        await axios.post('/api/vector/configs', configForm)
        ElMessage.success('添加配置成功')
      }
      showAddDialog.value = false
      resetForm()
      fetchConfigs()
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || '操作失败')
    } finally {
      submitting.value = false
    }
  })
}

const copyConfig = async (config: VectorConfig) => {
  try {
    await axios.post(`/api/vector/configs/${config.id}/copy`)
    ElMessage.success('复制配置成功')
    fetchConfigs()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '复制失败')
  }
}

const deleteConfig = (config: VectorConfig) => {
  ElMessageBox.confirm(
    `确定要删除配置"${config.name}"吗？此操作不可恢复。`,
    '确认删除',
    {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    }
  ).then(async () => {
    try {
      await axios.delete(`/api/vector/configs/${config.id}`)
      ElMessage.success('删除配置成功')
      fetchConfigs()
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || '删除失败')
    }
  })
}

const resetForm = () => {
  editingConfig.value = null
  yamlError.value = ''
  yamlValid.value = true
  configFormRef.value?.resetFields()
  Object.assign(configForm, {
    name: '',
    description: '',
    content: '',
    isTemplate: false
  })
}

onMounted(() => {
  fetchConfigs()
})
</script>

<style scoped lang="scss">
.vector-config-container {
  padding: 20px;
}

.page-header {
  margin-bottom: 20px;

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
  }
}

.filter-card {
  margin-bottom: 20px;
}

.loading-container,
.empty-container {
  padding: 40px;
  text-align: center;
}

.config-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 20px;
}

.config-card {
  transition: all 0.3s ease;

  &:hover {
    transform: translateY(-2px);
  }

  .config-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;

    .config-info {
      display: flex;
      gap: 12px;
      flex: 1;

      .config-icon {
        color: var(--el-color-primary);
        margin-top: 2px;
      }

      .config-title {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-bottom: 6px;

        .config-name {
          font-size: 16px;
          font-weight: 600;
        }
      }

      .config-desc {
        font-size: 13px;
        color: var(--el-text-color-secondary);
      }
    }

    .config-time {
      font-size: 12px;
      color: var(--el-text-color-secondary);
    }
  }

  .config-content {
    .code-preview {
      margin: 0;
      padding: 12px;
      background-color: var(--el-fill-color-light);
      border-radius: 4px;
      font-size: 12px;
      font-family: 'Monaco', 'Consolas', monospace;
      max-height: 200px;
      overflow: auto;
      white-space: pre-wrap;
      word-wrap: break-word;
    }
  }

  .config-actions {
    display: flex;
    gap: 8px;
  }
}

.code-view {
  margin: 12px 0;
  padding: 16px;
  background-color: var(--el-fill-color-light);
  border-radius: 4px;
  font-size: 13px;
  font-family: 'Monaco', 'Consolas', monospace;
  max-height: 500px;
  overflow: auto;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  padding: 20px 0;
}

.codemirror-wrapper {
  width: 100%;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  overflow: hidden;

  :deep(.cm-editor) {
    font-size: 14px;
    width: 100%;

    .cm-scroller {
      font-family: 'Monaco', 'Consolas', 'Courier New', monospace;
    }

    .cm-content {
      min-width: 100%;
    }
  }
}

.yaml-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
}

.yaml-error-text {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--el-color-danger);
  font-size: 13px;

  .el-icon {
    flex-shrink: 0;
  }
}

.yaml-success-text {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--el-color-success);
  font-size: 13px;

  .el-icon {
    flex-shrink: 0;
  }
}
</style>
