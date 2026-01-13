import request from '@/utils/request'

/**
 * Trace 列表项
 */
export interface TraceItem {
  traceId: string
  rootService: string
  rootOperation: string
  duration: number
  spanCount: number
  errorCount: number
  timestamp: string
  services: string[]
}

/**
 * Trace 列表响应
 */
export interface TraceListResponse {
  items: TraceItem[]
  total: number
  pageNum: number
  pageSize: number
}

/**
 * Span DTO
 */
export interface SpanDTO {
  id: string
  traceId: string
  parentId: string
  service: string
  operation: string
  duration: number
  startPercent: number
  widthPercent: number
  depth: number
  hasError: boolean
  isCriticalPath: boolean
  tags: Record<string, string>
  logs: SpanLog[]
  process: ProcessInfo
}

export interface SpanLog {
  timestamp: string
  message: string
}

export interface ProcessInfo {
  hostname: string
  ip: string
  version: string
}

/**
 * Trace 摘要
 */
export interface TraceSummary {
  rootService: string
  totalDuration: number
  totalSpans: number
  errorCount: number
  services: string[]
}

/**
 * 服务节点
 */
export interface ServiceNode {
  name: string
  hasError: boolean
  spanCount: number
}

/**
 * 服务连接
 */
export interface ServiceLink {
  source: string
  target: string
  hasError: boolean
}

/**
 * 服务依赖图
 */
export interface ServiceGraph {
  nodes: ServiceNode[]
  links: ServiceLink[]
}

/**
 * Trace 详情响应
 */
export interface TraceDetailResponse {
  traceId: string
  timestamp: string
  summary: TraceSummary
  spans: SpanDTO[]
  serviceGraph: ServiceGraph
}

/**
 * 查询 Trace 列表
 */
export function queryTraceList(params: {
  startTime: string
  endTime: string
  serviceName?: string
  minDuration?: number
  hasError?: boolean
  pageNum?: number
  pageSize?: number
}) {
  return request.get<TraceListResponse>('/api/traces', { params })
}

/**
 * 获取 Trace 详情
 */
export function getTraceDetail(traceId: string) {
  return request.get<TraceDetailResponse>(`/api/traces/${traceId}`)
}

/**
 * 获取服务列表
 */
export function getServiceList(startTime: string, endTime: string) {
  return request.get<string[]>('/api/traces/services', {
    params: { startTime, endTime }
  })
}

/**
 * 获取服务统计
 */
export function getServiceStats(startTime: string, endTime: string) {
  return request.get<Array<{
    service_name: string
    span_count: number
    error_count: number
    avg_duration: number
    p95_duration: number
    p99_duration: number
  }>>('/api/traces/services/stats', {
    params: { startTime, endTime }
  })
}
