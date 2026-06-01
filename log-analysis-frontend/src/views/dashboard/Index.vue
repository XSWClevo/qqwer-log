<template>
  <AppLayout>
    <div
      class="vector-dashboard"
      :class="{ loading }"
      @pointerover="showDashboardTooltip"
      @pointermove="moveDashboardTooltip"
      @pointerout="hideDashboardTooltip"
    >
      <section class="dashboard-toolbar">
        <div class="dashboard-heading">
          <span class="dashboard-kicker">
            <i class="auto-dot" :class="{ active: autoRefresh }"></i>
            Vector 监控
          </span>
          <h1>Vector 运行概览</h1>
          <p>已接入 {{ dashboard.hosts.length }} 台主机，集中查看 CPU、内存、网络、缓冲区和事件流。</p>
        </div>

        <div class="dash-header-actions">
          <label class="top-select">
            <span>时间范围</span>
            <select v-model="timeRange" @change="fetchDashboard()">
              <option value="1h">最近 1 小时</option>
              <option value="6h">最近 6 小时</option>
              <option value="24h">最近 24 小时</option>
              <option value="7d">最近 7 天</option>
            </select>
          </label>
          <button class="dash-header-button" type="button" :disabled="loading" @click="fetchDashboard()">
            <el-icon><Refresh /></el-icon>
          </button>
          <button class="dash-header-button" type="button" @click="toggleAutoRefresh">
            <span class="auto-dot" :class="{ active: autoRefresh }"></span>
            {{ autoRefresh ? '自动刷新' : '手动刷新' }}
          </button>
          <button class="dash-header-primary" type="button">
            <el-icon><Plus /></el-icon>
            添加视图
          </button>
        </div>
      </section>

      <section class="host-stage">
        <div class="panel-title">
          <div>
            <h2>Vector 主机 <span>{{ dashboard.hosts.length }}</span></h2>
            <p>展示已注册 Agent 的 CPU、内存、网络、运行时长和 Vector 版本。</p>
          </div>
          <div class="stage-actions">
            <button class="view-chip active" type="button">列表视图</button>
            <button class="nav-icon" type="button" @click="scrollHosts(-1)">
              <el-icon><ArrowLeft /></el-icon>
            </button>
            <button class="nav-icon" type="button" @click="scrollHosts(1)">
              <el-icon><ArrowRight /></el-icon>
            </button>
          </div>
        </div>

        <div ref="hostScroller" class="host-strip">
          <button
            v-for="host in dashboard.hosts"
            :key="host.id"
            class="host-card"
            :class="[{ selected: activeHost?.id === host.id }, `status-${host.status}`]"
            :data-tooltip="hostTooltip(host)"
            :title="hostTooltip(host)"
            type="button"
            @click="openHostDetails(host)"
          >
            <div class="host-card-head">
              <span class="host-icon">
                <i></i>
              </span>
              <div class="host-identity">
                <strong>{{ host.name }}</strong>
                <span>{{ host.ipAddress }} <em>{{ environmentLabel(host.environment) }}</em></span>
              </div>
              <span class="health-pill">{{ statusLabel(host.status) }}</span>
              <el-icon class="more"><MoreFilled /></el-icon>
            </div>

            <div class="host-metrics">
              <article>
                <span>CPU</span>
                <strong>{{ formatPercent(host.cpuPercent) }}</strong>
                <svg viewBox="0 0 110 24" preserveAspectRatio="none">
                  <polyline :points="linePoints(host.cpuSeries, 110, 24)" />
                  <circle
                    v-for="hit in linePointPositions(host.cpuSeries, 110, 24)"
                    :key="`host-cpu-${host.id}-${hit.point.timestamp}`"
                    class="chart-hit-point"
                    :cx="hit.x"
                    :cy="hit.y"
                    r="6"
                    :data-tooltip="pointTooltip(hit.point, 'CPU', formatPercent)"
                  />
                  <rect
                    v-for="hit in linePointHitZones(host.cpuSeries, 110, 24)"
                    :key="`host-cpu-zone-${host.id}-${hit.point.timestamp}`"
                    class="chart-hit-zone"
                    :x="hit.xStart"
                    y="0"
                    :width="hit.width"
                    height="24"
                    :data-tooltip="pointTooltip(hit.point, 'CPU', formatPercent)"
                  >
                    <title>{{ pointTooltip(hit.point, 'CPU', formatPercent) }}</title>
                  </rect>
                </svg>
              </article>
              <article>
                <span>内存</span>
                <strong>{{ formatPercent(host.memoryPercent) }}</strong>
                <svg viewBox="0 0 110 24" preserveAspectRatio="none">
                  <polyline :points="linePoints(host.memorySeries, 110, 24)" />
                  <circle
                    v-for="hit in linePointPositions(host.memorySeries, 110, 24)"
                    :key="`host-memory-${host.id}-${hit.point.timestamp}`"
                    class="chart-hit-point"
                    :cx="hit.x"
                    :cy="hit.y"
                    r="6"
                    :data-tooltip="pointTooltip(hit.point, '内存', formatPercent)"
                  />
                  <rect
                    v-for="hit in linePointHitZones(host.memorySeries, 110, 24)"
                    :key="`host-memory-zone-${host.id}-${hit.point.timestamp}`"
                    class="chart-hit-zone"
                    :x="hit.xStart"
                    y="0"
                    :width="hit.width"
                    height="24"
                    :data-tooltip="pointTooltip(hit.point, '内存', formatPercent)"
                  >
                    <title>{{ pointTooltip(hit.point, '内存', formatPercent) }}</title>
                  </rect>
                </svg>
              </article>
            </div>

            <footer>
              <span>运行 {{ host.uptime || '-' }}</span>
              <span>版本 {{ host.vectorVersion || '-' }}</span>
            </footer>

            <div class="host-hover-popover">
              <span>网络入 <strong>{{ formatMbps(host.networkInMbps) }}</strong></span>
              <span>网络出 <strong>{{ formatMbps(host.networkOutMbps) }}</strong></span>
              <span>事件/秒 <strong>{{ formatCompact(host.eventsPerSecond) }}</strong></span>
              <span>缓冲区 <strong>{{ formatPercent(host.bufferUsedPercent) }}</strong></span>
            </div>
          </button>

          <div v-if="!dashboard.hosts.length" class="empty-hosts">
            暂无 Vector 主机。Agent 注册后会显示在这里。
          </div>
        </div>
      </section>

      <section v-if="dashboard.warnings.length" class="warning-strip">
        <article v-for="warning in dashboard.warnings" :key="warning">{{ warning }}</article>
      </section>

      <section class="dashboard-grid">
        <article class="panel events-overview span-6">
          <div class="panel-title compact">
            <h3>事件概览 <span>全部主机</span></h3>
          </div>
          <div class="metric-row">
            <article
              v-for="metric in overviewMetrics"
              :key="metric.label"
              class="event-metric"
              :data-tooltip="metricTooltip(metric)"
              :title="metricTooltip(metric)"
            >
              <span>{{ metric.label }}</span>
              <strong>{{ metric.value }}</strong>
              <em :class="{ positive: metric.positive, negative: !metric.positive }">{{ metric.delta }}</em>
              <svg viewBox="0 0 116 26" preserveAspectRatio="none">
                <title>{{ metricTooltip(metric) }}</title>
                <polyline :points="linePoints(metric.series, 116, 26)" />
                <circle
                  v-for="hit in linePointPositions(metric.series, 116, 26)"
                  :key="`${metric.label}-${hit.point.timestamp}`"
                  class="chart-hit-point"
                  :cx="hit.x"
                  :cy="hit.y"
                  r="6"
                  :data-tooltip="pointTooltip(hit.point, metric.label, metric.formatter)"
                />
                <rect
                  v-for="hit in linePointHitZones(metric.series, 116, 26)"
                  :key="`${metric.label}-zone-${hit.point.timestamp}`"
                  class="chart-hit-zone"
                  :x="hit.xStart"
                  y="0"
                  :width="hit.width"
                  height="26"
                  :data-tooltip="pointTooltip(hit.point, metric.label, metric.formatter)"
                >
                  <title>{{ pointTooltip(hit.point, metric.label, metric.formatter) }}</title>
                </rect>
              </svg>
            </article>
          </div>
        </article>

        <article class="panel span-6">
          <div class="panel-title compact">
            <h3>事件趋势</h3>
            <span class="panel-select">时间序列</span>
          </div>
          <div class="legend">
            <span v-for="series in dashboard.eventsOverTime" :key="series.key">
              <i :style="{ background: series.color }"></i>
              {{ series.name }}
            </span>
          </div>
          <svg
            class="main-chart"
            viewBox="0 0 720 210"
            preserveAspectRatio="none"
            :data-tooltip="seriesTooltip(dashboard.eventsOverTime)"
          >
            <title>{{ seriesTooltip(dashboard.eventsOverTime) }}</title>
            <g class="grid-lines">
              <line v-for="line in 5" :key="line" x1="0" x2="720" :y1="line * 36" :y2="line * 36" />
            </g>
            <polyline
              v-for="series in dashboard.eventsOverTime"
              :key="series.key"
              :points="sharedLinePoints(series.points, dashboard.eventsOverTime, 720, 190, 8)"
              :style="{ stroke: series.color }"
            />
            <template v-for="series in dashboard.eventsOverTime" :key="`${series.key}-hits`">
              <circle
                v-for="hit in sharedLinePointPositions(series.points, dashboard.eventsOverTime, 720, 190, 8)"
                :key="`${series.key}-${hit.point.timestamp}`"
                class="chart-hit-point"
                :cx="hit.x"
                :cy="hit.y"
                r="8"
                :data-tooltip="pointTooltip(hit.point, series.name, formatCompact)"
              />
              <rect
                v-for="hit in sharedLineHitZones(series.points, dashboard.eventsOverTime, 720, 190, 8)"
                :key="`${series.key}-zone-${hit.point.timestamp}`"
                class="chart-hit-zone"
                :x="hit.xStart"
                y="0"
                :width="hit.width"
                height="210"
                :data-tooltip="pointTooltip(hit.point, series.name, formatCompact)"
              >
                <title>{{ pointTooltip(hit.point, series.name, formatCompact) }}</title>
              </rect>
            </template>
          </svg>
        </article>

        <article class="panel host-metrics-panel span-6">
          <div class="panel-title compact">
            <h3>主机指标 <span>{{ activeHost?.name || '请选择主机卡片' }}</span></h3>
          </div>
          <div class="host-detail-grid">
            <div class="rail-metrics">
              <article>
                <span>CPU 使用率</span>
                <strong>{{ formatPercent(activeHost?.cpuPercent) }}</strong>
                <i :style="{ width: `${activeHost?.cpuPercent || 0}%` }"></i>
              </article>
              <article>
                <span>内存使用率</span>
                <strong>{{ formatPercent(activeHost?.memoryPercent) }}</strong>
                <i :style="{ width: `${activeHost?.memoryPercent || 0}%` }"></i>
              </article>
              <article>
                <span>网络入</span>
                <strong>{{ formatMbps(activeHost?.networkInMbps) }}</strong>
                <i :style="{ width: railWidth(activeHost?.networkInMbps) }"></i>
              </article>
              <article>
                <span>网络出</span>
                <strong>{{ formatMbps(activeHost?.networkOutMbps) }}</strong>
                <i :style="{ width: railWidth(activeHost?.networkOutMbps) }"></i>
              </article>
            </div>
            <div class="mini-chart-grid">
              <MiniChart title="CPU 使用率" :series="activeHost?.cpuSeries || []" color="var(--vd-series-cpu)" :formatter="formatPercent" />
              <MiniChart title="内存使用率" :series="activeHost?.memorySeries || []" color="var(--vd-series-memory)" :formatter="formatPercent" />
              <MiniChart title="网络入 (Mbps)" :series="activeHost?.networkInSeries || []" color="var(--vd-series-in)" :formatter="formatMbps" />
              <MiniChart title="网络出 (Mbps)" :series="activeHost?.networkOutSeries || []" color="var(--vd-series-out)" :formatter="formatMbps" />
            </div>
          </div>
        </article>

        <article class="panel source-panel span-3">
          <div class="panel-title compact">
            <h3>主要来源</h3>
          </div>
            <div class="donut-wrap">
            <div class="donut" :style="{ background: topSourceDonut }" :data-tooltip="topSourcesTooltip" :title="topSourcesTooltip">
              <strong>{{ formatCompact(dashboard.metrics.eventsPerSecond) }}</strong>
              <span>事件/秒</span>
            </div>
            <div v-if="dashboard.topSources.length" class="source-list">
              <article
                v-for="source in dashboard.topSources"
                :key="source.name"
                :data-tooltip="sourceTooltip(source)"
                :title="sourceTooltip(source)"
              >
                <i :style="{ background: source.color }"></i>
                <span>{{ source.name }}</span>
                <strong>{{ formatCompact(source.eventsPerSecond) }}</strong>
                <em>{{ source.percentage.toFixed(1) }}%</em>
              </article>
            </div>
            <div v-else class="inline-empty">暂无业务组件来源指标</div>
          </div>
        </article>

        <article class="panel source-panel span-3">
          <div class="panel-title compact">
            <h3>缓冲区使用率</h3>
          </div>
          <div class="donut-wrap">
            <div class="donut buffer" :style="{ background: bufferDonut }" :data-tooltip="bufferTooltip" :title="bufferTooltip">
              <strong>{{ Math.round(dashboard.buffer.usedPercent || 0) }}%</strong>
              <span>已用</span>
            </div>
            <div class="buffer-list">
              <article :data-tooltip="bufferTooltip" :title="bufferTooltip"><span>已使用</span><strong>{{ formatBytes(dashboard.buffer.usedBytes) }}</strong></article>
              <article :data-tooltip="bufferTooltip" :title="bufferTooltip"><span>可用</span><strong>{{ formatBytes(dashboard.buffer.availableBytes) }}</strong></article>
              <article :data-tooltip="bufferTooltip" :title="bufferTooltip"><span>总量</span><strong>{{ formatBytes(dashboard.buffer.totalBytes) }}</strong></article>
            </div>
          </div>
        </article>

        <article class="panel span-3">
          <div class="panel-title compact">
            <h3>事件类型</h3>
          </div>
          <div v-if="hasEventTypeData" class="type-bars">
            <article
              v-for="type in dashboard.eventsByType"
              :key="type.type"
              :data-tooltip="eventTypeTooltip(type)"
              :title="eventTypeTooltip(type)"
            >
              <span>{{ eventTypeLabel(type.type) }}</span>
              <div><i :style="{ width: `${type.percentage}%`, background: type.color }"></i></div>
              <strong>{{ formatCompact(type.events) }}</strong>
            </article>
          </div>
          <div v-else class="inline-empty tall">暂无事件类型指标</div>
        </article>

        <article class="panel span-3">
          <div class="panel-title compact">
            <h3>输入/输出数据</h3>
          </div>
          <svg class="area-chart" viewBox="0 0 330 160" preserveAspectRatio="none" :data-tooltip="dataTransferTooltip">
            <title>{{ dataTransferTooltip }}</title>
            <polygon :points="areaPoints(dashboard.dataInSeries[0]?.points || [], 330, 150)" fill="var(--vd-series-in-fill)" />
            <polygon :points="areaPoints(dashboard.dataOutSeries[0]?.points || [], 330, 150)" fill="var(--vd-series-out-fill)" />
            <polyline :points="linePoints(dashboard.dataInSeries[0]?.points || [], 330, 140, 5)" stroke="var(--vd-series-in)" />
            <polyline :points="linePoints(dashboard.dataOutSeries[0]?.points || [], 330, 140, 5)" stroke="var(--vd-series-out)" />
            <circle
              v-for="hit in linePointPositions(dashboard.dataInSeries[0]?.points || [], 330, 140, 5)"
              :key="`data-in-${hit.point.timestamp}`"
              class="chart-hit-point"
              :cx="hit.x"
              :cy="hit.y"
              r="7"
              :data-tooltip="pointTooltip(hit.point, '输入数据', formatBytes)"
            />
            <circle
              v-for="hit in linePointPositions(dashboard.dataOutSeries[0]?.points || [], 330, 140, 5)"
              :key="`data-out-${hit.point.timestamp}`"
              class="chart-hit-point"
              :cx="hit.x"
              :cy="hit.y"
              r="7"
              :data-tooltip="pointTooltip(hit.point, '输出数据', formatBytes)"
            />
            <rect
              v-for="hit in linePointHitZones(dashboard.dataInSeries[0]?.points || [], 330, 140, 5)"
              :key="`data-in-zone-${hit.point.timestamp}`"
              class="chart-hit-zone"
              :x="hit.xStart"
              y="0"
              :width="hit.width"
              height="160"
              :data-tooltip="pointTooltip(hit.point, '输入数据', formatBytes)"
            >
              <title>{{ pointTooltip(hit.point, '输入数据', formatBytes) }}</title>
            </rect>
            <rect
              v-for="hit in linePointHitZones(dashboard.dataOutSeries[0]?.points || [], 330, 140, 5)"
              :key="`data-out-zone-${hit.point.timestamp}`"
              class="chart-hit-zone"
              :x="hit.xStart"
              y="0"
              :width="hit.width"
              height="160"
              :data-tooltip="pointTooltip(hit.point, '输出数据', formatBytes)"
            >
              <title>{{ pointTooltip(hit.point, '输出数据', formatBytes) }}</title>
            </rect>
          </svg>
        </article>

        <article class="panel span-3">
          <div class="panel-title compact">
            <h3>丢弃事件</h3>
          </div>
          <div class="bar-chart">
            <span
              v-for="point in dashboard.droppedSeries[0]?.points || []"
              :key="point.timestamp"
              :data-tooltip="pointTooltip(point, '丢弃事件', formatCompact)"
              :title="pointTooltip(point, '丢弃事件', formatCompact)"
              :style="{ height: barHeight(point.value, dashboard.droppedSeries[0]?.points || []) }"
            ></span>
          </div>
        </article>

        <article class="panel span-3 host-summary">
          <div class="panel-title compact">
            <h3>主机汇总</h3>
          </div>
          <table>
            <thead>
              <tr>
                <th>主机</th>
                <th>状态</th>
                <th>事件/秒</th>
                <th>输入数据</th>
                <th>CPU</th>
                <th>内存</th>
              </tr>
            </thead>
            <tbody v-if="dashboard.hostSummary.length">
              <tr v-for="row in dashboard.hostSummary" :key="row.host">
                <td>{{ row.host }}</td>
                <td><span :class="`table-status ${row.status}`">{{ statusLabel(row.status) }}</span></td>
                <td>{{ formatCompact(row.eventsPerSecond) }}</td>
                <td>{{ formatBytes(row.dataInBytes) }}</td>
                <td>{{ formatPercent(row.cpuPercent) }}</td>
                <td>{{ formatPercent(row.memoryPercent) }}</td>
              </tr>
            </tbody>
            <tbody v-else>
              <tr>
                <td colspan="6" class="table-empty">暂无主机汇总指标</td>
              </tr>
            </tbody>
          </table>
        </article>
      </section>

      <div
        v-if="hoverTooltip.visible"
        class="floating-tooltip"
        :style="{ left: `${hoverTooltip.x}px`, top: `${hoverTooltip.y}px` }"
      >
        {{ hoverTooltip.content }}
      </div>

      <Transition name="host-modal-motion">
        <div v-if="detailHost" class="host-modal-backdrop" @click.self="detailHost = null">
        <aside class="host-modal">
          <header>
            <div>
              <h3>主机详情：{{ detailHost.name }} ({{ detailHost.ipAddress }})</h3>
              <nav>
                <span class="active">概览</span>
                <span>CPU</span>
                <span>内存</span>
                <span>网络</span>
                <span>缓冲区</span>
                <span>系统</span>
              </nav>
            </div>
            <button class="nav-icon" type="button" @click="detailHost = null">
              <el-icon><Close /></el-icon>
            </button>
          </header>

          <div class="detail-stat-grid">
            <article><span>CPU</span><strong>{{ formatPercent(detailHost.cpuPercent) }}</strong></article>
            <article><span>内存</span><strong>{{ formatPercent(detailHost.memoryPercent) }}</strong></article>
            <article><span>网络入</span><strong>{{ formatMbps(detailHost.networkInMbps) }}</strong></article>
            <article><span>网络出</span><strong>{{ formatMbps(detailHost.networkOutMbps) }}</strong></article>
            <article><span>事件/秒</span><strong>{{ formatCompact(detailHost.eventsPerSecond) }}</strong></article>
            <article><span>输入数据</span><strong>{{ formatBytes(detailHost.dataInBytes) }}</strong></article>
            <article><span>输出数据</span><strong>{{ formatBytes(detailHost.dataOutBytes) }}</strong></article>
            <article><span>缓冲区</span><strong>{{ formatPercent(detailHost.bufferUsedPercent) }}</strong></article>
          </div>

          <MiniChart title="CPU 使用率" :series="detailHost.cpuSeries" color="var(--vd-series-cpu)" :formatter="formatPercent" large />
          <MiniChart title="内存使用率" :series="detailHost.memorySeries" color="var(--vd-series-memory)" :formatter="formatPercent" large />
          <div class="detail-split">
            <MiniChart title="网络入 (Mbps)" :series="detailHost.networkInSeries" color="var(--vd-series-in)" :formatter="formatMbps" />
            <MiniChart title="网络出 (Mbps)" :series="detailHost.networkOutSeries" color="var(--vd-series-out)" :formatter="formatMbps" />
          </div>

          <footer>
            <span>运行 {{ detailHost.uptime || '-' }}</span>
            <span>版本 {{ detailHost.vectorVersion || '-' }}</span>
            <span>系统 {{ detailHost.osType || '-' }}</span>
            <span>指标源 internal_metrics</span>
          </footer>
        </aside>
        </div>
      </Transition>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onMounted, onUnmounted, ref, type PropType } from 'vue'
import { ArrowLeft, ArrowRight, Close, MoreFilled, Plus, Refresh } from '@element-plus/icons-vue'
import AppLayout from '@/components/layout/AppLayout.vue'
import { getVectorDashboardOverview } from '@/api/dashboard'
import type {
  VectorDashboardOverview,
  VectorEventType,
  VectorHostCard,
  VectorPoint,
  VectorSeries,
  VectorTopSource
} from './types'

const createEmptyOverview = (): VectorDashboardOverview => ({
  generatedAt: '',
  range: '1h',
  selectedHostId: undefined,
  selectedHost: null,
  metrics: {
    eventsPerSecond: 0,
    dataInBytes: 0,
    dataOutBytes: 0,
    droppedEvents: 0,
    bufferUsedBytes: 0,
    bufferTotalBytes: 0,
    eventsChangePercent: 0,
    dataInChangePercent: 0,
    dataOutChangePercent: 0,
    droppedChangePercent: 0
  },
  buffer: { usedBytes: 0, availableBytes: 0, totalBytes: 0, usedPercent: 0 },
  hosts: [],
  eventsOverTime: [],
  dataInSeries: [],
  dataOutSeries: [],
  droppedSeries: [],
  eventsByType: [],
  topSources: [],
  hostSummary: [],
  warnings: []
})

const MiniChart = defineComponent({
  name: 'MiniChart',
  props: {
    title: { type: String, required: true },
    series: { type: Array as () => VectorPoint[], required: true },
    color: { type: String, required: true },
    formatter: {
      type: Function as PropType<(value?: number) => string>,
      default: formatCompact
    },
    large: { type: Boolean, default: false }
  },
  setup(props) {
    return () => h('article', { class: ['mini-chart', { large: props.large }] }, [
      h('div', { class: 'mini-chart-title' }, [
        h('span', props.title),
        h('strong', props.formatter(props.series[props.series.length - 1]?.value || 0))
      ]),
      h('svg', { viewBox: '0 0 260 86', preserveAspectRatio: 'none' }, [
        h('polyline', {
          points: linePoints(props.series, 260, 76, 5),
          style: { stroke: props.color }
        }),
        ...linePointPositions(props.series, 260, 76, 5).map(hit => h('circle', {
          key: `${props.title}-${hit.point.timestamp}`,
          class: 'chart-hit-point',
          cx: hit.x,
          cy: hit.y,
          r: props.large ? 8 : 7,
          fill: 'transparent',
          stroke: 'transparent',
          'data-tooltip': pointTooltip(hit.point, props.title, props.formatter)
        })),
        ...linePointHitZones(props.series, 260, 76, 5).map(hit => h('rect', {
          key: `${props.title}-zone-${hit.point.timestamp}`,
          class: 'chart-hit-zone',
          x: hit.xStart,
          y: 0,
          width: hit.width,
          height: 86,
          fill: 'transparent',
          stroke: 'transparent',
          'data-tooltip': pointTooltip(hit.point, props.title, props.formatter),
          title: pointTooltip(hit.point, props.title, props.formatter)
        }))
      ])
    ])
  }
})

const timeRange = ref('1h')
const autoRefresh = ref(true)
const loading = ref(false)
const selectedHostId = ref<string>()
const detailHost = ref<VectorHostCard | null>(null)
const dashboard = ref<VectorDashboardOverview>(createEmptyOverview())
const hostScroller = ref<HTMLElement | null>(null)
const tooltipTarget = ref<Element | null>(null)
const hoverTooltip = ref({
  visible: false,
  content: '',
  x: 0,
  y: 0
})
let refreshTimer: ReturnType<typeof setInterval> | null = null

const activeHost = computed(() => (
  dashboard.value.hosts.find(host => host.id === selectedHostId.value)
  || dashboard.value.selectedHost
  || dashboard.value.hosts[0]
  || null
))

const overviewMetrics = computed(() => [
  {
    label: '事件/秒',
    value: formatCompact(dashboard.value.metrics.eventsPerSecond),
    delta: formatDelta(dashboard.value.metrics.eventsChangePercent),
    positive: dashboard.value.metrics.eventsChangePercent >= 0,
    series: dashboard.value.eventsOverTime[0]?.points || [],
    formatter: formatCompact
  },
  {
    label: '输入数据',
    value: formatBytes(dashboard.value.metrics.dataInBytes),
    delta: formatDelta(dashboard.value.metrics.dataInChangePercent),
    positive: dashboard.value.metrics.dataInChangePercent >= 0,
    series: dashboard.value.dataInSeries[0]?.points || [],
    formatter: formatBytes
  },
  {
    label: '输出数据',
    value: formatBytes(dashboard.value.metrics.dataOutBytes),
    delta: formatDelta(dashboard.value.metrics.dataOutChangePercent),
    positive: dashboard.value.metrics.dataOutChangePercent >= 0,
    series: dashboard.value.dataOutSeries[0]?.points || [],
    formatter: formatBytes
  },
  {
    label: '丢弃事件',
    value: formatCompact(dashboard.value.metrics.droppedEvents),
    delta: formatDelta(dashboard.value.metrics.droppedChangePercent),
    positive: dashboard.value.metrics.droppedChangePercent <= 0,
    series: dashboard.value.droppedSeries[0]?.points || [],
    formatter: formatCompact
  },
  {
    label: '缓冲区使用',
    value: formatBytes(dashboard.value.metrics.bufferUsedBytes),
    delta: `已用 ${Math.round(dashboard.value.buffer.usedPercent || 0)}%`,
    positive: (dashboard.value.buffer.usedPercent || 0) < 70,
    series: dashboard.value.dataInSeries[0]?.points || [],
    formatter: formatBytes
  }
])

const hasEventTypeData = computed(() => dashboard.value.eventsByType.some(type => Number(type.events || 0) > 0))

const topSourceDonut = computed(() => buildConicGradient(
  dashboard.value.topSources.map(source => ({
    color: source.color,
    percentage: source.percentage
  })),
  'var(--vd-donut-empty)'
))

const bufferDonut = computed(() => {
  const used = Math.max(0, Math.min(100, dashboard.value.buffer.usedPercent || 0))
  return `conic-gradient(var(--vd-series-in) 0 ${used}%, var(--vd-series-out) ${used}% 100%)`
})

const topSourcesTooltip = computed(() => {
  if (!dashboard.value.topSources.length) {
    return '主要来源\n暂未收到 Vector pipeline 指标'
  }
  return [
    '主要来源',
    ...dashboard.value.topSources.map(source => `${source.name}: ${formatCompact(source.events)} 个事件 (${source.percentage.toFixed(1)}%)`)
  ].join('\n')
})

const bufferTooltip = computed(() => [
  '缓冲区使用率',
  `已使用: ${formatBytes(dashboard.value.buffer.usedBytes)} (${Math.round(dashboard.value.buffer.usedPercent || 0)}%)`,
  `可用: ${formatBytes(dashboard.value.buffer.availableBytes)}`,
  `总量: ${formatBytes(dashboard.value.buffer.totalBytes)}`
].join('\n'))

const dataTransferTooltip = computed(() => [
  '数据传输',
  `输入数据: ${formatBytes(dashboard.value.metrics.dataInBytes)}`,
  `输出数据: ${formatBytes(dashboard.value.metrics.dataOutBytes)}`
].join('\n'))

const fetchDashboard = async () => {
  loading.value = true
  try {
    const response: any = await getVectorDashboardOverview({
      range: timeRange.value,
      hostId: selectedHostId.value
    })
    const payload = response.data || response
    dashboard.value = {
      ...createEmptyOverview(),
      ...payload,
      metrics: { ...createEmptyOverview().metrics, ...(payload.metrics || {}) },
      buffer: { ...createEmptyOverview().buffer, ...(payload.buffer || {}) },
      hosts: payload.hosts || [],
      eventsOverTime: payload.eventsOverTime || [],
      dataInSeries: payload.dataInSeries || [],
      dataOutSeries: payload.dataOutSeries || [],
      droppedSeries: payload.droppedSeries || [],
      eventsByType: payload.eventsByType || [],
      topSources: payload.topSources || [],
      hostSummary: payload.hostSummary || [],
      warnings: payload.warnings || []
    }
    selectedHostId.value = dashboard.value.selectedHostId || dashboard.value.hosts[0]?.id
  } finally {
    loading.value = false
  }
}

const openHostDetails = (host: VectorHostCard) => {
  selectedHostId.value = host.id
  detailHost.value = host
}

const scrollHosts = (direction: number) => {
  hostScroller.value?.scrollBy({ left: direction * 360, behavior: 'smooth' })
}

const getTooltipTarget = (target: EventTarget | null) => {
  return target instanceof Element ? target.closest('[data-tooltip]') : null
}

const showDashboardTooltip = (event: PointerEvent) => {
  const target = getTooltipTarget(event.target)
  const content = target?.getAttribute('data-tooltip')
  if (!target || !content) {
    return
  }
  tooltipTarget.value = target
  hoverTooltip.value = {
    visible: true,
    content,
    x: event.clientX + 14,
    y: event.clientY + 14
  }
}

const moveDashboardTooltip = (event: PointerEvent) => {
  if (!hoverTooltip.value.visible) {
    return
  }
  hoverTooltip.value = {
    ...hoverTooltip.value,
    x: event.clientX + 14,
    y: event.clientY + 14
  }
}

const hideDashboardTooltip = (event: PointerEvent) => {
  const nextTarget = event.relatedTarget
  if (tooltipTarget.value && nextTarget instanceof Node && tooltipTarget.value.contains(nextTarget)) {
    return
  }
  tooltipTarget.value = null
  hoverTooltip.value = {
    ...hoverTooltip.value,
    visible: false
  }
}

const startAutoRefresh = () => {
  stopAutoRefresh()
  if (autoRefresh.value) {
    refreshTimer = setInterval(fetchDashboard, 10000)
  }
}

const stopAutoRefresh = () => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

const toggleAutoRefresh = () => {
  autoRefresh.value = !autoRefresh.value
  startAutoRefresh()
}

function linePoints(points: VectorPoint[], width: number, height: number, padding = 2) {
  const values = points.map(point => Number(point.value || 0))
  const max = Math.max(...values, 1)
  const min = Math.min(...values, 0)
  return linePointsWithScale(points, width, height, padding, min, max)
}

function sharedLinePoints(points: VectorPoint[], seriesList: Array<{ points: VectorPoint[] }>, width: number, height: number, padding = 2) {
  const values = seriesList.flatMap(series => series.points.map(point => Number(point.value || 0)))
  const max = Math.max(...values, 1)
  const min = Math.min(...values, 0)
  return linePointsWithScale(points, width, height, padding, min, max)
}

function linePointPositions(points: VectorPoint[], width: number, height: number, padding = 2) {
  const values = points.map(point => Number(point.value || 0))
  const max = Math.max(...values, 1)
  const min = Math.min(...values, 0)
  return pointPositionsWithScale(points, width, height, padding, min, max)
}

function sharedLinePointPositions(points: VectorPoint[], seriesList: Array<{ points: VectorPoint[] }>, width: number, height: number, padding = 2) {
  const values = seriesList.flatMap(series => series.points.map(point => Number(point.value || 0)))
  const max = Math.max(...values, 1)
  const min = Math.min(...values, 0)
  return pointPositionsWithScale(points, width, height, padding, min, max)
}

function linePointHitZones(points: VectorPoint[], width: number, height: number, padding = 2) {
  return toHitZones(linePointPositions(points, width, height, padding), width)
}

function sharedLineHitZones(points: VectorPoint[], seriesList: Array<{ points: VectorPoint[] }>, width: number, height: number, padding = 2) {
  return toHitZones(sharedLinePointPositions(points, seriesList, width, height, padding), width)
}

function toHitZones(positions: Array<{ point: VectorPoint; x: number; y: number }>, width: number) {
  if (!positions.length) {
    return []
  }
  const step = positions.length > 1 ? width / (positions.length - 1) : width
  return positions.map((hit, index) => {
    const leftBoundary = index === 0 ? 0 : (positions[index - 1]!.x + hit.x) / 2
    const rightBoundary = index === positions.length - 1 ? width : (hit.x + positions[index + 1]!.x) / 2
    return {
      ...hit,
      xStart: Number(Math.max(0, leftBoundary).toFixed(2)),
      width: Number(Math.max(8, rightBoundary - leftBoundary || step).toFixed(2))
    }
  })
}

function linePointsWithScale(points: VectorPoint[], width: number, height: number, padding: number, min: number, max: number) {
  if (!points.length) {
    return `0,${height - padding} ${width},${height - padding}`
  }
  const values = points.map(point => Number(point.value || 0))
  const spread = Math.max(1, max - min)
  return values.map((value, index) => {
    const x = points.length === 1 ? width / 2 : (index / (points.length - 1)) * width
    const y = height - padding - ((value - min) / spread) * (height - padding * 2)
    return `${x.toFixed(2)},${y.toFixed(2)}`
  }).join(' ')
}

function pointPositionsWithScale(points: VectorPoint[], width: number, height: number, padding: number, min: number, max: number) {
  if (!points.length) {
    return []
  }
  const spread = Math.max(1, max - min)
  return points.map((point, index) => {
    const value = Number(point.value || 0)
    const x = points.length === 1 ? width / 2 : (index / (points.length - 1)) * width
    const y = height - padding - ((value - min) / spread) * (height - padding * 2)
    return {
      point,
      x: Number(x.toFixed(2)),
      y: Number(y.toFixed(2))
    }
  })
}

function areaPoints(points: VectorPoint[], width: number, height: number) {
  const line = linePoints(points, width, height, 5)
  return `0,${height} ${line} ${width},${height}`
}

function barHeight(value: number, points: VectorPoint[]) {
  const max = Math.max(...points.map(point => point.value || 0), 1)
  return `${Math.max(8, (value / max) * 100)}%`
}

function buildConicGradient(parts: Array<{ color: string; percentage: number }>, fallback: string) {
  if (!parts.length) {
    return fallback
  }
  let cursor = 0
  const slices = parts.map(part => {
    const start = cursor
    cursor += Math.max(0, part.percentage)
    return `${part.color} ${start}% ${cursor}%`
  })
  if (cursor < 100) {
    slices.push(`${fallback} ${cursor}% 100%`)
  }
  return `conic-gradient(${slices.join(', ')})`
}

function formatPercent(value?: number) {
  return `${Math.round(value || 0)}%`
}

function formatMbps(value?: number) {
  return `${Number(value || 0).toFixed((value || 0) >= 10 ? 0 : 1)} Mbps`
}

function formatCompact(value?: number) {
  const number = Number(value || 0)
  if (number >= 1_000_000) return `${(number / 1_000_000).toFixed(1)}M`
  if (number >= 1_000) return `${(number / 1_000).toFixed(1)}K`
  return Number.isInteger(number) ? `${number}` : number.toFixed(1)
}

function formatBytes(value?: number) {
  const bytes = Number(value || 0)
  if (bytes >= 1024 ** 3) return `${(bytes / 1024 ** 3).toFixed(1)} GB`
  if (bytes >= 1024 ** 2) return `${(bytes / 1024 ** 2).toFixed(1)} MB`
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${Math.round(bytes)} B`
}

function formatDelta(value?: number) {
  const number = Number(value || 0)
  const prefix = number > 0 ? '+' : ''
  return `${prefix}${number.toFixed(1)}% 较上一周期`
}

function railWidth(value?: number) {
  return `${Math.max(6, Math.min(100, Number(value || 0)))}%`
}

function statusLabel(status: string) {
  if (status === 'healthy') return '健康'
  if (status === 'warning') return '告警'
  return '严重'
}

function environmentLabel(environment?: string) {
  if (environment === 'prod') return '生产'
  if (environment === 'staging') return '预发'
  if (environment === 'dev') return '开发'
  return environment || '-'
}

function hostTooltip(host: VectorHostCard) {
  return [
    host.name,
    `IP: ${host.ipAddress}`,
    `CPU: ${formatPercent(host.cpuPercent)}`,
    `内存: ${formatPercent(host.memoryPercent)}`,
    `网络入: ${formatMbps(host.networkInMbps)}`,
    `网络出: ${formatMbps(host.networkOutMbps)}`,
    `事件/秒: ${formatCompact(host.eventsPerSecond)}`,
    `缓冲区: ${formatPercent(host.bufferUsedPercent)}`,
    `状态: ${statusLabel(host.status)}`
  ].join('\n')
}

function metricTooltip(metric: { label: string; value: string; delta: string; series: VectorPoint[]; formatter: (value?: number) => string }) {
  const last = metric.series[metric.series.length - 1]
  return [
    metric.label,
    `当前值: ${metric.value}`,
    `变化: ${metric.delta}`,
    last ? `最新时间: ${formatPointTime(last)}` : '暂无时间序列点',
    last ? `最新数值: ${metric.formatter(last.value)}` : ''
  ].filter(Boolean).join('\n')
}

function formatPointTime(point: VectorPoint) {
  return point.timestamp || point.label || '-'
}

function eventTypeLabel(type: string) {
  if (type === 'log') return '日志'
  if (type === 'metric') return '指标'
  if (type === 'trace') return '链路'
  return '其他'
}

function sourceTooltip(source: VectorTopSource) {
  return [
    source.name,
    `事件数: ${formatCompact(source.events)}`,
    `事件/秒: ${formatCompact(source.eventsPerSecond)}`,
    `占比: ${source.percentage.toFixed(1)}%`
  ].join('\n')
}

function eventTypeTooltip(type: VectorEventType) {
  return [
    eventTypeLabel(type.type),
    `事件数: ${formatCompact(type.events)}`,
    `占比: ${type.percentage.toFixed(1)}%`
  ].join('\n')
}

function seriesTooltip(seriesList: VectorSeries[]) {
  if (!seriesList.length) {
    return '暂未收到 Vector pipeline 时间序列'
  }
  return seriesList.map(series => {
    const last = series.points[series.points.length - 1]
    return `${series.name}: ${last ? `${formatCompact(last.value)}，时间 ${formatPointTime(last)}` : '暂无数据点'}`
  }).join('\n')
}

function pointTooltip(point: VectorPoint, label: string, formatter: (value?: number) => string) {
  return [
    label,
    `时间: ${formatPointTime(point)}`,
    `数值: ${formatter(point.value)}`
  ].join('\n')
}

onMounted(() => {
  fetchDashboard()
  startAutoRefresh()
})

onUnmounted(stopAutoRefresh)
</script>

<style scoped lang="scss">
:deep(.mini-chart) {
  min-width: 0;
  padding: 10px;
  border: 1px solid var(--vd-panel-border);
  border-radius: 7px;
  background: var(--vd-control-bg);

  &.large {
    margin-top: 12px;
  }

  svg {
    width: 100%;
    height: 74px;
  }

  polyline {
    fill: none;
    stroke-width: 2;
    stroke-linecap: round;
    stroke-linejoin: round;
    pointer-events: none;
  }
}

:deep(.mini-chart-title) {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  color: var(--vd-soft);
  font-size: 12px;

  strong {
    color: var(--vd-heading);
  }
}

.dash-header-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

.top-select {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 0 10px;
  border: 1px solid var(--vd-control-border);
  border-radius: 7px;
  background: var(--vd-control-bg);
  color: var(--vd-soft);
  font-size: 12px;

  select {
    border: 0;
    background: transparent;
    color: var(--vd-heading);
    outline: none;
  }
}

.dash-header-button,
.dash-header-primary,
.nav-icon,
.view-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 36px;
  border: 1px solid var(--vd-control-border);
  border-radius: 7px;
  color: var(--vd-control-text);
  background: var(--vd-control-bg);
  cursor: pointer;
  transition:
    border-color 0.16s ease,
    background-color 0.16s ease,
    color 0.16s ease,
    transform 0.16s ease;

  &:hover:not(:disabled) {
    border-color: var(--vd-accent-border);
    background: var(--vd-control-hover);
    color: var(--vd-heading);
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.56;
  }
}

.dash-header-button {
  min-width: 36px;
  padding: 0 11px;
}

.dash-header-primary {
  padding: 0 13px;
  border-color: var(--vd-accent-border);
  background: linear-gradient(135deg, var(--vd-accent), var(--vd-accent-strong));
  color: #ffffff;
  font-weight: 700;
}

.auto-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--vd-muted);

  &.active {
    background: var(--vd-success);
    box-shadow: 0 0 12px rgba(34, 197, 94, 0.7);
  }
}

.vector-dashboard {
  --vd-page-bg: linear-gradient(180deg, var(--macos-bg-secondary) 0%, #19191b 100%);
  --vd-toolbar-bg: linear-gradient(180deg, rgba(30, 30, 30, 0.96), rgba(28, 28, 30, 0.96));
  --vd-surface: linear-gradient(180deg, rgba(30, 30, 30, 0.96), rgba(28, 28, 30, 0.96));
  --vd-card-bg: linear-gradient(180deg, rgba(44, 44, 46, 0.76), rgba(30, 30, 30, 0.96));
  --vd-control-bg: rgba(44, 44, 46, 0.72);
  --vd-control-hover: rgba(58, 58, 60, 0.88);
  --vd-control-border: rgba(255, 255, 255, 0.12);
  --vd-panel-border: rgba(255, 255, 255, 0.1);
  --vd-soft-border: rgba(255, 255, 255, 0.08);
  --vd-heading: var(--macos-text-primary);
  --vd-text: #e5e5ea;
  --vd-soft: #c7c7cc;
  --vd-muted: #98989d;
  --vd-control-text: #f5f5f7;
  --vd-tag-bg: var(--macos-indigo-light);
  --vd-tag-text: #b8b7ff;
  --vd-accent: var(--macos-indigo);
  --vd-accent-strong: var(--macos-teal);
  --vd-accent-border: rgba(94, 92, 230, 0.46);
  --vd-accent-glow: rgba(94, 92, 230, 0.18);
  --vd-success: var(--macos-success);
  --vd-danger: var(--macos-danger);
  --vd-warning: var(--macos-warning);
  --vd-series-cpu: var(--macos-indigo);
  --vd-series-memory: var(--macos-purple);
  --vd-series-in: var(--macos-success);
  --vd-series-out: var(--macos-teal);
  --vd-series-dropped: var(--macos-danger);
  --vd-series-in-fill: rgba(48, 209, 88, 0.17);
  --vd-series-out-fill: rgba(64, 200, 224, 0.15);
  --vd-success-soft-bg: rgba(48, 209, 88, 0.14);
  --vd-success-text: #9ff4b2;
  --vd-warning-soft-bg: rgba(255, 159, 10, 0.14);
  --vd-warning-text: #ffd085;
  --vd-danger-soft-bg: rgba(255, 69, 58, 0.14);
  --vd-danger-text: #ff9a94;
  --vd-warning-panel-bg: rgba(255, 159, 10, 0.1);
  --vd-warning-panel-border: rgba(255, 159, 10, 0.24);
  --vd-warning-panel-text: #ffd085;
  --vd-chip-bg: rgba(58, 58, 60, 0.74);
  --vd-icon-bg: rgba(28, 28, 30, 0.96);
  --vd-icon-border: rgba(255, 255, 255, 0.14);
  --vd-donut-center: #1c1c1e;
  --vd-donut-empty: rgba(99, 99, 102, 0.22);
  --vd-backdrop: rgba(0, 0, 0, 0.42);
  --vd-modal-bg: linear-gradient(180deg, rgba(30, 30, 30, 0.98), rgba(28, 28, 30, 0.98));
  --vd-shadow: 0 14px 30px rgba(0, 0, 0, 0.22);
  min-height: 100%;
  padding: 20px 24px 28px;
  background: var(--vd-page-bg);
  color: var(--vd-text);
  font-family: Inter, "SF Pro Display", "PingFang SC", "Microsoft YaHei", system-ui, sans-serif;
}

:global(html.light .vector-dashboard) {
  --vd-page-bg: linear-gradient(180deg, var(--macos-bg-secondary) 0%, #eef3f8 100%);
  --vd-toolbar-bg: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(247, 250, 253, 0.94));
  --vd-surface: linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(247, 250, 253, 0.96));
  --vd-card-bg: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(247, 250, 253, 0.98));
  --vd-control-bg: rgba(255, 255, 255, 0.86);
  --vd-control-hover: rgba(239, 246, 255, 0.94);
  --vd-control-border: rgba(100, 116, 139, 0.22);
  --vd-panel-border: rgba(100, 116, 139, 0.18);
  --vd-soft-border: rgba(100, 116, 139, 0.14);
  --vd-heading: #0f172a;
  --vd-text: #263244;
  --vd-soft: #475569;
  --vd-muted: #64748b;
  --vd-control-text: #3730a3;
  --vd-tag-bg: var(--macos-indigo-light);
  --vd-tag-text: #3730a3;
  --vd-accent: var(--macos-indigo);
  --vd-accent-strong: var(--macos-teal);
  --vd-accent-border: rgba(88, 86, 214, 0.36);
  --vd-accent-glow: rgba(88, 86, 214, 0.12);
  --vd-series-cpu: var(--macos-indigo);
  --vd-series-memory: var(--macos-purple);
  --vd-series-in: var(--macos-success);
  --vd-series-out: var(--macos-teal);
  --vd-series-dropped: var(--macos-danger);
  --vd-series-in-fill: rgba(52, 199, 89, 0.16);
  --vd-series-out-fill: rgba(50, 173, 230, 0.14);
  --vd-success-soft-bg: rgba(22, 163, 74, 0.12);
  --vd-success-text: #15803d;
  --vd-warning-soft-bg: rgba(217, 119, 6, 0.13);
  --vd-warning-text: #a16207;
  --vd-danger-soft-bg: rgba(225, 29, 72, 0.12);
  --vd-danger-text: #be123c;
  --vd-warning-panel-bg: rgba(217, 119, 6, 0.1);
  --vd-warning-panel-border: rgba(217, 119, 6, 0.22);
  --vd-warning-panel-text: #92400e;
  --vd-chip-bg: rgba(226, 232, 240, 0.86);
  --vd-icon-bg: #f1f5f9;
  --vd-icon-border: rgba(100, 116, 139, 0.2);
  --vd-donut-center: #f8fafc;
  --vd-donut-empty: rgba(148, 163, 184, 0.2);
  --vd-backdrop: rgba(15, 23, 42, 0.24);
  --vd-modal-bg: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(241, 245, 249, 0.98));
  --vd-shadow: 0 18px 42px rgba(15, 23, 42, 0.12);
}

.floating-tooltip {
  position: fixed;
  z-index: 3000;
  width: max-content;
  max-width: 280px;
  padding: 9px 11px;
  border: 1px solid var(--vd-control-border);
  border-radius: 7px;
  background: var(--vd-modal-bg);
  box-shadow: 0 14px 36px rgba(0, 0, 0, 0.24);
  color: var(--vd-heading);
  font-size: 12px;
  line-height: 1.55;
  pointer-events: none;
  text-align: left;
  white-space: pre-line;
}

.dashboard-toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 14px;
  padding: 16px;
  border: 1px solid var(--vd-panel-border);
  border-radius: 7px;
  background: var(--vd-toolbar-bg);
  box-shadow: var(--vd-shadow);
}

.dashboard-heading {
  min-width: 0;

  h1,
  p {
    margin: 0;
  }

  h1 {
    margin-top: 6px;
    color: var(--vd-heading);
    font-size: 24px;
    font-weight: 760;
    line-height: 1.12;
  }

  p {
    margin-top: 7px;
    color: var(--vd-muted);
    font-size: 13px;
  }
}

.dashboard-kicker {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--vd-soft);
  font-size: 12px;
  font-weight: 680;
}

.host-stage,
.panel {
  border: 1px solid var(--vd-panel-border);
  border-radius: 7px;
  background: var(--vd-surface);
  box-shadow: var(--vd-shadow);
}

.host-stage {
  margin-top: 0;
  padding: 14px;
}

.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;

  h2,
  h3,
  p {
    margin: 0;
  }

  h2,
  h3 {
    color: var(--vd-heading);
    font-weight: 740;
  }

  h2 {
    font-size: 18px;
  }

  h3 {
    font-size: 15px;
  }

  p,
  span {
    color: var(--vd-muted);
    font-size: 12px;
  }

  &.compact {
    margin-bottom: 10px;
  }
}

.stage-actions,
.legend {
  display: flex;
  align-items: center;
  gap: 9px;
}

.view-chip.active {
  color: var(--vd-heading);
  background: var(--vd-chip-bg);
}

.nav-icon {
  width: 34px;
  height: 34px;
  padding: 0;
}

.host-strip {
  display: grid;
  grid-auto-columns: minmax(300px, 1fr);
  grid-auto-flow: column;
  gap: 14px;
  overflow-x: auto;
  padding-bottom: 5px;
  scroll-snap-type: x proximity;
}

.host-card {
  position: relative;
  min-width: 300px;
  padding: 15px;
  border: 1px solid var(--vd-panel-border);
  border-radius: 7px;
  background: var(--vd-card-bg);
  color: var(--vd-text);
  text-align: left;
  cursor: pointer;
  scroll-snap-align: start;
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;

  &.selected {
    border-color: var(--vd-accent);
    box-shadow: 0 0 0 1px var(--vd-accent-border), 0 0 22px var(--vd-accent-glow);
  }

  &:hover {
    border-color: var(--vd-accent-border);
    box-shadow: 0 14px 30px var(--vd-accent-glow);
    transform: translateY(-2px);
  }

  footer,
  .host-card-head,
  .host-metrics {
    display: flex;
  }

  footer {
    justify-content: space-between;
    gap: 12px;
    margin-top: 12px;
    padding-top: 10px;
    border-top: 1px solid var(--vd-soft-border);
    color: var(--vd-muted);
    font-size: 12px;
  }
}

.host-hover-popover {
  position: absolute;
  right: 12px;
  bottom: 12px;
  left: 12px;
  z-index: 3;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  padding: 9px;
  border: 1px solid var(--vd-control-border);
  border-radius: 7px;
  background: var(--vd-modal-bg);
  box-shadow: 0 14px 32px rgba(0, 0, 0, 0.24);
  opacity: 0;
  pointer-events: none;
  transform: translateY(8px);
  transition:
    opacity 0.18s ease,
    transform 0.18s ease;

  span,
  strong {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span {
    color: var(--vd-muted);
    font-size: 11px;
  }

  strong {
    margin-top: 3px;
    color: var(--vd-heading);
    font-size: 12px;
  }
}

.host-card:hover .host-hover-popover {
  opacity: 1;
  transform: translateY(0);
}

.host-card-head {
  align-items: center;
  gap: 10px;
}

.host-icon {
  position: relative;
  display: inline-flex;
  width: 42px;
  height: 42px;
  border: 1px solid var(--vd-icon-border);
  border-radius: 7px;
  background: var(--vd-icon-bg);

  &::before,
  &::after,
  i {
    position: absolute;
    left: 10px;
    width: 22px;
    height: 3px;
    border-radius: 99px;
    background: var(--vd-accent);
    content: "";
  }

  &::before { top: 11px; }
  i { top: 19px; }
  &::after { top: 27px; background: var(--vd-success); }
}

.host-identity {
  min-width: 0;
  flex: 1;

  strong,
  span {
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: var(--vd-heading);
    font-size: 15px;
  }

  span {
    margin-top: 4px;
    color: var(--vd-muted);
    font-size: 12px;
  }

  em {
    margin-left: 6px;
    padding: 2px 6px;
    border-radius: 5px;
    background: var(--vd-tag-bg);
    color: var(--vd-tag-text);
    font-style: normal;
  }
}

.health-pill {
  padding: 5px 9px;
  border-radius: 999px;
  background: var(--vd-success-soft-bg);
  color: var(--vd-success-text);
  font-size: 12px;
  font-weight: 730;
}

.status-warning .health-pill {
  background: var(--vd-warning-soft-bg);
  color: var(--vd-warning-text);
}

.status-critical .health-pill {
  background: var(--vd-danger-soft-bg);
  color: var(--vd-danger-text);
}

.more {
  color: var(--vd-muted);
}

.host-metrics {
  gap: 22px;
  margin-top: 18px;

  article {
    min-width: 0;
    flex: 1;
  }

  span {
    display: block;
    color: var(--vd-soft);
    font-size: 12px;
  }

  strong {
    display: block;
    margin: 4px 0 2px;
    color: var(--vd-heading);
    font-size: 20px;
  }

  svg {
    width: 100%;
    height: 24px;
  }

  polyline {
    fill: none;
    stroke: var(--vd-accent);
    stroke-width: 2;
    pointer-events: none;
  }
}

.empty-hosts {
  min-height: 150px;
  display: grid;
  place-items: center;
  color: var(--vd-muted);
}

.warning-strip {
  display: grid;
  gap: 8px;
  margin-top: 12px;

  article {
    padding: 10px 12px;
    border: 1px solid var(--vd-warning-panel-border);
    border-radius: 7px;
    background: var(--vd-warning-panel-bg);
    color: var(--vd-warning-panel-text);
  }
}

.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 14px;
  margin-top: 14px;
}

.panel {
  min-width: 0;
  padding: 14px;
}

.span-3 { grid-column: span 3; }
.span-6 { grid-column: span 6; }

.metric-row {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  overflow: hidden;
  border: 1px solid var(--vd-soft-border);
  border-radius: 7px;
}

.event-metric {
  min-width: 0;
  padding: 13px;
  border-right: 1px solid var(--vd-soft-border);
  background: var(--vd-control-bg);

  &:last-child {
    border-right: 0;
  }

  span,
  em {
    color: var(--vd-muted);
    font-size: 12px;
  }

  strong {
    display: block;
    margin: 8px 0;
    color: var(--vd-heading);
    font-size: 26px;
    line-height: 1;
  }

  em {
    font-style: normal;

    &.positive { color: var(--vd-success); }
    &.negative { color: var(--vd-danger); }
  }

  svg {
    width: 100%;
    height: 26px;
  }

  polyline {
    fill: none;
    stroke: var(--vd-accent);
    stroke-width: 2;
    pointer-events: none;
  }
}

.legend {
  margin: 0 0 6px;
  flex-wrap: wrap;
  color: var(--vd-soft);
  font-size: 12px;

  i {
    display: inline-block;
    width: 9px;
    height: 9px;
    margin-right: 5px;
    border-radius: 2px;
  }
}

.panel-select {
  padding: 6px 9px;
  border-radius: 6px;
  background: var(--vd-chip-bg);
}

.main-chart,
.area-chart {
  width: 100%;
  height: 210px;

  polyline {
    fill: none;
    stroke-width: 2.2;
    stroke-linecap: round;
    stroke-linejoin: round;
    pointer-events: none;
  }
}

.chart-hit-point,
:deep(.chart-hit-point) {
  fill: transparent;
  stroke: transparent;
  pointer-events: all;
}

.chart-hit-zone,
:deep(.chart-hit-zone) {
  fill: transparent;
  stroke: transparent;
  pointer-events: all;
  cursor: crosshair;
}

.grid-lines line {
  stroke: var(--vd-soft-border);
  stroke-width: 1;
}

.host-detail-grid {
  display: grid;
  grid-template-columns: 150px 1fr;
  gap: 12px;
}

.rail-metrics {
  display: grid;
  gap: 10px;

  article {
    padding: 10px;
    border-radius: 7px;
    background: var(--vd-control-bg);
  }

  span {
    color: var(--vd-soft);
    font-size: 12px;
  }

  strong {
    float: right;
    color: var(--vd-heading);
    font-size: 12px;
  }

  i {
    display: block;
    height: 5px;
    margin-top: 10px;
    border-radius: 99px;
    background: linear-gradient(90deg, var(--vd-accent), var(--vd-success));
  }
}

.mini-chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.donut-wrap {
  display: grid;
  grid-template-columns: 132px minmax(0, 1fr);
  align-items: center;
  gap: 14px;
}

.donut {
  position: relative;
  display: grid;
  place-items: center;
  width: 122px;
  height: 122px;
  border-radius: 50%;

  &::after {
    position: absolute;
    inset: 25px;
    border-radius: 50%;
    background: var(--vd-donut-center);
    content: "";
  }

  strong,
  span {
    position: relative;
    z-index: 1;
  }

  strong {
    align-self: end;
    color: var(--vd-heading);
    font-size: 24px;
  }

  span {
    align-self: start;
    color: var(--vd-soft);
    font-size: 12px;
  }
}

.source-list,
.buffer-list {
  display: grid;
  gap: 8px;
  min-width: 0;

  article {
    display: grid;
    grid-template-columns: 10px minmax(0, 1fr) auto auto;
    gap: 7px;
    align-items: center;
    color: var(--vd-soft);
    font-size: 12px;
  }

  i {
    width: 8px;
    height: 8px;
    border-radius: 2px;
  }

  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: var(--vd-heading);
  }

  em {
    color: var(--vd-muted);
    font-style: normal;
  }
}

.buffer-list article {
  grid-template-columns: minmax(0, 1fr) auto;
}

.inline-empty {
  min-height: 82px;
  display: grid;
  place-items: center;
  border: 1px dashed var(--vd-soft-border);
  border-radius: 7px;
  color: var(--vd-muted);
  font-size: 12px;
  text-align: center;

  &.tall {
    min-height: 142px;
  }
}

.type-bars {
  display: grid;
  gap: 14px;

  article {
    display: grid;
    grid-template-columns: 50px minmax(0, 1fr) 58px;
    align-items: center;
    gap: 10px;
    color: var(--vd-soft);
    font-size: 12px;
  }

  div {
    height: 10px;
    overflow: hidden;
    border-radius: 999px;
    background: var(--vd-soft-border);
  }

  i {
    display: block;
    height: 100%;
    border-radius: inherit;
  }

  strong {
    color: var(--vd-heading);
    text-align: right;
  }
}

.bar-chart {
  display: flex;
  align-items: end;
  gap: 6px;
  height: 160px;

  span {
    flex: 1;
    min-width: 4px;
    border-radius: 4px 4px 0 0;
    background: linear-gradient(180deg, var(--vd-series-dropped), var(--vd-warning));
  }
}

.host-summary {
  overflow: auto;

  table {
    width: 100%;
    min-width: 520px;
    border-collapse: collapse;
    color: var(--vd-soft);
    font-size: 12px;
  }

  th,
  td {
    padding: 8px 10px;
    border-bottom: 1px solid var(--vd-soft-border);
    text-align: left;
    white-space: nowrap;
  }

  th {
    color: var(--vd-muted);
    font-weight: 640;
  }

  .table-empty {
    color: var(--vd-muted);
    text-align: center;
  }

  td:first-child {
    color: var(--vd-heading);
    font-weight: 700;
  }
}

.table-status {
  padding: 3px 7px;
  border-radius: 999px;
  background: var(--vd-success-soft-bg);
  color: var(--vd-success-text);

  &.warning {
    background: var(--vd-warning-soft-bg);
    color: var(--vd-warning-text);
  }

  &.critical {
    background: var(--vd-danger-soft-bg);
    color: var(--vd-danger-text);
  }
}

.host-modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 2200;
  display: grid;
  place-items: center;
  padding: 24px;
  background: var(--vd-backdrop);
  backdrop-filter: blur(6px);
}

.host-modal-motion-enter-active,
.host-modal-motion-leave-active {
  transition: opacity 0.18s ease;
}

.host-modal-motion-enter-from,
.host-modal-motion-leave-to {
  opacity: 0;
}

.host-modal-motion-enter-active .host-modal,
.host-modal-motion-leave-active .host-modal {
  transition:
    opacity 0.18s ease,
    transform 0.18s ease;
}

.host-modal-motion-enter-from .host-modal,
.host-modal-motion-leave-to .host-modal {
  opacity: 0;
  transform: translateY(12px) scale(0.98);
}

.host-modal {
  width: min(760px, calc(100vw - 32px));
  max-height: calc(100vh - 48px);
  margin: 0;
  overflow: auto;
  border: 1px solid var(--vd-control-border);
  border-radius: 7px;
  background: var(--vd-modal-bg);
  box-shadow: 0 24px 68px rgba(0, 0, 0, 0.34);

  header,
  footer {
    display: flex;
    justify-content: space-between;
    gap: 12px;
  }

  header {
    align-items: flex-start;
    padding: 14px;
    border-bottom: 1px solid var(--vd-soft-border);
  }

  h3 {
    margin: 0 0 12px;
    color: var(--vd-heading);
    font-size: 14px;
  }

  nav {
    display: flex;
    flex-wrap: wrap;
    gap: 16px;
    color: var(--vd-muted);
    font-size: 12px;

    .active {
      color: var(--vd-accent);
    }
  }

  > .mini-chart,
  .detail-split,
  .detail-stat-grid {
    margin: 12px 14px 0;
  }

  footer {
    margin-top: 12px;
    padding: 12px 14px 16px;
    color: var(--vd-muted);
    font-size: 12px;
    flex-wrap: wrap;
  }
}

.detail-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;

  article {
    padding: 10px;
    border: 1px solid var(--vd-panel-border);
    border-radius: 7px;
    background: var(--vd-control-bg);
  }

  span,
  strong {
    display: block;
  }

  span {
    color: var(--vd-muted);
    font-size: 11px;
  }

  strong {
    margin-top: 5px;
    color: var(--vd-heading);
    font-size: 15px;
  }
}

.detail-split {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

@media (max-width: 1300px) {
  .span-3,
  .span-6 {
    grid-column: span 6;
  }

  .metric-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .event-metric {
    border-bottom: 1px solid var(--vd-soft-border);
  }
}

@media (max-width: 860px) {
  .vector-dashboard {
    padding: 14px 14px 22px;
  }

  .dashboard-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .span-3,
  .span-6 {
    grid-column: span 12;
  }

  .host-detail-grid,
  .mini-chart-grid,
  .donut-wrap,
  .detail-split,
  .detail-stat-grid {
    grid-template-columns: 1fr;
  }

  .host-modal {
    margin: 12px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .host-card,
  .host-hover-popover,
  .host-modal-motion-enter-active,
  .host-modal-motion-leave-active,
  .host-modal-motion-enter-active .host-modal,
  .host-modal-motion-leave-active .host-modal {
    transition: none;
  }
}
</style>
