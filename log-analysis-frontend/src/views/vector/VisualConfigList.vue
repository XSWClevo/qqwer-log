<template>
  <AppLayout>
    <div class="visual-config-container">
      <el-card shadow="never" class="page-header">
        <div class="header-content">
          <div>
            <h2>可视化配置</h2>
            <p class="subtitle">使用流程图方式创建和管理 Vector 配置</p>
          </div>
          <el-button type="primary" @click="showCreateDialog = true">
            <el-icon><Plus /></el-icon>
            新建可视化配置
          </el-button>
        </div>
      </el-card>

      <!-- 配置列表 -->
      <div v-if="loading" class="loading-container">
        <el-skeleton :rows="5" animated />
      </div>
      <div v-else-if="configs.length === 0" class="empty-container">
        <el-empty description="暂无可视化配置">
          <el-button type="primary" @click="showCreateDialog = true">创建第一个配置</el-button>
        </el-empty>
      </div>
      <div v-else class="config-grid">
        <el-card
          v-for="config in configs"
          :key="config.id"
          shadow="hover"
          class="config-card"
          @click="openEditor(config)"
        >
          <div class="card-header">
            <el-icon class="card-icon" :size="24"><Share /></el-icon>
            <div class="card-info">
              <span class="card-name">{{ config.name }}</span>
              <el-tag size="small" type="info">{{ config.format }}</el-tag>
            </div>
          </div>
          <p class="card-desc">{{ config.description || '无描述' }}</p>
          <div class="card-meta">
            <span>{{ config.nodeCount || 0 }} 个节点</span>
            <span>{{ formatTime(config.updatedAt) }}</span>
          </div>
          <div class="card-actions" @click.stop>
            <el-button size="small" type="primary" @click="openEditor(config)">
              <el-icon><Edit /></el-icon>编辑
            </el-button>
            <el-button size="small" @click="exportConfig(config)">
              <el-icon><Download /></el-icon>导出
            </el-button>
            <el-button size="small" type="danger" @click="deleteConfig(config)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </el-card>
      </div>

      <!-- 新建对话框 -->
      <el-dialog
        v-model="showCreateDialog"
        title="新建可视化配置"
        width="500px"
        destroy-on-close
        class="create-dialog"
      >
        <p class="dialog-subtitle">使用流程图方式创建 Vector 配置</p>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
          <el-form-item label="配置名称" prop="name">
            <el-input v-model="form.name" placeholder="生产环境日志收集" />
          </el-form-item>
          <el-form-item label="描述" prop="description">
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="3"
              placeholder="收集应用日志并发送到 Elasticsearch"
            />
          </el-form-item>
          <el-form-item label="配置格式" prop="format">
            <el-select v-model="form.format" style="width: 100%">
              <el-option label="Namespace YAML (推荐)" value="namespace_yaml" />
              <el-option label="单文件 YAML" value="yaml" />
              <el-option label="TOML" value="toml" />
            </el-select>
            <div class="format-hint">
              Namespace YAML: 每个组件独立文件，使用 vector -C config/ 启动
            </div>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showCreateDialog = false">取消</el-button>
          <el-button type="primary" :loading="creating" @click="createConfig">
            创建并开始编辑
          </el-button>
        </template>
      </el-dialog>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Share, Edit, Download, Delete } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'
import AppLayout from '@/components/layout/AppLayout.vue'
import { visualConfigApi, type VisualConfig } from '@/api/vector'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

const router = useRouter()
const loading = ref(false)
const creating = ref(false)
const configs = ref<VisualConfig[]>([])
const showCreateDialog = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  description: '',
  format: 'namespace_yaml'
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入配置名称', trigger: 'blur' }]
}

const formatTime = (time: string) => dayjs(time).fromNow()

const fetchConfigs = async () => {
  loading.value = true
  try {
    const res = await visualConfigApi.getList()
    configs.value = res.data || []
  } catch {
    ElMessage.error('加载配置列表失败')
  } finally {
    loading.value = false
  }
}

const createConfig = async () => {
  await formRef.value?.validate(async (valid) => {
    if (!valid) return
    creating.value = true
    try {
      const res = await visualConfigApi.create(form)
      ElMessage.success('创建成功')
      showCreateDialog.value = false
      router.push(`/vector/visual-config/${res.data.id}`)
    } catch (e: any) {
      ElMessage.error(e.message || '创建失败')
    } finally {
      creating.value = false
    }
  })
}

const openEditor = (config: VisualConfig) => {
  router.push(`/vector/visual-config/${config.id}`)
}

const exportConfig = async (config: VisualConfig) => {
  try {
    const blob = await visualConfigApi.export(config.id)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${config.name}.yaml`
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('导出失败')
  }
}

const deleteConfig = (config: VisualConfig) => {
  ElMessageBox.confirm(`确定删除配置"${config.name}"吗？`, '确认删除', { type: 'warning' })
    .then(async () => {
      await visualConfigApi.delete(config.id)
      ElMessage.success('删除成功')
      fetchConfigs()
    })
}

onMounted(fetchConfigs)
</script>

<style scoped lang="scss">
.visual-config-container {
  padding: 20px;
}

.page-header {
  margin-bottom: 20px;
  .header-content {
    display: flex;
    justify-content: space-between;
    align-items: center;
    h2 { margin: 0 0 8px; font-size: 24px; }
    .subtitle { margin: 0; color: var(--el-text-color-secondary); font-size: 14px; }
  }
}

.loading-container, .empty-container {
  padding: 60px;
  text-align: center;
}

.config-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.config-card {
  cursor: pointer;
  transition: all 0.3s;
  &:hover { transform: translateY(-4px); box-shadow: var(--el-box-shadow-light); }

  .card-header {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 12px;
    .card-icon { color: var(--el-color-primary); }
    .card-info { display: flex; align-items: center; gap: 8px; }
    .card-name { font-size: 16px; font-weight: 600; }
  }

  .card-desc {
    color: var(--el-text-color-secondary);
    font-size: 13px;
    margin: 0 0 12px;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .card-meta {
    display: flex;
    justify-content: space-between;
    font-size: 12px;
    color: var(--el-text-color-placeholder);
    margin-bottom: 12px;
  }

  .card-actions {
    display: flex;
    gap: 8px;
    border-top: 1px solid var(--el-border-color-lighter);
    padding-top: 12px;
  }
}

.create-dialog {
  .dialog-subtitle {
    color: var(--el-text-color-secondary);
    font-size: 14px;
    margin: -10px 0 20px;
  }
  .format-hint {
    font-size: 12px;
    color: var(--el-text-color-placeholder);
    margin-top: 4px;
  }
}
</style>
