import request from '@/utils/request'

interface ApiResult<T> {
  code: number
  message: string
  data: T
}

interface PageResult<T> {
  records: T[]
  total: number
  size?: number
  current?: number
  pages?: number
}

/**
 * 系统配置项
 */
export interface SystemConfig {
  id: string
  configKey: string
  configValue: string
  configType: string
  description?: string
  createdAt: string
  updatedAt: string
}

/**
 * 配置更新请求
 */
export interface UpdateConfigRequest {
  configValue: string
}

/**
 * 配置历史记录
 */
export interface ConfigHistory {
  id: string
  configKey: string
  oldValue: string
  newValue: string
  changedBy: string
  changedAt: string
  remark?: string
}

/**
 * 获取所有配置
 */
export function getAllConfigs(): Promise<ApiResult<SystemConfig[]>> {
  return request<any, ApiResult<SystemConfig[]>>({
    url: '/api/config/settings/list',
    method: 'post'
  })
}

/**
 * 获取指定配置
 */
export function getConfig(key: string): Promise<ApiResult<SystemConfig>> {
  return request<any, ApiResult<SystemConfig>>({
    url: '/api/config/settings/get',
    method: 'post',
    data: { key }
  })
}

/**
 * 更新配置
 */
export function updateConfig(key: string, data: UpdateConfigRequest): Promise<ApiResult<SystemConfig>> {
  return request<any, ApiResult<SystemConfig>>({
    url: '/api/config/settings/update',
    method: 'post',
    data: {
      key,
      ...data
    }
  })
}

/**
 * 批量更新配置
 */
export function batchUpdateConfig(configs: Array<{ key: string; value: string }>): Promise<ApiResult<void>> {
  return request<any, ApiResult<void>>({
    url: '/api/config/settings/batch-update',
    method: 'post',
    data: {
      configs
    }
  })
}

/**
 * 获取配置历史
 */
export function getConfigHistory(params: {
  configKey?: string
  pageNum?: number
  pageSize?: number
}): Promise<ApiResult<PageResult<ConfigHistory>>> {
  return request<any, ApiResult<PageResult<ConfigHistory>>>({
    url: '/api/config/history/list',
    method: 'post',
    data: params
  })
}
