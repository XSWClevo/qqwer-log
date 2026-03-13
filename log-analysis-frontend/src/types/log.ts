/**
 * 日志查询类型定义
 */

// 日志条目
export interface LogEntry {
  id: string
  timestamp: string
  severity: string       // 数据库字段：日志级别
  source_type: string    // 数据库字段：来源类型
  source_ip?: string     // 源IP地址
  message: string
  hostname: string       // 数据库字段：主机名
  appname?: string       // 数据库字段：应用名
  facility?: string      // 日志设施类型
  procid?: string        // 进程ID
  raw?: string           // 原始日志
  [key: string]: any
}

// 消息条件
export interface MessageCondition {
  operator: 'contains' | 'notContains' | 'equals' | 'notEquals'
  value: string
}

// 筛选条件（统一管理筛选和排除）- 前端内部使用
export interface FilterCondition {
  field: string                    // 字段名: 'hosts', 'levels', 'sources', 'services', 'facilities', 'procids', 'sourceIps'
  type: 'include' | 'exclude'     // 类型: include=筛选, exclude=排除
  value: string                    // 字段值（单个）
}

// 后端API期望的筛选条件格式
export interface FieldFilter {
  field: string
  type: 'include' | 'exclude'
  values: string[]  // 后端期望数组
}

// 日志查询请求
export interface LogQueryRequest {
  // 数据源ID（可选，不传则使用默认的 ClickHouse syslog 表）
  datasourceId?: string

  // 时间范围
  startTime: string
  endTime: string

  // 统一的筛选条件（后端格式）
  fieldFilters?: FieldFilter[]

  // message/raw查询条件
  messageConditions?: MessageCondition[]
  rawConditions?: MessageCondition[]

  // 分页
  pageNum: number
  pageSize: number

  // 是否启用 MCP（仅 ClickHouse 生效）
  useMcp?: boolean
}

// 日志查询响应
export interface LogQueryResponse {
  total: number
  pageNum: number
  pageSize: number
  data: LogEntry[]
}

// 日志上下文查询请求
export interface LogContextRequest {
  // 数据源ID（可选）
  datasourceId?: string

  logId: string
  timestamp: string
  beforeCount?: number
  afterCount?: number

  // 统一的筛选条件（后端格式）
  fieldFilters?: FieldFilter[]
  messageConditions?: MessageCondition[]
  rawConditions?: MessageCondition[]

  // 是否启用 MCP（仅 ClickHouse 生效）
  useMcp?: boolean
}

// 日志上下文查询响应
export interface LogContextResponse {
  beforeLogs: LogEntry[]
  afterLogs: LogEntry[]
  totalBefore: number
  totalAfter: number
}

// 时间范围选项
export type TimeRangeType = '15m' | '1h' | '24h' | '7d' | 'custom'

// 字段统计项
export interface FieldStat {
  value: string
  count: number
  timeSeries?: TimeSeriesPoint[]
}

// 字段统计
export interface FieldStats {
  name: string
  label: string
  topValues: FieldStat[]
}

// 时间序列数据点
export interface TimeSeriesPoint {
  timestamp: string
  count: number
}
