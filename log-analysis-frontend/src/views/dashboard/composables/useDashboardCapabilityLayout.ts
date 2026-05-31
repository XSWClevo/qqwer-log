import type { DashboardCapability, DashboardCapabilityView, DashboardInsightTile, DashboardWorkspaceData } from '../types'

const TILE_DEFINITIONS: Partial<Record<DashboardCapabilityView, Omit<DashboardInsightTile, 'status' | 'emptyText'>>> = {
  trend: {
    key: 'trend',
    title: '全域流量趋势',
    description: '按级别观察日志波峰、异常抬升与写入节奏。',
    view: 'trend',
    capabilityKey: 'trend',
    emphasis: 'hero'
  },
  severity: {
    key: 'severity',
    title: '风险级别分布',
    description: '聚焦错误、严重日志在当前时间窗内的占比结构。',
    view: 'severity',
    capabilityKey: 'severity',
    emphasis: 'feature'
  },
  errors: {
    key: 'errors',
    title: '高频错误模式',
    description: '快速锁定重复出现的异常消息与故障模式。',
    view: 'errors',
    capabilityKey: 'errors',
    emphasis: 'feature'
  },
  'recent-logs': {
    key: 'recent-logs',
    title: '高危日志样本',
    description: '聚焦最近风险日志，便于快速下钻定位。',
    view: 'recent-logs',
    capabilityKey: 'recent-logs',
    emphasis: 'feature'
  },
  hosts: {
    key: 'hosts',
    title: '主机热度分布',
    description: '展示当前时间窗最活跃的日志来源主机。',
    view: 'hosts',
    capabilityKey: 'hosts',
    emphasis: 'support'
  },
  apps: {
    key: 'apps',
    title: '应用热度分布',
    description: '展示最活跃的应用与服务入口。',
    view: 'apps',
    capabilityKey: 'apps',
    emphasis: 'support'
  }
}

const EMPTY_TEXT_MAP: Record<string, string> = {
  trend: '当前数据集暂无可展示的趋势序列。',
  severity: '当前数据集暂无级别分布结果。',
  errors: '当前时间窗内没有可聚合的高频错误消息。',
  'recent-logs': '当前时间窗内没有高危日志样本。',
  hosts: '当前数据集暂无主机排行。',
  apps: '当前数据集暂无应用排行。'
}

const pushTile = (tiles: DashboardInsightTile[], capability: DashboardCapability, view: DashboardCapabilityView) => {
  if (tiles.some(item => item.view === view)) {
    return
  }

  const definition = TILE_DEFINITIONS[view]
  if (!definition) {
    return
  }

  tiles.push({
    ...definition,
    status: capability.supported ? 'ready' : 'empty',
    emptyText: capability.reason || EMPTY_TEXT_MAP[definition.key] || '当前卡片暂无数据。'
  })
}

export const buildDashboardLayout = (workspace: DashboardWorkspaceData): DashboardInsightTile[] => {
  const capabilityMap = new Map(workspace.capabilities.map(item => [item.view, item]))
  const orderedViews: DashboardCapabilityView[] = ['trend', 'severity', 'errors', 'recent-logs', 'hosts', 'apps']
  const tiles: DashboardInsightTile[] = []

  orderedViews.forEach(view => {
    const capability = capabilityMap.get(view)
    if (!capability) {
      return
    }

    if (capability.supported) {
      pushTile(tiles, capability, view)
      return
    }

    const fallbackView = capability.fallbackView
    if (fallbackView) {
      const fallbackViewKey = fallbackView as DashboardCapabilityView
      const fallbackCapability = capabilityMap.get(fallbackViewKey)
      if (fallbackCapability) {
        pushTile(tiles, fallbackCapability, fallbackViewKey)
        return
      }
    }

    pushTile(tiles, capability, view)
  })

  if (!tiles.length) {
    const errorTile = TILE_DEFINITIONS.errors
    if (!errorTile) {
      return []
    }

    return [
      {
        key: errorTile.key,
        title: errorTile.title,
        description: errorTile.description,
        view: errorTile.view,
        capabilityKey: errorTile.capabilityKey,
        emphasis: errorTile.emphasis,
        status: 'empty',
        emptyText: '当前没有可展示的图表能力，请先选择有效数据集。'
      }
    ]
  }

  return tiles.slice(0, 5)
}
