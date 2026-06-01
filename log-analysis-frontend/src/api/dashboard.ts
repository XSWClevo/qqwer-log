import request from '@/utils/request'

export interface DashboardQueryRequest {
  startTime: string
  endTime: string
  granularity?: string
  pageNum?: number
  pageSize?: number
  datasourceId?: string
}

export interface VectorDashboardQueryRequest {
  range?: string
  hostId?: string
}

// 系统指标
export const getSystemMetrics = () => request({ url: '/api/dashboard/metrics', method: 'GET' })

// 日志趋势
export const getLogTrend = (startTime: string, endTime: string, granularity = 'auto') =>
  request({ url: '/api/dashboard/log-trend', method: 'GET', params: { startTime, endTime, granularity } })

// Top 实体
export const getTopEntities = (type: 'host' | 'app', startTime: string, endTime: string) =>
  request({ url: '/api/dashboard/top-entities', method: 'GET', params: { type, startTime, endTime } })

// 重复异常
export const getRecurringExceptions = (startTime: string, endTime: string) =>
  request({ url: '/api/dashboard/recurring-exceptions', method: 'GET', params: { startTime, endTime } })

// 告警日志
export const getAlertLogs = (startTime: string, endTime: string, pageNum = 1, pageSize = 20) =>
  request({ url: '/api/dashboard/alert-logs', method: 'GET', params: { startTime, endTime, pageNum, pageSize } })

// 日志管道
export const getLogPipeline = (startTime: string, endTime: string) =>
  request({ url: '/api/dashboard/log-pipeline', method: 'GET', params: { startTime, endTime } })

// 核心概览
export const getCoreOverview = () => request({ url: '/api/dashboard/core-overview', method: 'GET' })

// 数据库状态
export const getDatabaseStatus = () => request({ url: '/api/dashboard/database-status', method: 'GET' })

// 仪表盘概览 (聚合所有数据)
export const getDashboardOverview = (data: DashboardQueryRequest) =>
  request({ url: '/api/dashboard/overview', method: 'POST', data })

// Vector 主机监控概览
export const getVectorDashboardOverview = (params: VectorDashboardQueryRequest = {}) =>
  request({ url: '/api/dashboard/vector-overview', method: 'GET', params })
