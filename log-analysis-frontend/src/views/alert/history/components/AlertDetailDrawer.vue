<template>
  <el-drawer
    v-model="visible"
    title="告警详情"
    size="720px"
    v-loading="loading"
  >
    <div v-if="alert && detailData" class="alert-detail">
      <!-- 头部信息 -->
      <div class="detail-header">
        <h2 class="rule-name">{{ alert.ruleName }}</h2>
        <div class="trigger-time">触发时间: {{ alert.triggerTime }}</div>
      </div>

      <!-- 摘要部分 -->
      <el-card class="detail-section" shadow="never">
        <template #header>
          <div class="section-title">触发原因</div>
        </template>
        <div class="summary-content">
          <el-alert
            :type="getSeverityType(alert.severity)"
            :closable="false"
            show-icon
          >
            <template #title>
              <div class="alert-title">
                条件 "Count > 10" 在最近 5 分钟内满足，实际值为 "{{ alert.triggeredValue }}"
              </div>
            </template>
          </el-alert>

          <div class="query-info">
            <div class="info-label">执行的查询:</div>
            <div class="query-code">
              service='payment' AND status >= 500
            </div>
          </div>
        </div>
      </el-card>

      <!-- 通知结果 -->
      <el-card class="detail-section" shadow="never">
        <template #header>
          <div class="section-title">通知结果</div>
        </template>
        <div class="notification-results">
          <div
            v-for="(result, index) in (detailData?.notificationResults || [])"
            :key="index"
            :class="['result-item', result.status === 'success' ? 'success' : 'failed']"
          >
            <el-icon :size="20">
              <SuccessFilled v-if="result.status === 'success'" />
              <CircleCloseFilled v-else />
            </el-icon>
            <div class="result-content">
              <div class="result-channel">{{ result.message }}</div>
              <div class="result-status">{{ result.status === 'success' ? '成功' : '失败' }}</div>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 上下文日志 -->
      <el-card class="detail-section" shadow="never">
        <template #header>
          <div class="section-title">
            <span>触发此告警的日志</span>
            <el-button type="primary" text size="small" @click="handleViewInSearch">
              在搜索页面查看
              <el-icon><Right /></el-icon>
            </el-button>
          </div>
        </template>
        <div class="contextual-logs">
          <div v-for="(log, index) in detailData.contextLogs" :key="index" class="log-entry">
            <div class="log-time">{{ log.timestamp }}</div>
            <div class="log-level" :class="`level-${log.severity?.toLowerCase() || 'info'}`">
              {{ log.severity }}
            </div>
            <div class="log-message">{{ log.message }}</div>
          </div>
        </div>
      </el-card>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { SuccessFilled, CircleCloseFilled, Right } from '@element-plus/icons-vue'
import { getAlertEventById } from '@/api/alert'

const props = defineProps<{
  modelValue: boolean
  alert: any
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const visible = ref(false)
const loading = ref(false)
const detailData = ref<any>(null)

// 加载详情数据
const loadDetail = async () => {
  if (!props.alert?.id) return
  
  loading.value = true
  try {
    const response = await getAlertEventById(props.alert.id)
    detailData.value = response.data || response
  } catch (error: any) {
    console.error('Failed to load alert detail:', error)
    ElMessage.error('加载告警详情失败: ' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val && props.alert) {
    loadDetail()
  }
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

const getSeverityType = (severity: string) => {
  const map: Record<string, any> = {
    critical: 'error',
    warning: 'warning',
    info: 'info'
  }
  return map[severity] || 'info'
}

const handleViewInSearch = () => {
  ElMessage.info('跳转到日志搜索页面...')
}
</script>

<style scoped lang="scss">
.alert-detail {
  .detail-header {
    margin-bottom: 24px;

    .rule-name {
      font-size: 20px;
      font-weight: 600;
      color: #262626;
      margin: 0 0 8px 0;
    }

    .trigger-time {
      font-size: 14px;
      color: #8C8C8C;
    }
  }

  .detail-section {
    margin-bottom: 16px;

    .section-title {
      font-size: 16px;
      font-weight: 600;
      color: #262626;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .summary-content {
      .alert-title {
        font-size: 14px;
      }

      .query-info {
        margin-top: 16px;

        .info-label {
          font-size: 13px;
          color: #8C8C8C;
          margin-bottom: 8px;
        }

        .query-code {
          background: #F5F5F5;
          padding: 12px;
          border-radius: 4px;
          font-family: 'Monaco', 'Courier New', monospace;
          font-size: 13px;
          color: #262626;
        }
      }
    }

    .notification-results {
      .result-item {
        display: flex;
        align-items: flex-start;
        gap: 12px;
        padding: 12px;
        border-radius: 4px;
        margin-bottom: 8px;

        &.success {
          background: #F6FFED;
          border: 1px solid #B7EB8F;

          .el-icon {
            color: #52C41A;
          }
        }

        &.failed {
          background: #FFF1F0;
          border: 1px solid #FFCCC7;

          .el-icon {
            color: #FF4D4F;
          }
        }

        .result-content {
          flex: 1;

          .result-channel {
            font-size: 14px;
            font-weight: 500;
            color: #262626;
            margin-bottom: 4px;
          }

          .result-status {
            font-size: 13px;
            color: #8C8C8C;
          }
        }
      }
    }

    .contextual-logs {
      max-height: 400px;
      overflow-y: auto;

      .log-entry {
        display: flex;
        gap: 12px;
        padding: 8px 0;
        border-bottom: 1px solid #F0F0F0;
        font-size: 13px;

        &:last-child {
          border-bottom: none;
        }

        .log-time {
          color: #8C8C8C;
          font-family: 'Monaco', 'Courier New', monospace;
          white-space: nowrap;
        }

        .log-level {
          font-weight: 600;
          padding: 2px 8px;
          border-radius: 2px;
          white-space: nowrap;

          &.level-error {
            background: #FFF1F0;
            color: #FF4D4F;
          }

          &.level-warn {
            background: #FFFBE6;
            color: #FAAD14;
          }

          &.level-info {
            background: #E6F7FF;
            color: #1890FF;
          }
        }

        .log-message {
          flex: 1;
          color: #262626;
          word-break: break-all;
        }
      }
    }
  }
}
</style>
