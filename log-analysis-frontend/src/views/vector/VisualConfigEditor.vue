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
              <!-- 用户自定义组件 -->
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
                  <el-tooltip 
                    v-if="isComponentShared(comp.id)" 
                    :content="`被其他 ${getComponentReferenceCount(comp.id)} 个配置使用`"
                    placement="top"
                  >
                    <el-tag size="small" type="warning" class="shared-tag">{{ getComponentReferenceCount(comp.id) }}</el-tag>
                  </el-tooltip>
                  <el-tag size="small" type="success" class="custom-tag">自定义</el-tag>
                </div>
              </template>
              <!-- 系统默认组件 -->
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
              <!-- 用户自定义组件 -->
              <template v-if="customTransforms.length > 0">
                <div class="component-group-label">自定义组件</div>
                <div
                  v-for="comp in customTransforms"
                  :key="'custom-' + comp.id"
                  class="component-item custom"
                  draggable="true"
                  @dragstart="onDragStart($event, 'transform', comp)"
                >
                  <el-icon><Operation /></el-icon>
                  <span>{{ comp.label }}</span>
                  <el-tooltip 
                    v-if="isComponentShared(comp.id)" 
                    :content="`被其他 ${getComponentReferenceCount(comp.id)} 个配置使用`"
                    placement="top"
                  >
                    <el-tag size="small" type="warning" class="shared-tag">{{ getComponentReferenceCount(comp.id) }}</el-tag>
                  </el-tooltip>
                  <el-tag size="small" type="success" class="custom-tag">自定义</el-tag>
                </div>
              </template>
              <!-- 系统默认组件 -->
              <div class="component-group-label">系统组件</div>
              <div
                v-for="comp in filteredDefaultTransforms"
                :key="'default-' + comp.type"
                class="component-item default"
                draggable="true"
                @dragstart="onDragStart($event, 'transform', comp)"
              >
                <el-icon><Operation /></el-icon>
                <span>{{ comp.label }}</span>
              </div>
            </div>
          </el-collapse-item>
          <el-collapse-item title="Sinks (输出)" name="sinks">
            <div class="component-list">
              <!-- 用户自定义组件 -->
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
                  <el-tooltip 
                    v-if="isComponentShared(comp.id)" 
                    :content="`被其他 ${getComponentReferenceCount(comp.id)} 个配置使用`"
                    placement="top"
                  >
                    <el-tag size="small" type="warning" class="shared-tag">{{ getComponentReferenceCount(comp.id) }}</el-tag>
                  </el-tooltip>
                  <el-tag size="small" type="success" class="custom-tag">自定义</el-tag>
                </div>
              </template>
              <!-- 系统默认组件 -->
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
      <div class="canvas-area" ref="canvasRef" @drop="onDrop" @dragover.prevent @dblclick="onCanvasDblClick">
        <div class="canvas-toolbar">
          <div class="toolbar-overview">
            <div class="overview-chip">
              <span>节点</span>
              <strong>{{ nodes.length }}</strong>
            </div>
            <div class="overview-chip">
              <span>Source</span>
              <strong>{{ sourceNodeCount }}</strong>
            </div>
            <div class="overview-chip">
              <span>Transform</span>
              <strong>{{ transformNodeCount }}</strong>
            </div>
            <div class="overview-chip">
              <span>Sink</span>
              <strong>{{ sinkNodeCount }}</strong>
            </div>
          </div>
          <div class="toolbar-actions">
            <el-button size="small" @click="arrangeGraph">整理布局</el-button>
            <el-button size="small" @click="fitCanvas">适配视图</el-button>
            <el-button size="small" @click="centerCanvas">居中</el-button>
            <el-button size="small" @click="zoomCanvas(-0.1)">-</el-button>
            <span class="zoom-indicator">{{ zoomPercent }}%</span>
            <el-button size="small" @click="zoomCanvas(0.1)">+</el-button>
          </div>
        </div>

        <div v-if="nodes.length === 0" class="canvas-placeholder">
          <el-icon :size="48"><Share /></el-icon>
          <p>从左侧拖拽组件到画布开始构建</p>
          <p class="hint">或双击画布快速添加组件</p>
        </div>

        <div v-else class="status-legend">
          <span><i class="legend-dot normal"></i>运行</span>
          <span><i class="legend-dot warning"></i>告警</span>
          <span><i class="legend-dot error"></i>异常</span>
          <span><i class="legend-dot stopped"></i>停止</span>
        </div>
      </div>

      <!-- 右侧属性面板 -->
      <div class="property-panel" v-if="selectedNode">
        <div class="panel-header">
          属性配置
          <el-button text size="small" @click="selectedNode = null">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
        <el-form label-position="top" size="small">
          <el-form-item label="节点名称">
            <el-input v-model="selectedNode.data.name" @change="updateNodeData" />
          </el-form-item>
          <el-form-item label="组件类型">
            <el-tag>{{ selectedNode.data.componentType }}</el-tag>
          </el-form-item>
          <el-divider>组件配置</el-divider>
          <component
            :is="getConfigComponent(selectedNode.data.componentType)"
            v-model="selectedNode.data.config"
            @change="updateNodeData"
            @routes-changed="onRoutesChanged"
          />
        </el-form>
      </div>
    </div>

    <!-- 快速添加弹窗 -->
    <el-dialog v-model="showQuickAdd" title="快速添加组件" width="400px" destroy-on-close>
      <el-input
        v-model="quickSearchKeyword"
        placeholder="搜索组件..."
        autofocus
        @keyup.enter="addQuickComponent"
      />
      <div class="quick-add-list">
        <div
          v-for="comp in quickFilteredComponents"
          :key="`${comp.category}-${comp.type}`"
          class="quick-add-item"
          @click="addComponentAtPosition(comp.category, comp)"
        >
          <el-icon>
            <Upload v-if="comp.category === 'source'" />
            <Operation v-if="comp.category === 'transform'" />
            <Download v-if="comp.category === 'sink'" />
          </el-icon>
          <span class="comp-name">{{ comp.label }}</span>
          <el-tag size="small">{{ comp.category }}</el-tag>
        </div>
      </div>
    </el-dialog>

    <!-- YAML 预览 -->
    <el-drawer v-model="showYamlPreview" title="YAML 预览" size="50%">
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between; width: 100%;">
          <span>YAML 预览</span>
          <el-button 
            type="primary" 
            size="small" 
            :loading="validating"
            @click="validateYaml"
          >
            <el-icon><Check /></el-icon>{{ validating ? '校验中...' : '校验配置' }}
          </el-button>
        </div>
      </template>
      <pre class="yaml-preview">{{ generatedYaml }}</pre>
    </el-drawer>

    <!-- 部署对话框 -->
    <el-dialog v-model="showDeployDialog" title="部署配置" width="500px" destroy-on-close>
      <el-alert 
        type="info" 
        :closable="false" 
        style="margin-bottom: 16px;"
      >
        将当前配置部署到选定的机器上运行 Vector
      </el-alert>
      <el-form label-width="80px">
        <el-form-item label="目标机器">
          <el-select 
            v-model="deployTargetHosts" 
            multiple 
            filterable 
            placeholder="选择要部署的机器"
            style="width: 100%"
            :loading="loadingHosts"
          >
            <el-option 
              v-for="host in availableHosts" 
              :key="host.id" 
              :label="`${host.name} (${host.ipAddress})`" 
              :value="host.id"
            >
              <div style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
                <span>{{ host.name }}</span>
                <span>
                  <el-tag 
                    :type="host.status === 'online' ? 'success' : 'info'" 
                    size="small"
                    style="margin-right: 8px;"
                  >
                    {{ host.status || 'offline' }}
                  </el-tag>
                  <span style="color: var(--macos-text-tertiary); font-size: 12px;">{{ host.ipAddress }}</span>
                </span>
              </div>
            </el-option>
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
        <el-button 
          type="primary" 
          :loading="deploying" 
          :disabled="deployTargetHosts.length === 0"
          @click="deployConfig"
        >
          开始部署
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, markRaw, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft, View, Check, Search, Upload, Operation, Download, Share, Close, Promotion
} from '@element-plus/icons-vue'
import { Graph } from '@antv/x6'
import yaml from 'js-yaml'
import { visualConfigApi, configComponentApi, vectorMachineApi, vectorDeploymentApi, vectorMetricsApi, type ConfigComponent } from '@/api/vector'

// 组件配置面板（简化版，可扩展）
import FileSourceConfig from './components/FileSourceConfig.vue'
import KafkaSourceConfig from './components/KafkaSourceConfig.vue'
import RemapTransformConfig from './components/RemapTransformConfig.vue'
import FilterTransformConfig from './components/FilterTransformConfig.vue'
import RouteTransformConfig from './components/RouteTransformConfig.vue'
import ElasticsearchSinkConfig from './components/ElasticsearchSinkConfig.vue'
import ClickhouseSinkConfig from './components/ClickhouseSinkConfig.vue'
import ConsoleSinkConfig from './components/ConsoleSinkConfig.vue'
import DefaultConfig from './components/DefaultConfig.vue'

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

const route = useRoute()
const router = useRouter()
const configId = route.params.id as string

const configName = ref('加载中...')
const configFormat = ref('')
const saving = ref(false)
const validating = ref(false)
const canvasRef = ref<HTMLElement>()
const searchKeyword = ref('')
const activeCollapse = ref(['sources', 'transforms', 'sinks'])
const selectedNode = ref<any>(null)
const showQuickAdd = ref(false)
const quickSearchKeyword = ref('')
const quickAddPosition = ref({ x: 0, y: 0 })
const showYamlPreview = ref(false)
const generatedYaml = ref('')
const nodes = ref<any[]>([])
const zoomPercent = ref(100)

// 部署相关
const showDeployDialog = ref(false)
const deploying = ref(false)
const loadingHosts = ref(false)
const deployTargetHosts = ref<string[]>([])
const deployMode = ref('restart')
const availableHosts = ref<any[]>([])

let graph: Graph | null = null
let statusPollingTimer: ReturnType<typeof setInterval> | null = null

// 组件状态类型
type ComponentStatus = 'normal' | 'warning' | 'error' | 'stopped'

// 组件状态映射
const componentStatusMap = ref<Map<string, ComponentStatus>>(new Map())

// 组件库数据（从后端加载 + 内置默认）
const sources = ref<ComponentItem[]>([])
const transforms = ref<ComponentItem[]>([])
const sinks = ref<ComponentItem[]>([])

// 自定义组件（用户创建的）
const customSources = ref<ComponentItem[]>([])
const customTransforms = ref<ComponentItem[]>([])
const customSinks = ref<ComponentItem[]>([])

// 组件引用数量（用于显示共享标记）
const componentReferenceCounts = ref<Record<string, number>>({})
const sourceNodeCount = computed(() => nodes.value.filter(node => node.getData?.()?.category === 'source').length)
const transformNodeCount = computed(() => nodes.value.filter(node => node.getData?.()?.category === 'transform').length)
const sinkNodeCount = computed(() => nodes.value.filter(node => node.getData?.()?.category === 'sink').length)

// 内置默认组件
const defaultSources: ComponentItem[] = [
  { type: 'file', label: 'File (文件)' },
  { type: 'kafka', label: 'Kafka' },
  { type: 'http_server', label: 'HTTP Server' },
  { type: 'syslog', label: 'Syslog' },
  { type: 'socket', label: 'Socket' },
  { type: 'docker_logs', label: 'Docker Logs' }
]

const defaultTransforms: ComponentItem[] = [
  { type: 'remap', label: 'Remap (VRL)' },
  { type: 'filter', label: 'Filter (过滤)' },
  { type: 'route', label: 'Route (路由)' },
  { type: 'sample', label: 'Sample (采样)' },
  { type: 'dedupe', label: 'Dedupe (去重)' }
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

// 加载组件库
const loadComponents = async () => {
  try {
    const res = await configComponentApi.getList() as any
    // API 返回格式: { code: 200, data: [...], message: '' }
    const list = Array.isArray(res) ? res : (res.data || [])
    
    console.log('API 返回的组件列表:', list)
    
    // 分离自定义组件
    customSources.value = list.filter((c: ConfigComponent) => c.componentType === 'source')
      .map((c: ConfigComponent) => {
        console.log('Source 组件:', c.name, 'configYaml:', c.configYaml)
        return { 
          id: c.id, 
          type: c.vectorType, 
          label: c.name, 
          configYaml: c.configYaml,
          visualData: c.visualData,
          isCustom: true
        }
      })
    customTransforms.value = list.filter((c: ConfigComponent) => c.componentType === 'transform')
      .map((c: ConfigComponent) => ({ 
        id: c.id, 
        type: c.vectorType, 
        label: c.name, 
        configYaml: c.configYaml,
        visualData: c.visualData,
        isCustom: true
      }))
    customSinks.value = list.filter((c: ConfigComponent) => c.componentType === 'sink')
      .map((c: ConfigComponent) => ({ 
        id: c.id, 
        type: c.vectorType, 
        label: c.name, 
        configYaml: c.configYaml,
        visualData: c.visualData,
        isCustom: true
      }))

    // 合并到 sources/transforms/sinks 用于搜索
    sources.value = [...customSources.value, ...defaultSources]
    transforms.value = [...customTransforms.value, ...defaultTransforms]
    sinks.value = [...customSinks.value, ...defaultSinks]
    
    // 加载组件引用数量
    await loadComponentReferences()
  } catch (e) {
    console.error('加载组件库失败:', e)
    // 加载失败使用默认组件
    sources.value = defaultSources
    transforms.value = defaultTransforms
    sinks.value = defaultSinks
  }
}

// 加载组件引用数量
const loadComponentReferences = async () => {
  try {
    const res = await configComponentApi.getReferenceCounts(configId) as any
    componentReferenceCounts.value = res.data || res || {}
  } catch (e) {
    console.warn('加载组件引用数量失败:', e)
    componentReferenceCounts.value = {}
  }
}

// 检查组件是否被其他配置引用
const isComponentShared = (componentId?: string): boolean => {
  if (!componentId) return false
  return (componentReferenceCounts.value[componentId] || 0) > 0
}

// 获取组件被引用的次数
const getComponentReferenceCount = (componentId?: string): number => {
  if (!componentId) return 0
  return componentReferenceCounts.value[componentId] || 0
}

const filteredSources = computed(() =>
  sources.value.filter(c => c.label.toLowerCase().includes(searchKeyword.value.toLowerCase()))
)
const filteredTransforms = computed(() =>
  transforms.value.filter(c => c.label.toLowerCase().includes(searchKeyword.value.toLowerCase()))
)
const filteredSinks = computed(() =>
  sinks.value.filter(c => c.label.toLowerCase().includes(searchKeyword.value.toLowerCase()))
)

// 过滤后的默认组件
const filteredDefaultSources = computed(() =>
  defaultSources.filter(c => c.label.toLowerCase().includes(searchKeyword.value.toLowerCase()))
)
const filteredDefaultTransforms = computed(() =>
  defaultTransforms.filter(c => c.label.toLowerCase().includes(searchKeyword.value.toLowerCase()))
)
const filteredDefaultSinks = computed(() =>
  defaultSinks.filter(c => c.label.toLowerCase().includes(searchKeyword.value.toLowerCase()))
)

const quickFilteredComponents = computed(() => {
  const kw = quickSearchKeyword.value.toLowerCase()
  const all = [
    ...sources.value.map(c => ({ ...c, category: 'source' })),
    ...transforms.value.map(c => ({ ...c, category: 'transform' })),
    ...sinks.value.map(c => ({ ...c, category: 'sink' }))
  ]
  return kw ? all.filter(c => c.label.toLowerCase().includes(kw)) : all
})

const getConfigComponent = (type: string) => {
  const map: Record<string, any> = {
    'file_source': markRaw(FileSourceConfig),
    'kafka_source': markRaw(KafkaSourceConfig),
    'remap_transform': markRaw(RemapTransformConfig),
    'filter_transform': markRaw(FilterTransformConfig),
    'route_transform': markRaw(RouteTransformConfig),
    'elasticsearch_sink': markRaw(ElasticsearchSinkConfig),
    'clickhouse_sink': markRaw(ClickhouseSinkConfig),
    'console_sink': markRaw(ConsoleSinkConfig)
  }
  return map[type] || markRaw(DefaultConfig)
}

// 检测是否为暗色模式
const isDarkMode = () => document.documentElement.classList.contains('dark')

// 获取主题相关颜色
const getThemeColors = () => {
  const dark = isDarkMode()
  return {
    canvasBg: dark ? '#1e1e1e' : '#f8f9fa',
    gridColor: dark ? '#3a3a3a' : '#ddd',
    nodeFill: dark ? '#2c2c2e' : '#fff',
    nodeLabel: dark ? '#f5f5f7' : '#333',
    portFill: dark ? '#2c2c2e' : '#fff',
    edgeStroke: dark ? '#5a5a5a' : '#c0c4cc'
  }
}

const syncViewportState = () => {
  if (!graph) return
  zoomPercent.value = Math.round(graph.zoom() * 100)
}

const initGraph = () => {
  if (!canvasRef.value) return

  const themeColors = getThemeColors()

  graph = new Graph({
    container: canvasRef.value,
    autoResize: true,
    background: { color: themeColors.canvasBg },
    grid: { visible: true, type: 'dot', size: 20, args: { color: themeColors.gridColor } },
    mousewheel: {
      enabled: true,
      modifiers: ['ctrl', 'meta'],
      minScale: 0.4,
      maxScale: 1.8
    },
    selecting: {
      enabled: true,
      multiple: true,
      rubberband: true,
      movable: true,
      showNodeSelectionBox: true
    },
    connecting: {
      router: {
        name: 'manhattan',
        args: {
          padding: 10,
          startDirections: ['right'],
          endDirections: ['left']
        }
      },
      connector: { name: 'rounded', args: { radius: 8 } },
      anchor: 'center',
      connectionPoint: 'anchor',
      allowBlank: false,
      allowMulti: 'withPort', // 允许同一端口多条连线
      allowLoop: false, // 不允许自连接
      allowNode: false, // 不允许连接到节点本身
      allowEdge: false, // 不允许连接到边
      allowPort: true, // 只允许连接到端口
      snap: { radius: 30 }, // 增大吸附半径，更容易连接
      createEdge() {
        const themeColors = getThemeColors()
        return graph!.createEdge({
          attrs: {
            line: {
              stroke: themeColors.edgeStroke,  // 默认灰色（停止状态）
              strokeWidth: 2,
              targetMarker: { name: 'block', width: 12, height: 8 }
            }
          },
          router: {
            name: 'manhattan',
            args: {
              padding: 10,
              startDirections: ['right'],
              endDirections: ['left']
            }
          },
          connector: { name: 'rounded', args: { radius: 8 } },
          zIndex: 0
        })
      },
      validateConnection({ sourceView, targetView, sourceMagnet, targetMagnet }) {
        // 不允许连接到自己
        if (sourceView === targetView) return false
        
        // 必须有连接点
        if (!sourceMagnet || !targetMagnet) return false
        
        // 不允许输入端口连接到输入端口，输出端口连接到输出端口
        const sourcePort = sourceMagnet.getAttribute('port')
        const targetPort = targetMagnet.getAttribute('port')
        if (sourcePort === targetPort) return false
        
        // Source 只能连接到 Transform 或 Sink
        // Transform 可以连接到 Transform 或 Sink
        // Sink 不能作为源
        const sourceNode = sourceView.cell
        const targetNode = targetView.cell
        const sourceCategory = sourceNode.getData()?.category
        const targetCategory = targetNode.getData()?.category
        
        // Sink 不能作为输出源
        if (sourceCategory === 'sink') return false
        
        // Source 不能作为输入目标
        if (targetCategory === 'source') return false
        
        return true
      }
    },
    highlighting: {
      magnetAdsorbed: {
        name: 'stroke',
        args: { attrs: { fill: '#5F95FF', stroke: '#5F95FF' } }
      },
      magnetAvailable: {
        name: 'stroke',
        args: {
          padding: 4,
          attrs: {
            strokeWidth: 4,
            stroke: '#52c41a'
          }
        }
      }
    }
  })

  syncViewportState()

  // 节点点击事件
  graph.on('node:click', ({ node }) => {
    selectedNode.value = node
  })

  // 画布点击取消选中
  graph.on('blank:click', () => {
    selectedNode.value = null
  })

  // 为边设置默认停止状态样式
  const setEdgeStoppedStyle = (edge: any) => {
    const themeColors = getThemeColors()
    edge.attr('line/stroke', themeColors.edgeStroke)
    edge.attr('line/strokeWidth', 2)
    edge.attr('line/strokeDasharray', '')  // 实线
  }

  // 连线完成事件
  graph.on('edge:connected', ({ edge }) => {
    // 设置默认停止状态，实际状态会在下次轮询时更新
    setEdgeStoppedStyle(edge)
    
    const targetNode = edge.getTargetNode()
    if (targetNode) {
      const targetData = targetNode.getData()
      const incomingEdges = graph!.getIncomingEdges(targetNode)
      
      // 如果有多个输入，显示提示
      if (incomingEdges && incomingEdges.length > 1) {
        ElMessage.success(`${targetData.name} 现在有 ${incomingEdges.length} 个输入源`)
      }
    }
  })
  
  // 边添加事件 - 为所有新边设置默认停止状态
  graph.on('edge:added', ({ edge }) => {
    setEdgeStoppedStyle(edge)
  })

  // 删除连线事件
  graph.on('edge:removed', ({ edge }) => {
    const targetNode = edge.getTargetNode()
    if (targetNode) {
      const incomingEdges = graph!.getIncomingEdges(targetNode)
      const count = incomingEdges ? incomingEdges.length : 0
      if (count > 0) {
        ElMessage.info(`剩余 ${count} 个输入源`)
      }
    }
  })

  // 节点删除事件
  graph.on('node:removed', () => {
    nodes.value = graph!.getNodes()
    if (selectedNode.value && !graph!.getCellById(selectedNode.value.id)) {
      selectedNode.value = null
    }
  })

  // 鼠标悬停显示工具
  graph.on('node:mouseenter', ({ node }) => {
    const tools: any[] = [
      {
        name: 'button-remove',
        args: {
          x: '100%',
          y: 0,
          offset: { x: -10, y: 10 }
        }
      }
    ]
    
    // 如果是共享组件，添加 tooltip
    const nodeData = node.getData()
    if (nodeData?.isShared && nodeData?.referenceCount > 0) {
      tools.push({
        name: 'button',
        args: {
          x: '100%',
          y: 0,
          offset: { x: -14, y: 14 },
          markup: [
            {
              tagName: 'title',
              textContent: `该组件被其他 ${nodeData.referenceCount} 个配置使用`
            }
          ]
        }
      })
    }
    
    node.addTools(tools)
  })

  graph.on('node:mouseleave', ({ node }) => {
    node.removeTools()
  })

  graph.on('edge:mouseenter', ({ edge }) => {
    edge.addTools([
      {
        name: 'button-remove',
        args: { distance: '50%' }
      }
    ])
  })

  graph.on('edge:mouseleave', ({ edge }) => {
    edge.removeTools()
  })

  // 键盘事件：Delete/Backspace 删除选中的节点或边
  graph.bindKey(['delete', 'backspace'], () => {
    const cells = graph!.getSelectedCells()
    if (cells.length) {
      graph!.removeCells(cells)
    }
  })
}

const zoomCanvas = (delta: number) => {
  if (!graph) return
  graph.zoom(delta, {
    minScale: 0.4,
    maxScale: 1.8
  })
  syncViewportState()
}

const fitCanvas = () => {
  if (!graph || nodes.value.length === 0) return
  graph.zoomToFit({
    padding: 48,
    maxScale: 1
  })
  syncViewportState()
}

const centerCanvas = () => {
  if (!graph || nodes.value.length === 0) return
  graph.centerContent()
  syncViewportState()
}

const arrangeGraph = () => {
  if (!graph) return

  const graphNodes = graph.getNodes()
  if (graphNodes.length === 0) return

  const nodeMap = new Map(graphNodes.map(node => [node.id, node]))
  const incomingMap = new Map<string, string[]>()
  graphNodes.forEach(node => incomingMap.set(node.id, []))

  graph.getEdges().forEach(edge => {
    const sourceId = edge.getSourceCellId()
    const targetId = edge.getTargetCellId()
    if (sourceId && targetId && incomingMap.has(targetId)) {
      incomingMap.get(targetId)!.push(sourceId)
    }
  })

  const getCategoryDepth = (node: any) => {
    const category = node.getData()?.category
    if (category === 'source') return 0
    if (category === 'transform') return 1
    return 2
  }

  const depthMap = new Map<string, number>()
  for (let round = 0; round < graphNodes.length; round += 1) {
    let progressed = false
    graphNodes.forEach(node => {
      if (depthMap.has(node.id)) return

      const incoming = (incomingMap.get(node.id) || []).filter(sourceId => nodeMap.has(sourceId))
      if (incoming.every(sourceId => depthMap.has(sourceId))) {
        const baseDepth = getCategoryDepth(node)
        const upstreamDepth = incoming.length
          ? Math.max(...incoming.map(sourceId => depthMap.get(sourceId) ?? 0)) + 1
          : baseDepth
        depthMap.set(node.id, Math.max(baseDepth, upstreamDepth))
        progressed = true
      }
    })

    if (!progressed) {
      break
    }
  }

  graphNodes.forEach(node => {
    if (!depthMap.has(node.id)) {
      depthMap.set(node.id, getCategoryDepth(node))
    }
  })

  const columns = new Map<number, any[]>()
  graphNodes.forEach(node => {
    const depth = depthMap.get(node.id) ?? 0
    if (!columns.has(depth)) {
      columns.set(depth, [])
    }
    columns.get(depth)!.push(node)
  })

  const startX = 80
  const startY = 100
  const columnGap = 260
  const rowGap = 140

  Array.from(columns.entries())
    .sort(([leftDepth], [rightDepth]) => leftDepth - rightDepth)
    .forEach(([depth, columnNodes]) => {
      columnNodes
        .sort((left, right) => (left.getPosition()?.y || 0) - (right.getPosition()?.y || 0))
        .forEach((node, index) => {
          node.position(startX + depth * columnGap, startY + index * rowGap)
        })
    })

  centerCanvas()
  fitCanvas()
  ElMessage.success('已按数据流方向整理布局')
}

const createNode = (category: string, comp: any, x: number, y: number) => {
  const colors: Record<string, string> = {
    source: '#52c41a',
    transform: '#1890ff',
    sink: '#722ed1'
  }

  const themeColors = getThemeColors()

  // 解析用户自定义配置
  let initialConfig: Record<string, any> = {}
  
  console.log('createNode - comp:', comp)
  console.log('createNode - comp.configYaml:', comp.configYaml)
  
  // 优先使用 configYaml
  if (comp.configYaml) {
    try {
      const parsed = yaml.load(comp.configYaml) as Record<string, any>
      console.log('createNode - parsed yaml:', parsed)
      if (parsed && typeof parsed === 'object') {
        // 移除 type 字段，因为它会自动添加
        const { type, ...config } = parsed
        initialConfig = config
        console.log('createNode - initialConfig:', initialConfig)
      }
    } catch (e) {
      console.warn('解析 configYaml 失败:', e)
    }
  }

  const nodeId = `${category}_${comp.type}_${Date.now()}`
  const nodeName = comp.id ? comp.label : `${comp.type}_${nodes.value.length + 1}`
  
  // 检查组件是否被其他配置引用
  const isShared = isComponentShared(comp.id)
  const refCount = getComponentReferenceCount(comp.id)
  
  // 判断是否是 route 组件
  const isRouteComponent = comp.type === 'route'
  
  // 为 route 组件创建多个输出端口
  let portItems: any[] = []
  let nodeHeight = 50
  
  if (category === 'source') {
    portItems = [{ id: 'out', group: 'out' }]
  } else if (category === 'sink') {
    portItems = [{ id: 'in', group: 'in' }]
  } else if (category === 'transform') {
    if (isRouteComponent) {
      // Route 组件：一个输入，多个输出（根据配置的路由条件）
      portItems = [
        { id: 'in', group: 'in' }
      ]
      
      // 从 initialConfig 中获取路由条件，如果没有则使用默认值
      let routeNames: string[] = []
      if (initialConfig.route && typeof initialConfig.route === 'object') {
        // 从已有配置中提取路由名称
        routeNames = Object.keys(initialConfig.route)
        console.log('从配置中提取的路由名称:', routeNames)
      }
      
      // 如果没有配置，使用默认值
      if (routeNames.length === 0) {
        routeNames = ['default']
        initialConfig = {
          route: {
            default: { type: 'vrl', condition: '' }
          },
          reroute_unmatched: true
        }
      }
      
      // 添加 _unmatched 端口（如果配置中没有明确禁用）
      if (initialConfig.reroute_unmatched !== false) {
        routeNames.push('_unmatched')
      }
      
      // 创建输出端口
      routeNames.forEach((routeName) => {
        portItems.push({
          id: `out_${routeName}`,
          group: 'out',
          attrs: {
            text: { text: routeName, fontSize: 10, fill: themeColors.nodeLabel }
          }
        })
      })
      
      // 增加节点高度以容纳多个输出端口
      nodeHeight = 50 + Math.max(0, routeNames.length - 1) * 25
    } else {
      portItems = [
        { id: 'in', group: 'in' },
        { id: 'out', group: 'out' }
      ]
    }
  }
  
  const node = graph!.addNode({
    id: nodeId,
    x,
    y,
    width: 180,
    height: nodeHeight,
    shape: 'rect',
    markup: [
      { tagName: 'rect', selector: 'body' },
      { tagName: 'text', selector: 'label' },
      // 共享标记图标（链接图标）
      {
        tagName: 'g',
        selector: 'sharedBadge',
        children: [
          { tagName: 'circle', selector: 'sharedBadgeBg' },
          { tagName: 'text', selector: 'sharedBadgeIcon' }
        ]
      }
    ],
    attrs: {
      body: {
        fill: themeColors.nodeFill,
        stroke: colors[category],
        strokeWidth: 2,
        rx: 6,
        ry: 6
      },
      label: {
        text: comp.label,
        fill: themeColors.nodeLabel,
        fontSize: 13,
        refX: '50%',
        refY: '50%',
        textAnchor: 'middle',
        textVerticalAnchor: 'middle'
      },
      // 共享标记背景圆
      sharedBadgeBg: {
        r: 10,
        fill: isShared ? '#ff9800' : 'transparent',
        refX: '100%',
        refY: 0,
        refX2: -14,
        refY2: 14,
        cursor: 'pointer'
      },
      // 共享标记图标（使用 🔗 或数字）
      sharedBadgeIcon: {
        text: isShared ? (refCount > 9 ? '9+' : String(refCount)) : '',
        fill: '#fff',
        fontSize: 10,
        fontWeight: 'bold',
        refX: '100%',
        refY: 0,
        refX2: -14,
        refY2: 14,
        textAnchor: 'middle',
        textVerticalAnchor: 'middle',
        cursor: 'pointer'
      }
    },
    ports: {
      groups: {
        in: {
          position: 'left',
          attrs: {
            circle: { 
              r: 8,
              magnet: true, 
              stroke: '#5F95FF', 
              strokeWidth: 2, 
              fill: themeColors.portFill,
              style: {
                cursor: 'crosshair'
              }
            }
          }
        },
        out: {
          position: {
            name: 'right',
            args: {
              // 多个输出端口时垂直分布
              strict: true
            }
          },
          attrs: {
            circle: { 
              r: 8,
              magnet: true, 
              stroke: '#5F95FF', 
              strokeWidth: 2, 
              fill: themeColors.portFill,
              style: {
                cursor: 'crosshair'
              }
            },
            text: {
              fontSize: 10,
              fill: 'var(--macos-text-secondary)'
            }
          },
          label: {
            position: {
              name: 'right',
              args: { x: 12, y: 0 }
            }
          }
        }
      },
      items: portItems
    },
    data: {
      category,
      componentType: `${comp.type}_${category}`,
      name: nodeName,
      config: initialConfig,
      isRoute: isRouteComponent,
      componentId: comp.id || null,  // 保存组件库 ID，用于同步最新配置
      isShared: isShared,  // 是否被其他配置引用
      referenceCount: refCount  // 被引用次数
    }
  })

  nodes.value = graph!.getNodes()
  syncViewportState()
  return node
}

const onDragStart = (e: DragEvent, category: string, comp: any) => {
  e.dataTransfer?.setData('category', category)
  e.dataTransfer?.setData('component', JSON.stringify(comp))
}

const onDrop = (e: DragEvent) => {
  const category = e.dataTransfer?.getData('category')
  const compStr = e.dataTransfer?.getData('component')
  if (!category || !compStr || !canvasRef.value) return

  const comp = JSON.parse(compStr)
  console.log('拖拽组件:', comp)
  console.log('configYaml:', comp.configYaml)
  
  const rect = canvasRef.value.getBoundingClientRect()
  const x = e.clientX - rect.left - 90
  const y = e.clientY - rect.top - 25

  createNode(category, comp, x, y)
}

const onCanvasDblClick = (e: MouseEvent) => {
  if (!canvasRef.value) return
  const rect = canvasRef.value.getBoundingClientRect()
  quickAddPosition.value = { x: e.clientX - rect.left, y: e.clientY - rect.top }
  quickSearchKeyword.value = ''
  showQuickAdd.value = true
}

const addComponentAtPosition = (category: string, comp: any) => {
  createNode(category, comp, quickAddPosition.value.x - 90, quickAddPosition.value.y - 25)
  showQuickAdd.value = false
}

const addQuickComponent = () => {
  const filtered = quickFilteredComponents.value
  if (filtered.length > 0) {
    addComponentAtPosition(filtered[0].category, filtered[0])
  }
}

const updateNodeData = () => {
  if (selectedNode.value && graph) {
    const node = graph.getCellById(selectedNode.value.id)
    if (node) {
      node.setData(selectedNode.value.data)
      node.attr('label/text', selectedNode.value.data.name)
    }
  }
}

// 处理 route 组件路由条件变化，更新输出端口
const onRoutesChanged = (routeNames: string[]) => {
  if (!selectedNode.value || !graph) return
  
  const node = graph.getCellById(selectedNode.value.id) as any
  if (!node || !selectedNode.value.data.isRoute) return
  
  // 获取当前所有输出端口
  const currentPorts = node.getPorts().filter((p: any) => p.group === 'out')
  const currentPortIds = currentPorts.map((p: any) => p.id)
  
  // 计算需要添加和删除的端口
  const newPortIds = routeNames.map(name => `out_${name}`)
  const portsToRemove = currentPortIds.filter((id: string) => !newPortIds.includes(id))
  const portsToAdd = newPortIds.filter(id => !currentPortIds.includes(id))
  
  // 删除旧端口（同时删除相关的连线）
  portsToRemove.forEach((portId: string) => {
    // 先删除连接到该端口的边
    const edges = graph!.getConnectedEdges(node).filter(edge => {
      const sourcePortId = edge.getSourcePortId()
      return sourcePortId === portId
    })
    edges.forEach(edge => graph!.removeEdge(edge))
    
    // 删除端口
    node.removePort(portId)
  })
  
  // 添加新端口
  portsToAdd.forEach(portId => {
    const routeName = portId.replace('out_', '')
    const themeColors = getThemeColors()
    node.addPort({
      id: portId,
      group: 'out',
      attrs: {
        text: { text: routeName, fontSize: 10, fill: themeColors.nodeLabel }
      }
    })
  })
  
  // 更新节点高度
  const totalOutputPorts = routeNames.length
  const newHeight = 50 + Math.max(0, totalOutputPorts - 1) * 25
  node.resize(180, newHeight)
  
  // 更新节点数据
  selectedNode.value.data.routeOutputs = routeNames
  node.setData(selectedNode.value.data)
}

const generateYaml = async () => {
  if (!graph) return ''

  // 先刷新组件库数据，确保使用最新配置
  await loadComponents()

  const graphNodes = graph.getNodes()
  const edges = graph.getEdges()

  const config: any = { sources: {}, transforms: {}, sinks: {} }

  graphNodes.forEach(node => {
    const data = node.getData()
    const name = data.name
    const type = data.componentType.replace(`_${data.category}`, '')

    // 获取所有输入节点（支持多个输入，并处理 route 组件的特殊输出端口）
    const inputs = edges
      .filter(e => e.getTargetCellId() === node.id)
      .map(e => {
        const sourceNode = graph!.getCellById(e.getSourceCellId())
        const sourceData = sourceNode?.getData()
        if (!sourceData) return null
        
        // 检查源节点是否是 route 组件
        if (sourceData.isRoute) {
          // 获取连线的源端口 ID
          const sourcePortId = e.getSourcePortId()
          if (sourcePortId && sourcePortId.startsWith('out_')) {
            // 提取路由条件名称
            const routeCondition = sourcePortId.replace('out_', '')
            // 返回 route_name.condition_name 格式
            return `${sourceData.name}.${routeCondition}`
          }
        }
        
        return sourceData.name
      })
      .filter(Boolean)

    const section = data.category === 'source' ? 'sources' : data.category === 'transform' ? 'transforms' : 'sinks'

    // 优先从组件库获取最新配置
    let nodeConfig: any = { type }
    
    if (data.componentId) {
      // 根据组件 ID 从组件库获取最新配置
      let componentList: ComponentItem[] = []
      if (data.category === 'source') componentList = customSources.value
      else if (data.category === 'transform') componentList = customTransforms.value
      else if (data.category === 'sink') componentList = customSinks.value
      
      const latestComp = componentList.find(c => c.id === data.componentId)
      if (latestComp?.configYaml) {
        try {
          const parsed = yaml.load(latestComp.configYaml) as Record<string, any>
          if (parsed && typeof parsed === 'object') {
            const { type: parsedType, ...latestConfig } = parsed
            nodeConfig = { type: parsedType || type, ...latestConfig }
          }
        } catch (e) {
          console.warn(`解析组件 ${name} 的最新配置失败:`, e)
          nodeConfig = { type, ...data.config }
        }
      } else {
        nodeConfig = { type, ...data.config }
      }
    } else {
      // 没有 componentId，尝试通过名称匹配
      let componentList: ComponentItem[] = []
      if (data.category === 'source') componentList = customSources.value
      else if (data.category === 'transform') componentList = customTransforms.value
      else if (data.category === 'sink') componentList = customSinks.value
      
      const nodeLabel = node.attr('label/text') as string
      const matchedComp = componentList.find(c => c.label === nodeLabel || c.label === name)
      
      if (matchedComp?.configYaml) {
        try {
          const parsed = yaml.load(matchedComp.configYaml) as Record<string, any>
          if (parsed && typeof parsed === 'object') {
            const { type: parsedType, ...latestConfig } = parsed
            nodeConfig = { type: parsedType || type, ...latestConfig }
          }
        } catch (e) {
          nodeConfig = { type, ...data.config }
        }
      } else {
        nodeConfig = { type, ...data.config }
      }
    }

    // 只有 transform 和 sink 需要 inputs
    if (inputs.length > 0 && (data.category === 'transform' || data.category === 'sink')) {
      nodeConfig.inputs = inputs
    }

    config[section][name] = nodeConfig
  })

  // 清理空对象
  Object.keys(config).forEach(k => {
    if (Object.keys(config[k]).length === 0) delete config[k]
  })

  return yaml.dump(config, { indent: 2, lineWidth: -1 })
}

const previewYaml = async () => {
  generatedYaml.value = await generateYaml()
  showYamlPreview.value = true
}

const validateYaml = async () => {
  validating.value = true
  try {
    const validateRes = await visualConfigApi.validate(generatedYaml.value) as any
    const result = validateRes.data || validateRes
    if (!result.valid) {
      // 格式化错误信息
      let errorMsg = result.error || '未知错误'
      
      if (errorMsg.includes('missing field')) {
        const match = errorMsg.match(/missing field `(\w+)`\s*in `(\w+)\.(\w+)`/)
        if (match) {
          const [, field, section, name] = match
          errorMsg = `组件 "${name}" 缺少必填字段 "${field}"，请在右侧属性面板中配置`
        }
      }
      
      ElMessage.error({
        message: `配置校验失败: ${errorMsg}`,
        duration: 5000,
        showClose: true
      })
    } else {
      ElMessage.success('配置校验通过')
    }
  } catch (e: any) {
    ElMessage.error(`配置校验失败: ${e.message || '校验服务异常'}`)
  } finally {
    validating.value = false
  }
}

const loadConfig = async () => {
  try {
    const res = await visualConfigApi.getById(configId) as any
    const data = res.data || res
    configName.value = data.name
    configFormat.value = data.format
    if (data.graphData) {
      const graphData = JSON.parse(data.graphData)
      graph?.fromJSON(graphData)
      nodes.value = graph?.getNodes() || []
      
      // 同步组件库的最新配置和名称
      nodes.value.forEach(node => {
        const nodeData = node.getData()
        if (!nodeData) return
        
        const category = nodeData.category
        const nodeName = nodeData.name
        const nodeLabel = node.attr('label/text') as string
        
        let componentList: ComponentItem[] = []
        if (category === 'source') componentList = customSources.value
        else if (category === 'transform') componentList = customTransforms.value
        else if (category === 'sink') componentList = customSinks.value
        
        // 通过 componentId 或名称/标签/类型匹配组件
        let matchedComp: ComponentItem | undefined
        if (nodeData.componentId) {
          matchedComp = componentList.find(c => c.id === nodeData.componentId)
        }
        if (!matchedComp) {
          // 尝试通过名称或标签匹配
          matchedComp = componentList.find(c => 
            c.label === nodeLabel || c.label === nodeName
          )
        }
        if (!matchedComp) {
          // 尝试通过组件类型匹配（如果只有一个同类型的自定义组件）
          const componentType = nodeData.componentType?.replace(`_${category}`, '')
          const sameTypeComps = componentList.filter(c => c.type === componentType)
          if (sameTypeComps.length === 1) {
            matchedComp = sameTypeComps[0]
            console.log(`通过类型匹配组件: ${componentType} -> ${matchedComp.label}`)
          }
        }
        
        if (matchedComp) {
          // 如果节点没有 componentId，补充它（迁移旧数据）
          if (!nodeData.componentId && matchedComp.id) {
            console.log(`补充节点 componentId: ${nodeLabel} -> ${matchedComp.id}`)
            nodeData.componentId = matchedComp.id
          }
          
          // 更新共享状态
          const isShared = isComponentShared(matchedComp.id)
          const refCount = getComponentReferenceCount(matchedComp.id)
          nodeData.isShared = isShared
          nodeData.referenceCount = refCount
          
          // 更新共享标记的显示
          node.attr('sharedBadgeBg/fill', isShared ? '#ff9800' : 'transparent')
          node.attr('sharedBadgeIcon/text', isShared ? (refCount > 9 ? '9+' : String(refCount)) : '')
          
          // 同步组件库的最新名称
          if (matchedComp.label !== nodeLabel) {
            console.log(`同步节点名称: ${nodeLabel} -> ${matchedComp.label}`)
            node.attr('label/text', matchedComp.label)
            nodeData.name = matchedComp.label
          }
          
          // 更新节点数据
          node.setData(nodeData)
          
          // 同步组件库的最新配置
          if (matchedComp.configYaml) {
            try {
              const parsed = yaml.load(matchedComp.configYaml) as Record<string, any>
              if (parsed && typeof parsed === 'object') {
                const { type, ...config } = parsed
                nodeData.config = config
                node.setData(nodeData)
                console.log(`同步节点 ${matchedComp.label} 的配置`)
              }
            } catch (e) {
              console.warn(`解析组件 ${matchedComp.label} 的 configYaml 失败:`, e)
            }
          }
        }
        
        // 处理 route 组件：根据配置恢复输出端口
        if (nodeData?.isRoute && nodeData.config?.route) {
          const routeConfig = nodeData.config.route
          const routeNames = Object.keys(routeConfig)
          if (nodeData.config.reroute_unmatched !== false) {
            routeNames.push('_unmatched')
          }
          
          // 获取当前端口
          const currentPorts = (node as any).getPorts().filter((p: any) => p.group === 'out')
          const currentPortIds = currentPorts.map((p: any) => p.id)
          const newPortIds = routeNames.map(name => `out_${name}`)
          
          // 添加缺失的端口
          const portThemeColors = getThemeColors()
          newPortIds.forEach(portId => {
            if (!currentPortIds.includes(portId)) {
              const routeName = portId.replace('out_', '')
              ;(node as any).addPort({
                id: portId,
                group: 'out',
                attrs: {
                  text: { text: routeName, fontSize: 10, fill: portThemeColors.nodeLabel }
                }
              })
            }
          })
          
          // 删除多余的端口
          currentPortIds.forEach((portId: string) => {
            if (!newPortIds.includes(portId)) {
              ;(node as any).removePort(portId)
            }
          })
          
          // 更新节点高度
          const totalOutputPorts = routeNames.length
          const newHeight = 50 + Math.max(0, totalOutputPorts - 1) * 25
          ;(node as any).resize(180, newHeight)
        }
      })
      
      // 初始化边为停止状态（灰色实线，无流动）
      // 实际状态会在 fetchComponentStatus 中根据后端数据更新
      const themeColors = getThemeColors()
      const edges = graph?.getEdges() || []
      edges.forEach(edge => {
        edge.attr('line/stroke', themeColors.edgeStroke)
        edge.attr('line/strokeWidth', 2)
        edge.attr('line/strokeDasharray', '')  // 实线
      })
      
      // 更新所有节点的主题颜色
      updateGraphTheme()
      syncViewportState()
    }
  } catch {
    ElMessage.error('加载配置失败')
  }
}

const saveConfig = async () => {
  if (!graph) return
  
  const yamlContent = await generateYaml()
  
  // 先校验配置
  validating.value = true
  try {
    const validateRes = await visualConfigApi.validate(yamlContent) as any
    const result = validateRes.data || validateRes
    if (!result.valid) {
      // 格式化错误信息，使其更易读
      let errorMsg = result.error || '未知错误'
      
      // 提取关键错误信息
      if (errorMsg.includes('missing field')) {
        const match = errorMsg.match(/missing field `(\w+)`\s*in `(\w+)\.(\w+)`/)
        if (match) {
          const [, field, section, name] = match
          errorMsg = `组件 "${name}" 缺少必填字段 "${field}"，请在右侧属性面板中配置`
        }
      }
      
      ElMessage.error({
        message: `配置校验失败: ${errorMsg}`,
        duration: 5000,
        showClose: true
      })
      validating.value = false
      return
    }
  } catch (e: any) {
    ElMessage.error(`配置校验失败: ${e.message || '校验服务异常'}`)
    validating.value = false
    return
  }
  validating.value = false
  
  // 校验通过后保存
  saving.value = true
  try {
    const graphData = graph.toJSON()
    await visualConfigApi.update(configId, {
      graphData: JSON.stringify(graphData),
      content: yamlContent,
      nodeCount: nodes.value.length
    })
    ElMessage.success('保存成功')
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

const goBack = () => router.push('/vector/visual-configs')

// 加载可用机器列表
const loadHosts = async () => {
  loadingHosts.value = true
  try {
    const res = await vectorMachineApi.getList() as any
    console.log('机器列表响应:', res)
    const list = Array.isArray(res) ? res : (res.data?.records || res.data || [])
    console.log('解析后的机器列表:', list)
    // 显示所有机器，不过滤状态
    availableHosts.value = list
  } catch (e) {
    console.error('加载机器列表失败:', e)
    ElMessage.error('加载机器列表失败')
  } finally {
    loadingHosts.value = false
  }
}

// 部署配置到选定机器
const deployConfig = async () => {
  if (deployTargetHosts.value.length === 0) {
    ElMessage.warning('请选择要部署的机器')
    return
  }

  // 先保存当前配置
  if (!graph) return
  
  const yamlContent = await generateYaml()
  if (!yamlContent.trim()) {
    ElMessage.warning('配置内容为空，请先添加组件')
    return
  }

  deploying.value = true
  try {
    // 先校验配置
    const validateRes = await visualConfigApi.validate(yamlContent) as any
    const result = validateRes.data || validateRes
    if (!result.valid) {
      ElMessage.error(`配置校验失败: ${result.error || '未知错误'}`)
      return
    }

    // 保存配置
    const graphData = graph.toJSON()
    await visualConfigApi.update(configId, {
      graphData: JSON.stringify(graphData),
      content: yamlContent,
      nodeCount: nodes.value.length
    })

    // 创建部署任务
    await vectorDeploymentApi.createDeployment({
      hostIds: deployTargetHosts.value,
      configId: configId,
      configContent: yamlContent,
      deployMode: deployMode.value
    })

    ElMessage.success({
      message: `已创建 ${deployTargetHosts.value.length} 个部署任务，等待 Agent 拉取配置`,
      duration: 5000
    })
    showDeployDialog.value = false
    deployTargetHosts.value = []
  } catch (e: any) {
    ElMessage.error(`部署失败: ${e.message || '未知错误'}`)
  } finally {
    deploying.value = false
  }
}

// 监听部署对话框打开，加载机器列表
watch(showDeployDialog, (val) => {
  if (val) {
    loadHosts()
  }
})

// 更新节点状态样式
const updateNodeStatus = (nodeName: string, status: ComponentStatus) => {
  if (!graph) return
  
  // 查找匹配的节点（支持多种匹配方式）
  const node = graph.getNodes().find(n => {
    const data = n.getData()
    const label = n.attr('label/text') as string
    const name = data?.name
    
    // 精确匹配
    if (name === nodeName || label === nodeName) return true
    
    // 模糊匹配：忽略大小写和下划线/空格差异
    const normalize = (s: string) => s?.toLowerCase().replace(/[_\s-]/g, '') || ''
    if (normalize(name) === normalize(nodeName) || normalize(label) === normalize(nodeName)) return true
    
    // 部分匹配：节点名称包含组件名或组件名包含节点名称
    if (name && (name.includes(nodeName) || nodeName.includes(name))) return true
    if (label && (label.includes(nodeName) || nodeName.includes(label))) return true
    
    return false
  })
  
  if (!node) {
    console.log(`未找到节点: ${nodeName}`)
    return
  }
  
  console.log(`更新节点状态: ${nodeName} -> ${status}`)
  
  // 更新节点边框颜色
  const colors: Record<ComponentStatus, string> = {
    normal: '#67c23a',
    warning: '#e6a23c',
    error: '#f56c6c',
    stopped: '#909399'
  }
  node.attr('body/stroke', colors[status])
  
  // 更新相关边的状态
  const outgoingEdges = graph.getOutgoingEdges(node)
  outgoingEdges?.forEach(edge => {
    // 清理之前的动画定时器
    const existingTimer = (edge as any)._flowTimer
    if (existingTimer) {
      clearInterval(existingTimer)
      ;(edge as any)._flowTimer = null
    }
    
    // 根据状态设置边的样式
    if (status === 'normal') {
      edge.attr('line/stroke', '#67c23a')
      edge.attr('line/strokeWidth', 3)
      edge.attr('line/strokeDasharray', '8 4')
      
      // 使用 setInterval 实现流动动画
      let offset = 0
      ;(edge as any)._flowTimer = setInterval(() => {
        offset = (offset - 1) % 12
        edge.attr('line/strokeDashoffset', offset)
      }, 50)
      console.log(`启动边流动动画: ${node.getData()?.name}`)
    } else if (status === 'warning') {
      edge.attr('line/stroke', '#e6a23c')
      edge.attr('line/strokeWidth', 3)
      edge.attr('line/strokeDasharray', '8 4')
      
      // 慢速流动
      let offset = 0
      ;(edge as any)._flowTimer = setInterval(() => {
        offset = (offset - 1) % 12
        edge.attr('line/strokeDashoffset', offset)
      }, 100)
    } else if (status === 'error') {
      edge.attr('line/stroke', '#f56c6c')
      edge.attr('line/strokeWidth', 3)
      edge.attr('line/strokeDasharray', '')
      edge.attr('line/strokeDashoffset', 0)
    } else {
      // stopped 状态 - 灰色实线
      const themeColors = getThemeColors()
      edge.attr('line/stroke', themeColors.edgeStroke)
      edge.attr('line/strokeWidth', 2)
      edge.attr('line/strokeDasharray', '')
      edge.attr('line/strokeDashoffset', 0)
    }
    
    console.log(`更新边样式: ${nodeName} -> ${status}`)
  })
}

// 批量更新所有组件状态
const updateAllComponentStatus = (statusData: Record<string, ComponentStatus>) => {
  componentStatusMap.value.clear()
  
  console.log('更新所有组件状态:', statusData)
  console.log('当前节点:', graph?.getNodes().map(n => ({ name: n.getData()?.name, label: n.attr('label/text') })))
  
  Object.entries(statusData).forEach(([name, status]) => {
    componentStatusMap.value.set(name, status)
    updateNodeStatus(name, status)
  })
}

// 获取组件状态（从后端）
// 已部署的机器列表（用于状态监控）
const deployedHosts = ref<string[]>([])

// 加载该配置已部署的机器
const loadDeployedHosts = async () => {
  try {
    // 查询该配置的部署记录
    const res = await vectorDeploymentApi.getDeployments({ configId: configId }) as any
    const deployments = res.data?.records || res.data || res || []
    
    // 提取成功部署的机器 ID
    const hostIds = new Set<string>()
    deployments.forEach((d: any) => {
      if (d.status === 'success' && d.machineId) {
        hostIds.add(d.machineId)
      }
    })
    deployedHosts.value = Array.from(hostIds)
    console.log('已部署机器:', deployedHosts.value)
  } catch (e) {
    console.warn('加载部署记录失败:', e)
  }
}

const fetchComponentStatus = async () => {
  if (!graph || nodes.value.length === 0) return

  try {
    // 优先使用已部署的机器，其次使用部署目标，最后使用第一个在线机器
    let targetHosts = deployedHosts.value.length > 0 ? deployedHosts.value : deployTargetHosts.value

    // 如果没有部署记录，尝试使用第一个在线机器
    if (targetHosts.length === 0 && availableHosts.value.length > 0) {
      const onlineHost = availableHosts.value.find(h => h.status === 'online')
      if (onlineHost) {
        targetHosts = [onlineHost.id]
        console.log('使用默认在线机器:', onlineHost.id, onlineHost.name)
      }
    }

    console.log('deployedHosts:', deployedHosts.value)
    console.log('deployTargetHosts:', deployTargetHosts.value)
    console.log('targetHosts:', targetHosts)

    if (targetHosts.length === 0) {
      // 没有可用机器时，显示停止状态（无流动效果）
      console.log('没有可用机器，显示停止状态')
      const stoppedStatus: Record<string, ComponentStatus> = {}
      nodes.value.forEach(node => {
        const name = node.getData()?.name
        if (name) stoppedStatus[name] = 'stopped'
      })
      updateAllComponentStatus(stoppedStatus)
      return
    }
    
    // 调用后端接口获取组件状态
    const [targetHostId] = targetHosts
    if (!targetHostId) {
      return
    }

    console.log('调用 API，machineId:', targetHostId)
    const res = await vectorMetricsApi.getComponentStatus(targetHostId) as any
    const responseData = res.data || res
    
    console.log('组件状态响应:', responseData)
    
    // 检查 Vector 是否运行
    const vectorRunning = responseData?.vectorRunning === true
    
    if (vectorRunning && responseData?.componentStatus && Object.keys(responseData.componentStatus).length > 0) {
      // Vector 运行中且有组件状态数据
      updateAllComponentStatus(responseData.componentStatus)
    } else if (vectorRunning) {
      // Vector 运行中但没有组件状态数据，显示正常状态
      const normalStatus: Record<string, ComponentStatus> = {}
      nodes.value.forEach(node => {
        const name = node.getData()?.name
        if (name) normalStatus[name] = 'normal'
      })
      updateAllComponentStatus(normalStatus)
    } else {
      // Vector 未运行，显示停止状态（无流动效果）
      const stoppedStatus: Record<string, ComponentStatus> = {}
      nodes.value.forEach(node => {
        const name = node.getData()?.name
        if (name) stoppedStatus[name] = 'stopped'
      })
      updateAllComponentStatus(stoppedStatus)
    }
  } catch (e) {
    console.warn('获取组件状态失败:', e)
    // 出错时显示停止状态
    const stoppedStatus: Record<string, ComponentStatus> = {}
    nodes.value.forEach(node => {
      const name = node.getData()?.name
      if (name) stoppedStatus[name] = 'stopped'
    })
    updateAllComponentStatus(stoppedStatus)
  }
}

// 启动状态轮询
const startStatusPolling = () => {
  // 先立即获取一次
  fetchComponentStatus()
  
  // 每 5 秒轮询一次
  statusPollingTimer = setInterval(() => {
    fetchComponentStatus()
  }, 5000)
}

// 停止状态轮询
const stopStatusPolling = () => {
  if (statusPollingTimer) {
    clearInterval(statusPollingTimer)
    statusPollingTimer = null
  }
}

// 更新画布和节点的主题颜色
const updateGraphTheme = () => {
  if (!graph) return
  
  const themeColors = getThemeColors()
  
  // 更新画布背景和网格
  graph.drawBackground({ color: themeColors.canvasBg })
  graph.drawGrid({ type: 'dot', args: { color: themeColors.gridColor } })
  
  // 更新所有节点的颜色
  graph.getNodes().forEach(node => {
    node.attr('body/fill', themeColors.nodeFill)
    node.attr('label/fill', themeColors.nodeLabel)
    
    // 更新端口颜色和文字颜色
    const ports = (node as any).getPorts()
    ports.forEach((port: any) => {
      node.setPortProp(port.id, 'attrs/circle/fill', themeColors.portFill)
      // 更新端口文字颜色（route 组件的输出端口有文字标签）
      if (port.group === 'out') {
        node.setPortProp(port.id, 'attrs/text/fill', themeColors.nodeLabel)
      }
    })
  })
  
  // 更新停止状态的边颜色
  graph.getEdges().forEach(edge => {
    const currentStroke = edge.attr('line/stroke')
    // 只更新停止状态（灰色）的边
    if (currentStroke === '#c0c4cc' || currentStroke === '#5a5a5a') {
      edge.attr('line/stroke', themeColors.edgeStroke)
    }
  })
}

// 监听主题变化
let themeObserver: MutationObserver | null = null

const startThemeObserver = () => {
  themeObserver = new MutationObserver((mutations) => {
    mutations.forEach((mutation) => {
      if (mutation.attributeName === 'class') {
        updateGraphTheme()
      }
    })
  })
  
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['class']
  })
}

const stopThemeObserver = () => {
  if (themeObserver) {
    themeObserver.disconnect()
    themeObserver = null
  }
}

onMounted(async () => {
  // 先加载组件库，再初始化画布和加载配置
  await loadComponents()
  initGraph()
  await loadConfig()

  // 加载所有可用机器列表（用于默认选择）
  await loadHosts()

  // 加载已部署的机器列表
  await loadDeployedHosts()

  // 启动状态轮询
  startStatusPolling()

  // 启动主题监听
  startThemeObserver()
})

onBeforeUnmount(() => {
  stopStatusPolling()
  stopThemeObserver()
  // 清理所有边的动画定时器
  if (graph) {
    graph.getEdges().forEach(edge => {
      const timer = (edge as any)._flowTimer
      if (timer) clearInterval(timer)
    })
  }
  graph?.dispose()
})
</script>

<style scoped lang="scss">
.visual-editor {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--macos-bg-secondary);
}

// ═══════════════════════════════════════
// 顶部工具栏 - 毛玻璃质感
// ═══════════════════════════════════════
.editor-header {
  height: 56px;
  background: var(--macos-bg-primary);
  backdrop-filter: blur(20px);
  border-bottom: 1px solid var(--macos-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  flex-shrink: 0;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);

  .header-left {
    display: flex;
    align-items: center;
    gap: 14px;

    .config-name {
      font-size: 16px;
      font-weight: 600;
      color: var(--macos-text-primary);
      letter-spacing: -0.3px;
    }
  }

  .header-right {
    display: flex;
    gap: 10px;

    :deep(.el-button) {
      border-radius: 8px;
      font-weight: 500;
      transition: all 0.2s ease;

      &:hover {
        transform: translateY(-1px);
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
      }
    }
  }
}

.editor-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

// ═══════════════════════════════════════
// 左侧组件面板 - 精致卡片
// ═══════════════════════════════════════
.component-panel {
  width: 256px;
  background: var(--macos-bg-primary);
  border-right: 1px solid var(--macos-border);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  box-shadow: 1px 0 8px rgba(0, 0, 0, 0.03);

  .panel-header {
    padding: 16px 20px 14px;
    font-weight: 700;
    font-size: 14px;
    letter-spacing: -0.2px;
    border-bottom: 1px solid var(--macos-border);
    color: var(--macos-text-primary);
    display: flex;
    align-items: center;
    gap: 8px;

    &::before {
      content: '';
      width: 3px;
      height: 16px;
      border-radius: 2px;
      background: linear-gradient(135deg, #667eea, #764ba2);
    }
  }

  .search-input {
    padding: 12px 14px;

    :deep(.el-input__wrapper) {
      border-radius: 8px;
      box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04), 0 0 0 1px var(--macos-border);

      &:focus-within {
        box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.2), 0 1px 4px rgba(0, 0, 0, 0.06);
      }
    }
  }

  :deep(.el-collapse) {
    border: none;
    flex: 1;
    overflow-y: auto;
    background: transparent;

    &::-webkit-scrollbar {
      width: 4px;
    }

    &::-webkit-scrollbar-thumb {
      background: rgba(0, 0, 0, 0.12);
      border-radius: 4px;
    }
  }

  :deep(.el-collapse-item__header) {
    padding: 0 18px;
    height: 40px;
    font-size: 12px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    background: transparent;
    color: var(--macos-text-secondary);
    border-bottom: none;
  }

  :deep(.el-collapse-item__wrap) {
    background: transparent;
    border-bottom: none;
  }

  :deep(.el-collapse-item__content) {
    background: transparent;
    padding-bottom: 8px;
  }

  .component-list {
    padding: 2px 10px;
  }

  .component-group-label {
    font-size: 10px;
    color: var(--macos-text-tertiary);
    padding: 10px 12px 6px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.8px;
    opacity: 0.7;
  }

  .component-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 12px;
    margin: 3px 0;
    background: var(--macos-bg-tertiary);
    border-radius: 10px;
    cursor: grab;
    font-size: 13px;
    font-weight: 450;
    transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
    color: var(--macos-text-primary);
    border: 1px solid transparent;
    position: relative;
    overflow: hidden;

    .el-icon {
      font-size: 16px;
      color: var(--macos-text-secondary);
      flex-shrink: 0;
    }

    &:hover {
      background: var(--macos-blue-light);
      border-color: rgba(102, 126, 234, 0.2);
      transform: translateX(2px);
      box-shadow: 0 2px 8px rgba(102, 126, 234, 0.1);

      .el-icon {
        color: #667eea;
      }
    }

    &:active {
      cursor: grabbing;
      transform: translateX(2px) scale(0.98);
    }

    &.custom {
      background: linear-gradient(135deg, rgba(103, 194, 58, 0.06), rgba(103, 194, 58, 0.02));
      border: 1px solid rgba(103, 194, 58, 0.25);

      .el-icon {
        color: #67c23a;
      }

      &:hover {
        background: linear-gradient(135deg, rgba(103, 194, 58, 0.12), rgba(103, 194, 58, 0.06));
        border-color: rgba(103, 194, 58, 0.4);
        box-shadow: 0 2px 8px rgba(103, 194, 58, 0.12);
      }
    }

    &.default {
      background: var(--macos-bg-tertiary);
      border: 1px solid var(--macos-border);
      border-style: solid;
    }

    span {
      flex: 1;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .custom-tag {
      font-size: 10px;
      padding: 0 6px;
      height: 18px;
      line-height: 18px;
      border-radius: 6px;
      font-weight: 500;
    }

    .shared-tag {
      font-size: 10px;
      padding: 0 5px;
      height: 18px;
      line-height: 18px;
      min-width: 18px;
      text-align: center;
      border-radius: 9px;
      font-weight: 600;
    }
  }
}

// ═══════════════════════════════════════
// 画布区域 - 渐变背景 + 浮动工具
// ═══════════════════════════════════════
.canvas-area {
  flex: 1;
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(ellipse at 10% 10%, rgba(102, 126, 234, 0.06), transparent 40%),
    radial-gradient(ellipse at 90% 90%, rgba(118, 75, 162, 0.05), transparent 40%),
    radial-gradient(ellipse at 50% 50%, rgba(34, 197, 94, 0.03), transparent 60%);

  .canvas-toolbar {
    position: absolute;
    top: 16px;
    left: 16px;
    right: 16px;
    z-index: 2;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    pointer-events: none;
  }

  .toolbar-overview,
  .toolbar-actions,
  .status-legend {
    pointer-events: auto;
    backdrop-filter: blur(20px) saturate(1.8);
    -webkit-backdrop-filter: blur(20px) saturate(1.8);
    background: rgba(255, 255, 255, 0.78);
    border: 1px solid rgba(255, 255, 255, 0.5);
    box-shadow:
      0 4px 16px rgba(0, 0, 0, 0.06),
      0 1px 3px rgba(0, 0, 0, 0.04),
      inset 0 1px 0 rgba(255, 255, 255, 0.6);
  }

  .toolbar-overview,
  .toolbar-actions {
    border-radius: 16px;
    padding: 8px 12px;
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .toolbar-actions {
    :deep(.el-button) {
      border-radius: 8px;
      font-size: 12px;
      font-weight: 500;
      border: none;
      background: rgba(0, 0, 0, 0.04);
      color: var(--macos-text-secondary);
      transition: all 0.2s;

      &:hover {
        background: rgba(102, 126, 234, 0.1);
        color: #667eea;
      }
    }
  }

  .overview-chip {
    min-width: 64px;
    padding: 8px 12px;
    border-radius: 12px;
    background: rgba(15, 23, 42, 0.04);
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 3px;
    transition: background 0.2s;

    &:hover {
      background: rgba(15, 23, 42, 0.07);
    }

    span {
      font-size: 10px;
      font-weight: 500;
      color: var(--macos-text-tertiary);
      line-height: 1;
      text-transform: uppercase;
      letter-spacing: 0.3px;
    }

    strong {
      font-size: 16px;
      font-weight: 700;
      color: var(--macos-text-primary);
      line-height: 1.2;
    }
  }

  .zoom-indicator {
    min-width: 48px;
    text-align: center;
    font-size: 12px;
    color: var(--macos-text-secondary);
    font-weight: 700;
    font-variant-numeric: tabular-nums;
    padding: 0 4px;
  }

  .canvas-placeholder {
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    text-align: center;
    color: var(--macos-text-tertiary);
    padding: 40px;
    border-radius: 20px;
    background: rgba(255, 255, 255, 0.5);
    backdrop-filter: blur(10px);
    border: 2px dashed var(--macos-border);

    .el-icon {
      color: rgba(102, 126, 234, 0.4);
    }

    p {
      margin: 14px 0 0;
      font-size: 14px;
      font-weight: 500;
    }

    .hint {
      font-size: 12px;
      color: var(--macos-text-tertiary);
      margin-top: 6px;
    }
  }

  .status-legend {
    position: absolute;
    left: 16px;
    bottom: 16px;
    z-index: 2;
    border-radius: 14px;
    padding: 10px 16px;
    display: flex;
    gap: 16px;
    align-items: center;
    color: var(--macos-text-secondary);
    font-size: 12px;
    font-weight: 500;

    span {
      display: inline-flex;
      align-items: center;
      gap: 6px;
    }
  }
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  display: inline-block;
  box-shadow: 0 0 0 3px rgba(0, 0, 0, 0.04);

  &.normal {
    background: #67c23a;
    box-shadow: 0 0 0 3px rgba(103, 194, 58, 0.15);
  }

  &.warning {
    background: #e6a23c;
    box-shadow: 0 0 0 3px rgba(230, 162, 60, 0.15);
  }

  &.error {
    background: #f56c6c;
    box-shadow: 0 0 0 3px rgba(245, 108, 108, 0.15);
  }

  &.stopped {
    background: #909399;
    box-shadow: 0 0 0 3px rgba(144, 147, 153, 0.15);
  }
}

// ═══════════════════════════════════════
// 右侧属性面板 - 分层质感
// ═══════════════════════════════════════
.property-panel {
  width: 300px;
  background: var(--macos-bg-primary);
  border-left: 1px solid var(--macos-border);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  box-shadow: -1px 0 8px rgba(0, 0, 0, 0.03);

  .panel-header {
    padding: 16px 20px 14px;
    font-weight: 700;
    font-size: 14px;
    letter-spacing: -0.2px;
    border-bottom: 1px solid var(--macos-border);
    display: flex;
    justify-content: space-between;
    align-items: center;
    color: var(--macos-text-primary);

    &::before {
      content: '';
      width: 3px;
      height: 16px;
      border-radius: 2px;
      background: linear-gradient(135deg, #1890ff, #36cfc9);
      margin-right: 10px;
    }
  }

  :deep(.el-form) {
    padding: 18px 16px;
    overflow-y: auto;
    flex: 1;

    &::-webkit-scrollbar {
      width: 4px;
    }

    &::-webkit-scrollbar-thumb {
      background: rgba(0, 0, 0, 0.12);
      border-radius: 4px;
    }
  }

  :deep(.el-form-item) {
    margin-bottom: 20px;
  }

  :deep(.el-form-item__label) {
    color: var(--macos-text-secondary);
    font-weight: 500;
    font-size: 12px;
  }

  :deep(.el-input__wrapper) {
    border-radius: 8px;
  }

  :deep(.el-divider__text) {
    font-size: 12px;
    font-weight: 600;
    color: var(--macos-text-tertiary);
    background: var(--macos-bg-primary);
  }
}

// ═══════════════════════════════════════
// 快速添加弹窗
// ═══════════════════════════════════════
.quick-add-list {
  max-height: 320px;
  overflow-y: auto;
  margin-top: 12px;

  &::-webkit-scrollbar {
    width: 4px;
  }

  &::-webkit-scrollbar-thumb {
    background: rgba(0, 0, 0, 0.1);
    border-radius: 4px;
  }

  .quick-add-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 14px;
    cursor: pointer;
    border-radius: 10px;
    transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
    color: var(--macos-text-primary);
    margin: 2px 0;

    .el-icon {
      font-size: 16px;
      color: var(--macos-text-secondary);
    }

    &:hover {
      background: var(--macos-blue-light);
      transform: translateX(2px);

      .el-icon {
        color: #667eea;
      }
    }

    .comp-name {
      flex: 1;
      font-weight: 450;
    }
  }
}

// ═══════════════════════════════════════
// YAML 预览
// ═══════════════════════════════════════
.yaml-preview {
  background: #1a1b26;
  color: #c0caf5;
  padding: 20px;
  border-radius: 12px;
  font-family: 'JetBrains Mono', 'Monaco', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.7;
  overflow: auto;
  height: calc(100% - 32px);
  white-space: pre-wrap;
  border: 1px solid rgba(255, 255, 255, 0.06);

  &::-webkit-scrollbar {
    width: 6px;
    height: 6px;
  }

  &::-webkit-scrollbar-thumb {
    background: rgba(255, 255, 255, 0.15);
    border-radius: 4px;
  }
}

// ═══════════════════════════════════════
// 暗色模式适配
// ═══════════════════════════════════════
:global(html.dark) {
  .toolbar-overview,
  .toolbar-actions,
  .status-legend {
    background: rgba(30, 30, 32, 0.85) !important;
    border-color: rgba(255, 255, 255, 0.08) !important;
    box-shadow:
      0 4px 16px rgba(0, 0, 0, 0.3),
      0 1px 3px rgba(0, 0, 0, 0.2),
      inset 0 1px 0 rgba(255, 255, 255, 0.05) !important;
  }

  .overview-chip {
    background: rgba(255, 255, 255, 0.06) !important;

    &:hover {
      background: rgba(255, 255, 255, 0.1) !important;
    }
  }

  .canvas-placeholder {
    background: rgba(30, 30, 32, 0.6) !important;
    border-color: rgba(255, 255, 255, 0.1) !important;
  }

  .component-item {
    &.default {
      border-style: solid !important;
    }
  }
}

// ═══════════════════════════════════════
// 响应式布局
// ═══════════════════════════════════════
@media (max-width: 1200px) {
  .component-panel {
    width: 220px;
  }

  .property-panel {
    width: 260px;
  }

  .canvas-area {
    .canvas-toolbar {
      flex-direction: column;
      align-items: flex-start;
      right: auto;
    }

    .toolbar-overview,
    .toolbar-actions,
    .status-legend {
      border-radius: 14px;
    }

    .toolbar-overview {
      flex-wrap: wrap;
    }
  }
}
</style>

<!-- 全局样式：流动动画 -->
<style>
/* 边流动动画 - 从右向左流动 */
@keyframes edge-flow {
  0% {
    stroke-dashoffset: 0;
  }
  100% {
    stroke-dashoffset: -12;
  }
}

/* 正常状态：绿色流动 */
.edge-flow-normal {
  stroke: #67c23a !important;
  stroke-width: 3px !important;
  stroke-dasharray: 8 4 !important;
  animation: edge-flow 0.5s linear infinite !important;
}

/* 警告状态：橙色慢速流动 */
.edge-flow-warning {
  stroke: #e6a23c !important;
  stroke-width: 3px !important;
  stroke-dasharray: 8 4 !important;
  animation: edge-flow 1s linear infinite !important;
}

/* 错误状态：红色闪烁 */
.edge-flow-error {
  stroke: #f56c6c !important;
  stroke-width: 3px !important;
  stroke-dasharray: none !important;
  animation: none !important;
}

/* 停止状态：灰色实线 */
.edge-flow-stopped {
  stroke: #c0c4cc !important;
  stroke-width: 2px !important;
  stroke-dasharray: none !important;
  animation: none !important;
}

/* 暗色模式下的停止状态 */
html.dark .edge-flow-stopped {
  stroke: #5a5a5a !important;
}

/* 节点状态样式 */
.x6-node.node-normal rect {
  stroke: #67c23a !important;
}

.x6-node.node-warning rect {
  stroke: #e6a23c !important;
  stroke-width: 3px !important;
}

.x6-node.node-error rect {
  stroke: #f56c6c !important;
  stroke-width: 3px !important;
}

.x6-node.node-stopped rect {
  stroke: #909399 !important;
  stroke-dasharray: 4 2;
}

/* 暗色模式下的节点停止状态 */
html.dark .x6-node.node-stopped rect {
  stroke: #6e6e73 !important;
}

/* X6 画布在暗色模式下的选择框样式 */
html.dark .x6-widget-selection-box {
  border-color: var(--macos-blue) !important;
}

html.dark .x6-widget-selection-inner {
  background-color: rgba(10, 132, 255, 0.1) !important;
}
</style>
