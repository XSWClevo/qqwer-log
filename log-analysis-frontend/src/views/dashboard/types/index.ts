// Dashboard 类型定义

export interface MachineStatus {
  cpuUsage: number
  memoryUsage: number
  diskFree: string
  diskTotal: string
}

export interface LogPipeline {
  ingestRate: number[]
  ingestRateTimes: string[]
  processingDelay: number
}

export interface CoreOverview {
  todayTotal: number
  errorRate: number
  warnRate: number
  infoRate: number
}

export interface DatabaseStatus {
  clusterStatus: 'healthy' | 'warning' | 'error'
  storageUsed: string
  storageTotal: string
  queryTps: number
}

export interface LogTrendItem {
  time: string
  info: number
  warn: number
  error: number
  fatal: number
}

export interface LevelDistribution {
  severity: string
  count: number
  color: string
}

export interface TopItem {
  name: string
  count: number
}

export interface ExceptionItem {
  className: string
  service: string
  count: number
}

export interface LogRecord {
  id: string
  timestamp: string
  severity: 'INFO' | 'WARN' | 'ERROR' | 'FATAL'
  hostname: string
  appname: string
  message: string
  rawData?: string
  jsonData?: Record<string, unknown>
}

export interface DashboardData {
  machineStatus: MachineStatus
  logPipeline: LogPipeline
  coreOverview: CoreOverview
  databaseStatus: DatabaseStatus
  logTrend: LogTrendItem[]
  levelDistribution: LevelDistribution[]
  topHosts: TopItem[]
  topApps: TopItem[]
  topExceptions: ExceptionItem[]
  realtimeLogs: LogRecord[]
}
