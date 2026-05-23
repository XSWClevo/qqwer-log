<template>
  <div class="log-source-mailbox">
    <el-popover
      v-model:visible="panelVisible"
      placement="bottom-end"
      :width="420"
      trigger="click"
      popper-class="log-source-mailbox-popper"
      @show="handleOpen"
    >
      <template #reference>
        <button
          class="mailbox-trigger"
          :class="{ 'has-unread': unreadCount > 0 }"
          type="button"
          aria-label="日志源通知"
        >
          <el-badge
            :value="unreadCount"
            :hidden="unreadCount === 0"
            :max="99"
            class="mailbox-badge"
          >
            <el-icon><Bell /></el-icon>
          </el-badge>
        </button>
      </template>

      <div class="mailbox-panel">
        <div class="panel-header">
          <div>
            <div class="panel-title">站内信</div>
            <div class="panel-subtitle">{{ unreadCount }} 条待审核日志源</div>
          </div>
          <el-button text circle :loading="loading" @click="checkNewSources">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </div>

        <el-scrollbar max-height="420px">
          <div v-if="newSources.length > 0" class="message-list">
            <div
              v-for="source in newSources"
              :key="source.sourceIp"
              class="message-item"
            >
              <div class="message-dot" />
              <div class="message-content">
                <div class="message-topline">
                  <span class="message-title">发现新的日志源</span>
                  <span class="message-count">{{ source.logCount || 0 }} 条</span>
                </div>
                <div class="source-line">
                  <span class="label">IP</span>
                  <span class="value">{{ source.sourceIp }}</span>
                </div>
                <div v-if="source.hostname" class="source-line">
                  <span class="label">主机</span>
                  <span class="value">{{ source.hostname }}</span>
                </div>
                <div v-if="source.recentLogPreview" class="log-preview">
                  {{ source.recentLogPreview }}
                </div>
                <div class="message-actions">
                  <el-button type="success" size="small" plain @click="handleTrust(source)">
                    <el-icon><Select /></el-icon>
                    信任
                  </el-button>
                  <el-button type="warning" size="small" plain @click="handleBlock(source)">
                    <el-icon><CircleClose /></el-icon>
                    拉黑
                  </el-button>
                  <el-button size="small" text @click="handleViewDetails">
                    <el-icon><View /></el-icon>
                    详情
                  </el-button>
                </div>
              </div>
            </div>
          </div>

          <el-empty
            v-else
            :image-size="72"
            description="暂无待审核日志源"
          />
        </el-scrollbar>
      </div>
    </el-popover>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bell, Select, CircleClose, Refresh, View } from '@element-plus/icons-vue'
import request from '@/utils/request'
import {
  trustLogSource,
  blockLogSource,
  type NewLogSourceNotification
} from '@/api/log-source'

const router = useRouter()
const newSources = ref<NewLogSourceNotification[]>([])
const panelVisible = ref(false)
const loading = ref(false)
let pollingTimer: number | null = null

const unreadCount = computed(() => newSources.value.length)

// 检测新日志源（改为获取待审核通知）
const checkNewSources = async () => {
  loading.value = true
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
  } finally {
    loading.value = false
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
    await checkNewSources()
  } catch (error: any) {
    console.error('信任日志源失败:', error)
    ElMessage.error(error.message || '操作失败')
  }
}

// 拉黑日志源
const handleBlock = async (source: NewLogSourceNotification) => {
  try {
    await blockLogSource(source.sourceIp)
    ElMessage.success(`已拉黑 ${source.sourceIp}`)
    await checkNewSources()
  } catch (error: any) {
    console.error('拉黑日志源失败:', error)
    ElMessage.error(error.message || '操作失败')
  }
}

// 查看详情
const handleViewDetails = () => {
  panelVisible.value = false
  router.push('/log-source')
}

const handleOpen = () => {
  checkNewSources()
}

// 开始轮询
const startPolling = () => {
  // 立即检测一次
  checkNewSources()
  // 每30秒检测一次
  pollingTimer = window.setInterval(checkNewSources, 30 * 1000)
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

.log-source-mailbox {
  position: fixed;
  top: 76px;
  right: 24px;
  z-index: 2000;
}

.mailbox-trigger {
  width: 40px;
  height: 40px;
  border: 1px solid var(--macos-border);
  border-radius: 50%;
  background: var(--macos-bg-primary);
  color: var(--macos-text-secondary);
  box-shadow: var(--macos-shadow-sm);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: var(--macos-transition-fast);

  &:hover,
  &.has-unread {
    color: var(--macos-blue);
    border-color: color-mix(in srgb, var(--macos-blue) 38%, var(--macos-border));
    box-shadow: var(--macos-shadow-md);
  }

  .el-icon {
    font-size: 20px;
  }
}

:deep(.mailbox-badge .el-badge__content) {
  background: #e5484d;
  border: 2px solid var(--macos-bg-primary);
  font-weight: 700;
}
</style>

<style lang="scss">
.log-source-mailbox-popper {
  padding: 0 !important;
  border-radius: 8px !important;
  overflow: hidden;
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.16) !important;

  .mailbox-panel {
    background: var(--macos-bg-primary);
  }

  .panel-header {
    height: 64px;
    padding: 0 16px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    border-bottom: 1px solid var(--macos-border);
  }

  .panel-title {
    font-size: 16px;
    font-weight: 700;
    color: var(--macos-text-primary);
  }

  .panel-subtitle {
    margin-top: 4px;
    font-size: 12px;
    color: var(--macos-text-tertiary);
  }

  .message-list {
    padding: 8px;
  }

  .message-item {
    display: grid;
    grid-template-columns: 8px minmax(0, 1fr);
    gap: 10px;
    padding: 12px 10px;
    border-radius: 8px;
    transition: background 0.15s ease;

    &:hover {
      background: var(--macos-bg-secondary);
    }
  }

  .message-dot {
    width: 8px;
    height: 8px;
    margin-top: 7px;
    border-radius: 50%;
    background: #e5484d;
  }

  .message-content {
    min-width: 0;
  }

  .message-topline {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }

  .message-title {
    font-size: 14px;
    font-weight: 700;
    color: var(--macos-text-primary);
  }

  .message-count {
    flex: 0 0 auto;
    font-size: 12px;
    color: var(--macos-text-tertiary);
  }

  .source-line {
    display: grid;
    grid-template-columns: 42px minmax(0, 1fr);
    gap: 8px;
    margin-top: 8px;
    font-size: 13px;

    .label {
      color: var(--macos-text-tertiary);
    }

    .value {
      min-width: 0;
      color: var(--macos-text-primary);
      font-family: Monaco, Consolas, monospace;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
  }

  .log-preview {
    margin-top: 8px;
    padding: 8px;
    border-radius: 6px;
    background: var(--macos-bg-secondary);
    color: var(--macos-text-secondary);
    font-family: Monaco, Consolas, monospace;
    font-size: 12px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .message-actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 12px;
  }
}
</style>
