import request from '@/utils/request'
import type { LogQueryRequest, LogQueryResponse, TimeSeriesPoint, LogContextRequest, LogContextResponse } from '@/types/log'

/**
 * 日志查询API
 */

// 字段信息类型
export interface FieldInfo {
  name: string
  type: string
  label: string
  isTimestamp: boolean
  isStatsDimension: boolean
  isContentField: boolean
}

// 获取数据源的表结构（字段列表）
export function getDatasourceSchema(datasourceId: string) {
  return request<FieldInfo[]>({
    url: `/api/stats/datasource/${datasourceId}/schema`,
    method: 'GET'
  })
}

// 查询日志列表
export function queryLogs(data: LogQueryRequest) {
  return request<LogQueryResponse>({
    url: '/api/stats/logs/query',
    method: 'POST',
    data
  })
}

// 查询日志上下文
export function queryLogContext(data: LogContextRequest) {
  return request<LogContextResponse>({
    url: '/api/stats/logs/context',
    method: 'POST',
    data
  })
}

// 查询时间序列数据
export function queryTimeSeries(data: Partial<LogQueryRequest> & { granularity?: string; datasourceId?: string }) {
  return request<{ granularity: string; series: TimeSeriesPoint[] }>({
    url: '/api/stats/timeseries',
    method: 'POST',
    data
  })
}

// 查询字段统计
export function queryFieldStats(data: {
  datasourceId?: string
  startTime: string
  endTime: string
  dimensions: string[]
  metrics?: string[]
  useMcp?: boolean
}) {
  return request<{
    dimensions: string[]
    metrics: string[]
    data: Record<string, Array<{ value: string; count: number }>>
  }>({
    url: '/api/stats/query',
    method: 'POST',
    data
  })
}

// 查询字段时序数据
export function queryFieldTimeSeries(params: {
  field: string
  value: string
  startTime: string
  endTime: string
  granularity?: string
}) {
  return request<{
    field: string
    value: string
    granularity: string
    series: TimeSeriesPoint[]
  }>({
    url: '/api/stats/field-timeseries',
    method: 'POST',
    params
  })
}

// 导出日志
export function exportLogs(data: LogQueryRequest, format: 'csv' | 'xlsx' = 'csv') {
  return request<string>({
    url: '/api/stats/export',
    method: 'POST',
    data,
    params: { format }
  })
}

// AI查询请求类型
export interface AiQueryRequest {
  query: string
  datasourceId?: string
  datasourceIds?: string[] // 多数据源联合查询
}

// AI查询响应类型
export interface AiQueryResponse {
  success: boolean
  sql: string | null
  result: any
  error: string | null
  sqlGenerationTime: number | null
  sqlExecutionTime: number | null
  totalExecutionTime: number | null
}

// AI自然语言查询
export function aiQuery(data: AiQueryRequest) {
  return request<AiQueryResponse>({
    url: '/api/stats/logs/ai-query',
    method: 'POST',
    data
  })
}
