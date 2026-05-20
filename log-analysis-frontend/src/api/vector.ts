/**
 * Vector 日志收集管理器 - API 封装
 */

import request from '@/utils/request'
import type {
  VectorHost,
  VectorConfig,
  VectorDeployment,
  VectorMetrics,
  RegisterRequest,
  RegisterResponse,
  HeartbeatRequest,
  HeartbeatResponse,
  TokenResponse,
  TokenCheckResponse,
  ConfigResponse,
  MetricsReportRequest,
  HostQueryParams,
  ConfigQueryParams,
  MetricsQueryParams,
  DeploymentTask,
  VectorConfigTemplate
} from '@/types/vector'

// ==================== 机器管理 API ====================

export const vectorHostApi = {
  /**
   * 生成 Agent Token
   */
  generateToken(): Promise<TokenResponse> {
    return request.post('/api/vector/hosts/generate-token')
  },

  /**
   * 检查 Token 是否已注册
   */
  checkToken(token: string): Promise<TokenCheckResponse> {
    return request.get(`/api/vector/hosts/check-token/${token}`)
  },

  /**
   * Agent 注册（公开接口，无需认证）
   */
  register(data: RegisterRequest): Promise<RegisterResponse> {
    return request.post('/api/vector/hosts/register', data)
  },

  /**
   * Agent 心跳（需要 Token 认证）
   */
  heartbeat(data: HeartbeatRequest): Promise<HeartbeatResponse> {
    return request.post('/api/vector/hosts/heartbeat', data)
  },

  /**
   * 查询机器列表
   */
  getHosts(params?: HostQueryParams): Promise<VectorHost[]> {
    return request.get('/api/vector/hosts', { params })
  },

  /**
   * 查询机器详情
   */
  getHostById(id: number): Promise<VectorHost> {
    return request.get(`/api/vector/hosts/${id}`)
  },

  /**
   * 删除机器
   */
  deleteHost(id: number): Promise<void> {
    return request.delete(`/api/vector/hosts/${id}`)
  },

  /**
   * 重启 Vector
   */
  restartVector(id: number): Promise<string> {
    return request.post(`/api/vector/hosts/${id}/restart`)
  },

  /**
   * 停止 Vector
   */
  stopVector(id: number): Promise<string> {
    return request.post(`/api/vector/hosts/${id}/stop`)
  },

  /**
   * 启动 Vector
   */
  startVector(id: number): Promise<string> {
    return request.post(`/api/vector/hosts/${id}/start`)
  }
}

// ==================== 配置管理 API ====================

export const vectorConfigApi = {
  /**
   * 创建配置
   */
  createConfig(data: Partial<VectorConfig>): Promise<VectorConfig> {
    return request.post('/api/vector/configs', data)
  },

  /**
   * 查询配置列表
   */
  getConfigs(params?: ConfigQueryParams): Promise<VectorConfig[]> {
    return request.get('/api/vector/configs', { params })
  },

  /**
   * 查询配置详情
   */
  getConfigById(id: number): Promise<VectorConfig> {
    return request.get(`/api/vector/configs/${id}`)
  },

  /**
   * 更新配置
   */
  updateConfig(id: number, data: Partial<VectorConfig>): Promise<VectorConfig> {
    return request.put(`/api/vector/configs/${id}`, data)
  },

  /**
   * 删除配置
   */
  deleteConfig(id: number): Promise<void> {
    return request.delete(`/api/vector/configs/${id}`)
  },

  /**
   * 发布配置
   */
  releaseConfig(id: number): Promise<void> {
    return request.post(`/api/vector/configs/${id}/release`)
  },

  /**
   * Agent 拉取最新配置（需要 Token 认证）
   */
  getLatestConfig(): Promise<ConfigResponse> {
    return request.get('/api/vector/configs/latest')
  },

  /**
   * 校验配置
   */
  validateConfig(id: number): Promise<{ valid: boolean; error?: string }> {
    return request.post(`/api/vector/configs/${id}/validate`)
  },

  /**
   * 获取配置模板列表
   */
  getTemplates(params?: { category?: string }): Promise<VectorConfigTemplate[]> {
    return request.get('/api/vector/config-templates', { params })
  },

  /**
   * 根据模板创建配置
   */
  createFromTemplate(templateId: number, variables: Record<string, string>): Promise<VectorConfig> {
    return request.post(`/api/vector/config-templates/${templateId}/apply`, { variables })
  }
}

// ==================== 部署管理 API ====================

export const vectorDeploymentApi = {
  /**
   * 创建部署任务
   */
  createDeployment(data: {
    hostIds: string[]
    configId: string
    deployMode: string
  }): Promise<any[]> {
    return request.post('/api/vector/deployments', data)
  },

  /**
   * 查询部署历史
   */
  getDeployments(params?: {
    machineId?: string
    configId?: string
    status?: string
    pageNum?: number
    pageSize?: number
  }): Promise<any> {
    return request.get('/api/vector/deployments', { params })
  },

  /**
   * 查询部署详情
   */
  getDeploymentById(id: string): Promise<any> {
    return request.get(`/api/vector/deployments/${id}`)
  },

  /**
   * 根据机器ID查询部署记录
   */
  getDeploymentsByMachine(machineId: string): Promise<any[]> {
    return request.get(`/api/vector/deployments/machine/${machineId}`)
  }
}

// ==================== 机器管理 API (新) ====================

export const vectorMachineApi = {
  /**
   * 查询机器列表
   */
  getList(status?: string): Promise<any[]> {
    return request.get('/api/vector/machines/list', { params: { status } })
  },

  /**
   * 分页查询机器列表
   */
  getPage(params?: {
    pageNum?: number
    pageSize?: number
    keyword?: string
    status?: string
  }): Promise<any> {
    return request.get('/api/vector/machines/page', { params })
  },

  /**
   * 查询机器详情
   */
  getById(id: string): Promise<any> {
    return request.get(`/api/vector/machines/${id}`)
  },

  /**
   * 获取机器详情（包含最新指标）
   */
  getDetail(id: string): Promise<MachineDetail> {
    return request.get(`/api/vector/machines/${id}/detail`)
  },

  /**
   * 获取机器指标历史
   */
  getMetrics(id: string, minutes?: number): Promise<MachineMetrics> {
    return request.get(`/api/vector/machines/${id}/metrics`, { params: { minutes } })
  },

  /**
   * 获取机器最新指标（轻量级）
   */
  getLatestMetrics(id: string): Promise<MetricsPoint | null> {
    return request.get(`/api/vector/machines/${id}/metrics/latest`)
  },

  /**
   * 添加机器
   */
  add(data: any): Promise<any> {
    return request.post('/api/vector/machines', data)
  },

  /**
   * 更新机器
   */
  update(id: string, data: any): Promise<any> {
    return request.put(`/api/vector/machines/${id}`, data)
  },

  /**
   * 删除机器
   */
  delete(id: string): Promise<void> {
    return request.delete(`/api/vector/machines/${id}`)
  },

  /**
   * 更新机器状态
   */
  updateStatus(id: string, status: string): Promise<void> {
    return request.put(`/api/vector/machines/${id}/status`, null, { params: { status } })
  }
}

// ==================== 机器指标类型定义 ====================

export interface NetworkInterfaceInfo {
  name: string
  bytesSent: number
  bytesRecv: number
  packetsSent: number
  packetsRecv: number
  errin: number
  errout: number
}

export interface MetricsPoint {
  timestamp: string
  cpuUsagePercent: number
  memoryUsagePercent: number
  memoryUsedMb: number
  diskUsagePercent: number
  diskUsedGb: number
  agentMemoryMb: number
  vectorRunning: boolean
  networkInterfaces?: NetworkInterfaceInfo[]
}

export interface MachineMetrics {
  machineId: string
  latest: MetricsPoint | null
  history: MetricsPoint[]
}

export interface MachineDetail {
  id: string
  name: string
  hostname: string
  ipAddress: string
  status: string
  osType: string
  vectorVersion: string
  agentVersion: string
  lastHeartbeat: string
  createdAt: string
  latestMetrics: MetricsPoint | null
}

// ==================== 指标监控 API ====================

export const vectorMetricsApi = {
  /**
   * Agent 上报指标（需要 Token 认证）
   */
  reportMetrics(data: MetricsReportRequest): Promise<void> {
    return request.post('/api/vector/metrics', data)
  },

  /**
   * 查询机器最新指标
   */
  getLatestMetrics(hostId: number): Promise<VectorMetrics> {
    return request.get(`/api/vector/hosts/${hostId}/metrics/latest`)
  },

  /**
   * 查询机器历史指标
   */
  getMetricsHistory(params: MetricsQueryParams): Promise<VectorMetrics[]> {
    return request.get(`/api/vector/hosts/${params.hostId}/metrics`, {
      params: {
        startTime: params.startTime,
        endTime: params.endTime,
        limit: params.limit
      }
    })
  },

  /**
   * 获取组件状态（用于可视化配置流动特效）
   */
  getComponentStatus(hostId: string): Promise<Record<string, string>> {
    return request.get(`/api/vector/agents/component-status/${hostId}`)
  }
}

// ==================== 组件库 API ====================

export interface ConfigComponent {
  id: string
  name: string
  componentType: string
  vectorType: string
  configYaml: string
  visualData?: string
  description?: string
  isTemplate?: boolean
  queryable?: boolean
  displayName?: string
  datasourceId?: string
  createdAt: string
  updatedAt: string
}

export interface ConfigComponentRequest {
  name: string
  componentType: string
  vectorType: string
  configYaml: string
  visualData?: string
  description?: string
  isTemplate?: boolean
  queryable?: boolean
  displayName?: string
  datasourceId?: string
}

export const configComponentApi = {
  getList(keyword?: string, componentType?: string): Promise<ConfigComponent[]> {
    return request.get('/api/vector/components', { params: { keyword, componentType } })
  },

  getById(id: string): Promise<ConfigComponent> {
    return request.get(`/api/vector/components/${id}`)
  },

  create(data: ConfigComponentRequest): Promise<ConfigComponent> {
    return request.post('/api/vector/components', data)
  },

  update(id: string, data: ConfigComponentRequest): Promise<ConfigComponent> {
    return request.put(`/api/vector/components/${id}`, data)
  },

  delete(id: string): Promise<void> {
    return request.delete(`/api/vector/components/${id}`)
  },

  /**
   * 根据可视化配置生成 YAML 预览
   */
  generateYaml(componentType: string, vectorType: string, visualConfig: string): Promise<{ yaml: string }> {
    return request.post('/api/vector/components/generate-yaml', {
      componentType,
      vectorType,
      visualConfig
    })
  },

  /**
   * 获取组件引用数量（排除指定配置）
   * 用于判断组件是否被其他配置引用
   */
  getReferenceCounts(excludeConfigId?: string): Promise<Record<string, number>> {
    return request.get('/api/vector/components/reference-counts', { 
      params: { excludeConfigId } 
    })
  },

  /**
   * 获取指定组件的引用详情
   */
  getReferences(componentId: string): Promise<{
    componentId: string
    componentName: string
    references: Array<{ configId: string; configName: string; nodeName: string }>
    referenceCount: number
  }> {
    return request.get(`/api/vector/components/${componentId}/references`)
  },

  /**
   * 获取可查询的数据源列表（queryable=true 的 Sink 组件）
   */
  getQueryableDataSources(): Promise<ConfigComponent[]> {
    return request.get('/api/vector/components/queryable')
  },

  /**
   * 更新组件的可查询状态
   */
  updateQueryable(id: string, queryable: boolean): Promise<void> {
    return request.put(`/api/vector/components/${id}/queryable`, null, { params: { queryable } })
  },

  /**
   * 测试数据源连接
   */
  testConnection(id: string): Promise<{
    success: boolean
    message: string
    version?: string
    responseTimeMs?: number
  }> {
    return request.post(`/api/datasource/${id}/test-connection`)
  },

  /**
   * 检查表是否存在
   */
  checkTable(id: string): Promise<{
    exists: boolean
    message: string
    tableName?: string
    fields?: Array<{ name: string; type: string; label: string }>
    rowCount?: number
  }> {
    return request.get(`/api/datasource/${id}/check-table`)
  },

  /**
   * 获取数据源中的表列表
   */
  listTables(id: string): Promise<string[]> {
    return request.get(`/api/datasource/${id}/tables`)
  },

  /**
   * 获取推荐的表结构
   */
  getRecommendedSchema(id: string): Promise<TableSchema> {
    return request.get(`/api/datasource/${id}/recommended-schema`)
  },

  /**
   * 预览建表 SQL
   */
  previewCreateTable(id: string, schema: TableSchema): Promise<string> {
    return request.post(`/api/datasource/${id}/preview-create-table`, schema)
  },

  /**
   * 执行建表
   */
  createTable(id: string, schema: TableSchema): Promise<{
    success: boolean
    message: string
    executedSQL?: string
  }> {
    return request.post(`/api/datasource/${id}/create-table`, schema)
  }
}

// 表结构定义
export interface TableSchema {
  tableName: string
  columns: ColumnDefinition[]
  engine?: string
  partitionBy?: string
  orderBy?: string
  primaryKey?: string
  ttl?: string
}

export interface ColumnDefinition {
  name: string
  type: string
  nullable?: boolean
  defaultValue?: string
  comment?: string
}

// ==================== 可视化配置 API ====================

export interface VisualConfig {
  id: string
  name: string
  description?: string
  format: string
  graphData?: string
  content?: string
  nodeCount?: number
  createdAt: string
  updatedAt: string
  createdBy?: string
}

export interface CreateVisualConfigRequest {
  name: string
  description?: string
  format: string
}

export interface UpdateVisualConfigRequest {
  name?: string
  description?: string
  graphData?: string
  content?: string
  nodeCount?: number
}

export interface PreviewVisualConfigResponse {
  content: string
}

export const visualConfigApi = {
  /**
   * 查询可视化配置列表
   */
  getList(keyword?: string): Promise<VisualConfig[]> {
    return request.get('/api/vector/visual-configs', { params: { keyword } })
  },

  /**
   * 查询配置详情
   */
  getById(id: string): Promise<VisualConfig> {
    return request.get(`/api/vector/visual-configs/${id}`)
  },

  /**
   * 创建配置
   */
  create(data: CreateVisualConfigRequest): Promise<VisualConfig> {
    return request.post('/api/vector/visual-configs', data)
  },

  /**
   * 根据 graphData 生成 YAML 预览
   */
  preview(graphData: string): Promise<PreviewVisualConfigResponse> {
    return request.post('/api/vector/visual-configs/preview', { graphData })
  },

  /**
   * 更新配置
   */
  update(id: string, data: UpdateVisualConfigRequest): Promise<VisualConfig> {
    return request.put(`/api/vector/visual-configs/${id}`, data)
  },

  /**
   * 删除配置
   */
  delete(id: string): Promise<void> {
    return request.delete(`/api/vector/visual-configs/${id}`)
  },

  /**
   * 导出配置
   */
  export(id: string): Promise<Blob> {
    return request.get(`/api/vector/visual-configs/${id}/export`, {
      responseType: 'blob'
    })
  },

  /**
   * 校验配置（使用 Vector 命令行校验）
   */
  validate(content: string): Promise<{ valid: boolean; error?: string }> {
    return request.post('/api/vector/visual-configs/validate', { content })
  }
}

// ==================== VRL 执行 API ====================

export interface VrlExecuteRequest {
  logSample: string
  parseMethod: string
  regexPattern?: string
  grokPattern?: string
  customVrl?: string
}

export interface ParsedField {
  name: string
  value: any
  type: string
}

export interface VrlExecuteResponse {
  success: boolean
  error?: string
  result?: Record<string, any>
  fields?: ParsedField[]
  rawOutput?: string
}

export const vrlApi = {
  /**
   * 执行 VRL 表达式解析日志
   */
  execute(data: VrlExecuteRequest): Promise<VrlExecuteResponse> {
    return request.post('/api/vector/vrl/execute', data)
  }
}

// ==================== 导出所有 API ====================

export default {
  host: vectorHostApi,
  config: vectorConfigApi,
  deployment: vectorDeploymentApi,
  metrics: vectorMetricsApi,
  visualConfig: visualConfigApi,
  vrl: vrlApi
}
