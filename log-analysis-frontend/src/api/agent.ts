import request, { refreshAccessToken, resolveApiUrl } from '@/utils/request'
import type { FieldInfo } from '@/api/log'
import { readStoredJwtToken, clearStoredAuthTokens } from '@/utils/jwt'

export interface AgentChatMessage {
  role: 'user' | 'assistant'
  content: string
}

export type AgentPageContext =
  | 'COMPONENT_LIBRARY'
  | 'LOG_PARSER_WIZARD'
  | 'AGENT_CHAT'
  | 'VECTOR_EDITOR'
  | 'UNKNOWN'

export interface AgentChatRequest {
  message: string
  datasourceId?: string
  skillId?: string
  pageContext?: AgentPageContext
  routePath?: string
  surfaceContext?: Record<string, any>
  sessionId?: string
  history?: AgentChatMessage[]
}

export interface AgentToolCall {
  toolCallId?: string
  toolName: string
  toolLabel: string
  status: string
  input?: Record<string, any>
  summary?: string
  durationMs?: number
}

export interface AgentVectorComponentField {
  name: string
  type: string
  sampleValue?: any
  suggestion?: string
  comment?: string
}

export interface AgentVectorDeploymentSummary {
  ready?: boolean
  visualConfigId?: string
  visualConfigName?: string
  nextAction?: string
  requiresHostSelection?: boolean
  warnings?: string[]
}

export interface AgentResult {
  type: 'schema' | 'logs' | 'timeseries' | 'text2sql' | 'vector_component_requirements' | 'vector_component_plan' | 'vector_component_commit'
  success?: boolean
  error?: string
  timeRangeLabel?: string
  schema?: FieldInfo[]
  logs?: Array<Record<string, any>>
  total?: number
  pageNum?: number
  pageSize?: number
  granularity?: string
  series?: Array<Record<string, any>>
  summary?: Record<string, any>
  sql?: string
  queryResultType?: 'metric' | 'category' | 'timeseries' | 'list'
  rawResult?: any
  rows?: Array<Record<string, any>>
  sqlGenerationTime?: number
  sqlExecutionTime?: number
  totalExecutionTime?: number
  planId?: string
  logSample?: string
  datasourceId?: string
  datasourceName?: string
  tableName?: string
  regexPattern?: string
  vrlScript?: string
  fields?: AgentVectorComponentField[]
  ddl?: string
  warnings?: string[]
  sourceType?: string
  sourceConfig?: Record<string, any>
  sourceComponentId?: string
  remapComponentId?: string
  sinkComponentId?: string
  visualConfigId?: string
  visualConfigName?: string
  deployment?: AgentVectorDeploymentSummary
}

export interface AgentChatResponse {
  success: boolean
  intent?: string
  answer?: string
  error?: string
  datasourceId?: string
  sessionId?: string
  datasourceName?: string
  toolCalls?: AgentToolCall[]
  result?: AgentResult
  suggestions?: string[]
}

export interface AgentConversationSummary {
  sessionId: string
  title: string
  preview?: string
  datasourceId?: string
  datasourceName?: string
  datasourceType?: string
  messageCount?: number
  createdAt?: string
  updatedAt?: string
  lastMessageAt?: string
}

export interface AgentConversationEntry {
  id: number
  role: 'user' | 'assistant'
  content: string
  toolCalls?: AgentToolCall[]
  result?: AgentResult
  suggestions?: string[]
  createdAt?: string
}

export interface AgentConversationDetail extends AgentConversationSummary {
  messages: AgentConversationEntry[]
}

export interface AgentEmailRequest {
  sessionId?: string
  conversationTitle?: string
  datasourceName?: string
  content: string
  toolCalls?: AgentToolCall[]
}

export interface AgentEmailResponse {
  recipient: string
  subject: string
  sentAt: string
}

export interface AgentStreamEvent {
  type: 'started' | 'token' | 'tool_started' | 'tool_finished' | 'done' | 'error'
  sessionId?: string
  delta?: string
  message?: string
  toolCall?: AgentToolCall
  response?: AgentChatResponse
}

interface StreamChatHandlers {
  onEvent?: (event: AgentStreamEvent) => void
}

const extractErrorMessage = async (response: Response) => {
  const text = await response.text()
  if (!text) {
    return `请求失败: ${response.status}`
  }
  try {
    const parsed = JSON.parse(text)
    return parsed?.message || parsed?.error || parsed?.data?.message || text
  } catch {
    return text
  }
}

const parseStreamEvent = (line: string) => {
  try {
    return JSON.parse(line) as AgentStreamEvent
  } catch {
    const preview = line.length > 160 ? `${line.slice(0, 160)}...` : line
    throw new Error(`流式响应解析失败，收到非 NDJSON 数据: ${preview}`)
  }
}

export function chatWithAgent(data: AgentChatRequest) {
  return request.post('/api/agent/chat', data)
}

export async function streamChatWithAgent(data: AgentChatRequest, handlers: StreamChatHandlers = {}) {
  const doFetch = (token: string) => fetch(resolveApiUrl('/api/agent/chat/stream'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/x-ndjson',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(data)
  })

  let response = await doFetch(readStoredJwtToken('accessToken'))
  if (response.status === 401) {
    try {
      const token = await refreshAccessToken()
      response = await doFetch(token)
    } catch {
      clearStoredAuthTokens()
    }
  }

  if (!response.ok) {
    if (response.status === 401 || response.status === 440) {
      clearStoredAuthTokens()
    }
    throw new Error(await extractErrorMessage(response))
  }

  if (!response.body) {
    throw new Error('流式响应体为空')
  }

  const decoder = new TextDecoder()
  const reader = response.body.getReader()
  let buffer = ''
  let finalResponse: AgentChatResponse | null = null

  while (true) {
    const { value, done } = await reader.read()
    if (done) {
      break
    }

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() || ''

    for (const line of lines) {
      const trimmed = line.trim()
      if (!trimmed) {
        continue
      }
      const event = parseStreamEvent(trimmed)
      handlers.onEvent?.(event)
      if (event.type === 'done' && event.response) {
        finalResponse = event.response
      }
      if (event.type === 'error') {
        throw new Error(event.message || '流式响应失败')
      }
    }
  }

  buffer += decoder.decode()
  const tail = buffer.trim()
  if (tail) {
    const event = parseStreamEvent(tail)
    handlers.onEvent?.(event)
    if (event.type === 'done' && event.response) {
      finalResponse = event.response
    }
    if (event.type === 'error') {
      throw new Error(event.message || '流式响应失败')
    }
  }

  if (!finalResponse) {
    throw new Error('流式响应未返回最终结果')
  }

  return finalResponse
}

export function listAgentConversations() {
  return request.get('/api/agent/conversations')
}

export function getAgentConversation(sessionId: string) {
  return request.get(`/api/agent/conversations/${sessionId}`)
}

export function deleteAgentConversation(sessionId: string) {
  return request.delete(`/api/agent/conversations/${sessionId}`)
}

export function sendAgentEmail(data: AgentEmailRequest) {
  return request.post('/api/agent/email', data)
}

export function commitVectorComponentPlan(planId: string, sessionId: string) {
  return request.post(`/api/agent/vector-component-plans/${planId}/commit`, { sessionId })
}
