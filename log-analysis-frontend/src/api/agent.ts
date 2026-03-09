import request from '@/utils/request'
import type { FieldInfo } from '@/api/log'
import { readStoredJwtToken, clearStoredAuthTokens } from '@/utils/jwt'

export interface AgentChatMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface AgentChatRequest {
  message: string
  datasourceId?: string
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

export interface AgentResult {
  type: 'schema' | 'logs' | 'timeseries' | 'text2sql'
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

const resolveApiUrl = (path: string) => {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || ''
  if (!baseUrl) {
    return path
  }
  return `${String(baseUrl).replace(/\/$/, '')}${path}`
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

export function chatWithAgent(data: AgentChatRequest) {
  return request.post('/api/agent/chat', data)
}

export async function streamChatWithAgent(data: AgentChatRequest, handlers: StreamChatHandlers = {}) {
  const token = readStoredJwtToken('accessToken')
  const response = await fetch(resolveApiUrl('/api/agent/chat/stream'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/x-ndjson',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(data)
  })

  if (!response.ok) {
    if (response.status === 401) {
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
      const event = JSON.parse(trimmed) as AgentStreamEvent
      handlers.onEvent?.(event)
      if (event.type === 'done' && event.response) {
        finalResponse = event.response
      }
      if (event.type === 'error') {
        throw new Error(event.message || '流式响应失败')
      }
    }
  }

  const tail = buffer.trim()
  if (tail) {
    const event = JSON.parse(tail) as AgentStreamEvent
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
