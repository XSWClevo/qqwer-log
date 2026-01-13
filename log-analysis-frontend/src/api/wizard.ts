import request from '@/utils/request'

/**
 * 解析日志请求
 */
export interface ParseLogRequest {
  logSample: string
  parseMethod: 'parse_json' | 'parse_syslog' | 'parse_regex' | 'parse_kv' | 'parse_key_value' | 'parse_grok' | 'custom'
  regexPattern?: string
  grokPattern?: string
  customVrl?: string
}

/**
 * 解析后的字段
 */
export interface ParsedField {
  name: string
  value: any
  type: string
  suggestion?: string
  comment?: string
}

/**
 * 类型建议
 */
export interface TypeSuggestion {
  type: string
  reason: string
}

/**
 * 后端返回的字段格式
 */
export interface ParsedFieldDTO {
  name: string
  sampleValue: any
  type: string
  suggestion?: TypeSuggestion | null
}

/**
 * 解析日志响应
 */
export interface ParseLogResponse {
  success: boolean
  error?: string
  format?: string
  fields?: ParsedFieldDTO[]
}

/**
 * 字段定义
 */
export interface FieldDefinition {
  name: string
  type: string
  comment?: string
}

/**
 * 生成 DDL 请求
 */
export interface GenerateDDLRequest {
  datasourceId: string
  tableName: string
  fields: FieldDefinition[]
}

/**
 * 生成 DDL 响应
 */
export interface GenerateDDLResponse {
  ddl: string
  configUsed: Record<string, string>
}

/**
 * 创建表请求
 */
export interface CreateTableRequest {
  datasourceId: string
  ddl: string
  tableName?: string
  vrlScript?: string
  parseMethod?: string
  autoCreateComponents?: boolean
}

/**
 * 创建表响应
 */
export interface CreateTableResponse {
  success: boolean
  error?: string
  tableName?: string
  remapComponentId?: string
  sinkComponentId?: string
}

/**
 * 表信息
 */
export interface TableInfo {
  name: string
  engine?: string
  createTime?: string
}

/**
 * 列信息
 */
export interface ColumnInfo {
  name: string
  type: string
  defaultValue?: string
  comment?: string
}

/**
 * 添加列请求
 */
export interface AddColumnRequest {
  datasourceId: string
  tableName: string
  columnName: string
  columnType: string
  comment?: string
}

/**
 * 解析日志样本
 */
export function parseLog(data: ParseLogRequest) {
  return request({
    url: '/api/wizard/parse-log',
    method: 'post',
    data
  })
}

/**
 * 生成 DDL
 */
export function generateDDL(data: GenerateDDLRequest) {
  return request({
    url: '/api/wizard/generate-ddl',
    method: 'post',
    data
  })
}

/**
 * 创建表
 */
export function createTable(data: CreateTableRequest) {
  return request({
    url: '/api/wizard/create-table',
    method: 'post',
    data
  })
}

/**
 * 查询表列表
 */
export function listTables(datasourceId: string) {
  return request({
    url: '/api/wizard/list-tables',
    method: 'post',
    data: { datasourceId }
  })
}

/**
 * 查询表结构
 */
export function describeTable(datasourceId: string, tableName: string) {
  return request({
    url: '/api/wizard/describe-table',
    method: 'post',
    data: { datasourceId, tableName }
  })
}

/**
 * 添加字段
 */
export function addColumn(data: AddColumnRequest) {
  return request({
    url: '/api/wizard/add-column',
    method: 'post',
    data
  })
}
