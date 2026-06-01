<template>
  <AppLayout>
    <div class="datasource-container">
      <!-- 顶部标题栏 -->
      <div class="page-header">
        <h1 class="page-title">数据源管理</h1>
      </div>

      <el-card class="content-card">
      <!-- 搜索和操作栏 -->
      <div class="header-actions">
        <div class="search-bar">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索数据源名称或描述"
            clearable
            style="width: 300px"
            @clear="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>

          <el-select
            v-model="filterType"
            placeholder="数据源类型"
            clearable
            style="width: 150px; margin-left: 10px"
            @change="handleSearch"
          >
            <el-option label="ClickHouse" value="clickhouse" />
            <el-option label="PostgreSQL" value="postgresql" />
            <el-option label="MySQL" value="mysql" />
            <el-option label="Elasticsearch" value="elasticsearch" />
            <el-option label="Loki" value="loki" />
          </el-select>

          <el-select
            v-model="filterStatus"
            placeholder="状态"
            clearable
            style="width: 120px; margin-left: 10px"
            @change="handleSearch"
          >
            <el-option label="活跃" value="active" />
            <el-option label="停用" value="inactive" />
            <el-option label="错误" value="error" />
          </el-select>

          <el-button
            type="primary"
            :icon="Search"
            style="margin-left: 10px"
            @click="handleSearch"
          >
            搜索
          </el-button>
        </div>

        <el-button type="primary" :icon="Plus" @click="handleCreate">
          新建数据源
        </el-button>
      </div>

      <!-- 数据源列表 -->
      <el-table
        v-loading="loading"
        :data="datasourceList"
        style="width: 100%; margin-top: 20px"
      >
        <el-table-column prop="name" label="名称" min-width="150" />

        <el-table-column prop="type" label="类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getTypeTagType(row.type)">
              {{ getTypeLabel(row.type) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="连接信息" min-width="200">
          <template #default="{ row }">
            <div>{{ row.host }}:{{ row.port }}</div>
            <div v-if="row.databaseName" style="color: var(--macos-text-tertiary); font-size: 12px">
              {{ row.databaseName }}
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="description" label="描述" min-width="150" show-overflow-tooltip />

        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="最后检查" width="180">
          <template #default="{ row }">
            <div v-if="row.lastCheckTime">
              <div style="font-size: 12px">
                {{ formatTime(row.lastCheckTime) }}
              </div>
              <el-tag
                v-if="row.lastCheckStatus"
                :type="row.lastCheckStatus === 'success' ? 'success' : 'danger'"
                size="small"
              >
                {{ row.lastCheckStatus === 'success' ? '成功' : '失败' }}
              </el-tag>
            </div>
            <span v-else style="color: var(--macos-text-tertiary)">未检查</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :icon="Connection"
              @click="handleTest(row)"
            >
              测试连接
            </el-button>
            <el-button
              link
              type="primary"
              :icon="Edit"
              @click="handleEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              link
              type="danger"
              :icon="Delete"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: 20px; justify-content: flex-end"
        @size-change="loadDatasources"
        @current-change="loadDatasources"
      />
    </el-card>

    <!-- 创建/编辑数据源对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      @close="handleDialogClose"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="120px"
      >
        <el-form-item label="数据源名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入数据源名称" />
        </el-form-item>

        <el-form-item label="数据源类型" prop="type">
          <el-select
            v-model="formData.type"
            placeholder="请选择数据源类型"
            :disabled="isEdit"
            style="width: 100%"
          >
            <el-option label="ClickHouse" value="clickhouse" />
            <el-option label="PostgreSQL" value="postgresql" />
            <el-option label="MySQL" value="mysql" />
            <el-option label="Elasticsearch" value="elasticsearch" />
            <el-option label="Loki" value="loki" />
          </el-select>
        </el-form-item>

        <el-form-item label="主机地址" prop="host">
          <el-input v-model="formData.host" placeholder="例如: 192.168.1.100" />
        </el-form-item>

        <el-form-item label="端口号" prop="port">
          <el-input-number
            v-model="formData.port"
            :min="1"
            :max="65535"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="数据库名称">
          <el-input v-model="formData.databaseName" placeholder="可选" />
        </el-form-item>

        <el-form-item label="用户名">
          <el-input v-model="formData.username" placeholder="可选" />
        </el-form-item>

        <el-form-item label="密码">
          <el-input
            v-model="formData.password"
            type="password"
            placeholder="可选"
            show-password
          />
        </el-form-item>

        <el-form-item label="启用 SSL">
          <el-switch v-model="formData.sslEnabled" />
        </el-form-item>

        <el-form-item label="描述">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="3"
            placeholder="可选"
          />
        </el-form-item>

        <el-form-item v-if="isEdit" label="状态">
          <el-radio-group v-model="formData.status">
            <el-radio label="active">活跃</el-radio>
            <el-radio label="inactive">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="info" :loading="testing" @click="handleTestConnection">
          测试连接
        </el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, Plus, Edit, Delete, Connection } from '@element-plus/icons-vue'
import AppLayout from '@/components/layout/AppLayout.vue'
import {
  listDatasources,
  createDatasource,
  updateDatasource,
  deleteDatasource,
  testDatasourceConnection,
  testNewDatasourceConnection,
  type Datasource,
  type CreateDatasourceRequest
} from '@/api/datasource'

type DatasourceFormData = CreateDatasourceRequest & { status: string }

// 搜索和筛选
const searchKeyword = ref('')
const filterType = ref('')
const filterStatus = ref('')

// 数据源列表
const loading = ref(false)
const datasourceList = ref<Datasource[]>([])
const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

// 对话框
const dialogVisible = ref(false)
const dialogTitle = ref('新建数据源')
const isEdit = ref(false)
const currentId = ref('')
const formRef = ref<FormInstance>()
const submitting = ref(false)
const testing = ref(false)

const formData = reactive<DatasourceFormData>({
  name: '',
  type: '',
  host: '',
  port: 8123,
  databaseName: '',
  username: '',
  password: '',
  sslEnabled: false,
  description: '',
  status: 'active'
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入数据源名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择数据源类型', trigger: 'change' }],
  host: [{ required: true, message: '请输入主机地址', trigger: 'blur' }],
  port: [{ required: true, message: '请输入端口号', trigger: 'blur' }]
}

// 加载数据源列表
const loadDatasources = async () => {
  loading.value = true
  try {
    const res = await listDatasources({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      keyword: searchKeyword.value || undefined,
      type: filterType.value || undefined,
      status: filterStatus.value || undefined
    })
    datasourceList.value = res.data.records
    pagination.total = res.data.total
  } catch (error) {
    ElMessage.error('加载数据源列表失败')
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  pagination.pageNum = 1
  loadDatasources()
}

// 新建数据源
const handleCreate = () => {
  dialogTitle.value = '新建数据源'
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

// 编辑数据源
const handleEdit = (row: Datasource) => {
  dialogTitle.value = '编辑数据源'
  isEdit.value = true
  currentId.value = row.id
  Object.assign(formData, {
    name: row.name,
    type: row.type,
    host: row.host,
    port: row.port,
    databaseName: row.databaseName,
    username: row.username,
    password: '', // 不回显密码
    sslEnabled: row.sslEnabled,
    description: row.description,
    status: row.status
  })
  dialogVisible.value = true
}

// 删除数据源
const handleDelete = async (row: Datasource) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除数据源"${row.name}"吗？此操作不可恢复。`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await deleteDatasource(row.id)
    ElMessage.success('删除成功')
    loadDatasources()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '删除失败')
    }
  }
}

// 测试连接
const handleTest = async (row: Datasource) => {
  const loadingMsg = ElMessage({
    message: '正在测试连接...',
    type: 'info',
    duration: 0
  })
  try {
    const res = await testDatasourceConnection(row.id)
    loadingMsg.close()

    // res 是 {code: 200, data: {success: true, responseTime: 123}}
    if (res.code === 200 && res.data?.success) {
      ElMessage.success({
        message: `连接成功！响应时间: ${res.data.responseTime}ms`,
        duration: 3000
      })
    } else {
      ElMessage.error({
        message: `连接失败: ${res.data?.message || res.message}`,
        duration: 5000
      })
    }
    loadDatasources() // 刷新列表以更新检查状态
  } catch (error: any) {
    loadingMsg.close()
    ElMessage.error(error.message || '测试连接失败')
  }
}

// 测试新连接
const handleTestConnection = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
    testing.value = true

    const res = isEdit.value
      ? await testDatasourceConnection(currentId.value)
      : await testNewDatasourceConnection(formData)

    // res 是 {code: 200, data: {success: true, responseTime: 123, version: '1.0'}}
    if (res.code === 200 && res.data?.success) {
      ElMessage.success({
        message: `连接成功！响应时间: ${res.data.responseTime}ms${res.data.version ? `，版本: ${res.data.version}` : ''}`,
        duration: 3000
      })
    } else {
      ElMessage.error({
        message: `连接失败: ${res.data?.message || res.message}`,
        duration: 5000
      })
    }
  } catch (error: any) {
    if (error !== false) { // 表单验证失败
      ElMessage.error(error.message || '测试连接失败')
    }
  } finally {
    testing.value = false
  }
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
    submitting.value = true

    if (isEdit.value) {
      await updateDatasource(currentId.value, formData)
      ElMessage.success('更新成功')
    } else {
      await createDatasource(formData)
      ElMessage.success('创建成功')
    }

    dialogVisible.value = false
    loadDatasources()
  } catch (error: any) {
    if (error !== false) {
      ElMessage.error(error.message || '操作失败')
    }
  } finally {
    submitting.value = false
  }
}

// 重置表单
const resetForm = () => {
  Object.assign(formData, {
    name: '',
    type: '',
    host: '',
    port: 8123,
    databaseName: '',
    username: '',
    password: '',
    sslEnabled: false,
    description: '',
    status: 'active'
  })
  formRef.value?.clearValidate()
}

// 对话框关闭
const handleDialogClose = () => {
  resetForm()
}

// 工具函数
const getTypeLabel = (type: string) => {
  const labels: Record<string, string> = {
    clickhouse: 'ClickHouse',
    postgresql: 'PostgreSQL',
    mysql: 'MySQL',
    elasticsearch: 'Elasticsearch',
    loki: 'Loki'
  }
  return labels[type] || type
}

const getTypeTagType = (type: string) => {
  const types: Record<string, any> = {
    clickhouse: 'primary',
    postgresql: 'success',
    mysql: 'warning',
    elasticsearch: 'info',
    loki: 'danger'
  }
  return types[type] || ''
}

const getStatusLabel = (status: string) => {
  const labels: Record<string, string> = {
    active: '活跃',
    inactive: '停用',
    error: '错误'
  }
  return labels[status] || status
}

const getStatusTagType = (status: string) => {
  const types: Record<string, any> = {
    active: 'success',
    inactive: 'info',
    error: 'danger'
  }
  return types[status] || ''
}

const formatTime = (time: string) => {
  return new Date(time).toLocaleString('zh-CN')
}

onMounted(() => {
  loadDatasources()
})
</script>

<style scoped lang="scss">
@use '@/assets/styles/macos.scss' as macos;

.datasource-container {
  @include macos.macos-page-container;
}

.page-header {
  @include macos.macos-page-header;

  .page-title {
    @include macos.macos-page-title;
  }
}

.content-card {
  @include macos.macos-content-card;
}

.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-bar {
  display: flex;
  align-items: center;
}
</style>
