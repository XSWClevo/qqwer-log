import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as dashboardApi from '@/api/dashboard'
import type {
  MachineStatus,
  LogPipeline,
  CoreOverview,
  DatabaseStatus,
  LogTrendItem,
  LevelDistribution,
  TopItem,
  ExceptionItem,
  LogRecord
} from '../types'



export function useDashboardData() {
  const loading = ref(false)
  const machineStatus = ref<MachineStatus>({ cpuUsage: 0, memoryUsage: 0, diskFree: '0', diskTotal: '0' })
  const logPipeline = ref<LogPipeline>({ ingestRate: [], ingestRateTimes: [], processingDelay: 0 })
  const coreOverview = ref<CoreOverview>({ todayTotal: 0, errorRate: 0, warnRate: 0, infoRate: 0 })
  const databaseStatus = ref<DatabaseStatus>({ clusterStatus: 'healthy', storageUsed: '0', storageTotal: '0', queryTps: 0 })
  const logTrend = ref<LogTrendItem[]>([])
  const levelDistribution = ref<LevelDistribution[]>([])
  const topHosts = ref<TopItem[]>([])
  const topApps = ref<TopItem[]>([])
  const topExceptions = ref<ExceptionItem[]>([])
  const realtimeLogs = ref<LogRecord[]>([])

  const getTimeRange = (timeRange: string): [string, string] => {
    const now = new Date()
    const end = now.toISOString().slice(0, 19).replace('T', ' ')
    let start: Date
    
    switch (timeRange) {
      case '1h': start = new Date(now.getTime() - 60 * 60 * 1000); break
      case '6h': start = new Date(now.getTime() - 6 * 60 * 60 * 1000); break
      case '24h': start = new Date(now.getTime() - 24 * 60 * 60 * 1000); break
      case '7d': start = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000); break
      default: start = new Date(now.getTime() - 24 * 60 * 60 * 1000)
    }
    
    const startStr = start.toISOString().slice(0, 19).replace('T', ' ')
    return [startStr, end]
  }

  const fetchAllData = async (timeRange: string) => {
    loading.value = true
    try {
      const [startTime, endTime] = getTimeRange(timeRange)
      
      // 调用聚合接口获取所有数据
      const response: any = await dashboardApi.getDashboardOverview({
        startTime,
        endTime,
        granularity: 'auto',
        pageNum: 1,
        pageSize: 20
      })
      
      // request 工具已经返回 response.data，所以直接访问 data 字段
      const data = response.data || response
      
      // 映射系统指标
      if (data.systemMetrics) {
        const metrics = data.systemMetrics
        machineStatus.value = {
          cpuUsage: Math.round(metrics.cpu?.usagePercent || 0),
          memoryUsage: Math.round(metrics.memory?.usagePercent || 0),
          diskFree: ((metrics.disk?.available || 0) / (1024 * 1024 * 1024)).toFixed(0),
          diskTotal: ((metrics.disk?.total || 0) / (1024 * 1024 * 1024)).toFixed(0)
        }
      }
      
      // 映射日志管道
      if (data.logPipeline) {
        const pipeline = data.logPipeline
        logPipeline.value = {
          ingestRate: pipeline.ingestRateTrends?.map((t: any) => t.count) || [],
          ingestRateTimes: pipeline.ingestRateTrends?.map((t: any) => t.timestamp.slice(11, 16)) || [],
          processingDelay: Math.round(pipeline.processingDelayMs || 0)
        }
      }
      
      // 映射核心概览
      if (data.coreOverview) {
        const overview = data.coreOverview
        coreOverview.value = {
          todayTotal: overview.todayTotalLogs || 0,
          errorRate: overview.errorRate || 0,
          warnRate: overview.warnRate || 0,
          infoRate: overview.infoRate || 0
        }
      }
      
      // 映射数据库状态
      if (data.databaseStatus) {
        const dbStatus = data.databaseStatus
        databaseStatus.value = {
          clusterStatus: dbStatus.clusterStatus || 'healthy',
          storageUsed: dbStatus.storageUsedGb?.toFixed(1) || '0',
          storageTotal: dbStatus.storageTotalGb?.toFixed(0) || '0',
          queryTps: Math.round(dbStatus.queryTps || 0)
        }
      }
      
      // 映射日志趋势
      if (data.logTrend?.series && data.logTrend?.timestamps) {
        const trend = data.logTrend
        console.log('Log trend data:', trend) // 调试日志
        
        // 创建 severity -> series 的映射，忽略大小写
        const seriesMap: Record<string, any> = {}
        trend.series.forEach((s: any) => {
          const severityUpper = (s.severity || '').toUpperCase()
          seriesMap[severityUpper] = s
        })
        
        logTrend.value = trend.timestamps.map((ts: string, idx: number) => ({
          time: ts.includes(' ') ? ts.split(' ')[1]?.slice(0, 5) || ts : ts, // 格式化时间显示
          info: seriesMap['INFO']?.data?.[idx] || 0,
          warn: seriesMap['WARN']?.data?.[idx] || seriesMap['WARNING']?.data?.[idx] || 0,
          error: seriesMap['ERROR']?.data?.[idx] || 0,
          fatal: seriesMap['FATAL']?.data?.[idx] || seriesMap['CRITICAL']?.data?.[idx] || 0
        }))
        
        console.log('Mapped logTrend:', logTrend.value) // 调试日志
        
        // 映射级别分布
        levelDistribution.value = trend.series.map((s: any) => ({
          severity: (s.severity || '').toUpperCase(),
          count: s.total || 0,
          color: getLevelColor(s.severity)
        }))
      }
      
      // 映射 Top 主机
      if (data.topHosts?.items) {
        topHosts.value = data.topHosts.items.map((item: any) => ({
          name: item.name,
          count: item.count
        }))
      }
      
      // 映射 Top 应用
      if (data.topApps?.items) {
        topApps.value = data.topApps.items.map((item: any) => ({
          name: item.name,
          count: item.count
        }))
      }
      
      // 映射异常
      if (data.recurringExceptions?.items) {
        topExceptions.value = data.recurringExceptions.items.slice(0, 8).map((item: any) => ({
          className: item.exceptionClassName || 'Unknown',
          service: item.service || 'unknown',
          count: item.count
        }))
      }
      
      // 映射告警日志
      if (data.alertLogs?.items) {
        realtimeLogs.value = data.alertLogs.items.map((item: any) => ({
          id: item.id,
          timestamp: item.timestamp,
          severity: (item.severity || 'INFO').toUpperCase(),
          hostname: item.hostname,
          appname: item.appName,
          message: item.message,
          rawData: item.raw
        }))
      }
    } catch (error) {
      console.error('Failed to fetch dashboard data:', error)
      ElMessage.error('获取仪表盘数据失败')
    } finally {
      loading.value = false
    }
  }

  const getLevelColor = (severity: string): string => {
    const colorMap: Record<string, string> = {
      'INFO': '#1890FF',
      'WARN': '#FAAD14',
      'WARNING': '#FAAD14',
      'ERROR': '#FF4D4F',
      'FATAL': '#722ED1',
      'CRITICAL': '#722ED1'
    }
    return colorMap[(severity || '').toUpperCase()] || '#909399'
  }

  return {
    loading, machineStatus, logPipeline, coreOverview, databaseStatus,
    logTrend, levelDistribution, topHosts, topApps, topExceptions, realtimeLogs, fetchAllData
  }
}
