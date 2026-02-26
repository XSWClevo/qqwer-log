import request from '@/utils/request'

/**
 * 日志源管理 API
 */

// 日志源 DTO
export interface LogSourceDTO {
  id: number
  sourceIp: string
  hostname?: string
  description?: string
  status: 'trusted' | 'blocked' | 'pending'
  firstSeenAt?: string
  lastSeenAt?: string
  trustedAt?: string
  trustedBy?: string
  logCount?: number
  remark?: string
}

// 新日志源通知
export interface NewLogSourceNotification {
  sourceIp: string
  hostname?: string
  firstSeenAt: string
  logCount: number
  recentLogPreview?: string
}

// 信任日志源请求
export interface TrustLogSourceRequest {
  sourceIp: string
  hostname?: string
  description?: string
  remark?: string
}

// 获取所有信任的日志源
export function getTrustedSources() {
  return request<LogSourceDTO[]>({
    url: '/api/log-sources/trusted',
    method: 'POST'
  })
}

// 获取待审核的日志源
export function getPendingSources() {
  return request<LogSourceDTO[]>({
    url: '/api/log-sources/pending',
    method: 'POST'
  })
}

// 获取被拉黑的日志源
export function getBlockedSources() {
  return request<LogSourceDTO[]>({
    url: '/api/log-sources/blocked',
    method: 'POST'
  })
}

// 根据状态查询日志源
export function getSourcesByStatus(status?: string) {
  return request<LogSourceDTO[]>({
    url: '/api/log-sources/list',
    method: 'POST',
    data: { status }
  })
}

// 信任日志源
export function trustLogSource(data: TrustLogSourceRequest) {
  return request<LogSourceDTO>({
    url: '/api/log-sources/trust',
    method: 'POST',
    data
  })
}

// 拉黑日志源
export function blockLogSource(sourceIp: string) {
  return request<void>({
    url: '/api/log-sources/block',
    method: 'POST',
    data: { sourceIp }
  })
}

// 删除日志源
export function deleteLogSource(sourceIp: string) {
  return request<void>({
    url: '/api/log-sources/delete',
    method: 'POST',
    data: { sourceIp }
  })
}

// 检查 IP 是否在白名单中
export function checkTrusted(sourceIp: string) {
  return request<boolean>({
    url: '/api/log-sources/check-trusted',
    method: 'POST',
    data: { sourceIp }
  })
}

// 检查 IP 是否被拉黑
export function checkBlocked(sourceIp: string) {
  return request<boolean>({
    url: '/api/log-sources/check-blocked',
    method: 'POST',
    data: { sourceIp }
  })
}
