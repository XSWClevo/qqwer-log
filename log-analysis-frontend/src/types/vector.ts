/**
 * Vector 日志收集管理器 - TypeScript 类型定义
 */

// ==================== 机器管理 ====================

export interface VectorHost {
  id: number
  hostname: string
  ipAddress: string
  agentToken: string
  agentVersion?: string
  vectorVersion?: string

  // 状态字段
  status: 'online' | 'offline' | 'error'
  lastHeartbeat?: string

  // 标签和分组
  tags?: string[]
  environment?: 'production' | 'staging' | 'test'

  // 配置版本
  currentConfigVersion?: string
  targetConfigVersion?: string

  // 系统信息
  osType?: string
  osVersion?: string
  cpuCores?: number
  totalMemoryMb?: number

  // 审计字段
  createdBy?: number
  createdAt?: string
  updatedAt?: string
}

export interface RegisterRequest {
  hostname: string
  ipAddress: string
  agentToken: string
  agentVersion?: string
  vectorVersion?: string
  osType?: string
  osVersion?: string
  cpuCores?: number
  totalMemoryMb?: number
}

export interface RegisterResponse {
  hostId: number
  message: string
}

export interface HeartbeatRequest {
  agentUptimeSeconds?: number
  vectorRunning: boolean
  status: 'online' | 'offline' | 'error'
}

export interface HeartbeatResponse {
  hasNewConfig: boolean
  latestConfigVersion?: string
}

export interface TokenResponse {
  token: string
  expireHours: number
}

export interface TokenCheckResponse {
  registered: boolean
}

// ==================== 配置管理 ====================

export interface VectorConfig {
  id: number
  version: string
  name: string
  description?: string

  // 配置内容
  yamlContent: string
  configJson?: any

  // 校验信息
  isValidated: boolean
  validationError?: string

  // 发布状态
  status: 'draft' | 'testing' | 'released' | 'deprecated'
  releasedAt?: string

  // 适用范围
  targetTags?: string[]
  targetEnvironment?: string

  // 审计
  createdBy?: number
  createdAt?: string
  updatedAt?: string
}

export interface VectorConfigTemplate {
  id: number
  name: string
  description?: string
  category: 'source' | 'transform' | 'sink' | 'full'
  yamlContent: string
  configJson?: any
  variables?: ConfigVariable[]
  isPublic: boolean
  isBuiltin: boolean
  createdAt?: string
  updatedAt?: string
}

export interface ConfigVariable {
  name: string
  type: 'string' | 'number' | 'boolean' | 'password'
  default: any
  description: string
}

export interface ConfigResponse {
  version: string
  yamlContent: string
  variables: Record<string, string>
}

// ==================== 部署管理 ====================

export interface VectorDeployment {
  id: number
  hostId: number
  configId: number
  configVersion: string
  deploymentType: 'manual' | 'auto' | 'rollback' | 'canary'
  status: 'pending' | 'deploying' | 'success' | 'failed' | 'rollback'
  startedAt?: string
  completedAt?: string
  errorMessage?: string
  previousConfigVersion?: string
  isRollback: boolean
  deployedBy?: number
  createdAt?: string
}

export interface DeploymentTask {
  configId: number
  hostIds: number[]
  deploymentType?: 'manual' | 'auto' | 'rollback' | 'canary'
}

// ==================== 指标监控 ====================

export interface VectorMetrics {
  id: number
  hostId: number
  collectedAt: string

  // 系统指标
  cpuUsagePercent?: number
  memoryUsagePercent?: number
  memoryUsedMb?: number
  diskUsagePercent?: number
  diskUsedGb?: number

  // Agent 状态
  agentUptimeSeconds?: number
  agentMemoryMb?: number

  // Vector 状态
  vectorRunning?: boolean
  vectorUptimeSeconds?: number
  vectorConfigReloadCount?: number
  vectorErrorCount?: number

  // Vector 吞吐量
  eventsInTotal?: number
  eventsOutTotal?: number
  eventsInRate?: number
  eventsOutRate?: number

  // 额外指标
  extraMetrics?: any
}

export interface MetricsReportRequest {
  // 系统指标
  cpuUsagePercent?: number
  memoryUsagePercent?: number
  memoryUsedMb?: number
  diskUsagePercent?: number
  diskUsedGb?: number

  // Agent 状态
  agentUptimeSeconds?: number
  agentMemoryMb?: number

  // Vector 状态
  vectorRunning?: boolean
  vectorUptimeSeconds?: number
  vectorConfigReloadCount?: number
  vectorErrorCount?: number

  // Vector 吞吐量
  eventsInTotal?: number
  eventsOutTotal?: number
  eventsInRate?: number
  eventsOutRate?: number
}

// ==================== 查询参数 ====================

export interface HostQueryParams {
  keyword?: string
  status?: string
  environment?: string
}

export interface ConfigQueryParams {
  keyword?: string
  status?: string
  category?: string
}

export interface MetricsQueryParams {
  hostId: number
  startTime?: string
  endTime?: string
  limit?: number
}

// ==================== 批量任务 ====================

export interface VectorBatchTask {
  id: number
  taskName: string
  taskType: 'deploy' | 'restart' | 'upgrade'
  targetHostIds?: number[]
  targetTags?: any
  taskParams?: any
  executionMode: 'parallel' | 'sequential' | 'canary'
  batchSize: number
  canaryPercent?: number
  status: 'pending' | 'running' | 'paused' | 'completed' | 'failed'
  progress: number
  totalHosts?: number
  successCount: number
  failedCount: number
  scheduledAt?: string
  startedAt?: string
  completedAt?: string
  createdBy: number
  createdAt?: string
}

// ==================== 操作日志 ====================

export interface VectorOperationLog {
  id: number
  hostId?: number
  configId?: number
  operation: 'deploy' | 'rollback' | 'restart' | 'start' | 'stop' | 'reload'
  operationDetail?: string
  status: 'pending' | 'success' | 'failed'
  errorMessage?: string
  startedAt: string
  completedAt?: string
  durationMs?: number
  executedBy: number
  ipAddress?: string
}

// ==================== Agent 日志 ====================

export interface VectorAgentLog {
  id: number
  hostId: number
  logLevel: 'DEBUG' | 'INFO' | 'WARN' | 'ERROR' | 'FATAL'
  message: string
  source?: 'agent' | 'vector' | 'system'
  loggedAt: string
  context?: any
}
