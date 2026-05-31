// Legacy types kept for existing dashboard child components that may still be reused.

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
  meta?: string
}

export interface ExceptionItem {
  className: string
  service: string
  count: number
}

export interface LogRecord {
  id: string
  timestamp: string
  severity: 'INFO' | 'WARN' | 'ERROR' | 'FATAL' | 'CRITICAL'
  hostname: string
  appname: string
  message: string
  rawData?: string
  jsonData?: Record<string, unknown>
}

export type DashboardViewStatus = 'idle' | 'loading' | 'ready' | 'empty' | 'error'
export type DashboardTone = 'primary' | 'success' | 'warning' | 'danger' | 'info' | 'neutral'

export interface DashboardMetricCard {
  key: string
  label: string
  value: string
  hint?: string
  footnote?: string
  badge?: string
  tone?: DashboardTone
}

export interface DashboardCapability {
  key: string
  supported: boolean
  reason?: string
  fallbackView?: string
}

export interface DashboardMetricDrilldown {
  metricKey: string
  title?: string
  description?: string
  unit?: string
}

export interface DashboardStorageVolume {
  value?: number
  unit?: string
  displayValue?: string
}

export interface DashboardLogKpis {
  totalLogs?: number
  currentEps?: number
  errorCount?: number
  criticalCount?: number
  errorRate?: number
  activeHostCount?: number
  activeAppCount?: number
  storageVolume?: DashboardStorageVolume | null
}

export interface DashboardDatasetContext {
  datasourceId?: string
  datasourceName: string
  databaseName?: string
  tableName?: string
  source?: string
  status?: string
  totalRows?: number
  latestLogTime?: string
  hasData?: boolean
}

export interface DashboardWarning {
  scope?: string
  level?: 'info' | 'warning' | 'error'
  message: string
}

export interface DashboardEmptyState {
  title: string
  description: string
  actionLabel?: string
  actionRoute?: string
}

export interface DashboardPanelState<T> {
  status: DashboardViewStatus
  items: T[]
  emptyText?: string
}

export interface DashboardWorkspaceData {
  status: DashboardViewStatus
  datasetContext: DashboardDatasetContext | null
  availableDatasets: DashboardDatasetContext[]
  capabilities?: DashboardCapability[]
  metricDrilldowns?: DashboardMetricDrilldown[]
  logKpis?: DashboardLogKpis | null
  platformMetrics: DashboardMetricCard[]
  logMetrics: DashboardMetricCard[]
  logTrend: DashboardPanelState<LogTrendItem>
  severityDistribution: DashboardPanelState<LevelDistribution>
  topHosts: DashboardPanelState<TopItem>
  topApps: DashboardPanelState<TopItem>
  topErrors: DashboardPanelState<TopItem>
  recentLogs: DashboardPanelState<LogRecord>
  warnings: DashboardWarning[]
  emptyState: DashboardEmptyState | null
  lastUpdatedLabel: string
}
