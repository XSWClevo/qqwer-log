<template>
  <AppLayout>
    <div class="alert-rules-page">
      <!-- 页面头部 -->
      <div class="page-header">
        <div class="header-content">
          <div class="title-section">
            <h1 class="page-title">告警规则</h1>
            <p class="page-subtitle">管理基于日志数据模式和指标的告警规则</p>
          </div>
          <el-button type="primary" size="large" @click="showCreateDrawer = true">
            <el-icon><Plus /></el-icon>
            创建新规则
          </el-button>
        </div>
      </div>

      <!-- 搜索和过滤栏 -->
      <div class="filter-bar">
        <div class="filter-bar-main">
          <el-input
            v-model="searchText"
            placeholder="搜索规则名称、查询条件或标签..."
            :prefix-icon="Search"
            clearable
            class="search-input"
          />
          <el-button
            :type="showAdvancedFilter ? 'primary' : 'default'"
            @click="showAdvancedFilter = !showAdvancedFilter"
          >
            <el-icon><Filter /></el-icon>
            高级筛选
            <el-badge v-if="activeFilterCount > 0" :value="activeFilterCount" class="filter-badge" />
          </el-button>
        </div>
        <Transition name="filter-expand">
          <div v-show="showAdvancedFilter" class="filter-bar-advanced">
            <el-select v-model="filters.status" placeholder="状态" clearable class="filter-select">
              <el-option label="全部" value="" />
              <el-option label="已启用" value="enabled" />
              <el-option label="已禁用" value="disabled" />
            </el-select>
            <el-select v-model="filters.severity" placeholder="严重程度" clearable class="filter-select">
              <el-option label="全部" value="" />
              <el-option label="严重" value="critical" />
              <el-option label="警告" value="warning" />
              <el-option label="信息" value="info" />
            </el-select>
            <el-select v-model="filters.type" placeholder="类型" clearable class="filter-select">
              <el-option label="全部" value="" />
              <el-option label="日志查询" value="log_query" />
              <el-option label="指标阈值" value="metric_threshold" />
              <el-option label="异常检测" value="anomaly" />
            </el-select>
            <el-select v-model="filters.channel" placeholder="通知渠道" clearable class="filter-select">
              <el-option label="全部" value="" />
              <el-option label="Slack" value="slack" />
              <el-option label="邮件" value="email" />
              <el-option label="Webhook" value="webhook" />
            </el-select>
            <el-button text @click="resetFilters" class="reset-btn">重置筛选</el-button>
          </div>
        </Transition>
      </div>

      <!-- 规则列表表格 -->
      <div class="table-container">
        <el-table :data="filteredRules" style="width: 100%" stripe>
          <el-table-column width="80" align="center">
            <template #header>状态</template>
            <template #default="{ row }">
              <el-switch
                v-model="row.enabled"
                @change="handleToggleStatus(row)"
              />
            </template>
          </el-table-column>

          <el-table-column label="规则名称" min-width="280">
            <template #default="{ row }">
              <div class="rule-name-cell">
                <div class="rule-name" @click="handleViewRule(row)">{{ row.name }}</div>
                <div class="rule-description">{{ row.description }}</div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="严重程度" width="120" align="center">
            <template #default="{ row }">
              <el-tag :type="getSeverityType(row.severity)" size="small">
                {{ getSeverityLabel(row.severity) }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="触发条件" min-width="200">
            <template #default="{ row }">
              <div class="trigger-condition">{{ row.triggerCondition }}</div>
            </template>
          </el-table-column>

          <el-table-column label="通知渠道" width="150" align="center">
            <template #default="{ row }">
              <div class="notification-icons">
                <el-tooltip v-for="channel in row.channels" :key="channel" :content="getChannelLabel(channel)">
                  <el-icon :size="18" :color="getChannelColor(channel)">
                    <component :is="getChannelIcon(channel)" />
                  </el-icon>
                </el-tooltip>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="最后触发" width="160">
            <template #default="{ row }">
              <span class="last-triggered">{{ row.lastTriggered || '从未' }}</span>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="80" align="center" fixed="right">
            <template #default="{ row }">
              <el-dropdown @command="(cmd) => handleAction(cmd, row)">
                <el-button text :icon="MoreFilled" />
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="edit">
                      <el-icon><Edit /></el-icon>编辑
                    </el-dropdown-item>
                    <el-dropdown-item command="duplicate">
                      <el-icon><CopyDocument /></el-icon>复制
                    </el-dropdown-item>
                    <el-dropdown-item command="test">
                      <el-icon><VideoPlay /></el-icon>测试规则
                    </el-dropdown-item>
                    <el-dropdown-item command="delete" divided>
                      <el-icon><Delete /></el-icon>删除
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 创建/编辑规则抽屉 -->
      <CreateRuleDrawer 
        v-model="showCreateDrawer" 
        :rule-id="editingRuleId"
        @success="handleCreateSuccess" 
      />
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus,
  Search,
  Edit,
  Delete,
  CopyDocument,
  VideoPlay,
  MoreFilled,
  Message,
  ChatDotRound,
  Link,
  Filter
} from '@element-plus/icons-vue'
import AppLayout from '@/components/layout/AppLayout.vue'
import CreateRuleDrawer from './components/CreateRuleDrawer.vue'
import {
  queryAlertRules,
  toggleAlertRuleStatus,
  duplicateAlertRule,
  testAlertRule,
  deleteAlertRule
} from '@/api/alert'

// 搜索和过滤
const searchText = ref('')
const showAdvancedFilter = ref(false)
const filters = ref({
  status: '',
  severity: '',
  type: '',
  channel: ''
})

// 计算激活的筛选数量
const activeFilterCount = computed(() => {
  return Object.values(filters.value).filter(v => v !== '').length
})

// 重置筛选
const resetFilters = () => {
  filters.value = { status: '', severity: '', type: '', channel: '' }
}

// 显示创建抽屉
const showCreateDrawer = ref(false)
const editingRuleId = ref<number | null>(null)

// 加载状态
const loading = ref(false)

// 规则列表
const rules = ref<any[]>([])
const pagination = ref({
  current: 1,
  pageSize: 20,
  total: 0
})

// 过滤后的规则（本地过滤，实际应该在后端过滤）
const filteredRules = computed(() => {
  return rules.value.filter(rule => {
    if (searchText.value && !rule.name.toLowerCase().includes(searchText.value.toLowerCase()) &&
        !rule.description.toLowerCase().includes(searchText.value.toLowerCase())) {
      return false
    }
    return true
  })
})

// 加载规则列表
const loadRules = async () => {
  loading.value = true
  try {
    const params = {
      keyword: searchText.value || undefined,
      status: filters.value.status || undefined,
      severity: filters.value.severity || undefined,
      type: filters.value.type || undefined,
      channel: filters.value.channel || undefined,
      pageNum: pagination.value.current,
      pageSize: pagination.value.pageSize
    }
    
    const response = await queryAlertRules(params)
    
    // response 已经是 res.data，因为 request 工具返回的是 res
    const data = response.data || response
    
    if (data && data.records) {
      rules.value = data.records.map((rule: any) => ({
        id: rule.id,
        name: rule.name,
        description: rule.description,
        enabled: rule.enabled,
        severity: rule.severity?.toLowerCase() || 'info',
        type: rule.type || 'log_query',
        triggerCondition: rule.triggerConditionSummary || '未配置',
        channels: rule.notificationChannels || [],
        lastTriggered: formatLastTriggered(rule.lastTriggeredAt),
        triggerCount: rule.triggerCount || 0
      }))
      
      pagination.value.total = data.total || 0
    } else {
      // 如果没有 records，可能是直接返回的数组
      console.log('Response structure:', response)
      rules.value = []
    }
  } catch (error: any) {
    console.error('Failed to load rules:', error)
    ElMessage.error('加载规则列表失败: ' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

// 格式化最后触发时间
const formatLastTriggered = (time: string | null) => {
  if (!time) return null
  
  const now = new Date()
  const triggered = new Date(time)
  const diff = now.getTime() - triggered.getTime()
  
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)
  
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`
  return triggered.toLocaleDateString()
}

// 初始化加载
onMounted(() => {
  loadRules()
})

// 严重程度相关
const getSeverityType = (severity: string) => {
  const map: Record<string, any> = {
    critical: 'danger',
    warning: 'warning',
    info: 'info'
  }
  return map[severity] || 'info'
}

const getSeverityLabel = (severity: string) => {
  const map: Record<string, string> = {
    critical: '严重',
    warning: '警告',
    info: '信息'
  }
  return map[severity] || severity
}

// 通知渠道相关
const getChannelIcon = (channel: string) => {
  const map: Record<string, any> = {
    slack: ChatDotRound,
    email: Message,
    webhook: Link
  }
  return map[channel] || Message
}

const getChannelLabel = (channel: string) => {
  const map: Record<string, string> = {
    slack: 'Slack',
    email: '邮件',
    webhook: 'Webhook'
  }
  return map[channel] || channel
}

const getChannelColor = (channel: string) => {
  const map: Record<string, string> = {
    slack: '#4A154B',
    email: '#1890FF',
    webhook: '#52C41A'
  }
  return map[channel] || '#666'
}

// 切换状态
const handleToggleStatus = async (rule: any) => {
  try {
    await toggleAlertRuleStatus(rule.id, rule.enabled)
    ElMessage.success(`规则 "${rule.name}" 已${rule.enabled ? '启用' : '禁用'}`)
  } catch (error: any) {
    rule.enabled = !rule.enabled // 回滚状态
    ElMessage.error('切换状态失败: ' + (error.message || '未知错误'))
  }
}

// 查看规则
const handleViewRule = (rule: any) => {
  console.log('View rule:', rule)
}

// 操作处理
const handleAction = async (command: string, rule: any) => {
  switch (command) {
    case 'edit':
      editingRuleId.value = rule.id
      showCreateDrawer.value = true
      break
    case 'duplicate':
      try {
        await duplicateAlertRule(rule.id)
        ElMessage.success(`规则 "${rule.name}" 已复制`)
        loadRules() // 重新加载列表
      } catch (error: any) {
        ElMessage.error('复制规则失败: ' + (error.message || '未知错误'))
      }
      break
    case 'test':
      try {
        const result = await testAlertRule(rule.id)
        ElMessage.success(`测试完成: ${result.message || '规则测试成功'}`)
      } catch (error: any) {
        ElMessage.error('测试规则失败: ' + (error.message || '未知错误'))
      }
      break
    case 'delete':
      try {
        await ElMessageBox.confirm(`确定要删除规则 "${rule.name}" 吗？`, '确认删除', {
          type: 'warning'
        })
        await deleteAlertRule(rule.id)
        ElMessage.success('删除成功')
        loadRules() // 重新加载列表
      } catch (error: any) {
        if (error !== 'cancel') {
          ElMessage.error('删除失败: ' + (error.message || '未知错误'))
        }
      }
      break
  }
}

// 创建成功
const handleCreateSuccess = () => {
  editingRuleId.value = null // 重置编辑 ID
  loadRules() // 重新加载列表
}
</script>

<style scoped lang="scss">
.alert-rules-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--macos-bg-secondary);
}

.page-header {
  background: var(--macos-card-bg);
  padding: 24px 32px;
  border-bottom: 1px solid var(--macos-border);

  .header-content {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .title-section {
    .page-title {
      font-size: 24px;
      font-weight: 600;
      color: var(--macos-text-primary);
      margin: 0 0 8px 0;
    }

    .page-subtitle {
      font-size: 14px;
      color: var(--macos-text-tertiary);
      margin: 0;
    }
  }
}

.filter-bar {
  background: var(--macos-bg-primary, #FFFFFF);
  padding: 16px 32px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  border-bottom: 1px solid var(--macos-border, #E8E8E8);

  .filter-bar-main {
    display: flex;
    align-items: center;
    gap: 12px;

    .search-input {
      flex: 1;
      max-width: 400px;
    }

    .filter-badge {
      margin-left: 4px;
    }
  }

  .filter-bar-advanced {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    padding-top: 12px;
    border-top: 1px solid var(--macos-border, #E8E8E8);

    .filter-select {
      width: 150px;
    }

    .reset-btn {
      color: var(--macos-text-tertiary);
    }
  }
}

.filter-expand-enter-active,
.filter-expand-leave-active {
  transition: all 0.25s ease;
  overflow: hidden;
}
.filter-expand-enter-from,
.filter-expand-leave-to {
  opacity: 0;
  max-height: 0;
  padding-top: 0;
}

.table-container {
  flex: 1;
  padding: 24px 32px;
  overflow: auto;

  .rule-name-cell {
    .rule-name {
      font-size: 14px;
      font-weight: 500;
      color: var(--macos-blue);
      cursor: pointer;
      margin-bottom: 4px;

      &:hover {
        text-decoration: underline;
      }
    }

    .rule-description {
      font-size: 12px;
      color: var(--macos-text-tertiary);
      font-family: 'Monaco', 'Courier New', monospace;
    }
  }

  .trigger-condition {
    font-size: 13px;
    color: var(--macos-text-secondary);
  }

  .notification-icons {
    display: flex;
    gap: 8px;
    justify-content: center;
  }

  .last-triggered {
    font-size: 13px;
    color: var(--macos-text-tertiary);
  }
}
</style>
