<template>
  <AppLayout>
    <div class="dashboard-container">
      <!-- Top Bar -->
      <div class="top-bar">
        <div class="top-bar-left">
          <h1 class="page-title">日志监控大屏</h1>
        </div>
        <div class="top-bar-right">
          <el-select v-model="timeRange" class="time-selector" @change="handleTimeRangeChange">
            <el-option label="最近 1 小时" value="1h" />
            <el-option label="最近 6 小时" value="6h" />
            <el-option label="最近 24 小时" value="24h" />
            <el-option label="最近 7 天" value="7d" />
          </el-select>
          <div class="auto-refresh">
            <span class="refresh-label">自动刷新</span>
            <el-switch v-model="autoRefresh" @change="handleAutoRefreshChange" />
            <el-select
              v-if="autoRefresh"
              v-model="refreshInterval"
              class="interval-selector"
              @change="handleIntervalChange"
            >
              <el-option label="5s" :value="5000" />
              <el-option label="10s" :value="10000" />
              <el-option label="30s" :value="30000" />
              <el-option label="60s" :value="60000" />
            </el-select>
          </div>
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-icon><User /></el-icon>
              <span>{{ userInfo }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

    <!-- Main Content -->
    <div class="dashboard-content">
      <!-- First Row: 核心指标卡片 -->
      <el-row :gutter="16" class="dashboard-row">
        <el-col :xs="24" :sm="12" :lg="6">
          <MachineStatusCard :data="machineStatus" :loading="loading" />
        </el-col>
        <el-col :xs="24" :sm="12" :lg="6">
          <LogPipelineCard :data="logPipeline" :loading="loading" />
        </el-col>
        <el-col :xs="24" :sm="12" :lg="6">
          <CoreOverviewCard :data="coreOverview" :loading="loading" />
        </el-col>
        <el-col :xs="24" :sm="12" :lg="6">
          <DatabaseStatusCard :data="databaseStatus" :loading="loading" />
        </el-col>
      </el-row>

      <!-- Second Row: 趋势与分布 -->
      <el-row :gutter="16" class="dashboard-row">
        <el-col :xs="24" :lg="16">
          <LogTrendChart :data="logTrend" :loading="loading" @drill-down="handleDrillDown" />
        </el-col>
        <el-col :xs="24" :lg="8">
          <LevelDistributionChart :data="levelDistribution" :loading="loading" />
        </el-col>
      </el-row>

      <!-- Third Row: 热点排行 -->
      <el-row :gutter="16" class="dashboard-row">
        <el-col :xs="24" :md="8">
          <TopHostsChart :data="topHosts" :loading="loading" />
        </el-col>
        <el-col :xs="24" :md="8">
          <TopAppsChart :data="topApps" :loading="loading" />
        </el-col>
        <el-col :xs="24" :md="8">
          <TopExceptionsCard :data="topExceptions" :loading="loading" />
        </el-col>
      </el-row>

      <!-- Fourth Row: 实时数据流 -->
      <el-row :gutter="16" class="dashboard-row">
        <el-col :span="24">
          <RealtimeLogsTable :data="realtimeLogs" :loading="loading" @row-click="handleLogRowClick" />
        </el-col>
      </el-row>
    </div>

      <!-- Log Detail Dialog -->
      <LogDetailDialog v-model:visible="logDetailVisible" :log="selectedLog" />
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import { User } from '@element-plus/icons-vue'
import AppLayout from '@/components/layout/AppLayout.vue'
import MachineStatusCard from './components/MachineStatusCard.vue'
import LogPipelineCard from './components/LogPipelineCard.vue'
import CoreOverviewCard from './components/CoreOverviewCard.vue'
import DatabaseStatusCard from './components/DatabaseStatusCard.vue'
import LogTrendChart from './components/LogTrendChart.vue'
import LevelDistributionChart from './components/LevelDistributionChart.vue'
import TopHostsChart from './components/TopHostsChart.vue'
import TopAppsChart from './components/TopAppsChart.vue'
import TopExceptionsCard from './components/TopExceptionsCard.vue'
import RealtimeLogsTable from './components/RealtimeLogsTable.vue'
import LogDetailDialog from './components/LogDetailDialog.vue'
import { useDashboardData } from './composables/useDashboardData'
import type { LogRecord } from './types'

const router = useRouter()
const authStore = useAuthStore()

// 状态
const timeRange = ref('1h')
const autoRefresh = ref(true)
const refreshInterval = ref(10000) // 默认 10 秒
let refreshTimer: ReturnType<typeof setInterval> | null = null

// 日志详情弹窗
const logDetailVisible = ref(false)
const selectedLog = ref<LogRecord | null>(null)

// 使用组合式函数获取数据
const {
  loading,
  machineStatus,
  logPipeline,
  coreOverview,
  databaseStatus,
  logTrend,
  levelDistribution,
  topHosts,
  topApps,
  topExceptions,
  realtimeLogs,
  fetchAllData
} = useDashboardData()

// 用户信息
const userInfo = computed(() => authStore.user?.username || '用户')

// 事件处理
const handleTimeRangeChange = () => {
  fetchAllData(timeRange.value)
}

const handleAutoRefreshChange = (val: boolean) => {
  if (val) {
    startAutoRefresh()
  } else {
    stopAutoRefresh()
  }
}

const handleIntervalChange = () => {
  if (autoRefresh.value) {
    startAutoRefresh()
  }
}

const handleCommand = async (command: string) => {
  if (command === 'logout') {
    await authStore.logout()
    router.push('/login')
  }
}

const handleDrillDown = (params: { time: string; level: string }) => {
  console.log('Drill down:', params)
  // 可跳转到日志搜索页面并带上筛选条件
}

const handleLogRowClick = (log: LogRecord) => {
  selectedLog.value = log
  logDetailVisible.value = true
}

// 自动刷新
const startAutoRefresh = () => {
  stopAutoRefresh()
  refreshTimer = setInterval(() => {
    fetchAllData(timeRange.value)
  }, refreshInterval.value)
}

const stopAutoRefresh = () => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

// 生命周期
onMounted(() => {
  fetchAllData(timeRange.value)
  if (autoRefresh.value) {
    startAutoRefresh()
  }
})

onUnmounted(() => {
  stopAutoRefresh()
})
</script>

<style scoped lang="scss">
.dashboard-container {
  min-height: 100vh;
  background: var(--macos-bg-secondary);
}

.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: var(--macos-glass-bg);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  padding: 0 24px;
  height: 64px;
  box-shadow: var(--macos-shadow-sm);
  position: sticky;
  top: 0;
  z-index: 100;
  border-bottom: 1px solid var(--macos-border);
}

.top-bar-left {
  .page-title {
    margin: 0;
    font-size: 20px;
    font-weight: 600;
    color: var(--macos-text-primary);
  }
}

.top-bar-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.time-selector {
  width: 140px;
}

.auto-refresh {
  display: flex;
  align-items: center;
  gap: 8px;
  
  .refresh-label {
    font-size: 14px;
    color: var(--macos-text-secondary);
  }
  
  .interval-selector {
    width: 72px;
  }
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 8px 14px;
  border-radius: 8px;
  background: var(--macos-bg-primary);
  border: 1px solid var(--macos-border);
  transition: all 0.2s ease;
  font-size: 14px;
  font-weight: 500;
  color: var(--macos-text-primary);

  &:hover {
    background: var(--macos-bg-secondary);
    border-color: var(--macos-blue);
    box-shadow: var(--macos-shadow-sm);
  }

  :deep(.el-icon) {
    font-size: 18px;
    color: var(--macos-blue);
  }
}

.dashboard-content {
  padding: 20px;
}

.dashboard-row {
  margin-bottom: 16px;
  
  &:last-child {
    margin-bottom: 0;
  }
}

// 响应式调整
@media (max-width: 768px) {
  .top-bar {
    flex-direction: column;
    height: auto;
    padding: 12px 16px;
    gap: 12px;
  }
  
  .top-bar-right {
    flex-wrap: wrap;
    justify-content: center;
  }
  
  .dashboard-content {
    padding: 12px;
  }
  
  .dashboard-row {
    margin-bottom: 12px;
  }
}
</style>
