import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as dashboardApi from '@/api/dashboard'
import type {
  DashboardCapability,
  DashboardDatasetContext,
  DashboardLogKpis,
  DashboardMetricCard,
  DashboardMetricDrilldown,
  DashboardPanelState,
  DashboardTone,
  DashboardWorkspaceData,
  DashboardWarning,
  LevelDistribution,
  LogRecord,
  LogTrendItem,
  TopItem
} from '../types'

const LEVEL_COLORS: Record<string, string> = {
  INFO: '#1890FF',
  WARN: '#FAAD14',
  WARNING: '#FAAD14',
  ERROR: '#FF4D4F',
  FATAL: '#722ED1',
  CRITICAL: '#722ED1'
}

const createEmptyPanel = <T>(emptyText: string): DashboardPanelState<T> => ({
  status: 'empty',
  items: [],
  emptyText
})

const createIdleWorkspace = (): DashboardWorkspaceData => ({
  status: 'idle',
  datasetContext: null,
  availableDatasets: [],
  capabilities: [],
  metricDrilldowns: [],
  logKpis: null,
  platformMetrics: [],
  logMetrics: [],
  logTrend: createEmptyPanel<LogTrendItem>('请选择可用数据集后查看趋势。'),
  severityDistribution: createEmptyPanel<LevelDistribution>('暂无级别分布数据。'),
  topHosts: createEmptyPanel<TopItem>('暂无主机排行。'),
  topApps: createEmptyPanel<TopItem>('暂无应用排行。'),
  topErrors: createEmptyPanel<TopItem>('暂无高频错误消息。'),
  recentLogs: createEmptyPanel<LogRecord>('暂无高危日志。'),
  warnings: [],
  emptyState: null,
  lastUpdatedLabel: '未刷新'
})

const formatNumber = (value?: number | null, fallback = '0') => {
  if (value == null || Number.isNaN(Number(value))) {
    return fallback
  }
  return Number(value).toLocaleString('zh-CN')
}

const formatPercent = (value?: number | null) => {
  if (value == null || Number.isNaN(Number(value))) {
    return '0%'
  }
  return `${Number(value).toFixed(Number(value) >= 10 ? 1 : 2)}%`
}

const formatRate = (value?: number | null, suffix = '/s') => {
  if (value == null || Number.isNaN(Number(value))) {
    return `0${suffix}`
  }
  return `${Number(value).toFixed(Number(value) >= 100 ? 0 : 1)}${suffix}`
}

const formatDateLabel = () => {
  return new Date().toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

const mapWarnings = (warnings: unknown): DashboardWarning[] => {
  if (!Array.isArray(warnings)) {
    return []
  }
  return warnings
    .map((item: any) => {
      if (typeof item === 'string') {
        return { message: item, level: 'warning' as const }
      }
      return {
        scope: item?.scope,
        level: item?.level || 'warning',
        message: item?.message || item?.reason || '存在部分降级信息'
      }
    })
    .filter(item => item.message)
}

const mapDatasetContext = (datasetContext: any): DashboardDatasetContext | null => {
  if (!datasetContext) {
    return null
  }
  return {
    datasourceId: datasetContext.datasourceId,
    datasourceName: datasetContext.datasourceName || datasetContext.displayName || '未命名数据集',
    databaseName: datasetContext.databaseName,
    tableName: datasetContext.tableName,
    source: datasetContext.source,
    status: datasetContext.status,
    totalRows: datasetContext.totalRows,
    latestLogTime: datasetContext.latestLogTime,
    hasData: datasetContext.hasData
  }
}

const mapPlatformMetrics = (platformHealth: any): DashboardMetricCard[] => [
  {
    key: 'vector-hosts',
    label: '在线 Vector 主机',
    value: `${platformHealth?.onlineVectorHosts ?? 0} / ${platformHealth?.totalVectorHosts ?? 0}`,
    hint: '按最后心跳与机器状态综合判断',
    footnote: platformHealth?.lastHeartbeatTime ? `最近心跳 ${platformHealth.lastHeartbeatTime}` : '暂无心跳数据',
    contextValue: platformHealth?.clickHouseStatus === 'UP' ? '采集与存储链路可用' : '建议检查存储链路',
    capabilityKey: 'dataset',
    tone: 'primary'
  },
  {
    key: 'throughput',
    label: '5 分钟吞吐',
    value: formatRate(platformHealth?.pipelineThroughputLast5m),
    hint: '最近 5 分钟 pipeline 输出速率',
    delta: platformHealth?.pipelineThroughputLast5m ? '活跃' : '低流量',
    capabilityKey: 'trend',
    tone: 'success'
  },
  {
    key: 'component-errors',
    label: '5 分钟组件错误',
    value: formatNumber(platformHealth?.componentErrorsLast5m),
    hint: 'Vector 组件异常累计数',
    contextValue: Number(platformHealth?.componentErrorsLast5m || 0) > 0 ? '需优先排查' : '当前未见异常',
    capabilityKey: 'warnings',
    tone: Number(platformHealth?.componentErrorsLast5m || 0) > 0 ? 'warning' : 'neutral'
  },
  {
    key: 'queryable-datasets',
    label: '可查询数据集',
    value: formatNumber(platformHealth?.queryableDatasetCount),
    hint: '来自 queryable ClickHouse sink',
    badge: platformHealth?.clickHouseStatus || 'unknown',
    contextValue: platformHealth?.clickHouseStatus === 'UP' ? '可以进行下钻查询' : '查询能力受限',
    capabilityKey: 'dataset',
    tone: platformHealth?.clickHouseStatus === 'UP' ? 'success' : 'warning'
  }
]

const mapLogKpis = (logKpis: any): DashboardLogKpis | null => {
  if (!logKpis || typeof logKpis !== 'object') {
    return null
  }

  return {
    totalLogs: logKpis.totalLogs,
    currentEps: logKpis.currentEps,
    errorCount: logKpis.errorCount,
    criticalCount: logKpis.criticalCount,
    errorRate: logKpis.errorRate,
    activeHostCount: logKpis.activeHostCount,
    activeAppCount: logKpis.activeAppCount,
    storageVolume: logKpis.storageVolume || null
  }
}

const mapLogMetrics = (logKpis: DashboardLogKpis | null, datasetContext: DashboardDatasetContext | null): DashboardMetricCard[] => {
  if (!datasetContext) {
    return []
  }

  return [
    {
      key: 'total-logs',
      label: '日志总量',
      value: formatNumber(logKpis?.totalLogs),
      hint: '当前时间范围内命中的日志总数',
      contextValue: datasetContext.tableName ? `来源 ${datasetContext.tableName}` : '当前选中数据集',
      capabilityKey: 'trend',
      tone: 'primary'
    },
    {
      key: 'current-eps',
      label: '当前 EPS',
      value: formatRate(logKpis?.currentEps),
      hint: '当前数据集实时写入速率',
      delta: Number(logKpis?.currentEps || 0) > 0 ? '实时写入中' : '暂未写入',
      capabilityKey: 'trend',
      tone: 'success'
    },
    {
      key: 'error-count',
      label: '错误日志',
      value: formatNumber(logKpis?.errorCount),
      hint: 'ERROR / FATAL / CRITICAL',
      contextValue: Number(logKpis?.errorCount || 0) > 0 ? '建议查看高频错误' : '当前错误量稳定',
      capabilityKey: 'errors',
      tone: 'danger'
    },
    {
      key: 'critical-count',
      label: '高危日志',
      value: formatNumber(logKpis?.criticalCount),
      hint: 'FATAL / CRITICAL',
      contextValue: Number(logKpis?.criticalCount || 0) > 0 ? '存在高优先级风险' : '当前未发现高危',
      capabilityKey: 'recent-logs',
      tone: 'warning'
    },
    {
      key: 'error-rate',
      label: '错误率',
      value: formatPercent(logKpis?.errorRate),
      hint: '错误日志占总量比例',
      delta: Number(logKpis?.errorRate || 0) >= 10 ? '偏高' : '平稳',
      capabilityKey: 'severity',
      tone: 'warning'
    },
    {
      key: 'active-entities',
      label: '活跃范围',
      value: `${logKpis?.activeHostCount ?? 0} 主机 / ${logKpis?.activeAppCount ?? 0} 应用`,
      hint: '按 hostname 与 appname 去重',
      contextValue: Number(logKpis?.activeHostCount || 0) > Number(logKpis?.activeAppCount || 0) ? '主机分布更广' : '应用分布更广',
      capabilityKey: 'hosts',
      tone: 'neutral'
    }
  ]
}

const mapTrend = (rawTrend: any): DashboardPanelState<LogTrendItem> => {
  if (!rawTrend) {
    return createEmptyPanel<LogTrendItem>('暂无趋势数据。')
  }

  if (Array.isArray(rawTrend.items)) {
    return {
      status: rawTrend.items.length ? 'ready' : 'empty',
      items: rawTrend.items,
      emptyText: '暂无趋势数据。'
    }
  }

  if (Array.isArray(rawTrend.timestamps) && Array.isArray(rawTrend.series)) {
    const seriesMap = rawTrend.series.reduce((acc: Record<string, any>, series: any) => {
      acc[(series.severity || '').toUpperCase()] = series
      return acc
    }, {})
    const items = rawTrend.timestamps.map((timestamp: string, index: number) => ({
      time: timestamp.includes(' ') ? timestamp.split(' ')[1]?.slice(0, 5) || timestamp : timestamp,
      info: seriesMap.INFO?.data?.[index] || 0,
      warn: seriesMap.WARN?.data?.[index] || seriesMap.WARNING?.data?.[index] || 0,
      error: seriesMap.ERROR?.data?.[index] || 0,
      fatal: seriesMap.FATAL?.data?.[index] || seriesMap.CRITICAL?.data?.[index] || 0
    }))
    return {
      status: items.length ? 'ready' : 'empty',
      items,
      emptyText: '暂无趋势数据。'
    }
  }

  return createEmptyPanel<LogTrendItem>('暂无趋势数据。')
}

const mapSeverityDistribution = (rawDistribution: any, rawTrend: any): DashboardPanelState<LevelDistribution> => {
  const items = Array.isArray(rawDistribution?.items)
    ? rawDistribution.items
    : Array.isArray(rawTrend?.series)
      ? rawTrend.series.map((series: any) => ({
          severity: (series.severity || '').toUpperCase(),
          count: series.total || 0,
          color: LEVEL_COLORS[(series.severity || '').toUpperCase()] || '#909399'
        }))
      : []

  return {
    status: items.length ? 'ready' : 'empty',
    items: items.map((item: any) => ({
      severity: item.severity,
      count: item.count,
      color: item.color || LEVEL_COLORS[(item.severity || '').toUpperCase()] || '#909399'
    })),
    emptyText: '暂无级别分布数据。'
  }
}

const mapRankedList = (rawPanel: any, fallbackText: string): DashboardPanelState<TopItem> => {
  const items = Array.isArray(rawPanel?.items)
    ? rawPanel.items.map((item: any) => ({
        name: item.name || item.message || item.label || '-',
        count: item.count || 0,
        meta: item.meta || item.service || item.severity
      }))
    : []

  return {
    status: items.length ? 'ready' : 'empty',
    items,
    emptyText: fallbackText
  }
}

const mapRecentLogs = (rawPanel: any): DashboardPanelState<LogRecord> => {
  const items = Array.isArray(rawPanel?.items)
    ? rawPanel.items.map((item: any, index: number) => ({
        id: item.id || `${index}`,
        timestamp: item.timestamp || item.time || '',
        severity: normalizeSeverity(item.severity),
        hostname: item.hostname || '-',
        appname: item.appname || item.appName || '-',
        message: item.message || item.raw || '-',
        rawData: item.raw || item.rawData
      }))
    : []

  return {
    status: items.length ? 'ready' : 'empty',
    items,
    emptyText: '暂无高危日志。'
  }
}

const normalizeSeverity = (value: unknown): LogRecord['severity'] => {
  switch (String(value || 'INFO').toUpperCase()) {
    case 'WARN':
    case 'WARNING':
      return 'WARN'
    case 'ERROR':
      return 'ERROR'
    case 'FATAL':
      return 'FATAL'
    case 'CRITICAL':
      return 'CRITICAL'
    case 'INFO':
      return 'INFO'
    default:
      return 'UNKNOWN'
  }
}

const buildCapabilities = (workspace: Pick<
  DashboardWorkspaceData,
  'datasetContext' | 'logTrend' | 'severityDistribution' | 'topHosts' | 'topApps' | 'topErrors' | 'recentLogs' | 'warnings'
>): DashboardCapability[] => {
  const hasDataset = Boolean(workspace.datasetContext)
  const hasWarnings = workspace.warnings.length > 0

  return [
    {
      key: 'trend',
      label: '流量趋势',
      supported: workspace.logTrend.status === 'ready',
      view: 'trend',
      priority: 10,
      status: workspace.logTrend.status === 'ready' ? 'ready' : 'fallback',
      reason: workspace.logTrend.emptyText,
      fallbackView: 'severity'
    },
    {
      key: 'severity',
      label: '级别分布',
      supported: workspace.severityDistribution.status === 'ready',
      view: 'severity',
      priority: 9,
      status: workspace.severityDistribution.status === 'ready' ? 'ready' : 'fallback',
      reason: workspace.severityDistribution.emptyText,
      fallbackView: 'errors'
    },
    {
      key: 'errors',
      label: '错误模式',
      supported: workspace.topErrors.status === 'ready',
      view: 'errors',
      priority: 8,
      status: workspace.topErrors.status === 'ready' ? 'ready' : 'fallback',
      reason: workspace.topErrors.emptyText,
      fallbackView: 'recent-logs'
    },
    {
      key: 'recent-logs',
      label: '风险日志',
      supported: workspace.recentLogs.status === 'ready',
      view: 'recent-logs',
      priority: 7,
      status: workspace.recentLogs.status === 'ready' ? 'ready' : 'fallback',
      reason: workspace.recentLogs.emptyText,
      fallbackView: 'hosts'
    },
    {
      key: 'hosts',
      label: '主机排行',
      supported: workspace.topHosts.status === 'ready',
      view: 'hosts',
      priority: 6,
      status: workspace.topHosts.status === 'ready' ? 'ready' : 'fallback',
      reason: workspace.topHosts.emptyText,
      fallbackView: 'apps'
    },
    {
      key: 'apps',
      label: '应用排行',
      supported: workspace.topApps.status === 'ready',
      view: 'apps',
      priority: 5,
      status: workspace.topApps.status === 'ready' ? 'ready' : 'fallback',
      reason: workspace.topApps.emptyText,
      fallbackView: 'dataset'
    },
    {
      key: 'dataset',
      label: '数据集上下文',
      supported: hasDataset,
      view: 'dataset',
      priority: 4,
      status: hasDataset ? 'ready' : 'fallback',
      reason: hasDataset ? undefined : '当前还没有可展示的数据集上下文。',
      fallbackView: 'warnings'
    },
    {
      key: 'warnings',
      label: '风险提醒',
      supported: hasWarnings,
      view: 'warnings',
      priority: 3,
      status: hasWarnings ? 'ready' : 'missing',
      reason: hasWarnings ? undefined : '当前没有额外告警提醒。',
      fallbackView: 'dataset'
    }
  ]
}

const buildMetricDrilldowns = (
  platformMetrics: DashboardMetricCard[],
  logMetrics: DashboardMetricCard[],
  capabilities: DashboardCapability[],
  datasetContext: DashboardDatasetContext | null
): DashboardMetricDrilldown[] => {
  const allMetrics = [...platformMetrics, ...logMetrics]
  const capabilitySet = new Set(capabilities.filter(item => item.supported).map(item => item.view))
  const toneMap = new Map<string, DashboardTone | undefined>(allMetrics.map(metric => [metric.key, metric.tone]))
  const drilldownViews = new Set(['trend', 'severity', 'errors', 'recent-logs', 'hosts', 'apps'])

  const createHighlights = (metric: DashboardMetricCard) => {
    const highlights = [metric.hint, metric.footnote, metric.delta, metric.contextValue].filter(Boolean) as string[]
    if (datasetContext?.datasourceName) {
      highlights.push(`作用数据集：${datasetContext.datasourceName}`)
    }
    return highlights.slice(0, 3)
  }

  return allMetrics.map(metric => {
    const relatedViews = metric.capabilityKey && drilldownViews.has(metric.capabilityKey) && capabilitySet.has(metric.capabilityKey)
      ? [metric.capabilityKey]
      : capabilities
        .filter(item => item.supported && drilldownViews.has(item.view))
        .slice(0, 2)
        .map(item => item.view)

    return {
      metricKey: metric.key,
      title: metric.label,
      description: metric.hint || `${metric.label} 当前正在为指挥视图提供实时信号。`,
      unit: metric.value.replace(/[-\d.,\s/]/g, '').trim() || undefined,
      tone: toneMap.get(metric.key),
      highlights: createHighlights(metric),
      relatedViews,
      status: relatedViews.length ? 'ready' : 'fallback'
    }
  })
}

const normalizeOverview = (data: any): DashboardWorkspaceData => {
  const datasetContext = mapDatasetContext(data?.datasetContext)
  const availableDatasets = Array.isArray(data?.availableDatasets)
    ? data.availableDatasets.map((item: any) => mapDatasetContext(item)).filter(Boolean)
    : []
  const warnings = mapWarnings(data?.warnings)
  const logTrend = mapTrend(data?.logTrend)
  const logKpis = mapLogKpis(data?.logKpis)
  const platformMetrics = mapPlatformMetrics(data?.platformHealth)
  const logMetrics = mapLogMetrics(logKpis, datasetContext)

  const workspace: DashboardWorkspaceData = {
    status: data?.emptyState ? 'empty' : 'ready',
    datasetContext,
    availableDatasets: availableDatasets as DashboardDatasetContext[],
    capabilities: [],
    metricDrilldowns: [],
    logKpis,
    platformMetrics,
    logMetrics,
    logTrend,
    severityDistribution: mapSeverityDistribution(data?.severityDistribution, data?.logTrend),
    topHosts: mapRankedList(data?.topHosts, '暂无主机排行。'),
    topApps: mapRankedList(data?.topApps, '暂无应用排行。'),
    topErrors: mapRankedList(data?.topErrorMessages, '暂无高频错误消息。'),
    recentLogs: mapRecentLogs(data?.recentHighRiskLogs || data?.alertLogs),
    warnings,
    emptyState: data?.emptyState
      ? {
          title: data.emptyState.title || '暂无可统计日志数据集',
          description: data.emptyState.description || '请先在组件库中开启 queryable Sink，或创建日志解析组件。',
          actionLabel: data.emptyState.actionLabel || '前往组件库',
          actionRoute: data.emptyState.actionRoute || '/vector/components'
        }
      : null,
    lastUpdatedLabel: formatDateLabel()
  }

  workspace.capabilities = buildCapabilities(workspace)
  workspace.metricDrilldowns = buildMetricDrilldowns(
    workspace.platformMetrics,
    workspace.logMetrics,
    workspace.capabilities,
    workspace.datasetContext
  )

  if (!workspace.logMetrics.length) {
    workspace.logMetrics = []
  }

  return workspace
}

export function useDashboardData() {
  const loading = ref(false)
  const workspace = ref<DashboardWorkspaceData>(createIdleWorkspace())
  const selectedDatasourceId = ref('')

  const getTimeRange = (timeRange: string): [string, string] => {
    const now = new Date()
    const end = now.toISOString().slice(0, 19).replace('T', ' ')
    let start = new Date(now.getTime() - 60 * 60 * 1000)

    switch (timeRange) {
      case '6h':
        start = new Date(now.getTime() - 6 * 60 * 60 * 1000)
        break
      case '24h':
        start = new Date(now.getTime() - 24 * 60 * 60 * 1000)
        break
      case '7d':
        start = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000)
        break
      default:
        start = new Date(now.getTime() - 60 * 60 * 1000)
        break
    }

    return [start.toISOString().slice(0, 19).replace('T', ' '), end]
  }

  /**
   * 按时间范围和当前选中的数据集拉取 Dashboard 聚合数据。
   */
  const fetchAllData = async (timeRange: string, datasourceId?: string) => {
    loading.value = true
    workspace.value = {
      ...workspace.value,
      status: 'loading'
    }

    try {
      const [startTime, endTime] = getTimeRange(timeRange)
      const response: any = await dashboardApi.getDashboardOverview({
        startTime,
        endTime,
        granularity: 'auto',
        pageNum: 1,
        pageSize: 20,
        datasourceId: datasourceId || selectedDatasourceId.value || undefined
      })
      const responseData = response.data || response
      workspace.value = normalizeOverview(responseData)
      selectedDatasourceId.value = workspace.value.datasetContext?.datasourceId || ''
    } catch (error) {
      console.error('Failed to fetch dashboard data:', error)
      workspace.value = {
        ...createIdleWorkspace(),
        status: 'error',
        warnings: [{ level: 'error', message: '获取 Dashboard 数据失败，请检查后端服务与 ClickHouse 连通性。' }],
        lastUpdatedLabel: formatDateLabel()
      }
      ElMessage.error('获取仪表盘数据失败')
    } finally {
      loading.value = false
    }
  }

  return {
    loading,
    selectedDatasourceId,
    workspace,
    fetchAllData
  }
}
