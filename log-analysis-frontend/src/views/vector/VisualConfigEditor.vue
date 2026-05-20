<template>
  <div class="visual-editor">
    <!-- 顶部工具栏 -->
    <div class="editor-header">
      <div class="header-left">
        <el-button text @click="goBack">
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <span class="config-name">{{ configName }}</span>
        <el-tag size="small" type="info">{{ configFormat }}</el-tag>
      </div>
      <div class="header-right">
        <el-button @click="previewYaml">
          <el-icon><View /></el-icon>预览 YAML
        </el-button>
        <el-button type="primary" :loading="saving || validating" @click="saveConfig">
          <el-icon><Check /></el-icon>{{ validating ? '校验中...' : '保存' }}
        </el-button>
        <el-button type="success" @click="showDeployDialog = true">
          <el-icon><Promotion /></el-icon>部署
        </el-button>
      </div>
    </div>

    <div class="editor-body">
      <!-- 左侧组件面板 -->
      <div class="component-panel">
        <div class="panel-header">组件库</div>
        <el-input v-model="searchKeyword" placeholder="搜索组件..." clearable class="search-input">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>

        <el-collapse v-model="activeCollapse">
          <el-collapse-item title="Sources (输入)" name="sources">
            <div class="component-list">
              <template v-if="customSources.length > 0">
                <div class="component-group-label">自定义组件</div>
                <div
                  v-for="comp in customSources"
                  :key="'custom-' + comp.id"
                  class="component-item custom"
                  draggable="true"
                  @dragstart="onDragStart($event, 'source', comp)"
                >
                  <el-icon><Upload /></el-icon>
                  <span>{{ comp.label }}</span>
                  <el-tag size="small" type="success" class="custom-tag">自定义</el-tag>
                </div>
              </template>
              <div class="component-group-label">系统组件</div>
              <div
                v-for="comp in filteredDefaultSources"
                :key="'default-' + comp.type"
                class="component-item default"
                draggable="true"
                @dragstart="onDragStart($event, 'source', comp)"
              >
                <el-icon><Upload /></el-icon>
                <span>{{ comp.label }}</span>
              </div>
            </div>
          </el-collapse-item>
          <el-collapse-item title="Transforms (处理)" name="transforms">
            <div class="component-list">
              <div class="component-group-label">拖入画布创建容器</div>
              <div
                class="component-item default processors-drag"
                draggable="true"
                @dragstart="onDragStart($event, 'transform', { type: 'processors', label: 'Processors' })"
              >
                <el-icon><Operation /></el-icon>
                <span>Processors 容器</span>
                <el-tag size="small" type="info">拖入</el-tag>
              </div>
              <div class="component-group-label" style="margin-top: 8px; opacity: 0.6; font-size: 11px;">
                拖入后在右侧面板中添加具体处理步骤
              </div>
            </div>
          </el-collapse-item>
          <el-collapse-item title="Sinks (输出)" name="sinks">
            <div class="component-list">
              <template v-if="customSinks.length > 0">
                <div class="component-group-label">自定义组件</div>
                <div
                  v-for="comp in customSinks"
                  :key="'custom-' + comp.id"
                  class="component-item custom"
                  draggable="true"
                  @dragstart="onDragStart($event, 'sink', comp)"
                >
                  <el-icon><Download /></el-icon>
                  <span>{{ comp.label }}</span>
                  <el-tag size="small" type="success" class="custom-tag">自定义</el-tag>
                </div>
              </template>
              <div class="component-group-label">系统组件</div>
              <div
                v-for="comp in filteredDefaultSinks"
                :key="'default-' + comp.type"
                class="component-item default"
                draggable="true"
                @dragstart="onDragStart($event, 'sink', comp)"
              >
                <el-icon><Download /></el-icon>
                <span>{{ comp.label }}</span>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>

      <!-- 画布区域 -->
      <div class="canvas-area" @drop="onDrop" @dragover.prevent>
        <VueFlow
          ref="vueFlowRef"
          v-model:nodes="nodes"
          v-model:edges="edges"
          :node-types="nodeTypes"
          :default-edge-options="defaultEdgeOptions"
          :connection-line-style="{ stroke: '#94a3b8', strokeWidth: 1.5 }"
          :snap-to-grid="true"
          :snap-grid="[20, 20]"
          :nodes-draggable="true"
          :nodes-connectable="true"
          fit-view-on-init
          @connect="onConnect"
          @node-click="onNodeClick"
          @pane-click="onPaneClick"
          @nodes-change="onNodesChange"
          :is-valid-connection="isValidConnection"
        >
          <Background :gap="20" :size="1" />
          <Controls position="bottom-right" />
          <MiniMap position="bottom-left" />

          <template #node-source="nodeProps">
            <SourceSinkNode :data="nodeProps.data" :selected="nodeProps.selected" />
          </template>
          <template #node-sink="nodeProps">
            <SourceSinkNode :data="nodeProps.data" :selected="nodeProps.selected" />
          </template>
          <template #node-processors="nodeProps">
            <ProcessorsNode :data="nodeProps.data" :selected="nodeProps.selected" />
          </template>
        </VueFlow>

        <div class="canvas-toolbar">
          <div class="toolbar-overview">
            <div class="overview-chip"><span>节点</span><strong>{{ nodes.length }}</strong></div>
            <div class="overview-chip"><span>Source</span><strong>{{ sourceNodeCount }}</strong></div>
            <div class="overview-chip"><span>Transform</span><strong>{{ transformNodeCount }}</strong></div>
            <div class="overview-chip"><span>Sink</span><strong>{{ sinkNodeCount }}</strong></div>
          </div>
          <div class="toolbar-actions">
            <el-button size="small" @click="arrangeGraph">整理布局</el-button>
            <el-button size="small" @click="fitView">适配视图</el-button>
          </div>
        </div>
      </div>

      <!-- 右侧属性面板 -->
      <div class="property-panel" v-if="selectedNode">
        <div class="panel-header">
          属性配置
          <el-button text size="small" @click="selectedNode = null; selectedStepIndex = -1">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>

        <!-- Processors 容器面板 -->
        <template v-if="selectedNode.data?.isProcessorsContainer">
          <div class="processors-panel">
            <div class="processors-title">
              <span>PROCESSORS ({{ selectedNode.data?.steps?.length || 0 }})</span>
              <el-popover
                ref="addProcessorPopoverRef"
                trigger="click"
                :width="280"
                placement="bottom-end"
                popper-class="processor-picker-popover"
                @show="processorSearchKeyword = ''"
              >
                <template #reference>
                  <el-button type="primary" size="small" plain>
                    <el-icon><Plus /></el-icon> Add
                  </el-button>
                </template>
                <div class="processor-picker">
                  <el-input
                    v-model="processorSearchKeyword"
                    placeholder="搜索 Transform 组件..."
                    clearable
                    size="small"
                    class="processor-search"
                  >
                    <template #prefix><el-icon><Search /></el-icon></template>
                  </el-input>
                  <template v-if="!processorSearchKeyword && recentTransforms.length > 0">
                    <div class="picker-group-label">最近使用</div>
                    <div
                      v-for="comp in recentTransforms"
                      :key="'recent-' + comp.type"
                      class="picker-item"
                      @click="selectProcessor(comp)"
                    >
                      <el-icon class="picker-icon recent"><Clock /></el-icon>
                      <span>{{ comp.label }}</span>
                    </div>
                  </template>
                  <template v-if="filteredPickerCustomTransforms.length > 0">
                    <div class="picker-group-label">自定义组件</div>
                    <div
                      v-for="comp in filteredPickerCustomTransforms"
                      :key="'custom-' + comp.type"
                      class="picker-item"
                      @click="selectProcessor(comp)"
                    >
                      <el-icon class="picker-icon custom"><UserFilled /></el-icon>
                      <span>{{ comp.label }}</span>
                      <el-tag size="small" type="success" class="picker-tag">自定义</el-tag>
                    </div>
                  </template>
                  <template v-for="group in filteredPickerSystemGroups" :key="group.name">
                    <div class="picker-group-label">{{ group.name }}</div>
                    <div
                      v-for="comp in group.items"
                      :key="'sys-' + comp.type"
                      class="picker-item"
                      @click="selectProcessor(comp)"
                    >
                      <el-icon class="picker-icon system"><Operation /></el-icon>
                      <span>{{ comp.label }}</span>
                    </div>
                  </template>
                  <div v-if="pickerHasNoResult" class="picker-empty">
                    无匹配的 Transform 组件
                  </div>
                </div>
              </el-popover>
            </div>
            <!-- 子步骤列表 -->
            <div class="step-list" v-if="selectedNode.data?.steps?.length > 0">
              <div
                v-for="(step, idx) in (selectedNode.data?.steps || [])"
                :key="step.id"
                class="step-item"
                :class="{ active: selectedStepIndex === idx }"
                @click="selectedStepIndex = idx"
              >
                <span class="step-index">{{ idx + 1 }}</span>
                <span class="step-label">{{ step.label }}</span>
                <span class="step-type">{{ step.type }}</span>
                <el-button text size="small" class="step-delete" @click.stop="removeProcessorStep(idx)">
                  <el-icon><Close /></el-icon>
                </el-button>
              </div>
            </div>
            <div v-else class="step-list-empty">
              <p>暂无处理步骤</p>
              <p class="hint">点击上方 "+ Add" 按钮添加</p>
            </div>
            <!-- 选中子步骤的配置 -->
            <template v-if="selectedStepIndex >= 0 && selectedNode.data?.steps?.[selectedStepIndex]">
              <el-divider>{{ selectedNode.data.steps[selectedStepIndex].label }} 配置</el-divider>
              <component
                :is="getConfigComponent(selectedNode.data.steps[selectedStepIndex].type + '_transform')"
                v-model="selectedNode.data.steps[selectedStepIndex].config"
                @change="onStepConfigChange"
              />
            </template>
          </div>
        </template>

        <!-- Source/Sink 普通面板 -->
        <template v-else>
          <el-form label-position="top" size="small">
            <el-form-item label="节点名称">
              <el-input v-model="selectedNode.data.name" />
            </el-form-item>
            <el-form-item label="组件类型">
              <el-tag>{{ selectedNode.data.componentType }}</el-tag>
            </el-form-item>
            <el-divider>组件配置</el-divider>
            <component
              :is="getConfigComponent(selectedNode.data.componentType)"
              v-model="selectedNode.data.config"
            />
          </el-form>
        </template>
      </div>
    </div>

    <!-- YAML 预览 -->
    <el-drawer v-model="showYamlPreview" title="YAML 预览" size="50%">
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between; width: 100%;">
          <span>YAML 预览</span>
          <el-button type="primary" size="small" :loading="validating" @click="validateYaml">
            <el-icon><Check /></el-icon>{{ validating ? '校验中...' : '校验配置' }}
          </el-button>
        </div>
      </template>
      <pre class="yaml-preview">{{ generatedYaml }}</pre>
    </el-drawer>

    <!-- 部署对话框 -->
    <el-dialog v-model="showDeployDialog" title="部署配置" width="500px" destroy-on-close>
      <el-alert type="info" :closable="false" style="margin-bottom: 16px;">
        将当前配置部署到选定的机器上运行 Vector
      </el-alert>
      <el-form label-width="80px">
        <el-form-item label="目标机器">
          <el-select v-model="deployTargetHosts" multiple filterable placeholder="选择要部署的机器" style="width: 100%" :loading="loadingHosts">
            <el-option v-for="host in availableHosts" :key="host.id" :label="`${host.name} (${host.ipAddress})`" :value="host.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="部署方式">
          <el-radio-group v-model="deployMode">
            <el-radio value="restart">重启 Vector</el-radio>
            <el-radio value="reload">热重载配置</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDeployDialog = false">取消</el-button>
        <el-button type="primary" :loading="deploying" :disabled="deployTargetHosts.length === 0" @click="deployConfig">
          开始部署
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, markRaw, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft, View, Check, Search, Upload, Operation, Download, Close, Promotion, Plus, Clock, UserFilled
} from '@element-plus/icons-vue'
import { VueFlow, Position } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import type { Connection, Node, Edge, NodeChange } from '@vue-flow/core'
import SourceSinkNode from './components/SourceSinkNode.vue'
import ProcessorsNode from './components/ProcessorsNode.vue'
import yaml from 'js-yaml'
import { visualConfigApi, configComponentApi, vectorMachineApi, vectorDeploymentApi, vectorMetricsApi, type ConfigComponent } from '@/api/vector'

import FileSourceConfig from './components/FileSourceConfig.vue'
import KafkaSourceConfig from './components/KafkaSourceConfig.vue'
import RemapTransformConfig from './components/RemapTransformConfig.vue'
import FilterTransformConfig from './components/FilterTransformConfig.vue'
import ElasticsearchSinkConfig from './components/ElasticsearchSinkConfig.vue'
import ClickhouseSinkConfig from './components/ClickhouseSinkConfig.vue'
import ConsoleSinkConfig from './components/ConsoleSinkConfig.vue'
import DefaultConfig from './components/DefaultConfig.vue'

// ═══════════════════════════════════════════════════════════
// Types
// ═══════════════════════════════════════════════════════════

interface ComponentItem {
  id?: string
  type: string
  label: string
  configYaml?: string
  visualData?: string
  isCustom?: boolean
  isShared?: boolean
  componentKey?: string
}

interface ProcessorStep {
  id: string
  type: string
  label: string
  config: Record<string, any>
  componentId?: string
}

interface TransformGroup {
  name: string
  items: ComponentItem[]
}

// ═══════════════════════════════════════════════════════════
// Vue Flow Setup
// ═══════════════════════════════════════════════════════════

const nodeTypes = {
  source: markRaw(SourceSinkNode),
  sink: markRaw(SourceSinkNode),
  processors: markRaw(ProcessorsNode)
}

const defaultEdgeOptions = {
  type: 'smoothstep',
  animated: false,
  style: { stroke: '#94a3b8', strokeWidth: 1.5 },
  markerEnd: { type: 'arrowclosed', color: '#94a3b8' }
}

// ═══════════════════════════════════════════════════════════
// State
// ═══════════════════════════════════════════════════════════

const route = useRoute()
const router = useRouter()
const configId = route.params.id as string

const configName = ref('加载中...')
const configFormat = ref('')
const saving = ref(false)
const validating = ref(false)
const searchKeyword = ref('')
const activeCollapse = ref(['sources', 'transforms', 'sinks'])
const selectedNode = ref<Node | null>(null)
const selectedStepIndex = ref(-1)
const showYamlPreview = ref(false)
const generatedYaml = ref('')

const nodes = ref<Node[]>([])
const edges = ref<Edge[]>([])

// Deploy
const showDeployDialog = ref(false)
const deploying = ref(false)
const loadingHosts = ref(false)
const deployTargetHosts = ref<string[]>([])
const deployMode = ref('restart')
const availableHosts = ref<any[]>([])

// Component library
const customSources = ref<ComponentItem[]>([])
const customTransforms = ref<ComponentItem[]>([])
const customSinks = ref<ComponentItem[]>([])

const defaultSources: ComponentItem[] = [
  { type: 'file', label: 'File (文件)' },
  { type: 'kafka', label: 'Kafka' },
  { type: 'http_server', label: 'HTTP Server' },
  { type: 'syslog', label: 'Syslog' },
  { type: 'socket', label: 'Socket' },
  { type: 'docker_logs', label: 'Docker Logs' }
]

const defaultTransforms: ComponentItem[] = [
  { type: 'filter', label: 'Filter (过滤)' },
  { type: 'remap', label: 'Remap (VRL)' },
  { type: 'sample', label: 'Sample (采样)' },
  { type: 'dedupe', label: 'Dedupe (去重)' },
  { type: 'throttle', label: 'Quota (限流)' }
]

const defaultSinks: ComponentItem[] = [
  { type: 'elasticsearch', label: 'Elasticsearch' },
  { type: 'clickhouse', label: 'ClickHouse' },
  { type: 'kafka', label: 'Kafka' },
  { type: 'console', label: 'Console' },
  { type: 'file', label: 'File (文件)' },
  { type: 'http', label: 'HTTP' },
  { type: 'loki', label: 'Loki' }
]

// Processor Picker
const addProcessorPopoverRef = ref()
const processorSearchKeyword = ref('')
const RECENT_TRANSFORMS_KEY = 'visual-editor-recent-transforms'
const MAX_RECENT_COUNT = 5
const recentTransforms = ref<ComponentItem[]>(loadRecentTransforms())

// ═══════════════════════════════════════════════════════════
// Computed
// ═══════════════════════════════════════════════════════════

const sourceNodeCount = computed(() => nodes.value.filter(n => n.type === 'source').length)
const transformNodeCount = computed(() => nodes.value.filter(n => n.type === 'processors').length)
const sinkNodeCount = computed(() => nodes.value.filter(n => n.type === 'sink').length)

const filteredDefaultSources = computed(() =>
  defaultSources.filter(c => c.label.toLowerCase().includes(searchKeyword.value.toLowerCase()))
)
const filteredDefaultSinks = computed(() =>
  defaultSinks.filter(c => c.label.toLowerCase().includes(searchKeyword.value.toLowerCase()))
)

const filteredPickerCustomTransforms = computed(() => {
  const keyword = processorSearchKeyword.value.toLowerCase()
  if (!keyword) return customTransforms.value
  return customTransforms.value.filter(c => c.label.toLowerCase().includes(keyword))
})

const systemTransformGroups: TransformGroup[] = [
  { name: '数据转换', items: [{ type: 'remap', label: 'Remap (VRL)' }] },
  { name: '过滤 & 路由', items: [{ type: 'filter', label: 'Filter (过滤)' }, { type: 'sample', label: 'Sample (采样)' }] },
  { name: '去重 & 限流', items: [{ type: 'dedupe', label: 'Dedupe (去重)' }, { type: 'throttle', label: 'Quota (限流)' }] }
]

const filteredPickerSystemGroups = computed(() => {
  const keyword = processorSearchKeyword.value.toLowerCase()
  if (!keyword) return systemTransformGroups
  return systemTransformGroups
    .map(group => ({ name: group.name, items: group.items.filter(c => c.label.toLowerCase().includes(keyword)) }))
    .filter(group => group.items.length > 0)
})

const pickerHasNoResult = computed(() =>
  filteredPickerCustomTransforms.value.length === 0 && filteredPickerSystemGroups.value.length === 0
)

// ═══════════════════════════════════════════════════════════
// Methods
// ═══════════════════════════════════════════════════════════

function loadRecentTransforms(): ComponentItem[] {
  try {
    const raw = localStorage.getItem(RECENT_TRANSFORMS_KEY)
    return raw ? JSON.parse(raw) : []
  } catch { return [] }
}

function saveRecentTransform(comp: ComponentItem) {
  const list = recentTransforms.value.filter(item => item.type !== comp.type)
  list.unshift({ type: comp.type, label: comp.label, id: comp.id })
  recentTransforms.value = list.slice(0, MAX_RECENT_COUNT)
  localStorage.setItem(RECENT_TRANSFORMS_KEY, JSON.stringify(recentTransforms.value))
}

const getConfigComponent = (type: string) => {
  const map: Record<string, any> = {
    'file_source': markRaw(FileSourceConfig),
    'kafka_source': markRaw(KafkaSourceConfig),
    'remap_transform': markRaw(RemapTransformConfig),
    'filter_transform': markRaw(FilterTransformConfig),
    'elasticsearch_sink': markRaw(ElasticsearchSinkConfig),
    'clickhouse_sink': markRaw(ClickhouseSinkConfig),
    'console_sink': markRaw(ConsoleSinkConfig)
  }
  return map[type] || markRaw(DefaultConfig)
}

const parseCompConfig = (comp: any): Record<string, any> => {
  if (!comp.configYaml) return {}
  try {
    const parsed = yaml.load(comp.configYaml) as Record<string, any>
    if (parsed && typeof parsed === 'object') {
      const { type, ...config } = parsed
      return config
    }
  } catch (e) { console.warn('解析 configYaml 失败:', e) }
  return {}
}

const goBack = () => router.back()

// ═══════════════ Drag & Drop ═══════════════

let dragData: { category: string; comp: any } | null = null

const onDragStart = (event: DragEvent, category: string, comp: any) => {
  dragData = { category, comp }
  event.dataTransfer?.setData('application/json', JSON.stringify({ category, comp }))
}

const vueFlowRef = ref<any>(null)

const onDrop = (event: DragEvent) => {
  event.preventDefault()
  if (!dragData) return

  const { category, comp } = dragData
  const bounds = (event.currentTarget as HTMLElement).getBoundingClientRect()
  const rawPos = { x: event.clientX - bounds.left, y: event.clientY - bounds.top }
  // 通过 VueFlow 实例的 project 方法转换坐标（考虑 zoom/pan）
  const position = vueFlowRef.value?.project?.(rawPos) ?? rawPos

  createNode(category, comp, position)
  dragData = null
}

const createNode = (category: string, comp: any, position: { x: number; y: number }) => {
  const nodeId = `${category}_${comp.type}_${Date.now()}`
  const nodeName = comp.id ? comp.label : `${comp.type}_${nodes.value.length + 1}`
  const initialConfig = parseCompConfig(comp)

  if (category === 'transform') {
    const newNode: Node = {
      id: nodeId,
      type: 'processors',
      position,
      data: {
        category: 'transform',
        name: nodeName,
        isProcessorsContainer: true,
        steps: [],
        componentType: 'processors_transform'
      }
    }
    nodes.value = [...nodes.value, newNode]
  } else {
    const newNode: Node = {
      id: nodeId,
      type: category as 'source' | 'sink',
      position,
      data: {
        category,
        componentType: `${comp.type}_${category}`,
        name: nodeName,
        config: initialConfig,
        isShared: false,
        referenceCount: 0
      }
    }
    nodes.value = [...nodes.value, newNode]
  }
}

// ═══════════════ Connection ═══════════════

const isValidConnection = (connection: Connection): boolean => {
  const sourceNode = nodes.value.find(n => n.id === connection.source)
  const targetNode = nodes.value.find(n => n.id === connection.target)
  if (!sourceNode || !targetNode) return false

  const sourceCategory = sourceNode.data?.category
  const targetCategory = targetNode.data?.category

  if (sourceCategory === 'sink') return false
  if (targetCategory === 'source') return false
  return true
}

const onConnect = (connection: Connection) => {
  const newEdge: Edge = {
    id: `e-${connection.source}-${connection.target}-${Date.now()}`,
    source: connection.source!,
    target: connection.target!,
    type: 'smoothstep',
    style: { stroke: '#94a3b8', strokeWidth: 1.5 },
    markerEnd: { type: 'arrowclosed', color: '#94a3b8' }
  }
  edges.value = [...edges.value, newEdge]
}

// ═══════════════ Node Selection ═══════════════

const onNodeClick = (event: any) => {
  const node = event.node || event
  selectedNode.value = node
  if (node.data?.isProcessorsContainer && node.data?.steps?.length > 0) {
    selectedStepIndex.value = 0
  } else {
    selectedStepIndex.value = -1
  }
}

const onPaneClick = (event: any) => {
  // 如果点击来自 Popover 或属性面板，不要清空选中状态
  const target = event?.event?.target as HTMLElement | undefined
  if (target?.closest('.el-popover, .el-popper, .property-panel, .processor-picker')) return
  selectedNode.value = null
  selectedStepIndex.value = -1
}

const onNodesChange = (changes: NodeChange[]) => {
  const removedIds = changes.filter((c: any) => c.type === 'remove').map((c: any) => c.id)
  if (removedIds.length > 0 && selectedNode.value && removedIds.includes(selectedNode.value.id)) {
    selectedNode.value = null
    selectedStepIndex.value = -1
  }
}

// ═══════════════ Processor Steps ═══════════════

const selectProcessor = async (comp: ComponentItem) => {
  if (!selectedNode.value) return
  const initialConfig = parseCompConfig(comp)
  const newStep: ProcessorStep = {
    id: `step_${Date.now()}`,
    type: comp.type,
    label: comp.label,
    config: initialConfig,
    componentId: comp.id || undefined
  }

  const nodeId = selectedNode.value.id
  const nodeIndex = nodes.value.findIndex(n => n.id === nodeId)
  if (nodeIndex === -1) return

  const node = nodes.value[nodeIndex]
  const updatedSteps = [...(node.data.steps || []), newStep]
  // 直接替换整个节点来触发响应式更新
  nodes.value[nodeIndex] = { ...node, data: { ...node.data, steps: updatedSteps } }
  nodes.value = [...nodes.value]

  await nextTick()
  selectedNode.value = nodes.value[nodeIndex]
  selectedStepIndex.value = updatedSteps.length - 1
  saveRecentTransform(comp)
  addProcessorPopoverRef.value?.hide?.()
}

const removeProcessorStep = async (idx: number) => {
  if (!selectedNode.value) return
  const nodeId = selectedNode.value.id
  const nodeIndex = nodes.value.findIndex(n => n.id === nodeId)
  if (nodeIndex === -1) return

  const node = nodes.value[nodeIndex]
  const currentSteps = [...(node.data.steps || [])]
  if (currentSteps.length <= 1) {
    nodes.value = nodes.value.filter(n => n.id !== nodeId)
    edges.value = edges.value.filter(e => e.source !== nodeId && e.target !== nodeId)
    selectedNode.value = null
    selectedStepIndex.value = -1
    return
  }

  currentSteps.splice(idx, 1)
  nodes.value[nodeIndex] = { ...node, data: { ...node.data, steps: currentSteps } }
  nodes.value = [...nodes.value]

  await nextTick()
  selectedNode.value = nodes.value[nodeIndex]
  selectedStepIndex.value = Math.min(selectedStepIndex.value, currentSteps.length - 1)
}

const onStepConfigChange = () => {
  // Vue Flow 自动响应式，无需额外操作
}

// ═══════════════ Layout ═══════════════

const fitView = () => {
  vueFlowRef.value?.fitView?.({ padding: 0.2 })
}

const arrangeGraph = () => {
  const sourceNodes = nodes.value.filter(n => n.type === 'source')
  const transformNodes = nodes.value.filter(n => n.type === 'processors')
  const sinkNodes = nodes.value.filter(n => n.type === 'sink')

  const startX = 80
  const startY = 100
  const columnGap = 300
  const rowGap = 120

  sourceNodes.forEach((n, i) => { n.position = { x: startX, y: startY + i * rowGap } })
  transformNodes.forEach((n, i) => { n.position = { x: startX + columnGap, y: startY + i * rowGap } })
  sinkNodes.forEach((n, i) => { n.position = { x: startX + columnGap * 2, y: startY + i * rowGap } })

  nodes.value = [...nodes.value]
  setTimeout(() => fitView(), 100)
  ElMessage.success('已按数据流方向整理布局')
}

// ═══════════════ YAML Generation ═══════════════

const generateYamlFromGraph = (): string => {
  const config: any = { sources: {}, transforms: {}, sinks: {} }

  nodes.value.forEach(node => {
    const data = node.data
    if (node.type === 'source') {
      const compType = (data.componentType || '').replace('_source', '')
      config.sources[data.name] = { type: compType, ...(data.config || {}) }
    } else if (node.type === 'processors') {
      const steps = data.steps || []
      steps.forEach((step: ProcessorStep, idx: number) => {
        const transformName = `${data.name}_${step.type}_${idx}`
        const inputs = idx === 0
          ? edges.value.filter(e => e.target === node.id).map(e => {
              const src = nodes.value.find(n => n.id === e.source)
              return src?.data?.name || e.source
            })
          : [`${data.name}_${steps[idx - 1].type}_${idx - 1}`]
        config.transforms[transformName] = { type: step.type, inputs, ...(step.config || {}) }
      })
    } else if (node.type === 'sink') {
      const compType = (data.componentType || '').replace('_sink', '')
      const inputs = edges.value.filter(e => e.target === node.id).map(e => {
        const src = nodes.value.find(n => n.id === e.source)
        if (src?.type === 'processors') {
          const steps = src.data.steps || []
          return steps.length > 0 ? `${src.data.name}_${steps[steps.length - 1].type}_${steps.length - 1}` : src.data.name
        }
        return src?.data?.name || e.source
      })
      config.sinks[data.name] = { type: compType, inputs, ...(data.config || {}) }
    }
  })

  return yaml.dump(config, { lineWidth: -1 })
}

const previewYaml = () => {
  generatedYaml.value = generateYamlFromGraph()
  showYamlPreview.value = true
}

const validateYaml = async () => {
  validating.value = true
  try {
    const yamlContent = generateYamlFromGraph()
    const result = await visualConfigApi.validate(yamlContent)
    if (result.valid) {
      ElMessage.success('配置校验通过')
    } else {
      ElMessage.error('校验失败: ' + (result.error || ''))
    }
  } catch (e: any) {
    ElMessage.error(e.message || '配置校验失败')
  } finally {
    validating.value = false
  }
}

// ═══════════════ Save & Deploy ═══════════════

const saveConfig = async () => {
  saving.value = true
  try {
    const content = generateYamlFromGraph()
    const graphData = JSON.stringify({ nodes: nodes.value, edges: edges.value })
    await visualConfigApi.update(configId, { content, graphData, nodeCount: nodes.value.length })
    ElMessage.success('保存成功')
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const deployConfig = async () => {
  deploying.value = true
  try {
    const yamlContent = generateYamlFromGraph()
    await vectorDeploymentApi.createDeployment({
      hostIds: deployTargetHosts.value,
      configId,
      configContent: yamlContent,
      deployMode: deployMode.value
    })
    ElMessage.success('部署任务已创建')
    showDeployDialog.value = false
  } catch (e: any) {
    ElMessage.error(e.message || '部署失败')
  } finally {
    deploying.value = false
  }
}

// ═══════════════ Load Data ═══════════════

const loadComponents = async () => {
  try {
    const res = await configComponentApi.getList() as any
    const list = Array.isArray(res) ? res : (res.data || [])

    customSources.value = list.filter((c: ConfigComponent) => c.componentType === 'source')
      .map((c: ConfigComponent) => ({ id: c.id, type: c.vectorType, label: c.name, configYaml: c.configYaml, isCustom: true }))
    customTransforms.value = list.filter((c: ConfigComponent) => c.componentType === 'transform')
      .map((c: ConfigComponent) => ({ id: c.id, type: c.vectorType, label: c.name, configYaml: c.configYaml, isCustom: true }))
    customSinks.value = list.filter((c: ConfigComponent) => c.componentType === 'sink')
      .map((c: ConfigComponent) => ({ id: c.id, type: c.vectorType, label: c.name, configYaml: c.configYaml, isCustom: true }))
  } catch (e) {
    console.error('加载组件库失败:', e)
  }
}

const loadConfig = async () => {
  try {
    const res = await visualConfigApi.getById(configId) as any
    const data = res.data || res
    configName.value = data.name || '未命名配置'
    configFormat.value = data.format || 'yaml'

    if (data.graphData) {
      try {
        const visual = JSON.parse(data.graphData)
        if (visual.nodes) nodes.value = visual.nodes
        if (visual.edges) edges.value = visual.edges
      } catch (e) {
        console.warn('解析 graphData 失败:', e)
      }
    }
  } catch (e: any) {
    ElMessage.error('加载配置失败: ' + (e.message || ''))
  }
}

const loadHosts = async () => {
  loadingHosts.value = true
  try {
    const res = await vectorMachineApi.getList() as any
    availableHosts.value = Array.isArray(res) ? res : (res.data || [])
  } catch (e) {
    console.error('加载机器列表失败:', e)
  } finally {
    loadingHosts.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadComponents(), loadConfig(), loadHosts()])
})
</script>

<style>
/* Vue Flow 必须用全局样式，scoped 会破坏拖拽/连线/Handle 功能 */
@import '@vue-flow/core/dist/style.css';
@import '@vue-flow/core/dist/theme-default.css';
@import '@vue-flow/controls/dist/style.css';
@import '@vue-flow/minimap/dist/style.css';
</style>

<style scoped>

.visual-editor {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--macos-bg-secondary, #f5f5f7);
}
.editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: var(--macos-bg-primary, #fff);
  border-bottom: 1px solid var(--macos-border, #e5e7eb);
}
.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.config-name {
  font-size: 16px;
  font-weight: 600;
}
.header-right {
  display: flex;
  gap: 8px;
}
.editor-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}
.component-panel {
  width: 240px;
  background: var(--macos-bg-primary, #fff);
  border-right: 1px solid var(--macos-border, #e5e7eb);
  overflow-y: auto;
  flex-shrink: 0;
}
.component-panel .panel-header {
  padding: 12px 16px;
  font-size: 14px;
  font-weight: 600;
  border-bottom: 1px solid var(--macos-border-light, #f0f0f0);
}
.search-input {
  margin: 8px 12px;
  width: calc(100% - 24px);
}
.component-group-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--macos-text-tertiary, #999);
  padding: 8px 12px 4px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.component-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: grab;
  border-radius: 6px;
  margin: 2px 8px;
  font-size: 13px;
  transition: background 0.15s;
}
.component-item:hover {
  background: var(--macos-hover, #f5f5f7);
}
.component-item:active {
  cursor: grabbing;
}
.custom-tag {
  margin-left: auto;
  font-size: 10px;
}
.canvas-area {
  flex: 1;
  position: relative;
  overflow: hidden;
}
.canvas-toolbar {
  position: absolute;
  top: 12px;
  left: 12px;
  right: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  pointer-events: none;
  z-index: 10;
}
.canvas-toolbar > * {
  pointer-events: auto;
}
.toolbar-overview {
  display: flex;
  gap: 8px;
  background: var(--macos-bg-primary, #fff);
  padding: 6px 12px;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
}
.overview-chip {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--macos-text-secondary, #666);
}
.overview-chip strong {
  color: var(--macos-text-primary, #333);
}
.toolbar-actions {
  display: flex;
  gap: 4px;
  background: var(--macos-bg-primary, #fff);
  padding: 4px 8px;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
}
.property-panel {
  width: 320px;
  background: var(--macos-bg-primary, #fff);
  border-left: 1px solid var(--macos-border, #e5e7eb);
  overflow-y: auto;
  flex-shrink: 0;
  padding: 0 16px 16px;
}
.property-panel .panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  font-size: 14px;
  font-weight: 600;
  border-bottom: 1px solid var(--macos-border-light, #f0f0f0);
  margin-bottom: 12px;
}
.processors-panel .processors-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 12px;
}
.step-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.step-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid var(--macos-border, #e5e7eb);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
}
.step-item:hover {
  background: var(--macos-hover, #f5f5f7);
}
.step-item.active {
  border-color: #3b82f6;
  background: rgba(59, 130, 246, 0.04);
}
.step-index {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #3b82f6;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.step-label {
  flex: 1;
  font-size: 13px;
  font-weight: 500;
}
.step-type {
  font-size: 11px;
  color: var(--macos-text-tertiary, #999);
  background: var(--macos-bg-secondary, #f5f5f7);
  padding: 2px 6px;
  border-radius: 4px;
}
.step-delete {
  opacity: 0;
  transition: opacity 0.15s;
}
.step-item:hover .step-delete {
  opacity: 1;
}
.step-list-empty {
  text-align: center;
  padding: 24px 0;
  color: var(--macos-text-tertiary, #999);
  font-size: 13px;
}
.step-list-empty .hint {
  font-size: 12px;
  margin-top: 4px;
}
.yaml-preview {
  background: var(--macos-bg-secondary, #f5f5f7);
  padding: 16px;
  border-radius: 8px;
  font-family: 'SF Mono', Menlo, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-x: auto;
}

/* Processor Picker */
.processor-picker {
  max-height: 360px;
  overflow-y: auto;
}
.processor-picker .processor-search {
  margin-bottom: 8px;
}
.processor-picker .picker-group-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--macos-text-tertiary, #999);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 8px 4px 4px;
  border-bottom: 1px solid var(--macos-border-light, #f0f0f0);
  margin-bottom: 2px;
}
.processor-picker .picker-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 8px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  transition: background 0.15s;
}
.processor-picker .picker-item:hover {
  background: var(--macos-hover, #f5f5f7);
}
.processor-picker .picker-icon {
  font-size: 14px;
  flex-shrink: 0;
}
.processor-picker .picker-icon.recent { color: #f59e0b; }
.processor-picker .picker-icon.custom { color: #22c55e; }
.processor-picker .picker-icon.system { color: #3b82f6; }
.processor-picker .picker-tag { margin-left: auto; }
.processor-picker .picker-empty {
  padding: 16px 0;
  text-align: center;
  color: var(--macos-text-tertiary, #999);
  font-size: 12px;
}
</style>
