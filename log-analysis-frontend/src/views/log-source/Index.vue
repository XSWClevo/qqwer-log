<template>
  <AppLayout>
    <div class="log-source-container">
      <!-- 页面标题 -->
      <el-card class="header-card" shadow="never">
        <div class="header-content">
          <div class="title-section">
            <h2>日志源管理</h2>
            <p class="subtitle">管理可信任的日志源 IP 地址白名单</p>
          </div>
          <div class="action-section">
            <el-button @click="handleAddManually">
              <el-icon><Plus /></el-icon>
              手动添加
            </el-button>
            <el-button @click="loadData">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </el-card>

      <!-- 待审核通知 -->
      <el-alert
        v-if="pendingCount > 0"
        type="warning"
        :closable="false"
        show-icon
        class="pending-alert"
      >
        <template #title>
          <span>发现 <strong>{{ pendingCount }}</strong> 个待审核的日志源，请及时处理</span>
        </template>
      </el-alert>

      <!-- 标签页 -->
      <el-card class="content-card" shadow="never">
        <el-tabs v-model="activeTab" @tab-change="handleTabChange">
          <!-- 信任列表 -->
          <el-tab-pane label="信任列表" name="trusted">
            <template #label>
              <span>
                <el-icon><Select /></el-icon>
                信任列表 ({{ trustedSources.length }})
              </span>
            </template>
            <LogSourceTable
              :data="trustedSources"
              :loading="loading"
              status="trusted"
              @trust="handleTrust"
              @block="handleBlock"
              @delete="handleDelete"
            />
          </el-tab-pane>

          <!-- 待审核列表 -->
          <el-tab-pane label="待审核" name="pending">
            <template #label>
              <el-badge :value="pendingCount" :hidden="pendingCount === 0" type="warning">
                <span>
                  <el-icon><Clock /></el-icon>
                  待审核 ({{ pendingSources.length }})
                </span>
              </el-badge>
            </template>
            <LogSourceTable
              :data="pendingSources"
              :loading="loading"
              status="pending"
              @trust="handleTrust"
              @block="handleBlock"
              @delete="handleDelete"
            />
          </el-tab-pane>

          <!-- 拉黑列表 -->
          <el-tab-pane label="拉黑列表" name="blocked">
            <template #label>
              <span>
                <el-icon><CircleClose /></el-icon>
                拉黑列表 ({{ blockedSources.length }})
              </span>
            </template>
            <LogSourceTable
              :data="blockedSources"
              :loading="loading"
              status="blocked"
              @trust="handleTrust"
              @block="handleBlock"
              @delete="handleDelete"
            />
          </el-tab-pane>
        </el-tabs>
      </el-card>

      <!-- 手动添加对话框 -->
      <el-dialog
        v-model="addDialogVisible"
        title="手动添加日志源"
        width="500px"
      >
        <el-form :model="addForm" :rules="addRules" ref="addFormRef" label-width="100px">
          <el-form-item label="IP地址" prop="sourceIp">
            <el-input v-model="addForm.sourceIp" placeholder="例如: 192.168.1.100" />
          </el-form-item>
          <el-form-item label="主机名" prop="hostname">
            <el-input v-model="addForm.hostname" placeholder="可选" />
          </el-form-item>
          <el-form-item label="描述" prop="description">
            <el-input v-model="addForm.description" type="textarea" :rows="3" placeholder="可选" />
          </el-form-item>
          <el-form-item label="备注" prop="remark">
            <el-input v-model="addForm.remark" type="textarea" :rows="2" placeholder="可选" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="addDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSubmitAdd" :loading="submitting">确定</el-button>
        </template>
      </el-dialog>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, Plus, Refresh, Select, Clock, CircleClose } from '@element-plus/icons-vue'
import AppLayout from '@/components/layout/AppLayout.vue'
import LogSourceTable from './components/LogSourceTable.vue'
import {
  getTrustedSources,
  getPendingSources,
  getBlockedSources,
  trustLogSource,
  blockLogSource,
  deleteLogSource,
  type LogSourceDTO,
  type TrustLogSourceRequest
} from '@/api/log-source'
import request from '@/utils/request'
import type { NewLogSourceNotification } from '@/api/log-source'

// 状态
const activeTab = ref('trusted')
const loading = ref(false)
const submitting = ref(false)
const addDialogVisible = ref(false)

// 数据
const trustedSources = ref<LogSourceDTO[]>([])
const pendingSources = ref<LogSourceDTO[]>([])
const blockedSources = ref<LogSourceDTO[]>([])

// 待审核数量
const pendingCount = computed(() => pendingSources.value.length)

// 添加表单
const addFormRef = ref<FormInstance>()
const addForm = ref<TrustLogSourceRequest>({
  sourceIp: '',
  hostname: '',
  description: '',
  remark: ''
})

// 表单验证规则
const addRules: FormRules = {
  sourceIp: [
    { required: true, message: '请输入IP地址', trigger: 'blur' },
    {
      pattern: /^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$/,
      message: 'IP地址格式不正确',
      trigger: 'blur'
    }
  ]
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const [trustedRes, pendingRes, blockedRes] = await Promise.all([
      getTrustedSources(),
      getPendingSources(),
      getBlockedSources()
    ])
    trustedSources.value = trustedRes.data || []
    pendingSources.value = pendingRes.data || []
    blockedSources.value = blockedRes.data || []
  } catch (error) {
    console.error('加载日志源列表失败:', error)
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

// 标签页切换
const handleTabChange = (tabName: string) => {
  console.log('切换到标签页:', tabName)
}

// 手动添加
const handleAddManually = () => {
  addForm.value = {
    sourceIp: '',
    hostname: '',
    description: '',
    remark: ''
  }
  addDialogVisible.value = true
}

// 提交添加
const handleSubmitAdd = async () => {
  if (!addFormRef.value) return

  await addFormRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      await trustLogSource(addForm.value)
      ElMessage.success('添加成功')
      addDialogVisible.value = false
      await loadData()
      activeTab.value = 'trusted'
    } catch (error: any) {
      console.error('添加日志源失败:', error)
      ElMessage.error(error.message || '添加失败')
    } finally {
      submitting.value = false
    }
  })
}

// 信任日志源
const handleTrust = async (source: LogSourceDTO) => {
  try {
    await ElMessageBox.confirm(
      `确定要信任日志源 ${source.sourceIp} 吗？`,
      '确认信任',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await trustLogSource({
      sourceIp: source.sourceIp,
      hostname: source.hostname,
      description: source.description,
      remark: source.remark
    })

    ElMessage.success('已添加到信任列表')
    await loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('信任日志源失败:', error)
      ElMessage.error(error.message || '操作失败')
    }
  }
}

// 拉黑日志源
const handleBlock = async (source: LogSourceDTO) => {
  try {
    await ElMessageBox.confirm(
      `确定要拉黑日志源 ${source.sourceIp} 吗？拉黑后将不再接收该IP的日志。`,
      '确认拉黑',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await blockLogSource(source.sourceIp)
    ElMessage.success('已添加到拉黑列表')
    await loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('拉黑日志源失败:', error)
      ElMessage.error(error.message || '操作失败')
    }
  }
}

// 删除日志源
const handleDelete = async (source: LogSourceDTO) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除日志源 ${source.sourceIp} 吗？此操作不可恢复。`,
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'error'
      }
    )

    await deleteLogSource(source.sourceIp)
    ElMessage.success('删除成功')
    await loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('删除日志源失败:', error)
      ElMessage.error(error.message || '操作失败')
    }
  }
}

// 初始化
onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
@use '@/assets/styles/macos.scss' as *;

.log-source-container {
  padding: 24px;
  background: var(--macos-bg-secondary);
  min-height: 100vh;
}

.header-card {
  margin-bottom: 16px;
  border-radius: var(--macos-radius-lg);
  border: 1px solid var(--macos-border);
  background: var(--macos-glass-bg);
  backdrop-filter: blur(20px);
  box-shadow: var(--macos-shadow-sm);

  .header-content {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .title-section {
      h2 {
        margin: 0 0 8px 0;
        font-size: 24px;
        font-weight: 600;
        color: var(--macos-text-primary);
      }

      .subtitle {
        margin: 0;
        font-size: 14px;
        color: var(--macos-text-secondary);
      }
    }

    .action-section {
      display: flex;
      gap: 12px;
    }
  }
}

.pending-alert {
  margin-bottom: 16px;
  border-radius: var(--macos-radius-md);
}

.content-card {
  border-radius: var(--macos-radius-lg);
  border: 1px solid var(--macos-border);
  background: var(--macos-bg-primary);
  box-shadow: var(--macos-shadow-sm);

  :deep(.el-tabs__item) {
    display: flex;
    align-items: center;
    gap: 6px;
  }
}
</style>
