<template>
  <div v-if="newSources.length > 0" class="new-log-source-notification">
    <el-alert
      v-for="source in newSources"
      :key="source.sourceIp"
      type="warning"
      :closable="true"
      @close="handleDismiss(source.sourceIp)"
      class="notification-item"
    >
      <template #title>
        <div class="notification-content">
          <div class="notification-header">
            <el-icon class="warning-icon"><Warning /></el-icon>
            <span class="title-text">发现新的日志源</span>
          </div>
          <div class="notification-body">
            <div class="source-info">
              <span class="label">IP地址:</span>
              <span class="value">{{ source.sourceIp }}</span>
            </div>
            <div v-if="source.hostname" class="source-info">
              <span class="label">主机名:</span>
              <span class="value">{{ source.hostname }}</span>
            </div>
            <div class="source-info">
              <span class="label">日志数量:</span>
              <span class="value">{{ source.logCount }}</span>
            </div>
            <div v-if="source.recentLogPreview" class="log-preview">
              <span class="label">最近日志:</span>
              <span class="value">{{ source.recentLogPreview }}</span>
            </div>
          </div>
          <div class="notification-actions">
            <el-button type="success" size="small" @click="handleTrust(source)">
              <el-icon><Select /></el-icon>
              信任
            </el-button>
            <el-button type="warning" size="small" @click="handleBlock(source)">
              <el-icon><CircleClose /></el-icon>
              拉黑
            </el-button>
            <el-button size="small" @click="handleViewDetails">
              查看详情
            </el-button>
          </div>
        </div>
      </template>
    </el-alert>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Warning, Select, CircleClose } from '@element-plus/icons-vue'
import request from '@/utils/request'
import {
  trustLogSource,
  blockLogSource,
  type NewLogSourceNotification
} from '@/api/log-source'

const router = useRouter()
const newSources = ref<NewLogSourceNotification[]>([])
let pollingTimer: number | null = null

// 检测新日志源（改为获取待审核通知）
const checkNewSources = async () => {
  try {
    // 调用新接口获取待审核的通知
    const { data } = await request<NewLogSourceNotification[]>({
      url: '/api/log-sources/pending-notifications',
      method: 'POST'
    })

    if (data && data.length > 0) {
      newSources.value = data
    } else {
      newSources.value = []
    }
  } catch (error) {
    console.error('获取待审核通知失败:', error)
  }
}

// 信任日志源
const handleTrust = async (source: NewLogSourceNotification) => {
  try {
    await trustLogSource({
      sourceIp: source.sourceIp,
      hostname: source.hostname,
      description: `自动检测到的日志源，首次发现于 ${source.firstSeenAt}`
    })
    ElMessage.success(`已信任 ${source.sourceIp}`)
    // 从列表中移除
    newSources.value = newSources.value.filter(s => s.sourceIp !== source.sourceIp)
  } catch (error: any) {
    console.error('信任日志源失败:', error)
    ElMessage.error(error.message || '操作失败')
  }
}

// 拉黑日志源
const handleBlock = async (source: NewLogSourceNotification) => {
  try {
    // 先信任（创建记录），再拉黑
    await trustLogSource({
      sourceIp: source.sourceIp,
      hostname: source.hostname
    })
    await blockLogSource(source.sourceIp)
    ElMessage.success(`已拉黑 ${source.sourceIp}`)
    // 从列表中移除
    newSources.value = newSources.value.filter(s => s.sourceIp !== source.sourceIp)
  } catch (error: any) {
    console.error('拉黑日志源失败:', error)
    ElMessage.error(error.message || '操作失败')
  }
}

// 查看详情
const handleViewDetails = () => {
  router.push('/log-source')
}

// 忽略通知
const handleDismiss = (sourceIp: string) => {
  newSources.value = newSources.value.filter(s => s.sourceIp !== sourceIp)
}

// 开始轮询
const startPolling = () => {
  // 立即检测一次
  checkNewSources()
  // 每5分钟检测一次
  pollingTimer = window.setInterval(checkNewSources, 5 * 60 * 1000)
}

// 停止轮询
const stopPolling = () => {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

onMounted(() => {
  startPolling()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped lang="scss">
@use '@/assets/styles/macos.scss' as *;

.new-log-source-notification {
  position: fixed;
  top: 80px;
  right: 24px;
  z-index: 2000;
  max-width: 500px;

  .notification-item {
    margin-bottom: 12px;
    border-radius: var(--macos-radius-md);
    box-shadow: var(--macos-shadow-lg);

    :deep(.el-alert__content) {
      width: 100%;
    }
  }

  .notification-content {
    .notification-header {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 12px;

      .warning-icon {
        font-size: 20px;
        color: var(--el-color-warning);
      }

      .title-text {
        font-size: 16px;
        font-weight: 600;
        color: var(--macos-text-primary);
      }
    }

    .notification-body {
      margin-bottom: 12px;

      .source-info {
        display: flex;
        gap: 8px;
        margin-bottom: 6px;
        font-size: 14px;

        .label {
          color: var(--macos-text-secondary);
          min-width: 80px;
        }

        .value {
          color: var(--macos-text-primary);
          font-family: 'Monaco', 'Consolas', monospace;
        }
      }

      .log-preview {
        display: flex;
        gap: 8px;
        margin-top: 8px;
        padding: 8px;
        background: var(--macos-bg-secondary);
        border-radius: 4px;
        font-size: 12px;

        .label {
          color: var(--macos-text-secondary);
          min-width: 80px;
        }

        .value {
          color: var(--macos-text-primary);
          font-family: 'Monaco', 'Consolas', monospace;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      }
    }

    .notification-actions {
      display: flex;
      gap: 8px;
      justify-content: flex-end;
    }
  }
}
</style>
