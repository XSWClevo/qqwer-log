<template>
  <AppLayout>
    <div class="alert-history-page">
      <!-- 页面头部 -->
      <div class="page-header">
        <div class="header-content">
          <div class="title-section">
            <h1 class="page-title">告警历史</h1>
            <p class="page-subtitle">所有触发告警的执行详情日志</p>
          </div>
        </div>
      </div>

      <!-- 时间和过滤控制栏 -->
      <div class="filter-bar">
        <el-select v-model="timeRange" class="time-selector">
          <el-option label="最近 24 小时" value="24h" />
          <el-option label="最近 7 天" value="7d" />
          <el-option label="最近 30 天" value="30d" />
          <el-option label="自定义" value="custom" />
        </el-select>
        <el-input
          v-model="searchText"
          placeholder="搜索规则名称或告警消息..."
          :prefix-icon="Search"
          clearable
          class="search-input"
        />
        <div class="quick-filters">
          <el-tag
            :type="quickFilter === 'critical' ? 'danger' : 'info'"
            :effect="quickFilter === 'critical' ? 'dark' : 'plain'"
            @click="toggleQuickFilter('critical')"
            class="filter-tag"
          >
            仅严重
          </el-tag>
          <el-tag
            :type="quickFilter === 'warning' ? 'warning' : 'info'"
            :effect="quickFilter === 'warning' ? 'dark' : 'plain'"
            @click="toggleQuickFilter('warning')"
            class="filter-tag"
          >
            仅警告
          </el-tag>
          <el-tag
            :type="quickFilter === 'failed' ? 'danger' : 'info'"
            :effect="quickFilter === 'failed' ? 'dark' : 'plain'"
            @click="toggleQuickFilter('failed')"
            class="filter-tag"
          >
            通知失败
          </el-tag>
        </div>
      </div>

      <!-- 告警时间线可视化 -->
      <div class="timeline-chart">
        <div ref="chartRef" style="width: 100%; height: 200px"></div>
      </div>

      <!-- 告警事件表格 -->
      <div class="table-container">
        <el-table :data="filteredAlerts" style="width: 100%" stripe>
          <el-table-column label="触发时间" width="180">
            <template #default="{ row }">
              <div class="timestamp">{{ row.triggerTime }}</div>
            </template>
          </el-table-column>

          <el-table-column label="严重程度" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="getSeverityType(row.severity)" size="small">
                {{ getSeverityLabel(row.severity) }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="规则名称" min-width="250">
            <template #default="{ row }">
              <div class="rule-name" @click="handleViewRule(row)">{{ row.ruleName }}</div>
            </template>
          </el-table-column>

          <el-table-column label="触发值" width="180">
            <template #default="{ row }">
              <div class="trigger-value">{{ row.triggeredValue }}</div>
            </template>
          </el-table-column>

          <el-table-column label="相关实体" width="180">
            <template #default="{ row }">
              <div class="related-entity">{{ row.relatedEntity }}</div>
            </template>
          </el-table-column>

          <el-table-column label="通知状态" width="140" align="center">
            <template #default="{ row }">
              <div class="notification-status">
                <el-icon v-if="row.notificationStatus === 'sent'" color="#52C41A" :size="16">
                  <SuccessFilled />
                </el-icon>
                <el-icon v-else color="#FF4D4F" :size="16">
                  <CircleCloseFilled />
                </el-icon>
                <span :class="row.notificationStatus === 'sent' ? 'status-sent' : 'status-failed'">
                  {{ row.notificationStatus === 'sent' ? '已发送' : '发送失败' }}
                </span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="120" align="center" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" text @click="handleViewDetails(row)">
                查看详情
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination">
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :total="totalAlerts"
            :page-sizes="[20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
          />
        </div>
      </div>

      <!-- 告警详情侧边栏 -->
      <AlertDetailDrawer v-model="showDetailDrawer" :alert="selectedAlert" />
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { Search, SuccessFilled, CircleCloseFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import AppLayout from '@/components/layout/AppLayout.vue'
import AlertDetailDrawer from './components/AlertDetailDrawer.vue'
import { queryAlertEvents, getAlertTrend } from '@/api/alert'

// 时间范围和搜索
const timeRange = ref('24h')
const searchText = ref('')
const quickFilter = ref('')

// 分页
const currentPage = ref(1)
const pageSize = ref(20)
const totalAlerts = ref(100)

// 详情抽屉
const showDetailDrawer = ref(false)
const selectedAlert = ref<any>(null)

// 图表
const chartRef = ref<HTMLElement>()

// 加载状态
const loading = ref(false)

// 告警列表
const alerts = ref<any[]>([])

// 加载告警事件列表
const loadAlertEvents = async () => {
  loading.value = true
  try {
    const params = {
      timeRange: timeRange.value,
      keyword: searchText.value || undefined,
      severity: quickFilter.value === 'critical' || quickFilter.value === 'warning' ? quickFilter.value : undefined,
      notificationStatus: quickFilter.value === 'failed' ? 'failed' : undefined,
      pageNum: currentPage.value,
      pageSize: pageSize.value
    }
    
    const response = await queryAlertEvents(params)
    
    // response 已经是 res.data，因为 request 工具返回的是 res
    const data = response.data || response
    
    if (data && data.records) {
      alerts.value = data.records.map((event: any) => ({
        id: event.id,
        triggerTime: formatDateTime(event.triggeredAt),
        severity: event.severity?.toLowerCase() || 'info',
        ruleName: event.ruleName,
        triggeredValue: event.triggeredValue || 'N/A',
        relatedEntity: event.relatedEntity || 'N/A',
        notificationStatus: event.notificationStatus || 'sent',
        ruleId: event.ruleId
      }))
      
      totalAlerts.value = data.total || 0
    } else {
      console.log('Response structure:', response)
      alerts.value = []
    }
  } catch (error: any) {
    console.error('Failed to load alert events:', error)
    ElMessage.error('加载告警事件失败: ' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

// 格式化日期时间
const formatDateTime = (dateTime: string) => {
  if (!dateTime) return 'N/A'
  return dateTime.replace('T', ' ').substring(0, 19)
}

// 监听过滤条件变化
watch([timeRange, searchText, quickFilter, currentPage, pageSize], () => {
  loadAlertEvents()
})

// 过滤后的告警
const filteredAlerts = computed(() => {
  return alerts.value.filter(alert => {
    if (searchText.value && !alert.ruleName.toLowerCase().includes(searchText.value.toLowerCase())) {
      return false
    }
    if (quickFilter.value === 'critical' && alert.severity !== 'critical') {
      return false
    }
    if (quickFilter.value === 'warning' && alert.severity !== 'warning') {
      return false
    }
    if (quickFilter.value === 'failed' && alert.notificationStatus !== 'failed') {
      return false
    }
    return true
  })
})

// 切换快速过滤
const toggleQuickFilter = (filter: string) => {
  quickFilter.value = quickFilter.value === filter ? '' : filter
}

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

// 查看规则
const handleViewRule = (alert: any) => {
  console.log('View rule:', alert)
}

// 查看详情
const handleViewDetails = (alert: any) => {
  selectedAlert.value = alert
  showDetailDrawer.value = true
}

// 初始化图表
const initChart = async () => {
  if (!chartRef.value) return

  try {
    const response = await getAlertTrend(timeRange.value)
    const trendData = response.data || response
    
    const chart = echarts.init(chartRef.value)
    
    const option = {
      title: {
        text: '告警趋势',
        left: 20,
        top: 10,
        textStyle: {
          fontSize: 14,
          fontWeight: 600
        }
      },
      tooltip: {
        trigger: 'axis',
        axisPointer: {
          type: 'shadow'
        }
      },
      legend: {
        data: ['严重', '警告'],
        right: 20,
        top: 10
      },
      grid: {
        left: 50,
        right: 30,
        top: 50,
        bottom: 30
      },
      xAxis: {
        type: 'category',
        data: trendData.timestamps || ['00:00', '04:00', '08:00', '12:00', '16:00', '20:00', '24:00']
      },
      yAxis: {
        type: 'value'
      },
      series: [
        {
          name: '严重',
          type: 'bar',
          stack: 'total',
          data: trendData.critical || [5, 3, 8, 12, 15, 10, 6],
          itemStyle: {
            color: '#FF4D4F'
          }
        },
        {
          name: '警告',
          type: 'bar',
          stack: 'total',
          data: trendData.warning || [8, 6, 10, 15, 20, 12, 9],
          itemStyle: {
            color: '#FAAD14'
          }
        }
      ]
    }

    chart.setOption(option)

    // 响应式
    window.addEventListener('resize', () => {
      chart.resize()
    })
  } catch (error) {
    console.error('Failed to load alert trend:', error)
  }
}

// 监听时间范围变化，重新加载图表
watch(timeRange, () => {
  nextTick(() => {
    initChart()
  })
})

onMounted(() => {
  loadAlertEvents()
  nextTick(() => {
    initChart()
  })
})
</script>

<style scoped lang="scss">
.alert-history-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #F0F2F5;
}

.page-header {
  background: #FFFFFF;
  padding: 24px 32px;
  border-bottom: 1px solid #E8E8E8;

  .header-content {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .title-section {
    .page-title {
      font-size: 24px;
      font-weight: 600;
      color: #262626;
      margin: 0 0 8px 0;
    }

    .page-subtitle {
      font-size: 14px;
      color: #8C8C8C;
      margin: 0;
    }
  }
}

.filter-bar {
  background: #FFFFFF;
  padding: 16px 32px;
  display: flex;
  gap: 12px;
  align-items: center;
  border-bottom: 1px solid #E8E8E8;

  .time-selector {
    width: 180px;
  }

  .search-input {
    flex: 1;
    max-width: 400px;
  }

  .quick-filters {
    display: flex;
    gap: 8px;

    .filter-tag {
      cursor: pointer;
      user-select: none;
    }
  }
}

.timeline-chart {
  background: #FFFFFF;
  padding: 16px 32px;
  border-bottom: 1px solid #E8E8E8;
}

.table-container {
  flex: 1;
  padding: 24px 32px;
  overflow: auto;
  display: flex;
  flex-direction: column;

  .timestamp {
    font-size: 13px;
    color: #595959;
    font-family: 'Monaco', 'Courier New', monospace;
  }

  .rule-name {
    font-size: 14px;
    color: #1890FF;
    cursor: pointer;

    &:hover {
      text-decoration: underline;
    }
  }

  .trigger-value {
    font-size: 13px;
    color: #595959;
    font-family: 'Monaco', 'Courier New', monospace;
  }

  .related-entity {
    font-size: 13px;
    color: #8C8C8C;
  }

  .notification-status {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;

    .status-sent {
      color: #52C41A;
    }

    .status-failed {
      color: #FF4D4F;
    }
  }

  .pagination {
    margin-top: 16px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>
