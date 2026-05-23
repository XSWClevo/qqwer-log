import request from '@/utils/request'

export interface AttackLogDataset {
  id: number
  name: string
  datasourceType: string
  datasourceId?: string
  databaseName?: string
  tableName?: string
  indexName?: string
  fieldMapping: Record<string, string>
  capabilities: Record<string, any>
  enabled: boolean
  scanCursorTimestamp?: string
  batchSize?: number
}

export interface AttackDatasetPayload {
  name: string
  datasourceType: string
  datasourceId?: string
  databaseName?: string
  tableName?: string
  indexName?: string
  fieldMapping: Record<string, string>
  capabilities?: Record<string, any>
  enabled?: boolean
  batchSize?: number
}

export interface AttackDetectionRule {
  id: number
  ruleId: string
  name: string
  attackType: string
  attackSubType?: string
  severity: string
  confidence: number
  enabled: boolean
  priority: number
}

export interface AttackClassificationRecord {
  classificationKey: string
  datasourceType: string
  datasourceId?: string
  databaseName?: string
  tableName?: string
  indexName?: string
  logFingerprint: string
  logTimestamp: string
  sourceIp?: string
  hostname?: string
  message?: string
  raw?: string
  attackType: string
  attackSubType?: string
  severity: string
  confidence: number
  ruleId: string
  ruleName: string
  reason?: string
  mitreTactic?: string
  mitreTechnique?: string
  status: string
  classifiedAt: string
}

export interface AttackClassificationQuery {
  startTime?: string
  endTime?: string
  datasourceType?: string
  datasourceId?: string
  databaseName?: string
  tableName?: string
  indexName?: string
  attackType?: string
  attackSubType?: string
  severity?: string
  sourceIp?: string
  hostname?: string
  keyword?: string
  pageNum?: number
  pageSize?: number
}

export function queryAttackDatasets(params?: {
  keyword?: string
  datasourceType?: string
  enabled?: boolean
  pageNum?: number
  pageSize?: number
}) {
  return request({
    url: '/api/attack/datasets',
    method: 'get',
    params
  })
}

export function createAttackDataset(data: AttackDatasetPayload) {
  return request({
    url: '/api/attack/datasets',
    method: 'post',
    data
  })
}

export function updateAttackDataset(id: number, data: Partial<AttackDatasetPayload>) {
  return request({
    url: `/api/attack/datasets/${id}`,
    method: 'put',
    data
  })
}

export function deleteAttackDataset(id: number) {
  return request({
    url: `/api/attack/datasets/${id}`,
    method: 'delete'
  })
}

export function queryAttackRules(params?: {
  keyword?: string
  attackType?: string
  enabled?: boolean
  pageNum?: number
  pageSize?: number
}) {
  return request({
    url: '/api/attack/rules',
    method: 'get',
    params
  })
}

export function queryAttackClassifications(data: AttackClassificationQuery) {
  return request({
    url: '/api/attack/classifications/query',
    method: 'post',
    data
  })
}

export function runAttackClassification(data: {
  datasetIds?: number[]
  startTime?: string
  endTime?: string
  limit?: number
}) {
  return request({
    url: '/api/attack/classifications/run',
    method: 'post',
    data
  })
}
