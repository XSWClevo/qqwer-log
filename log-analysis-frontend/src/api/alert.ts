import request from '@/utils/request'
import type { AxiosRequestConfig } from 'axios'

export type AlertSeverity = string
export type AlertRuleStatus = string
export type AlertNotificationStatus = string

export interface ApiResult<T> {
  code: number
  message: string
  data: T
  timestamp?: number
  traceId?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages?: number
}

export interface AlertAggregateCondition {
  function?: string
  field?: string
}

export interface AlertTriggerCondition {
  operator?: string
  threshold?: number | string
  warningThreshold?: number | string
  recoveryThreshold?: number | string
  timeWindow?: string
}

export interface AlertMonitorOptions {
  notifyNoData?: boolean
  noDataTimeframe?: string
  requireFullWindow?: boolean
  evaluationDelaySeconds?: number
  newGroupDelaySeconds?: number
  renotifyIntervalMinutes?: number
  renotifyOccurrences?: number
  includeTags?: boolean
  priority?: string
  team?: string
  tags?: string[]
  alertMode?: string
  escalationMessage?: string
}

export interface AlertFieldFilter {
  field: string
  type: 'include' | 'exclude'
  values: string[]
}

export interface AlertTextCondition {
  operator: 'contains' | 'notContains' | 'equals' | 'notEquals'
  value: string
}

export interface AlertQueryFilters {
  fieldFilters?: AlertFieldFilter[]
  messageConditions?: AlertTextCondition[]
  rawConditions?: AlertTextCondition[]
}

export interface AlertThreshold {
  level?: string
  operator?: string
  threshold?: number | string
  timeWindow?: string
}

export interface AlertThresholds {
  critical?: AlertThreshold
  warning?: AlertThreshold
  recovery?: AlertThreshold
}

export interface AlertCurrentState {
  state?: string
  previousState?: string
  datasourceId?: string
  tableName?: string
  groupKey?: string
  groupValues?: Record<string, any>
  lastValue?: number | string
  lastThreshold?: number | string
  lastEvaluatedAt?: string
  lastStateChangedAt?: string
  lastNotifiedAt?: string
  renotifyCount?: number
}

export interface AlertEvaluationRun {
  id?: number
  startedAt?: string
  finishedAt?: string
  windowStart?: string
  windowEnd?: string
  status?: string
  matchedCount?: number
  errorMessage?: string
  details?: Record<string, any>
}

export interface AlertDowntimeStatus {
  active?: boolean
  downtimeId?: number
  reason?: string
  startsAt?: string
  endsAt?: string
}

export interface AlertCondition {
  query?: string
  filters?: AlertQueryFilters
  groupBy?: string | string[]
  aggregate?: AlertAggregateCondition
  trigger?: AlertTriggerCondition
  options?: AlertMonitorOptions
  [key: string]: any
}

export interface AlertRule {
  id: number
  name: string
  description?: string
  ruleType?: string
  scopeType?: string
  categoryCodes?: string[]
  datasourceIds?: string[]
  tableNames?: string[]
  condition?: AlertCondition
  thresholds?: AlertThresholds
  monitorOptions?: AlertMonitorOptions
  currentState?: AlertCurrentState
  lastEvaluation?: AlertEvaluationRun
  downtimeStatus?: AlertDowntimeStatus
  evalEvery?: string
  consecutiveHits?: number
  severity?: AlertSeverity
  notificationChannels?: string[]
  messageTemplate?: string
  silencePeriod?: number
  enabled?: boolean
  createdBy?: number
  createdAt?: string
  updatedAt?: string
  lastTriggeredAt?: string
  triggerCount?: number
  type?: string
  triggerConditionSummary?: string
}

export interface AlertRulePayload {
  name: string
  description?: string
  ruleType?: string
  scopeType?: string
  categoryCodes?: string[]
  datasourceIds?: string[]
  tableNames?: string[]
  condition: AlertCondition
  thresholds: AlertThresholds
  monitorOptions?: AlertMonitorOptions
  evalEvery?: string
  consecutiveHits?: number
  severity: string
  notificationChannels?: string[]
  messageTemplate?: string
  silencePeriod?: number
  enabled?: boolean
}

export interface AlertRuleQueryParams {
  keyword?: string
  status?: AlertRuleStatus | ''
  severity?: string
  type?: string
  channel?: string
  pageNum?: number
  pageSize?: number
}

export interface AlertNotificationResult {
  channel?: string
  status?: 'success' | 'failed' | string
  message?: string
  sentAt?: string
}

export interface AlertEvent {
  id: number
  ruleId?: number
  ruleName?: string
  severity?: AlertSeverity
  state?: string
  previousState?: string
  thresholdLevel?: string
  message?: string
  logData?: Record<string, any>
  evaluationRunId?: number
  triggeredAt?: string
  acknowledged?: boolean
  acknowledgedBy?: number
  acknowledgedAt?: string
  triggeredValue?: string
  relatedEntity?: string
  notificationStatus?: AlertNotificationStatus | string
  notificationResults?: AlertNotificationResult[]
  contextLogs?: Record<string, any>[]
}

export interface AlertEventQueryParams {
  timeRange?: string
  startTime?: string
  endTime?: string
  keyword?: string
  severity?: string
  ruleId?: number
  notificationStatus?: AlertNotificationStatus | ''
  pageNum?: number
  pageSize?: number
}

export interface AlertTrend {
  timestamps?: string[]
  critical?: number[]
  warning?: number[]
  info?: number[]
}

const apiRequest = <T>(config: AxiosRequestConfig) => request(config) as unknown as Promise<ApiResult<T>>

// ==================== 告警规则 API ====================

export function queryAlertRules(params: AlertRuleQueryParams) {
  return apiRequest<PageResult<AlertRule>>({
    url: '/api/alert/rules',
    method: 'get',
    params
  })
}

export function getAlertRuleById(id: number) {
  return apiRequest<AlertRule>({
    url: `/api/alert/rules/${id}`,
    method: 'get'
  })
}

export function createAlertRule(data: AlertRulePayload) {
  return apiRequest<AlertRule>({
    url: '/api/alert/rules',
    method: 'post',
    data
  })
}

export function updateAlertRule(id: number, data: Partial<AlertRulePayload>) {
  return apiRequest<AlertRule>({
    url: `/api/alert/rules/${id}`,
    method: 'put',
    data
  })
}

export function deleteAlertRule(id: number) {
  return apiRequest<null>({
    url: `/api/alert/rules/${id}`,
    method: 'delete'
  })
}

export function toggleAlertRuleStatus(id: number, enabled: boolean) {
  return apiRequest<AlertRule>({
    url: `/api/alert/rules/${id}/status`,
    method: 'patch',
    params: { enabled }
  })
}

export function duplicateAlertRule(id: number) {
  return apiRequest<AlertRule>({
    url: `/api/alert/rules/${id}/duplicate`,
    method: 'post'
  })
}

export function testAlertRule(id: number) {
  return apiRequest<Record<string, any>>({
    url: `/api/alert/rules/${id}/test`,
    method: 'post'
  })
}

// ==================== 告警事件 API ====================

export function queryAlertEvents(params: AlertEventQueryParams) {
  return apiRequest<PageResult<AlertEvent>>({
    url: '/api/alert/events',
    method: 'get',
    params
  })
}

export function getAlertEventById(id: number) {
  return apiRequest<AlertEvent>({
    url: `/api/alert/events/${id}`,
    method: 'get'
  })
}

export function getAlertTrend(timeRange: string = '24h') {
  return apiRequest<AlertTrend>({
    url: '/api/alert/events/trend',
    method: 'get',
    params: { timeRange }
  })
}

export function acknowledgeAlertEvent(id: number) {
  return apiRequest<null>({
    url: `/api/alert/events/${id}/acknowledge`,
    method: 'post'
  })
}
