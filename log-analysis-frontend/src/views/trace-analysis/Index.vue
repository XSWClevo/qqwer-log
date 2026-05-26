<template>
  <AppLayout>
    <div class="trace-page">
      <div class="page-header">
        <div>
          <h1 class="page-title">链路分析</h1>
          <p class="page-subtitle">围绕来源 IP、主机、攻击类型、规则和证据日志构建一次安全事件链路</p>
        </div>
        <div class="header-actions">
          <el-button :icon="Refresh" :loading="loading" @click="loadChainData">刷新</el-button>
        </div>
      </div>

      <div class="toolbar">
        <el-select v-model="timeRange" class="time-select" @change="handleTimeRangeChange">
          <el-option label="最近 1 小时" value="1h" />
          <el-option label="最近 6 小时" value="6h" />
          <el-option label="最近 24 小时" value="24h" />
          <el-option label="最近 7 天" value="7d" />
          <el-option label="最近 30 天" value="30d" />
          <el-option label="自定义" value="custom" />
        </el-select>
        <el-date-picker
          v-if="timeRange === 'custom'"
          v-model="customTimeRange"
          type="datetimerange"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          range-separator="至"
          value-format="YYYY-MM-DD HH:mm:ss"
          class="custom-time"
          @change="loadChainData"
        />
        <el-input v-model="filters.sourceIp" placeholder="来源 IP" clearable class="filter-input" @keyup.enter="loadChainData" />
        <el-input v-model="filters.hostname" placeholder="主机名" clearable class="filter-input" @keyup.enter="loadChainData" />
        <el-select v-model="filters.attackType" placeholder="攻击类型" clearable class="type-select">
          <el-option v-for="item in attackTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="filters.severity" placeholder="风险等级" clearable class="severity-select">
          <el-option label="严重" value="critical" />
          <el-option label="高危" value="high" />
          <el-option label="中危" value="medium" />
          <el-option label="低危" value="low" />
        </el-select>
        <el-input
          v-model="filters.keyword"
          :prefix-icon="Search"
          placeholder="日志、规则、原因"
          clearable
          class="keyword-input"
          @keyup.enter="loadChainData"
        />
        <el-button type="primary" :icon="Search" :loading="loading" @click="loadChainData">分析</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>

      <div class="summary-grid">
        <div v-for="item in summaryItems" :key="item.label" class="summary-tile">
          <span class="summary-label">{{ item.label }}</span>
          <span class="summary-value">{{ item.value }}</span>
          <span class="summary-sub">{{ item.sub }}</span>
        </div>
      </div>

      <div class="stage-strip">
        <div
          v-for="stage in stageRows"
          :key="stage.key"
          class="stage-card"
          :class="{ active: activeStage === stage.key }"
          @click="toggleStage(stage.key)"
        >
          <div class="stage-top">
            <span class="stage-name">{{ stage.name }}</span>
            <span class="stage-count">{{ stage.count }}</span>
          </div>
          <div class="stage-bar">
            <span :style="{ width: `${stage.percent}%` }"></span>
          </div>
          <p>{{ stage.timeRange || '无命中' }}</p>
        </div>
      </div>

      <div class="analysis-grid">
        <section class="panel graph-panel">
          <div class="panel-header">
            <div>
              <h2>实体关系图</h2>
              <p>来源 IP -> 主机/资产 -> 攻击类型 -> 命中规则</p>
            </div>
            <el-tag v-if="activeNode" closable @close="activeNodeId = ''">
              {{ activeNode.name }}
            </el-tag>
          </div>
          <div v-loading="loading" class="chain-flow-shell">
            <div class="graph-legend" aria-label="链路图例">
              <div class="legend-row">
                <span class="legend-line animated"></span>
                <span>流动虚线：高危/严重链路</span>
              </div>
              <div class="legend-row">
                <span class="legend-line solid"></span>
                <span>颜色：关系最高风险</span>
              </div>
              <div class="legend-row">
                <span class="legend-line thick"></span>
                <span>粗细/数字：命中次数</span>
              </div>
            </div>
            <VueFlow
              :nodes="flowNodes"
              :edges="flowEdges"
              :default-edge-options="defaultEdgeOptions"
              :nodes-draggable="true"
              :nodes-connectable="false"
              :edges-updatable="false"
              :elements-selectable="true"
              :min-zoom="0.35"
              :max-zoom="1.8"
              fit-view-on-init
              class="chain-flow"
              @node-click="handleFlowNodeClick"
              @pane-click="clearActiveNode"
            >
              <Background :gap="24" :size="1" pattern-color="#d7dce5" />
              <Controls position="bottom-right" />
              <MiniMap
                position="bottom-left"
                :node-color="miniMapNodeColor"
                :node-stroke-color="miniMapNodeStrokeColor"
                pannable
                zoomable
              />

              <template #node-entity="nodeProps">
                <div
                  class="entity-flow-node"
                  :class="[nodeProps.data.category, nodeProps.data.riskClass, { active: nodeProps.data.active }]"
                >
                  <Handle type="target" :position="Position.Left" class="flow-handle target" />
                  <div class="node-accent" :style="{ background: nodeProps.data.categoryColor }"></div>
                  <div class="node-content">
                    <div class="node-topline">
                      <span class="node-type">{{ nodeProps.data.categoryLabel }}</span>
                      <span class="node-count">{{ nodeProps.data.count }} 次</span>
                    </div>
                    <div class="node-name" :title="nodeProps.data.name">{{ nodeProps.data.name }}</div>
                    <div class="node-bottomline">
                      <span class="risk-dot" :style="{ background: nodeProps.data.severityColor }"></span>
                      <span>{{ nodeProps.data.severityLabel }}</span>
                    </div>
                  </div>
                  <Handle type="source" :position="Position.Right" class="flow-handle source" />
                </div>
              </template>
            </VueFlow>
          </div>
        </section>

        <section class="panel side-panel">
          <div class="panel-header compact">
            <h2>{{ activeNode ? '实体上下文' : '高风险实体' }}</h2>
          </div>
          <div v-if="activeNode" class="entity-context">
            <div class="entity-title">
              <span class="entity-category">{{ categoryLabel(activeNode.category) }}</span>
              <strong>{{ activeNode.name }}</strong>
            </div>
            <dl>
              <dt>命中次数</dt>
              <dd>{{ activeNode.count }}</dd>
              <dt>最高风险</dt>
              <dd>
                <el-tag :type="severityTag(activeNode.maxSeverity)" size="small">
                  {{ severityLabel(activeNode.maxSeverity) }}
                </el-tag>
              </dd>
              <dt>关联事件</dt>
              <dd>{{ activeNodeRecords.length }}</dd>
            </dl>
            <el-button type="primary" plain size="small" @click="focusNodeAsFilter">按该实体过滤</el-button>
          </div>

          <div v-else class="top-entities">
            <div v-for="item in topEntities" :key="item.id" class="entity-row" @click="activeNodeId = item.id">
              <span class="entity-dot" :style="{ backgroundColor: categoryColor(item.category) }"></span>
              <div>
                <strong>{{ item.name }}</strong>
                <p>{{ categoryLabel(item.category) }}，{{ item.count }} 次命中</p>
              </div>
              <el-tag :type="severityTag(item.maxSeverity)" size="small">{{ severityLabel(item.maxSeverity) }}</el-tag>
            </div>
            <el-empty v-if="!topEntities.length" description="暂无实体" :image-size="64" />
          </div>
        </section>
      </div>

      <section class="panel timeline-panel">
        <div class="panel-header compact">
          <h2>证据时间线</h2>
          <span class="timeline-count">{{ visibleTimeline.length }} / {{ filteredTimeline.length }} 条</span>
        </div>
        <div class="timeline-list">
          <div
            v-for="record in visibleTimeline"
            :key="record.classificationKey"
            class="timeline-item"
            :class="record.severity"
            @click="openDetail(record)"
          >
            <div class="timeline-time">
              <span>{{ formatDateTime(record.logTimestamp || record.classifiedAt) }}</span>
              <el-tag :type="severityTag(record.severity)" size="small">{{ severityLabel(record.severity) }}</el-tag>
            </div>
            <div class="timeline-body">
              <div class="timeline-title">
                <span>{{ attackTypeLabel(record.attackType) }}</span>
                <small>{{ record.attackSubType || record.ruleName }}</small>
              </div>
              <p>{{ record.message || record.raw || record.reason || '-' }}</p>
              <div class="timeline-meta">
                <span>来源 {{ record.sourceIp || '-' }}</span>
                <span>主机 {{ record.hostname || '-' }}</span>
                <span>规则 {{ record.ruleId }}</span>
                <span>表 {{ record.tableName || record.indexName || '-' }}</span>
              </div>
            </div>
          </div>
          <el-empty v-if="!visibleTimeline.length" description="当前条件下没有链路证据" :image-size="88" />
        </div>
      </section>

      <el-drawer v-model="detailVisible" title="证据详情" size="560px">
        <template v-if="selectedRecord">
          <dl class="detail-list">
            <dt>日志时间</dt>
            <dd>{{ formatDateTime(selectedRecord.logTimestamp || selectedRecord.classifiedAt) }}</dd>
            <dt>风险等级</dt>
            <dd>{{ severityLabel(selectedRecord.severity) }}，置信度 {{ selectedRecord.confidence ?? '-' }}</dd>
            <dt>来源 IP</dt>
            <dd>{{ selectedRecord.sourceIp || '-' }}</dd>
            <dt>主机</dt>
            <dd>{{ selectedRecord.hostname || '-' }}</dd>
            <dt>攻击类型</dt>
            <dd>{{ attackTypeLabel(selectedRecord.attackType) }} / {{ selectedRecord.attackSubType || '-' }}</dd>
            <dt>命中规则</dt>
            <dd>{{ selectedRecord.ruleName }}（{{ selectedRecord.ruleId }}）</dd>
            <dt>MITRE</dt>
            <dd>{{ selectedRecord.mitreTactic || '-' }} / {{ selectedRecord.mitreTechnique || '-' }}</dd>
            <dt>判断原因</dt>
            <dd>{{ selectedRecord.reason || '-' }}</dd>
            <dt>原始日志</dt>
            <dd class="raw-log">{{ selectedRecord.raw || selectedRecord.message || '-' }}</dd>
          </dl>
        </template>
      </el-drawer>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { Handle, MarkerType, Position, useVueFlow, VueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import type { Edge, Node, NodeMouseEvent } from '@vue-flow/core'
import AppLayout from '@/components/layout/AppLayout.vue'
import { queryAttackClassifications, type AttackClassificationRecord } from '@/api/attack'

type NodeCategory = 'source' | 'host' | 'attack' | 'rule'

interface GraphNode {
  id: string
  name: string
  category: NodeCategory
  count: number
  maxSeverity: string
}

interface FlowNodeData extends GraphNode {
  active: boolean
  categoryColor: string
  categoryLabel: string
  riskClass: string
  severityColor: string
  severityLabel: string
}

interface GraphLink {
  id: string
  source: string
  target: string
  count: number
  maxSeverity: string
}

interface StageRow {
  key: string
  name: string
  count: number
  percent: number
  timeRange: string
}

const loading = ref(false)
const records = ref<AttackClassificationRecord[]>([])
const timeRange = ref('24h')
const customTimeRange = ref<[string, string]>()
const activeNodeId = ref('')
const activeStage = ref('')
const detailVisible = ref(false)
const selectedRecord = ref<AttackClassificationRecord>()

const filters = reactive({
  sourceIp: '',
  hostname: '',
  attackType: '',
  severity: '',
  keyword: ''
})

const { fitView } = useVueFlow()

const defaultEdgeOptions = {
  type: 'smoothstep',
  style: { stroke: '#9ca3af', strokeWidth: 1.8 },
  markerEnd: { type: MarkerType.ArrowClosed, color: '#9ca3af' }
}

const attackTypeOptions = [
  { value: 'authentication_attack', label: '认证攻击' },
  { value: 'web_attack', label: 'Web 攻击' },
  { value: 'command_execution', label: '命令执行' },
  { value: 'scan_probe', label: '扫描探测' },
  { value: 'privilege_abuse', label: '权限异常' }
]

const graphSourceRecords = computed(() => {
  if (!activeStage.value) {
    return records.value
  }
  return records.value.filter(record => stageForRecord(record).key === activeStage.value)
})

const graphData = computed(() => buildGraph(graphSourceRecords.value))
const activeNode = computed(() => graphData.value.nodes.find(item => item.id === activeNodeId.value))
const topEntities = computed(() => graphData.value.nodes
  .filter(item => item.category === 'source' || item.category === 'host')
  .sort((a, b) => severityRank(b.maxSeverity) - severityRank(a.maxSeverity) || b.count - a.count)
  .slice(0, 8))

const flowNodes = computed<Node<FlowNodeData>[]>(() => buildFlowNodes(graphData.value.nodes))
const flowEdges = computed<Edge[]>(() => buildFlowEdges(graphData.value.links))

const filteredTimeline = computed(() => {
  let result = [...records.value]
  if (activeStage.value) {
    result = result.filter(item => stageForRecord(item).key === activeStage.value)
  }
  if (activeNode.value) {
    result = result.filter(item => recordMatchesNode(item, activeNode.value!))
  }
  return result.sort((a, b) => timestampOf(a).localeCompare(timestampOf(b)))
})

const visibleTimeline = computed(() => filteredTimeline.value.slice(0, 200))
const activeNodeRecords = computed(() => activeNode.value
  ? records.value.filter(item => recordMatchesNode(item, activeNode.value!))
  : [])

const stageRows = computed<StageRow[]>(() => {
  const total = records.value.length || 1
  return stageDefinitions.map(stage => {
    const items = records.value.filter(record => stageForRecord(record).key === stage.key)
    return {
      key: stage.key,
      name: stage.name,
      count: items.length,
      percent: Math.min(100, Math.round((items.length / total) * 100)),
      timeRange: buildStageTimeRange(items)
    }
  })
})

const summaryItems = computed(() => {
  const sources = new Set(records.value.map(item => item.sourceIp).filter(Boolean))
  const hosts = new Set(records.value.map(item => item.hostname).filter(Boolean))
  const highRisk = records.value.filter(item => ['critical', 'high'].includes(item.severity)).length
  const first = records.value.length ? formatDateTime(records.value.map(timestampOf).sort()[0]) : '-'
  const sortedTimes = records.value.map(timestampOf).sort()
  const last = records.value.length ? formatDateTime(sortedTimes[sortedTimes.length - 1]) : '-'
  return [
    { label: '事件数', value: records.value.length.toLocaleString(), sub: `${highRisk} 条高危及以上` },
    { label: '来源 IP', value: sources.size.toLocaleString(), sub: '攻击入口实体' },
    { label: '受影响主机', value: hosts.size.toLocaleString(), sub: '资产影响面' },
    { label: '时间跨度', value: records.value.length ? `${first.slice(5, 16)} - ${last.slice(5, 16)}` : '-', sub: '按日志时间排序' }
  ]
})

const loadChainData = async () => {
  const [startTime, endTime] = getTimeRange()
  loading.value = true
  try {
    const response: any = await queryAttackClassifications({
      startTime,
      endTime,
      sourceIp: filters.sourceIp || undefined,
      hostname: filters.hostname || undefined,
      attackType: filters.attackType || undefined,
      severity: filters.severity || undefined,
      keyword: filters.keyword || undefined,
      pageNum: 1,
      pageSize: 1000
    })
    records.value = response.data?.records || []
    activeNodeId.value = ''
    fitGraph()
  } catch (error) {
    ElMessage.error('链路分析加载失败')
  } finally {
    loading.value = false
  }
}

const handleTimeRangeChange = () => {
  if (timeRange.value !== 'custom') {
    loadChainData()
  }
}

const resetFilters = () => {
  filters.sourceIp = ''
  filters.hostname = ''
  filters.attackType = ''
  filters.severity = ''
  filters.keyword = ''
  activeNodeId.value = ''
  activeStage.value = ''
  loadChainData()
}

const toggleStage = (stage: string) => {
  activeStage.value = activeStage.value === stage ? '' : stage
  activeNodeId.value = ''
  fitGraph()
}

const focusNodeAsFilter = () => {
  if (!activeNode.value) return
  if (activeNode.value.category === 'source') filters.sourceIp = activeNode.value.name
  if (activeNode.value.category === 'host') filters.hostname = activeNode.value.name
  if (activeNode.value.category === 'attack') filters.attackType = reverseAttackTypeLabel(activeNode.value.name)
  activeNodeId.value = ''
  loadChainData()
}

const openDetail = (record: AttackClassificationRecord) => {
  selectedRecord.value = record
  detailVisible.value = true
}

const handleFlowNodeClick = ({ node }: NodeMouseEvent) => {
  activeNodeId.value = node.id
}

const clearActiveNode = () => {
  activeNodeId.value = ''
}

const fitGraph = () => {
  nextTick(() => {
    window.setTimeout(() => fitView({ padding: 0.18, duration: 280 }), 40)
  })
}

const buildFlowNodes = (nodes: GraphNode[]): Node<FlowNodeData>[] => {
  const groups: Record<NodeCategory, GraphNode[]> = {
    source: [],
    host: [],
    attack: [],
    rule: []
  }
  nodes.forEach(node => groups[node.category].push(node))

  const categoryOrder: NodeCategory[] = ['source', 'host', 'attack', 'rule']
  const columnX: Record<NodeCategory, number> = {
    source: 40,
    host: 360,
    attack: 680,
    rule: 1000
  }
  const maxRows = Math.max(...categoryOrder.map(category => groups[category].length), 1)
  const canvasCenter = Math.max(260, (maxRows - 1) * 106 / 2 + 52)

  return categoryOrder.flatMap(category => {
    const items = groups[category]
      .sort((left, right) => severityRank(right.maxSeverity) - severityRank(left.maxSeverity) || right.count - left.count)
    const groupHeight = (items.length - 1) * 106
    const startY = canvasCenter - groupHeight / 2

    return items.map((item, index) => ({
      id: item.id,
      type: 'entity',
      position: { x: columnX[item.category], y: startY + index * 106 },
      draggable: true,
      selectable: true,
      connectable: false,
      data: {
        ...item,
        active: activeNodeId.value === item.id,
        categoryColor: categoryColor(item.category),
        categoryLabel: categoryLabel(item.category),
        riskClass: riskClass(item.maxSeverity),
        severityColor: severityColor(item.maxSeverity),
        severityLabel: severityLabel(item.maxSeverity)
      }
    }))
  })
}

const buildFlowEdges = (links: GraphLink[]): Edge[] => links.map(link => {
  const stroke = severityColor(link.maxSeverity)
  return {
    id: link.id,
    source: link.source,
    target: link.target,
    type: 'smoothstep',
    animated: severityRank(link.maxSeverity) >= 3,
    label: link.count > 1 ? String(link.count) : undefined,
    markerEnd: { type: MarkerType.ArrowClosed, color: stroke },
    style: {
      stroke,
      strokeWidth: Math.min(4, 1.3 + Math.sqrt(link.count)),
      opacity: 0.78
    },
    labelStyle: {
      fill: '#475569',
      fontWeight: 650,
      fontSize: 11
    },
    labelBgStyle: {
      fill: '#ffffff',
      fillOpacity: 0.92
    },
    labelBgPadding: [4, 4],
    labelBgBorderRadius: 6
  }
})

const buildGraph = (rows: AttackClassificationRecord[]) => {
  const nodes = new Map<string, GraphNode>()
  const links = new Map<string, GraphLink>()

  rows.forEach(record => {
    const source = record.sourceIp || '未知来源'
    const host = record.hostname || record.tableName || record.indexName || '未知主机'
    const attack = attackTypeLabel(record.attackType)
    const rule = record.ruleName || record.ruleId || '未知规则'

    const sourceId = addNode(nodes, `source:${source}`, source, 'source', record.severity)
    const hostId = addNode(nodes, `host:${host}`, host, 'host', record.severity)
    const attackId = addNode(nodes, `attack:${attack}`, attack, 'attack', record.severity)
    const ruleId = addNode(nodes, `rule:${rule}`, rule, 'rule', record.severity)

    addLink(links, sourceId, hostId, record.severity)
    addLink(links, hostId, attackId, record.severity)
    addLink(links, attackId, ruleId, record.severity)
  })

  return {
    nodes: Array.from(nodes.values()),
    links: Array.from(links.values())
  }
}

const addNode = (nodes: Map<string, GraphNode>, id: string, name: string, category: NodeCategory, severity: string) => {
  const existing = nodes.get(id)
  if (existing) {
    existing.count += 1
    existing.maxSeverity = maxSeverity(existing.maxSeverity, severity)
    return id
  }
  nodes.set(id, { id, name, category, count: 1, maxSeverity: severity })
  return id
}

const addLink = (links: Map<string, GraphLink>, source: string, target: string, severity: string) => {
  const id = `${source}->${target}`
  const existing = links.get(id)
  if (existing) {
    existing.count += 1
    existing.maxSeverity = maxSeverity(existing.maxSeverity, severity)
    return
  }
  links.set(id, { id, source, target, count: 1, maxSeverity: severity })
}

const recordMatchesNode = (record: AttackClassificationRecord, node: GraphNode) => {
  if (node.category === 'source') return (record.sourceIp || '未知来源') === node.name
  if (node.category === 'host') return (record.hostname || record.tableName || record.indexName || '未知主机') === node.name
  if (node.category === 'attack') return attackTypeLabel(record.attackType) === node.name
  return (record.ruleName || record.ruleId || '未知规则') === node.name
}

const stageDefinitions = [
  { key: 'scan', name: '扫描探测' },
  { key: 'auth', name: '凭证攻击' },
  { key: 'web', name: 'Web 入侵' },
  { key: 'execution', name: '命令执行' },
  { key: 'privilege', name: '权限异常' },
  { key: 'other', name: '其他行为' }
]

const stageForRecord = (record: AttackClassificationRecord) => {
  const type = record.attackType || ''
  if (type.includes('scan')) return stageDefinitions[0]!
  if (type.includes('authentication')) return stageDefinitions[1]!
  if (type.includes('web')) return stageDefinitions[2]!
  if (type.includes('command') || type.includes('execution')) return stageDefinitions[3]!
  if (type.includes('privilege')) return stageDefinitions[4]!
  return stageDefinitions[5]!
}

const buildStageTimeRange = (items: AttackClassificationRecord[]) => {
  if (!items.length) return ''
  const times = items.map(timestampOf).sort()
  return `${formatDateTime(times[0]).slice(5, 16)} - ${formatDateTime(times[times.length - 1]).slice(5, 16)}`
}

const getTimeRange = (): [string, string] => {
  if (timeRange.value === 'custom' && customTimeRange.value?.length === 2) {
    return customTimeRange.value
  }

  const end = new Date()
  const start = new Date(end)
  if (timeRange.value === '1h') start.setHours(start.getHours() - 1)
  else if (timeRange.value === '6h') start.setHours(start.getHours() - 6)
  else if (timeRange.value === '7d') start.setDate(start.getDate() - 7)
  else if (timeRange.value === '30d') start.setDate(start.getDate() - 30)
  else start.setHours(start.getHours() - 24)
  return [formatDate(start), formatDate(end)]
}

const timestampOf = (record: AttackClassificationRecord) => record.logTimestamp || record.classifiedAt || ''

const formatDateTime = (value?: string) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

const formatDate = (date: Date) => {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const maxSeverity = (left: string, right: string) => severityRank(right) > severityRank(left) ? right : left

const severityRank = (severity?: string) => {
  const ranks: Record<string, number> = { critical: 4, high: 3, medium: 2, low: 1 }
  return ranks[severity || ''] || 0
}

const severityColor = (severity?: string) => {
  const map: Record<string, string> = {
    critical: '#991B1B',
    high: '#DC2626',
    medium: '#D97706',
    low: '#64748B'
  }
  return map[severity || ''] || '#94A3B8'
}

const severityTag = (severity?: string) => {
  const map: Record<string, any> = {
    critical: 'danger',
    high: 'danger',
    medium: 'warning',
    low: 'info'
  }
  return map[severity || ''] || 'info'
}

const severityLabel = (severity?: string) => {
  const map: Record<string, string> = {
    critical: '严重',
    high: '高危',
    medium: '中危',
    low: '低危'
  }
  return map[severity || ''] || severity || '未知'
}

const attackTypeLabel = (type?: string) => {
  const map: Record<string, string> = {
    authentication_attack: '认证攻击',
    web_attack: 'Web 攻击',
    command_execution: '命令执行',
    scan_probe: '扫描探测',
    privilege_abuse: '权限异常'
  }
  return map[type || ''] || type || '未知类型'
}

const reverseAttackTypeLabel = (label: string) => {
  const item = attackTypeOptions.find(option => option.label === label)
  return item?.value || ''
}

const categoryLabel = (category: NodeCategory) => {
  const map: Record<NodeCategory, string> = {
    source: '来源 IP',
    host: '主机/资产',
    attack: '攻击类型',
    rule: '命中规则'
  }
  return map[category]
}

const riskClass = (severity?: string) => {
  if (severity === 'critical' || severity === 'high') return 'risk-high'
  if (severity === 'medium') return 'risk-medium'
  return 'risk-low'
}

const categoryColor = (category: NodeCategory) => {
  const map: Record<NodeCategory, string> = {
    source: '#2563EB',
    host: '#059669',
    attack: '#D97706',
    rule: '#7C3AED'
  }
  return map[category]
}

const miniMapNodeColor = (node: any) => categoryColor(node.data?.category || 'rule')
const miniMapNodeStrokeColor = (node: any) => severityColor(node.data?.maxSeverity)

onMounted(async () => {
  await loadChainData()
})
</script>

<style lang="scss">
@import '@vue-flow/core/dist/style.css';
@import '@vue-flow/core/dist/theme-default.css';
@import '@vue-flow/controls/dist/style.css';
@import '@vue-flow/minimap/dist/style.css';
</style>

<style scoped lang="scss">
.trace-page {
  min-height: 100vh;
  padding: 20px 24px 28px;
  background: var(--macos-fill-tertiary);
  color: var(--macos-text-primary);
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 650;
}

.page-subtitle {
  margin: 6px 0 0;
  color: var(--macos-text-secondary);
  font-size: 13px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 12px;
  margin-bottom: 14px;
  background: var(--macos-card-bg);
  border: 1px solid var(--macos-border);
  border-radius: 8px;
}

.time-select,
.type-select,
.severity-select,
.filter-input {
  width: 140px;
}

.custom-time {
  width: 360px;
}

.keyword-input {
  width: 240px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(160px, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.summary-tile {
  min-height: 88px;
  padding: 14px 16px;
  background: var(--macos-card-bg);
  border: 1px solid var(--macos-border);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.summary-label {
  font-size: 12px;
  color: var(--macos-text-secondary);
}

.summary-value {
  margin-top: 8px;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.25;
  word-break: break-word;
}

.summary-sub {
  margin-top: 8px;
  color: var(--macos-text-tertiary);
  font-size: 12px;
}

.stage-strip {
  display: grid;
  grid-template-columns: repeat(6, minmax(120px, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.stage-card {
  padding: 12px;
  background: var(--macos-card-bg);
  border: 1px solid var(--macos-border);
  border-radius: 8px;
  cursor: pointer;
  transition: var(--macos-transition-fast);

  &.active {
    border-color: var(--macos-blue);
    box-shadow: 0 0 0 2px var(--macos-blue-light);
  }

  p {
    margin: 8px 0 0;
    font-size: 11px;
    color: var(--macos-text-tertiary);
  }
}

.stage-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.stage-name {
  font-size: 13px;
  font-weight: 650;
}

.stage-count {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-weight: 700;
}

.stage-bar {
  height: 6px;
  margin-top: 10px;
  border-radius: 999px;
  background: var(--macos-fill-secondary);
  overflow: hidden;

  span {
    display: block;
    height: 100%;
    min-width: 0;
    background: #2563EB;
    border-radius: inherit;
  }
}

.analysis-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 14px;
  margin-bottom: 14px;
}

.panel {
  background: var(--macos-card-bg);
  border: 1px solid var(--macos-border);
  border-radius: 8px;
  overflow: hidden;
}

.panel-header {
  min-height: 56px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--macos-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;

  &.compact {
    min-height: 48px;
  }

  h2 {
    margin: 0;
    font-size: 15px;
    font-weight: 650;
  }

  p {
    margin: 4px 0 0;
    font-size: 12px;
    color: var(--macos-text-secondary);
  }
}

.chain-flow-shell {
  height: 520px;
  position: relative;
  background:
    linear-gradient(90deg, rgba(37, 99, 235, 0.04), transparent 18%, transparent 82%, rgba(124, 58, 237, 0.04)),
    var(--macos-card-bg);
}

.graph-legend {
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 5;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  max-width: calc(100% - 24px);
  padding: 8px 10px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(203, 213, 225, 0.85);
  border-radius: 8px;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.08);
  color: #475569;
  font-size: 12px;
  pointer-events: none;
}

.legend-row {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

.legend-line {
  width: 34px;
  height: 2px;
  border-radius: 999px;
  background: #dc2626;

  &.animated {
    height: 3px;
    background:
      repeating-linear-gradient(90deg, #dc2626 0 8px, transparent 8px 13px);
    background-size: 26px 3px;
    animation: legend-flow 0.9s linear infinite;
  }

  &.solid {
    background: #d97706;
  }

  &.thick {
    height: 4px;
    background: #2563eb;
    position: relative;

    &::after {
      content: '3';
      position: absolute;
      top: -12px;
      right: -8px;
      min-width: 16px;
      height: 16px;
      padding: 0 4px;
      border-radius: 999px;
      background: #ffffff;
      border: 1px solid #cbd5e1;
      color: #475569;
      font-size: 10px;
      line-height: 14px;
      text-align: center;
      box-sizing: border-box;
    }
  }
}

@keyframes legend-flow {
  from {
    background-position: 0 0;
  }

  to {
    background-position: 26px 0;
  }
}

.chain-flow {
  height: 100%;

  :deep(.vue-flow__pane) {
    cursor: grab;
  }

  :deep(.vue-flow__edge-path) {
    transition: stroke-width 0.2s ease, opacity 0.2s ease;
  }

  :deep(.vue-flow__edge.animated path) {
    stroke-dasharray: 9 5;
  }

  :deep(.vue-flow__edge-textbg) {
    stroke: rgba(148, 163, 184, 0.38);
  }

  :deep(.vue-flow__controls) {
    border: 1px solid var(--macos-border);
    border-radius: 8px;
    overflow: hidden;
    box-shadow: var(--macos-shadow-sm);
  }

  :deep(.vue-flow__minimap) {
    border: 1px solid var(--macos-border);
    border-radius: 8px;
    overflow: hidden;
    background: rgba(255, 255, 255, 0.92);
  }
}

.entity-flow-node {
  width: 236px;
  min-height: 78px;
  position: relative;
  display: grid;
  grid-template-columns: 5px 1fr;
  overflow: hidden;
  background: #ffffff;
  border: 1px solid #d9e1ec;
  border-radius: 8px;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.08);
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;

  &:hover,
  &.active {
    border-color: var(--macos-blue);
    box-shadow: 0 10px 26px rgba(37, 99, 235, 0.18);
    transform: translateY(-1px);
  }

  &.risk-high {
    border-color: rgba(220, 38, 38, 0.46);
  }

  &.risk-medium {
    border-color: rgba(217, 119, 6, 0.42);
  }
}

.node-accent {
  width: 5px;
  height: 100%;
}

.node-content {
  min-width: 0;
  padding: 10px 12px 10px 14px;
}

.node-topline,
.node-bottomline {
  display: flex;
  align-items: center;
  gap: 8px;
}

.node-topline {
  justify-content: space-between;
}

.node-type {
  color: #64748b;
  font-size: 11px;
  font-weight: 700;
}

.node-count {
  color: #475569;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 999px;
  background: #f1f5f9;
}

.node-name {
  margin-top: 6px;
  color: #0f172a;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-bottomline {
  margin-top: 8px;
  color: #64748b;
  font-size: 12px;
}

.risk-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.flow-handle {
  width: 8px;
  height: 8px;
  border: 2px solid #ffffff;
  background: #94a3b8;

  &.target {
    left: -5px;
  }

  &.source {
    right: -5px;
  }
}

.side-panel {
  min-height: 578px;
}

.entity-context {
  padding: 16px;

  dl {
    display: grid;
    grid-template-columns: 84px 1fr;
    row-gap: 12px;
    margin: 16px 0;
    font-size: 13px;
  }

  dt {
    color: var(--macos-text-secondary);
  }

  dd {
    margin: 0;
    font-weight: 600;
  }
}

.entity-title {
  display: flex;
  flex-direction: column;
  gap: 6px;
  word-break: break-all;
}

.entity-category {
  color: var(--macos-text-secondary);
  font-size: 12px;
}

.top-entities {
  padding: 10px;
}

.entity-row {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: 8px;
  cursor: pointer;

  &:hover {
    background: var(--macos-fill-secondary);
  }

  strong {
    display: block;
    font-size: 13px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  p {
    margin: 4px 0 0;
    color: var(--macos-text-secondary);
    font-size: 12px;
  }
}

.entity-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
}

.timeline-panel {
  min-height: 360px;
}

.timeline-count {
  font-size: 12px;
  color: var(--macos-text-tertiary);
}

.timeline-list {
  max-height: 520px;
  overflow: auto;
  padding: 8px 12px 12px;
}

.timeline-item {
  display: grid;
  grid-template-columns: 184px minmax(0, 1fr);
  gap: 14px;
  padding: 12px;
  border: 1px solid var(--macos-border);
  border-left-width: 4px;
  border-radius: 8px;
  margin-top: 8px;
  background: var(--macos-card-bg);
  cursor: pointer;

  &.critical,
  &.high {
    border-left-color: var(--macos-danger);
  }

  &.medium {
    border-left-color: var(--macos-warning);
  }

  &.low {
    border-left-color: var(--macos-text-tertiary);
  }

  &:hover {
    background: var(--macos-fill-tertiary);
  }
}

.timeline-time {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  color: var(--macos-text-secondary);
}

.timeline-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-weight: 650;

  small {
    color: var(--macos-text-secondary);
    font-weight: 500;
  }
}

.timeline-body {
  min-width: 0;

  p {
    margin: 8px 0;
    color: var(--macos-text-primary);
    font-size: 13px;
    line-height: 1.5;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.timeline-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  color: var(--macos-text-secondary);
  font-size: 12px;
}

.detail-list {
  display: grid;
  grid-template-columns: 96px 1fr;
  gap: 14px 12px;

  dt {
    color: var(--macos-text-secondary);
  }

  dd {
    margin: 0;
    min-width: 0;
    word-break: break-word;
  }
}

.raw-log {
  padding: 12px;
  background: var(--macos-fill-tertiary);
  border: 1px solid var(--macos-border);
  border-radius: 8px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  white-space: pre-wrap;
}

@media (max-width: 1320px) {
  .stage-strip {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .analysis-grid {
    grid-template-columns: 1fr;
  }

  .side-panel {
    min-height: auto;
  }
}

@media (max-width: 860px) {
  .trace-page {
    padding: 14px;
  }

  .page-header,
  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .time-select,
  .type-select,
  .severity-select,
  .filter-input,
  .keyword-input,
  .custom-time {
    width: 100%;
  }

  .summary-grid,
  .stage-strip {
    grid-template-columns: 1fr;
  }

  .timeline-item {
    grid-template-columns: 1fr;
  }
}
</style>
