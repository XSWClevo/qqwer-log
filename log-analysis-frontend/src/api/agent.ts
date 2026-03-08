import request from '@/utils/request'
import type { FieldInfo } from '@/api/log'

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

export function chatWithAgent(data: AgentChatRequest) {
  return request.post('/api/agent/chat', data)
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
