import request from '@/utils/request'

// ==================== 告警规则 API ====================

/**
 * 查询告警规则列表
 */
export function queryAlertRules(params: {
  keyword?: string
  status?: string
  severity?: string
  type?: string
  channel?: string
  pageNum?: number
  pageSize?: number
}) {
  return request({
    url: '/api/alert/rules',
    method: 'get',
    params
  })
}

/**
 * 获取规则详情
 */
export function getAlertRuleById(id: number) {
  return request({
    url: `/api/alert/rules/${id}`,
    method: 'get'
  })
}

/**
 * 创建告警规则
 */
export function createAlertRule(data: {
  name: string
  description?: string
  condition: Record<string, any>
  severity: string
  notificationChannels?: string[]
  silencePeriod?: number
  enabled?: boolean
}) {
  return request({
    url: '/api/alert/rules',
    method: 'post',
    data
  })
}

/**
 * 更新告警规则
 */
export function updateAlertRule(id: number, data: {
  name?: string
  description?: string
  condition?: Record<string, any>
  severity?: string
  notificationChannels?: string[]
  silencePeriod?: number
  enabled?: boolean
}) {
  return request({
    url: `/api/alert/rules/${id}`,
    method: 'put',
    data
  })
}

/**
 * 删除告警规则
 */
export function deleteAlertRule(id: number) {
  return request({
    url: `/api/alert/rules/${id}`,
    method: 'delete'
  })
}

/**
 * 切换规则状态
 */
export function toggleAlertRuleStatus(id: number, enabled: boolean) {
  return request({
    url: `/api/alert/rules/${id}/status`,
    method: 'patch',
    params: { enabled }
  })
}

/**
 * 复制规则
 */
export function duplicateAlertRule(id: number) {
  return request({
    url: `/api/alert/rules/${id}/duplicate`,
    method: 'post'
  })
}

/**
 * 测试规则
 */
export function testAlertRule(id: number) {
  return request({
    url: `/api/alert/rules/${id}/test`,
    method: 'post'
  })
}

// ==================== 告警事件 API ====================

/**
 * 查询告警事件列表
 */
export function queryAlertEvents(params: {
  timeRange?: string
  startTime?: string
  endTime?: string
  keyword?: string
  severity?: string
  ruleId?: number
  notificationStatus?: string
  pageNum?: number
  pageSize?: number
}) {
  return request({
    url: '/api/alert/events',
    method: 'get',
    params
  })
}

/**
 * 获取事件详情
 */
export function getAlertEventById(id: number) {
  return request({
    url: `/api/alert/events/${id}`,
    method: 'get'
  })
}

/**
 * 获取告警趋势
 */
export function getAlertTrend(timeRange: string = '24h') {
  return request({
    url: '/api/alert/events/trend',
    method: 'get',
    params: { timeRange }
  })
}

/**
 * 确认告警
 */
export function acknowledgeAlertEvent(id: number) {
  return request({
    url: `/api/alert/events/${id}/acknowledge`,
    method: 'post'
  })
}
