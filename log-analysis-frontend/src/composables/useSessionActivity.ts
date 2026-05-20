import { onMounted, onUnmounted } from 'vue'
import { useAuthStore } from '@/store/auth'
import service from '@/utils/request'

const HEARTBEAT_INTERVAL_MS = 5 * 60 * 1000 // 每5分钟发送一次心跳
const ACTIVITY_EVENTS = ['mousedown', 'keydown', 'scroll', 'touchstart', 'click']

/**
 * 会话活跃监听 composable
 *
 * 监听用户操作（鼠标、键盘、滚动、触摸等），
 * 定期向后端发送心跳以刷新会话活跃时间。
 * 仅在用户有实际操作时才发送心跳，避免无效请求。
 */
export function useSessionActivity() {
  let heartbeatTimer: ReturnType<typeof setInterval> | null = null
  let hasActivity = false

  const sendHeartbeat = async () => {
    const authStore = useAuthStore()
    if (!authStore.isAuthenticated || !hasActivity) {
      return
    }

    hasActivity = false
    try {
      await service.post('/api/session/heartbeat')
    } catch {
      // 心跳失败不阻塞用户操作，440 会由响应拦截器统一处理
    }
  }

  const onUserActivity = () => {
    hasActivity = true
  }

  const startListening = () => {
    // 登录后立即标记有活动
    hasActivity = true

    // 监听用户操作事件
    ACTIVITY_EVENTS.forEach(event => {
      document.addEventListener(event, onUserActivity, { passive: true })
    })

    // 定期发送心跳
    heartbeatTimer = setInterval(sendHeartbeat, HEARTBEAT_INTERVAL_MS)
  }

  const stopListening = () => {
    ACTIVITY_EVENTS.forEach(event => {
      document.removeEventListener(event, onUserActivity)
    })

    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  onMounted(startListening)
  onUnmounted(stopListening)

  return { startListening, stopListening }
}
