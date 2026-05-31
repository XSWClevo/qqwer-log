<template>
  <section class="insight-board" data-testid="adaptive-insight-board">
    <component
      :is="resolveComponent(tile.view)"
      v-for="tile in views"
      :key="tile.key"
      v-bind="resolveProps(tile)"
      class="board-item"
    >
      <template v-if="tile.view === 'hosts' || tile.view === 'apps' || tile.view === 'errors'" #default>
        <div class="rank-list">
          <article
            v-for="item in resolveList(tile.view).items"
            :key="item.name"
            class="rank-item"
          >
            <div>
              <strong>{{ item.name }}</strong>
              <span v-if="item.meta">{{ item.meta }}</span>
            </div>
            <em>{{ item.count.toLocaleString('zh-CN') }}</em>
          </article>
        </div>
      </template>

      <template v-else-if="tile.view === 'recent-logs'" #default>
        <div class="log-list">
          <article
            v-for="item in workspace.recentLogs.items.slice(0, 6)"
            :key="item.id"
            class="log-item"
          >
            <div class="log-item-head">
              <strong>{{ item.severity }}</strong>
              <span>{{ item.timestamp }}</span>
            </div>
            <p>{{ item.message }}</p>
            <footer>{{ item.hostname }} / {{ item.appname }}</footer>
          </article>
        </div>
      </template>
    </component>
  </section>
</template>

<script setup lang="ts">
import InsightPanel from './InsightPanel.vue'
import LogTrendChart from './LogTrendChart.vue'
import LevelDistributionChart from './LevelDistributionChart.vue'
import type { DashboardInsightTile, DashboardPanelState, DashboardWorkspaceData, TopItem } from '../types'

const props = defineProps<{
  views: DashboardInsightTile[]
  workspace: DashboardWorkspaceData
}>()

const resolveComponent = (view: DashboardInsightTile['view']) => {
  if (view === 'trend') {
    return LogTrendChart
  }
  if (view === 'severity') {
    return LevelDistributionChart
  }
  return InsightPanel
}

const resolveList = (view: DashboardInsightTile['view']): DashboardPanelState<TopItem> => {
  if (view === 'hosts') {
    return props.workspace.topHosts
  }
  if (view === 'apps') {
    return props.workspace.topApps
  }
  return props.workspace.topErrors
}

const resolveProps = (tile: DashboardInsightTile) => {
  if (tile.view === 'trend') {
    return { data: props.workspace.logTrend }
  }
  if (tile.view === 'severity') {
    return { data: props.workspace.severityDistribution }
  }
  if (tile.view === 'recent-logs') {
    return {
      title: tile.title,
      description: tile.description,
      status: props.workspace.recentLogs.status,
      emptyTitle: tile.title,
      emptyDescription: props.workspace.recentLogs.emptyText || tile.emptyText
    }
  }

  const panel = resolveList(tile.view)
  return {
    title: tile.title,
    description: tile.description,
    status: panel.status,
    emptyTitle: tile.title,
    emptyDescription: panel.emptyText || tile.emptyText
  }
}
</script>

<style scoped lang="scss">
.insight-board {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.board-item {
  min-width: 0;
}

.rank-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.log-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rank-item {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--macos-bg-secondary) 88%, transparent);

  strong {
    display: block;
    margin-bottom: 4px;
    color: var(--macos-text-primary);
  }

  span {
    font-size: 12px;
    color: var(--macos-text-secondary);
  }

  em {
    font-style: normal;
    font-weight: 600;
    color: var(--macos-text-primary);
  }
}

.log-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--macos-bg-secondary) 88%, transparent);

  p {
    margin: 0;
    font-size: 13px;
    line-height: 1.6;
    color: var(--macos-text-primary);
  }

  footer {
    font-size: 12px;
    color: var(--macos-text-secondary);
  }
}

.log-item-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;

  strong {
    color: var(--macos-text-primary);
  }

  span {
    font-size: 12px;
    color: var(--macos-text-secondary);
  }
}

@media (max-width: 960px) {
  .insight-board {
    grid-template-columns: 1fr;
  }
}
</style>
