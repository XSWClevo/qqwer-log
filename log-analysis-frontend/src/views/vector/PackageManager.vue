<template>
  <AppLayout>
    <div class="package-manager-container">
      <el-card shadow="never" class="page-header">
        <div class="header-content">
          <div>
            <h2>安装包管理</h2>
            <p class="subtitle">管理 Vector 和 Agent 的安装包版本</p>
          </div>
          <div class="header-actions">
            <el-button type="primary" @click="showUploadDialog = true">
              <el-icon><Upload /></el-icon>
              上传安装包
            </el-button>
          </div>
        </div>
      </el-card>

      <!-- 筛选器 -->
      <el-card shadow="never" class="filter-card">
        <el-alert type="info" :closable="false" style="margin-bottom: 16px;">
          Bundle 包含 Vector Agent 和 Vector，升级时会同时更新两者。
        </el-alert>
      </el-card>

      <!-- 安装包列表 -->
      <el-card shadow="never">
        <el-table :data="packages" v-loading="loading" stripe>
          <el-table-column prop="packageType" label="类型" width="120">
            <template #default>
              <el-tag type="warning">Bundle</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="version" label="版本" width="120">
            <template #default="{ row }">
              {{ row.version }}
              <el-tag v-if="row.isLatest" type="success" size="small" style="margin-left: 4px;">最新</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="osType" label="系统" width="100">
            <template #default="{ row }">
              {{ row.osType === 'darwin' ? 'macOS' : 'Linux' }}
            </template>
          </el-table-column>
          <el-table-column prop="arch" label="架构" width="100" />
          <el-table-column prop="fileSize" label="大小" width="120">
            <template #default="{ row }">
              {{ formatFileSize(row.fileSize) }}
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="上传时间" width="180">
            <template #default="{ row }">
              {{ formatDateTime(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column prop="changelog" label="更新日志" min-width="200" show-overflow-tooltip />
          <el-table-column label="操作" width="180" fixed="right" align="center">
            <template #default="{ row }">
              <div style="display: flex; gap: 8px; justify-content: center; align-items: center;">
                <el-button size="small" type="primary" @click="downloadPackage(row)">
                  <el-icon><Download /></el-icon>
                  下载
                </el-button>
                <el-button size="small" type="danger" link @click="deletePackage(row)">
                  <el-icon><Delete /></el-icon>
                  删除
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 上传对话框 -->
      <el-dialog v-model="showUploadDialog" title="上传安装包" width="500px">
        <el-form :model="uploadForm" label-width="100px">
          <el-form-item label="包类型">
            <el-input value="Bundle (Agent+Vector)" disabled />
            <input type="hidden" v-model="uploadForm.packageType" />
          </el-form-item>
          <el-form-item label="版本号" required>
            <el-input v-model="uploadForm.version" placeholder="如: 1.0.0" />
          </el-form-item>
          <el-form-item label="操作系统" required>
            <el-select v-model="uploadForm.osType" style="width: 100%">
              <el-option label="macOS" value="darwin" />
              <el-option label="Linux" value="linux" disabled />
            </el-select>
          </el-form-item>
          <el-form-item label="CPU架构">
            <el-select v-model="uploadForm.arch" style="width: 100%">
              <el-option label="arm64 (Apple Silicon)" value="arm64" />
              <el-option label="amd64 (x86_64)" value="amd64" disabled />
            </el-select>
          </el-form-item>
          <el-form-item label="更新日志">
            <el-input v-model="uploadForm.changelog" type="textarea" :rows="3" placeholder="本次更新内容..." />
          </el-form-item>
          <el-form-item label="安装包" required>
            <el-upload
              ref="uploadRef"
              :auto-upload="false"
              :limit="1"
              :on-change="handleFileChange"
              :on-remove="handleFileRemove"
            >
              <el-button type="primary">选择文件</el-button>
              <template #tip>
                <div class="el-upload__tip">上传 Bundle 包（.tar.gz 格式，包含 Agent 和 Vector）</div>
              </template>
            </el-upload>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showUploadDialog = false">取消</el-button>
          <el-button type="primary" :loading="uploading" @click="submitUpload">上传</el-button>
        </template>
      </el-dialog>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type UploadInstance, type UploadFile } from 'element-plus'
import { Upload, Download, Delete } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import AppLayout from '@/components/layout/AppLayout.vue'
import request from '@/utils/request'

interface VectorPackage {
  id: string
  packageType: string
  version: string
  osType: string
  arch: string
  fileName: string
  fileSize: number
  checksum: string
  changelog: string
  isLatest: boolean
  createdAt: string
}

const loading = ref(false)
const uploading = ref(false)
const packages = ref<VectorPackage[]>([])
const showUploadDialog = ref(false)
const uploadRef = ref<UploadInstance>()
const selectedFile = ref<File | null>(null)

const filters = reactive({
  packageType: 'vector-agent-bundle'
})

const uploadForm = reactive({
  packageType: 'vector-agent-bundle',
  version: '',
  osType: 'darwin',
  arch: 'arm64',
  changelog: ''
})

const formatDateTime = (time: string) => {
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

const formatFileSize = (bytes: number) => {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

const fetchPackages = async () => {
  loading.value = true
  try {
    const data: any = await request.get('/api/vector/packages/list', {
      params: { packageType: filters.packageType || undefined }
    })
    if (data.code === 200) {
      packages.value = data.data || []
    }
  } catch (error) {
    ElMessage.error('加载安装包列表失败')
  } finally {
    loading.value = false
  }
}

const handleFileChange = (file: UploadFile) => {
  selectedFile.value = file.raw || null
}

const handleFileRemove = () => {
  selectedFile.value = null
}

const submitUpload = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请选择安装包文件')
    return
  }
  if (!uploadForm.version) {
    ElMessage.warning('请输入版本号')
    return
  }

  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', selectedFile.value)
    formData.append('packageType', uploadForm.packageType)
    formData.append('version', uploadForm.version)
    formData.append('osType', uploadForm.osType)
    formData.append('arch', uploadForm.arch)
    formData.append('changelog', uploadForm.changelog || '')

    const data: any = await request.post('/api/vector/packages/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })

    if (data.code === 200) {
      ElMessage.success('上传成功')
      showUploadDialog.value = false
      resetUploadForm()
      fetchPackages()
    } else {
      ElMessage.error(data.message || '上传失败')
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

const resetUploadForm = () => {
  uploadForm.version = ''
  uploadForm.changelog = ''
  selectedFile.value = null
  uploadRef.value?.clearFiles()
}

const downloadPackage = (pkg: VectorPackage) => {
  window.open(`/api/vector/packages/download/${pkg.id}`, '_blank')
}

const deletePackage = (pkg: VectorPackage) => {
  ElMessageBox.confirm(
    `确定要删除 ${pkg.packageType} ${pkg.version} (${pkg.osType}) 吗？`,
    '确认删除',
    { type: 'warning' }
  ).then(async () => {
    try {
      const data: any = await request.delete(`/api/vector/packages/${pkg.id}`)
      if (data.code === 200) {
        ElMessage.success('删除成功')
        fetchPackages()
      } else {
        ElMessage.error(data.message || '删除失败')
      }
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || '删除失败')
    }
  })
}

onMounted(() => {
  fetchPackages()
})
</script>

<style scoped lang="scss">
.package-manager-container {
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
</style>
