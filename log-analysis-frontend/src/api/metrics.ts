import request from '@/utils/request'

/**
 * 指标查询请求
 */
export interface MetricQueryRequest {
  startTime: string
  endTime: string
  metric: string
  aggregation?: string
  groupBy?: string
  filter?: string
  granularity?: string
}

/**
 * 指标查询响应
 */
export interface MetricQueryResponse {
  metric: string
  aggregation: string
  granularity: string
  timestamps: string[]
  series: MetricSeries[]
  stats: MetricStats
  anomalies: Anomaly[]
  shifts: TrendShift[]
}

export interface MetricSeries {
  name: string
  color: string
  data: number[]
  currentValue: number
  avgValue: number
}

export interface MetricStats {
  min: number
  max: number
  avg: number
  current: number
  p95: number
  p99: number
}

export interface Anomaly {
  time: string
  description: string
  value: number
  baseline: number
}

export interface TrendShift {
  type: 'increase' | 'decrease'
  message: string
  changePercent: number
}

/**
 * 查询指标数据
 */
export function queryMetrics(params: MetricQueryRequest) {
  return request.post<MetricQueryResponse>('/api/metrics/query', params)
}

/**
 * 获取可用指标列表
 */
export function getMetricList() {
  return request.get<Array<{
    name: string
    label: string
    unit: string
    category: string
  }>>('/api/metrics/list')
}

/**
 * 获取可用聚合方式
 */
export function getAggregations() {
  return request.get<Array<{
    value: string
    label: string
  }>>('/api/metrics/aggregations')
}

/**
 * 获取可用分组字段
 */
export function getGroupByFields() {
  return request.get<Array<{
    value: string
    label: string
  }>>('/api/metrics/group-by-fields')
}
