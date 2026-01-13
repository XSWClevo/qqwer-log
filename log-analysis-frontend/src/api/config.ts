import request from '@/utils/request'

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
export function getAllConfigs() {
  return request({
    url: '/api/config/settings/list',
    method: 'post'
  })
}

/**
 * 获取指定配置
 */
export function getConfig(key: string) {
  return request({
    url: '/api/config/settings/get',
    method: 'post',
    data: { key }
  })
}

/**
 * 更新配置
 */
export function updateConfig(key: string, data: UpdateConfigRequest) {
  return request({
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
export function batchUpdateConfig(configs: Array<{ key: string; value: string }>) {
  return request({
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
}) {
  return request({
    url: '/api/config/history/list',
    method: 'post',
    data: params
  })
}
