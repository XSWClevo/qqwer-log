import request from '@/utils/request'

/**
 * 数据源类型
 */
export interface Datasource {
  id: string
  name: string
  type: string
  host: string
  port: number
  databaseName?: string
  username?: string
  password?: string
  sslEnabled?: boolean
  connectionParams?: string
  description?: string
  status: string
  lastCheckTime?: string
  lastCheckStatus?: string
  lastCheckMessage?: string
  createdAt: string
  updatedAt: string
}

/**
 * 创建数据源请求
 */
export interface CreateDatasourceRequest {
  name: string
  type: string
  host: string
  port: number
  databaseName?: string
  username?: string
  password?: string
  sslEnabled?: boolean
  connectionParams?: string
  description?: string
}

/**
 * 更新数据源请求
 */
export interface UpdateDatasourceRequest {
  name?: string
  host?: string
  port?: number
  databaseName?: string
  username?: string
  password?: string
  sslEnabled?: boolean
  connectionParams?: string
  description?: string
  status?: string
}

/**
 * 数据源测试结果
 */
export interface DatasourceTestResult {
  success: boolean
  message: string
  responseTime?: number
  version?: string
  details?: string
}

/**
 * 分页查询数据源列表
 */
export function listDatasources(params: {
  pageNum?: number
  pageSize?: number
  keyword?: string
  type?: string
  status?: string
}) {
  return request({
    url: '/api/datasources',
    method: 'get',
    params
  })
}

/**
 * 获取所有活跃的数据源
 */
export function listActiveDatasources() {
  return request({
    url: '/api/datasources/active',
    method: 'get'
  })
}

/**
 * 根据类型查询数据源
 */
export function listDatasourcesByType(type: string) {
  return request({
    url: `/api/datasources/by-type/${type}`,
    method: 'get'
  })
}

/**
 * 获取数据源详情
 */
export function getDatasource(id: string) {
  return request({
    url: `/api/datasources/${id}`,
    method: 'get'
  })
}

/**
 * 创建数据源
 */
export function createDatasource(data: CreateDatasourceRequest) {
  return request({
    url: '/api/datasources',
    method: 'post',
    data
  })
}

/**
 * 更新数据源
 */
export function updateDatasource(id: string, data: UpdateDatasourceRequest) {
  return request({
    url: `/api/datasources/${id}`,
    method: 'put',
    data
  })
}

/**
 * 删除数据源
 */
export function deleteDatasource(id: string) {
  return request({
    url: `/api/datasources/${id}`,
    method: 'delete'
  })
}

/**
 * 测试数据源连接
 */
export function testDatasourceConnection(id: string) {
  return request({
    url: `/api/datasources/${id}/test`,
    method: 'post'
  })
}

/**
 * 测试新数据源连接（创建前测试）
 */
export function testNewDatasourceConnection(data: CreateDatasourceRequest) {
  return request({
    url: '/api/datasources/test',
    method: 'post',
    data
  })
}
