<template>
  <AppLayout>
    <div class="vector-machine-container">
    <el-card shadow="never" class="page-header">
      <div class="header-content">
        <div>
          <h2>主机管理</h2>
          <p class="subtitle">管理 Vector 日志收集器部署的服务器（Agent 模式）</p>
        </div>
        <div class="header-actions">
          <el-button type="success" @click="showTokenDialog = true">
            <el-icon><Key /></el-icon>
            生成 Agent Token
          </el-button>
          <el-button type="primary" @click="showAddDialog = true">
            <el-icon><Plus /></el-icon>
            手动添加主机
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 筛选器 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true">
        <el-form-item label="关键词">
          <el-input
            v-model="filters.keyword"
            placeholder="搜索主机名、IP"
            clearable
            @clear="fetchMachines"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部状态" clearable>
            <el-option label="在线" value="online" />
            <el-option label="离线" value="offline" />
            <el-option label="错误" value="error" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchMachines">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 主机列表 -->
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="5" animated />
    </div>
    <div v-else-if="machines.length === 0" class="empty-container">
      <el-empty description="暂无主机">
        <el-button type="success" @click="showTokenDialog = true">生成 Token 添加主机</el-button>
      </el-empty>
    </div>
    <div v-else class="machine-grid">
      <el-card
        v-for="machine in machines"
        :key="machine.id"
        shadow="hover"
        class="machine-card"
      >
        <div class="machine-header">
          <div class="machine-info">
            <el-icon class="machine-icon" :size="24"><Monitor /></el-icon>
            <div>
              <div class="machine-name">{{ machine.name }}</div>
              <div class="machine-hostname">{{ machine.hostname }}</div>
            </div>
          </div>
          <el-tag
            :type="getStatusType(machine.status)"
            size="small"
            effect="plain"
          >
            {{ getStatusText(machine.status) }}
          </el-tag>
        </div>

        <el-divider />

        <div class="machine-details">
          <div class="detail-item">
            <span class="label">Machine ID</span>
            <span class="value machine-id">
              {{ machine.id }}
              <el-icon class="copy-icon" @click="copyMachineId(machine.id)">
                <CopyDocument />
              </el-icon>
            </span>
          </div>
          <div class="detail-item">
            <span class="label">IP 地址</span>
            <span class="value">{{ machine.ipAddress }}</span>
          </div>
          <div class="detail-item">
            <span class="label">管理方式</span>
            <el-tag size="small" :type="machine.managementMethod === 'agent' ? 'success' : 'info'">
              {{ machine.managementMethod === 'agent' ? 'Agent' : machine.managementMethod }}
            </el-tag>
          </div>
          <div v-if="machine.vectorVersion" class="detail-item">
            <span class="label">Vector 版本</span>
            <span class="value">{{ machine.vectorVersion }}</span>
          </div>
          <div class="detail-item">
            <span class="label">Bundle 最新版</span>
            <span class="value">{{ getLatestAgentVersion(machine) }}</span>
          </div>
          <div v-if="machine.lastHeartbeat" class="detail-item">
            <span class="label">最后心跳</span>
            <span class="value" :class="{ 'text-warning': isHeartbeatStale(machine.lastHeartbeat) }">
              {{ formatTime(machine.lastHeartbeat) }}
            </span>
          </div>
          <div v-if="machine.osType" class="detail-item">
            <span class="label">操作系统</span>
            <span class="value">{{ machine.osType }}</span>
          </div>
        </div>

        <el-divider />

        <div class="machine-actions">
          <el-dropdown trigger="click" @command="(cmd: string) => handleCommand(machine, cmd)">
            <el-button size="small" type="success">
              <el-icon><VideoPlay /></el-icon>
              控制
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="start_vector">
                  <el-icon><VideoPlay /></el-icon>
                  启动 Vector
                </el-dropdown-item>
                <el-dropdown-item command="stop_vector">
                  <el-icon><VideoPause /></el-icon>
                  停止 Vector
                </el-dropdown-item>
                <el-dropdown-item command="restart_vector">
                  <el-icon><RefreshRight /></el-icon>
                  重启 Vector
                </el-dropdown-item>
                <el-dropdown-item command="reload_vector" divided>
                  <el-icon><Refresh /></el-icon>
                  重载配置
                </el-dropdown-item>
                <el-dropdown-item command="upgrade_agent" divided>
                  <el-icon><Upload /></el-icon>
                  升级 Bundle
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button size="small" @click="viewMachineMetrics(machine)">
            <el-icon><DataLine /></el-icon>
            监控
          </el-button>
        </div>
        <div class="machine-actions-row">
          <el-button size="small" @click="viewMachine(machine)">
            <el-icon><View /></el-icon>
            详情
          </el-button>
          <el-button size="small" type="primary" @click="editMachine(machine)">
            <el-icon><Edit /></el-icon>
            编辑
          </el-button>
          <el-button
            size="small"
            type="danger"
            plain
            @click="deleteMachine(machine)"
          >
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
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="fetchMachines"
        @current-change="fetchMachines"
      />
    </div>

    <!-- 生成 Token 对话框 -->
    <el-dialog
      v-model="showTokenDialog"
      title="生成 Agent Token"
      width="650px"
    >
      <el-alert type="info" :closable="false" style="margin-bottom: 16px;">
        <template #title>
          <span>Agent 模式说明</span>
        </template>
        <p>Agent 会自动注册到服务器，无需手动添加主机。只需在目标机器上安装 Agent 并配置 Token 即可。</p>
      </el-alert>

      <div v-if="!generatedToken" class="token-generate">
        <el-button type="primary" size="large" @click="generateToken" :loading="generatingToken">
          <el-icon><Key /></el-icon>
          点击生成新 Token
        </el-button>
      </div>

      <div v-else class="token-result">
        <el-form label-width="100px">
          <el-form-item label="Agent Token">
            <el-input v-model="generatedToken" readonly>
              <template #append>
                <el-button @click="copyToken">
                  <el-icon><CopyDocument /></el-icon>
                </el-button>
              </template>
            </el-input>
          </el-form-item>
        </el-form>

        <el-divider>一键安装命令</el-divider>

        <div class="install-command">
          <pre>{{ installCommand }}</pre>
          <el-button type="primary" size="small" @click="copyInstallCommand">
            <el-icon><CopyDocument /></el-icon>
            复制命令
          </el-button>
        </div>

        <el-alert type="success" :closable="false" style="margin-top: 16px;">
          <template #title>安装说明</template>
          <p>1. 在目标机器上以 root 权限执行上述命令</p>
          <p>2. 安装包含 Vector 和 Agent，无需额外配置</p>
          <p>3. 安装完成后机器会自动注册到本页面</p>
        </el-alert>

        <el-collapse style="margin-top: 16px;">
          <el-collapse-item title="安装后常用命令" name="commands">
            <div class="manual-steps">
              <pre># 查看服务状态
systemctl status vector-agent
systemctl status vector

# 查看 Vector 版本
vector --version

# 查看日志
journalctl -u vector-agent -f
journalctl -u vector -f

# 重启服务
systemctl restart vector-agent
systemctl restart vector</pre>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>

      <template #footer>
        <el-button @click="showTokenDialog = false; generatedToken = ''">关闭</el-button>
        <el-button v-if="generatedToken" type="primary" @click="generatedToken = ''">
          重新生成
        </el-button>
      </template>
    </el-dialog>

    <!-- 添加/编辑对话框 -->
    <el-dialog
      v-model="showAddDialog"
      :title="editingMachine ? '编辑主机' : '手动添加主机'"
      width="600px"
    >
      <el-alert v-if="!editingMachine" type="warning" :closable="false" style="margin-bottom: 16px;">
        推荐使用 Agent 方式自动注册主机，手动添加仅用于特殊场景。
      </el-alert>

      <el-form
        ref="machineFormRef"
        :model="machineForm"
        :rules="machineRules"
        label-width="120px"
      >
        <el-form-item label="主机名称" prop="name">
          <el-input v-model="machineForm.name" placeholder="Web Server 01" />
        </el-form-item>
        <el-form-item label="主机名" prop="hostname">
          <el-input v-model="machineForm.hostname" placeholder="web-01.example.com" />
        </el-form-item>
        <el-form-item label="IP 地址" prop="ipAddress">
          <el-input v-model="machineForm.ipAddress" placeholder="10.0.1.10" />
        </el-form-item>
        <el-form-item label="操作系统">
          <el-select v-model="machineForm.osType" style="width: 100%">
            <el-option label="Linux" value="linux" />
            <el-option label="macOS" value="darwin" />
            <el-option label="Windows" value="windows" />
          </el-select>
        </el-form-item>
        <el-form-item label="Agent Token">
          <el-input v-model="machineForm.agentToken" placeholder="可选，用于关联 Agent" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitMachine">
          {{ editingMachine ? '更新' : '添加' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 主机详情抽屉 -->
    <el-drawer v-model="showDetailDrawer" title="主机详情" size="500px">
      <template v-if="selectedMachine">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="Machine ID">
            <div class="machine-id-row">
              <code>{{ selectedMachine.id }}</code>
              <el-button size="small" text @click="copyMachineId(selectedMachine.id)">
                <el-icon><CopyDocument /></el-icon>
                复制
              </el-button>
            </div>
          </el-descriptions-item>
          <el-descriptions-item label="主机名称">{{ selectedMachine.name }}</el-descriptions-item>
          <el-descriptions-item label="主机名">{{ selectedMachine.hostname }}</el-descriptions-item>
          <el-descriptions-item label="IP 地址">{{ selectedMachine.ipAddress }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusType(selectedMachine.status)">
              {{ getStatusText(selectedMachine.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="管理方式">{{ selectedMachine.managementMethod }}</el-descriptions-item>
          <el-descriptions-item label="操作系统">{{ selectedMachine.osType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="Vector 版本">{{ selectedMachine.vectorVersion || '-' }}</el-descriptions-item>
          <el-descriptions-item label="Bundle 最新版">{{ getLatestAgentVersion(selectedMachine) }}</el-descriptions-item>
          <el-descriptions-item label="最后心跳">
            {{ selectedMachine.lastHeartbeat ? formatTime(selectedMachine.lastHeartbeat) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(selectedMachine.createdAt) }}</el-descriptions-item>
        </el-descriptions>

        <el-divider>部署历史</el-divider>
        <div v-if="deployments.length === 0" class="empty-deployments">
          暂无部署记录
        </div>
        <el-timeline v-else>
          <el-timeline-item
            v-for="dep in deployments"
            :key="dep.id"
            :type="getDeployStatusType(dep.status)"
            :timestamp="formatDateTime(dep.createdAt)"
          >
            <div class="deployment-item">
              <span>配置版本: {{ dep.configVersion }}</span>
              <el-tag size="small" :type="getDeployStatusType(dep.status)">
                {{ getDeployStatusText(dep.status) }}
              </el-tag>
            </div>
            <div v-if="dep.errorMessage" class="error-message">
              {{ dep.errorMessage }}
            </div>
          </el-timeline-item>
        </el-timeline>

        <el-divider>命令历史</el-divider>
        <div v-if="commands.length === 0" class="empty-deployments">
          暂无命令记录
        </div>
        <el-timeline v-else>
          <el-timeline-item
            v-for="cmd in commands"
            :key="cmd.id"
            :type="getCommandStatusType(cmd.status)"
            :timestamp="formatDateTime(cmd.createdAt)"
          >
            <div class="deployment-item">
              <span>{{ getCommandTypeName(cmd.commandType) }}</span>
              <el-tag size="small" :type="getCommandStatusType(cmd.status)">
                {{ getCommandStatusText(cmd.status) }}
              </el-tag>
            </div>
            <div v-if="cmd.errorMessage" class="error-message">
              {{ cmd.errorMessage }}
            </div>
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-drawer>

    <!-- 机器监控抽屉 -->
    <MachineDetailDrawer
      v-model="showMetricsDrawer"
      :machine="metricsMachine"
    />
  </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Monitor, View, Edit, Delete, Key, CopyDocument, VideoPlay, VideoPause, RefreshRight, Refresh, ArrowDown, Upload, DataLine } from '@element-plus/icons-vue'
import axios from 'axios'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'
import AppLayout from '@/components/layout/AppLayout.vue'
import MachineDetailDrawer from './components/MachineDetailDrawer.vue'

dayjs.extend(relativeTime)
dayjs.locale('zh-cn')

interface VectorMachine {
  id: string
  name: string
  hostname: string
  ipAddress: string
  status: 'online' | 'offline' | 'error'
  managementMethod: string
  osType?: string
  vectorVersion?: string
  agentVersion?: string
  agentToken?: string
  lastHeartbeat?: string
  createdAt: string
}

interface Deployment {
  id: string
  configVersion: string
  status: string
  errorMessage?: string
  createdAt: string
}

interface Command {
  id: string
  commandType: string
  status: string
  errorMessage?: string
  createdAt: string
}

const loading = ref(false)
const machines = ref<VectorMachine[]>([])
const total = ref(0)
const showAddDialog = ref(false)
const showTokenDialog = ref(false)
const showDetailDrawer = ref(false)
const showMetricsDrawer = ref(false)
const metricsMachine = ref<{ id: string; name: string } | null>(null)
const submitting = ref(false)
const generatingToken = ref(false)
const editingMachine = ref<VectorMachine | null>(null)
const selectedMachine = ref<VectorMachine | null>(null)
const machineFormRef = ref<FormInstance>()
const generatedToken = ref('')
const deployments = ref<Deployment[]>([])
const commands = ref<Command[]>([])

// 最新包版本
const latestAgentVersion = ref<Record<string, string>>({})  // key: osType-arch
const latestVectorVersion = ref<Record<string, string>>({})

// 服务器地址（从当前页面获取）
const serverUrl = computed(() => {
  return `${window.location.protocol}//${window.location.hostname}:8080`
})

// 安装命令（一键安装）
const installCommand = computed(() => {
  return `curl -fsSL "${serverUrl.value}/api/vector/agents/install-script?token=${generatedToken.value}" | sudo bash`
})

const filters = reactive({
  keyword: '',
  status: ''
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10
})

const machineForm = reactive({
  name: '',
  hostname: '',
  ipAddress: '',
  osType: 'linux',
  agentToken: ''
})

const machineRules: FormRules = {
  name: [{ required: true, message: '请输入主机名称', trigger: 'blur' }],
  hostname: [{ required: true, message: '请输入主机名', trigger: 'blur' }],
  ipAddress: [
    { required: true, message: '请输入IP地址', trigger: 'blur' },
    { pattern: /^(\d{1,3}\.){3}\d{1,3}$/, message: '请输入有效的IP地址', trigger: 'blur' }
  ]
}

const getStatusType = (status: string) => {
  const map: Record<string, any> = {
    online: 'success',
    offline: 'info',
    error: 'danger'
  }
  return map[status] || 'info'
}

const getStatusText = (status: string) => {
  const map: Record<string, string> = {
    online: '在线',
    offline: '离线',
    error: '错误'
  }
  return map[status] || status
}

const getDeployStatusType = (status: string) => {
  const map: Record<string, any> = {
    success: 'success',
    failed: 'danger',
    pending: 'warning',
    deploying: 'primary'
  }
  return map[status] || 'info'
}

const getDeployStatusText = (status: string) => {
  const map: Record<string, string> = {
    success: '成功',
    failed: '失败',
    pending: '等待中',
    deploying: '部署中'
  }
  return map[status] || status
}

const getCommandStatusType = (status: string) => {
  const map: Record<string, any> = {
    success: 'success',
    failed: 'danger',
    pending: 'warning',
    executing: 'primary'
  }
  return map[status] || 'info'
}

const getCommandStatusText = (status: string) => {
  const map: Record<string, string> = {
    success: '成功',
    failed: '失败',
    pending: '等待中',
    executing: '执行中'
  }
  return map[status] || status
}

const getCommandTypeName = (type: string) => {
  const map: Record<string, string> = {
    start_vector: '启动 Vector',
    stop_vector: '停止 Vector',
    restart_vector: '重启 Vector',
    reload_vector: '重载配置',
    upgrade_vector: '升级 Vector',
    upgrade_agent: '升级 Agent'
  }
  return map[type] || type
}

const formatTime = (time: string) => {
  return dayjs(time).fromNow()
}

const formatDateTime = (time: string) => {
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

const isHeartbeatStale = (time: string) => {
  return dayjs().diff(dayjs(time), 'minute') > 2
}

const fetchMachines = async () => {
  loading.value = true
  try {
    const { data } = await axios.get('/api/vector/machines/page', {
      params: {
        pageNum: pagination.pageNum,
        pageSize: pagination.pageSize,
        keyword: filters.keyword || undefined,
        status: filters.status || undefined
      }
    })
    if (data.code === 200 && data.data) {
      machines.value = data.data.records || []
      total.value = data.data.total || 0
    }
  } catch (error) {
    ElMessage.error('加载主机列表失败')
  } finally {
    loading.value = false
  }
}

// 获取最新包版本
const fetchLatestVersions = async () => {
  try {
    const { data } = await axios.get('/api/vector/packages/list')
    if (data.code === 200 && data.data) {
      // 按类型和平台分组，取最新版本
      for (const pkg of data.data) {
        if (pkg.isLatest) {
          const key = `${pkg.osType}-${pkg.arch}`
          if (pkg.packageType === 'vector-agent' || pkg.packageType === 'vector-agent-bundle') {
            // Bundle 和 Agent 都算作 Agent 版本
            latestAgentVersion.value[key] = pkg.version
          }
          if (pkg.packageType === 'vector') {
            latestVectorVersion.value[key] = pkg.version
          }
        }
      }
    }
  } catch (error) {
    console.error('获取最新版本失败:', error)
  }
}

// 获取机器对应的最新 Agent 版本
const getLatestAgentVersion = (machine: VectorMachine) => {
  const osType = machine.osType || 'linux'
  const arch = 'amd64'  // 默认架构
  return latestAgentVersion.value[`${osType}-${arch}`] || '-'
}

// 获取机器对应的最新 Vector 版本
const getLatestVectorVersion = (machine: VectorMachine) => {
  const osType = machine.osType || 'linux'
  const arch = 'amd64'  // 默认架构
  return latestVectorVersion.value[`${osType}-${arch}`] || '-'
}

const resetFilters = () => {
  filters.keyword = ''
  filters.status = ''
  pagination.pageNum = 1
  fetchMachines()
}

const generateToken = async () => {
  generatingToken.value = true
  try {
    const { data } = await axios.post('/api/vector/machines/generate-token')
    if (data.code === 200 && data.data) {
      generatedToken.value = data.data.token || ''
      ElMessage.success('Token 生成成功')
    }
  } catch (error) {
    ElMessage.error('生成 Token 失败')
  } finally {
    generatingToken.value = false
  }
}

const copyToken = async () => {
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(generatedToken.value)
      ElMessage.success('Token 已复制')
    } else {
      // 降级方案：使用传统方法
      fallbackCopyToClipboard(generatedToken.value)
      ElMessage.success('Token 已复制')
    }
  } catch (err) {
    console.error('复制失败:', err)
    ElMessage.error('复制失败，请手动复制')
  }
}

const copyInstallCommand = async () => {
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(installCommand.value)
      ElMessage.success('安装命令已复制')
    } else {
      fallbackCopyToClipboard(installCommand.value)
      ElMessage.success('安装命令已复制')
    }
  } catch (err) {
    console.error('复制失败:', err)
    ElMessage.error('复制失败，请手动复制')
  }
}

const copyMachineId = async (machineId: string) => {
  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(machineId)
      ElMessage.success('Machine ID 已复制')
    } else {
      fallbackCopyToClipboard(machineId)
      ElMessage.success('Machine ID 已复制')
    }
  } catch (err) {
    console.error('复制失败:', err)
    ElMessage.error('复制失败，请手动复制')
  }
}

// 降级复制方案（兼容非 HTTPS 环境）
const fallbackCopyToClipboard = (text: string) => {
  const textArea = document.createElement('textarea')
  textArea.value = text
  textArea.style.position = 'fixed'
  textArea.style.left = '-999999px'
  textArea.style.top = '-999999px'
  document.body.appendChild(textArea)
  textArea.focus()
  textArea.select()

  try {
    document.execCommand('copy')
  } catch (err) {
    console.error('降级复制失败:', err)
    throw err
  } finally {
    document.body.removeChild(textArea)
  }
}

const viewMachine = async (machine: VectorMachine) => {
  selectedMachine.value = machine
  showDetailDrawer.value = true
  
  // 加载部署历史
  try {
    const { data } = await axios.get(`/api/vector/deployments/machine/${machine.id}`)
    if (data.code === 200) {
      deployments.value = data.data || []
    }
  } catch (error) {
    console.error('加载部署历史失败:', error)
  }
  
  // 加载命令历史
  try {
    const { data } = await axios.get(`/api/vector/agents/commands/${machine.id}`)
    if (data.code === 200) {
      commands.value = data.data || []
    }
  } catch (error) {
    console.error('加载命令历史失败:', error)
  }
}

const viewMachineMetrics = (machine: VectorMachine) => {
  metricsMachine.value = { id: machine.id, name: machine.name }
  showMetricsDrawer.value = true
}

const editMachine = (machine: VectorMachine) => {
  editingMachine.value = machine
  Object.assign(machineForm, {
    name: machine.name,
    hostname: machine.hostname,
    ipAddress: machine.ipAddress,
    osType: machine.osType || 'linux',
    agentToken: machine.agentToken || ''
  })
  showAddDialog.value = true
}

const submitMachine = async () => {
  await machineFormRef.value?.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      if (editingMachine.value) {
        await axios.put(`/api/vector/machines/${editingMachine.value.id}`, machineForm)
        ElMessage.success('更新主机成功')
      } else {
        await axios.post('/api/vector/machines', {
          ...machineForm,
          managementMethod: 'agent'
        })
        ElMessage.success('添加主机成功')
      }
      showAddDialog.value = false
      resetForm()
      fetchMachines()
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || '操作失败')
    } finally {
      submitting.value = false
    }
  })
}

const deleteMachine = (machine: VectorMachine) => {
  ElMessageBox.confirm(
    `确定要删除主机"${machine.name}"吗？此操作不可恢复。`,
    '确认删除',
    {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    }
  ).then(async () => {
    try {
      await axios.delete(`/api/vector/machines/${machine.id}`)
      ElMessage.success('删除主机成功')
      fetchMachines()
    } catch (error: any) {
      ElMessage.error(error.response?.data?.message || '删除失败')
    }
  })
}

const sendCommand = async (machine: VectorMachine, commandType: string) => {
  const commandNames: Record<string, string> = {
    start_vector: '启动 Vector',
    stop_vector: '停止 Vector',
    restart_vector: '重启 Vector',
    reload_vector: '重载配置'
  }
  
  if (machine.status !== 'online') {
    ElMessage.warning('主机不在线，无法发送命令')
    return
  }
  
  try {
    const { data } = await axios.post('/api/vector/agents/send-command', {
      machineId: machine.id,
      commandType
    })
    
    if (data.code === 200) {
      ElMessage.success(`命令已发送: ${commandNames[commandType]}`)
    } else {
      ElMessage.error(data.message || '发送命令失败')
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '发送命令失败')
  }
}

const handleCommand = (machine: VectorMachine, command: string) => {
  if (command.startsWith('upgrade_')) {
    sendUpgradeCommand(machine, command)
  } else {
    sendCommand(machine, command)
  }
}

const sendUpgradeCommand = async (machine: VectorMachine, commandType: string) => {
  // Bundle 模式：升级 Agent 即升级整个 Bundle（包含 Vector）
  const packageType = 'vector-agent-bundle'
  
  if (machine.status !== 'online') {
    ElMessage.warning('主机不在线，无法发送升级命令')
    return
  }
  
  try {
    // 先检查是否有可用的升级包
    const { data: pkgData } = await axios.get('/api/vector/packages/latest', {
      params: {
        packageType,
        osType: machine.osType || 'darwin',
        arch: 'arm64'
      }
    })
    
    if (pkgData.code !== 200 || !pkgData.data) {
      ElMessage.warning('没有找到 Bundle 安装包，请先上传')
      return
    }
    
    const pkg = pkgData.data
    
    // 确认升级
    await ElMessageBox.confirm(
      `确定要将 ${machine.name} 的 Bundle 升级到 ${pkg.version} 吗？\n（包含 Agent 和 Vector）`,
      '确认升级',
      {
        type: 'warning',
        confirmButtonText: '确定升级',
        cancelButtonText: '取消'
      }
    )
    
    // 发送升级命令
    const { data } = await axios.post('/api/vector/packages/upgrade', {
      machineId: machine.id,
      packageType,
      targetVersion: pkg.version
    })
    
    if (data.code === 200) {
      ElMessage.success(`升级命令已发送: Bundle -> ${pkg.version}`)
    } else {
      ElMessage.error(data.message || '发送升级命令失败')
    }
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '发送升级命令失败')
    }
  }
}

const resetForm = () => {
  editingMachine.value = null
  machineFormRef.value?.resetFields()
  Object.assign(machineForm, {
    name: '',
    hostname: '',
    ipAddress: '',
    osType: 'linux',
    agentToken: ''
  })
}

onMounted(() => {
  fetchMachines()
  fetchLatestVersions()
})
</script>

<style scoped lang="scss">
.vector-machine-container {
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

    .header-actions {
      display: flex;
      gap: 12px;
    }
  }
}

.filter-card {
  margin-bottom: 20px;
}

.loading-container {
  padding: 20px;
}

.empty-container {
  padding: 40px;
  text-align: center;
}

.machine-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 20px;
  margin-bottom: 20px;
}

.machine-card {
  transition: all 0.3s ease;

  &:hover {
    transform: translateY(-2px);
  }

  .machine-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 16px;

    .machine-info {
      display: flex;
      gap: 12px;

      .machine-icon {
        color: var(--el-color-primary);
      }

      .machine-name {
        font-size: 16px;
        font-weight: 600;
        margin-bottom: 4px;
      }

      .machine-hostname {
        font-size: 13px;
        color: var(--el-text-color-secondary);
      }
    }
  }

  .machine-details {
    .detail-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 8px 0;
      font-size: 14px;

      .label {
        color: var(--el-text-color-secondary);
      }

      .value {
        font-family: 'Monaco', 'Consolas', monospace;

        &.text-warning {
          color: var(--el-color-warning);
        }

        &.machine-id {
          display: flex;
          align-items: center;
          gap: 8px;
          font-size: 12px;

          .copy-icon {
            cursor: pointer;
            color: var(--el-color-primary);
            transition: all 0.2s;

            &:hover {
              transform: scale(1.2);
            }
          }
        }
      }
    }
  }

  .machine-actions {
    display: flex;
    gap: 8px;
    margin-bottom: 8px;

    .el-button {
      flex: 1;
    }
  }

  .machine-actions-row {
    display: flex;
    gap: 8px;

    .el-button {
      flex: 1;
    }
  }
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  padding: 20px 0;
}

.token-generate {
  text-align: center;
  padding: 40px;
}

.token-result {
  .install-command {
    background: #1e1e1e;
    border-radius: 8px;
    padding: 16px;
    position: relative;

    pre {
      color: #d4d4d4;
      font-family: 'Monaco', 'Consolas', monospace;
      font-size: 13px;
      margin: 0;
      white-space: pre-wrap;
      word-break: break-all;
    }

    .el-button {
      position: absolute;
      top: 8px;
      right: 8px;
    }
  }

  .manual-steps {
    p {
      margin: 16px 0 8px;
      
      &:first-child {
        margin-top: 0;
      }
    }

    pre {
      background: #f5f7fa;
      padding: 12px;
      border-radius: 4px;
      font-size: 12px;
      overflow-x: auto;
    }
  }
}

.empty-deployments {
  text-align: center;
  color: var(--el-text-color-secondary);
  padding: 20px;
}

.deployment-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.error-message {
  color: var(--el-color-danger);
  font-size: 12px;
  margin-top: 4px;
}

.machine-id-row {
  display: flex;
  align-items: center;
  gap: 12px;

  code {
    flex: 1;
    background: #f5f7fa;
    padding: 4px 8px;
    border-radius: 4px;
    font-size: 12px;
    font-family: 'Monaco', 'Consolas', monospace;
  }
}
</style>
