<template>
  <AppLayout>
    <div class="agent-page">
      <section class="hero-shell">
        <div class="hero-copy">
          <div class="hero-eyebrow">Smart Agent Workspace</div>
          <h1>智能助手</h1>
          <p>
            让日志查询从“先想筛选条件”变成“先说问题”。当前页面支持历史对话保存、会话切换和删除；如果数据源是
            ClickHouse，还会优先走自然语言统计查询和图表结果展示。
          </p>
        </div>

        <div class="hero-metrics">
          <div class="metric-card">
            <span class="metric-label">历史会话</span>
            <strong>{{ conversations.length }}</strong>
            <small>按当前登录用户保存</small>
          </div>
          <div class="metric-card">
            <span class="metric-label">当前数据源</span>
            <strong>{{ selectedDatasource?.name || '未选择' }}</strong>
            <small>{{ selectedDatasource?.vectorType || '请选择可查询数据源' }}</small>
          </div>
          <div class="metric-card">
            <span class="metric-label">当前状态</span>
            <strong>{{ currentConversationState }}</strong>
            <small>{{ currentConversationSubtitle }}</small>
          </div>
        </div>
      </section>

      <div class="workspace-grid">
        <aside class="left-rail">
          <el-card shadow="never" class="panel-card datasource-card">
            <template #header>
              <div class="panel-header">
                <div class="panel-title">
                  <el-icon><Connection /></el-icon>
                  <span>数据源</span>
                </div>
              </div>
            </template>

            <el-select
              v-model="selectedDatasourceId"
              class="full-width"
              filterable
              placeholder="选择可查询数据源"
              :loading="datasourceLoading"
            >
              <el-option
                v-for="item in datasources"
                :key="item.id"
                :label="itemLabel(item)"
                :value="item.id"
              />
            </el-select>

            <div v-if="selectedDatasource" class="datasource-summary">
              <div class="summary-row">
                <span>组件</span>
                <strong>{{ selectedDatasource.name }}</strong>
              </div>
              <div class="summary-row">
                <span>类型</span>
                <el-tag size="small" effect="plain">{{ selectedDatasource.vectorType }}</el-tag>
              </div>
              <div v-if="selectedTableName" class="summary-row">
                <span>表 / 索引</span>
                <strong>{{ selectedTableName }}</strong>
              </div>
            </div>
          </el-card>

          <el-card shadow="never" class="panel-card history-card">
            <template #header>
              <div class="panel-header">
                <div class="panel-title">
                  <el-icon><Document /></el-icon>
                  <span>历史对话</span>
                </div>

                <div class="panel-actions">
                  <el-button text @click="refreshConversationList" :loading="historyLoading">
                    <el-icon><RefreshRight /></el-icon>
                  </el-button>
                  <el-button type="primary" plain @click="startNewConversation()">
                    <el-icon><Plus /></el-icon>
                    新建对话
                  </el-button>
                </div>
              </div>
            </template>

            <div class="history-body">
              <el-skeleton :loading="historyLoading" animated :rows="6">
                <template #template>
                  <div class="history-skeleton">
                    <div class="history-skeleton-item" v-for="index in 4" :key="index" />
                  </div>
                </template>

                <template #default>
                  <div class="draft-card" :class="{ active: !selectedConversationId }" @click="startNewConversation()">
                    <div class="draft-card-title">
                      <span>新对话</span>
                      <el-tag size="small" type="success" effect="plain">草稿</el-tag>
                    </div>
                    <p>保留当前数据源，开始一段新的上下文，不覆盖历史会话。</p>
                  </div>

                  <div v-if="conversations.length" class="conversation-list">
                    <div
                      v-for="conversation in conversations"
                      :key="conversation.sessionId"
                      class="conversation-item"
                      :class="{ active: conversation.sessionId === selectedConversationId }"
                      @click="openConversation(conversation)"
                    >
                      <div class="conversation-main">
                        <div class="conversation-title-row">
                          <strong>{{ formatConversationLabel(conversation) }}</strong>
                          <el-tag
                            v-if="conversation.datasourceType"
                            size="small"
                            effect="plain"
                            class="conversation-type"
                          >
                            {{ conversation.datasourceType }}
                          </el-tag>
                        </div>
                        <div class="conversation-meta">
                          <span>{{ truncateText(conversation.datasourceName || '未绑定数据源', 15) }}</span>
                          <span>{{ formatConversationTime(conversation.lastMessageAt || conversation.updatedAt) }}</span>
                        </div>
                      </div>

                      <el-button
                        text
                        class="delete-btn"
                        :loading="deletingSessionId === conversation.sessionId"
                        @click.stop="handleDeleteConversation(conversation)"
                      >
                        <el-icon><Delete /></el-icon>
                      </el-button>
                    </div>
                  </div>

                  <el-empty
                    v-else
                    description="还没有保存过智能助手会话"
                    :image-size="88"
                  />
                </template>
              </el-skeleton>
            </div>
          </el-card>

          <el-card shadow="never" class="panel-card">
            <template #header>
              <div class="panel-header">
                <div class="panel-title">
                  <el-icon><MagicStick /></el-icon>
                  <span>快速提问</span>
                </div>
              </div>
            </template>

            <div class="prompt-grid">
              <button
                v-for="prompt in quickPrompts"
                :key="prompt"
                type="button"
                class="prompt-chip"
                @click="usePrompt(prompt)"
              >
                {{ prompt }}
              </button>
            </div>
          </el-card>

          <el-card shadow="never" class="panel-card">
            <template #header>
              <div class="panel-header">
                <div class="panel-title">
                  <el-icon><InfoFilled /></el-icon>
                  <span>使用说明</span>
                </div>
              </div>
            </template>

            <ul class="usage-list">
              <li>历史记录由后端保存，刷新页面后仍可继续之前的会话。</li>
              <li>切换数据源时会自动开始新对话，避免不同数据源上下文串联。</li>
              <li>ClickHouse 数据源下，统计类问题会优先走自然语言统计查询。</li>
            </ul>
          </el-card>
        </aside>

        <section class="chat-panel">
          <el-card shadow="never" class="chat-card">
            <div class="chat-toolbar">
              <div class="chat-toolbar-main">
                <div class="chat-eyebrow">当前会话</div>
                <h2>{{ currentConversationTitle }}</h2>
                <p class="chat-toolbar-subtitle">{{ currentConversationSubtitle }}</p>
                <div class="toolbar-tags">
                  <el-tag size="small" type="success" effect="dark">
                    {{ currentConversationState }}
                  </el-tag>
                  <el-tag v-if="selectedDatasource" size="small" effect="plain">
                    {{ selectedDatasource.name }}
                  </el-tag>
                  <el-tag v-if="selectedDatasource?.vectorType" size="small" effect="plain">
                    {{ selectedDatasource.vectorType }}
                  </el-tag>
                  <el-tag v-if="selectedTableName" size="small" effect="plain">
                    {{ selectedTableName }}
                  </el-tag>
                </div>
              </div>

              <div class="chat-toolbar-actions">
                <el-button plain @click="startNewConversation()">
                  <el-icon><Plus /></el-icon>
                  新建对话
                </el-button>
                <el-button
                  v-if="selectedConversationId"
                  plain
                  type="danger"
                  :loading="deletingSessionId === selectedConversationId"
                  @click="handleDeleteConversation(selectedConversationId)"
                >
                  <el-icon><Delete /></el-icon>
                  删除历史
                </el-button>
              </div>
            </div>

            <div
              v-loading="conversationLoading"
              ref="messageContainerRef"
              class="message-list"
              @scroll="handleMessageScroll"
            >
              <div v-for="entry in messages" :key="entry.id" class="message-row" :class="entry.role">
                <div class="message-avatar">
                  <el-icon v-if="entry.role === 'assistant'"><Cpu /></el-icon>
                  <el-icon v-else><User /></el-icon>
                </div>

                <div class="message-stack">
                  <div class="message-bubble" :class="[entry.role, { system: entry.system }]">
                    <div class="message-role">
                      {{ entry.role === 'assistant' ? '日志助手' : '你' }}
                    </div>

                    <div v-if="entry.loading" class="loading-state">
                      <el-icon class="is-loading"><Loading /></el-icon>
                      <span>正在分析请求并调用工具...</span>
                    </div>
                    <div
                      v-else-if="entry.role === 'assistant'"
                      class="message-text markdown-body"
                      v-html="renderMarkdown(entry.content)"
                    />
                    <div v-else class="message-text plain-text">{{ entry.content }}</div>
                  </div>

                  <div v-if="entry.toolCalls?.length" class="tool-call-list">
                    <div v-for="tool in entry.toolCalls" :key="`${entry.id}-${tool.toolName}`" class="tool-call-card">
                      <div class="tool-call-header">
                        <strong>{{ tool.toolLabel }}</strong>
                        <el-tag size="small" type="success">{{ tool.status }}</el-tag>
                      </div>
                      <div class="tool-call-summary">{{ tool.summary }}</div>
                      <div class="tool-call-meta">
                        <span>{{ tool.toolName }}</span>
                        <span v-if="tool.durationMs != null">{{ tool.durationMs }} ms</span>
                      </div>
                    </div>
                  </div>

                  <div v-if="entry.role === 'assistant' && !entry.loading && entry.content" class="message-actions">
                    <el-button
                      size="small"
                      plain
                      :loading="emailingEntryId === entry.id"
                      @click="handleSendEmail(entry)"
                    >
                      <el-icon><Message /></el-icon>
                      发送到我的邮箱
                    </el-button>
                  </div>

                  <div v-if="entry.result && !entry.loading" class="result-panel">
                    <template v-if="entry.result.type === 'schema'">
                      <div class="result-header">
                        <h3>字段结构</h3>
                        <div class="summary-tags">
                          <el-tag v-if="entry.result.summary?.fieldCount">字段 {{ entry.result.summary.fieldCount }}</el-tag>
                          <el-tag v-if="entry.result.summary?.timestampFields?.length" type="success">
                            时间字段 {{ entry.result.summary.timestampFields?.join('、') }}
                          </el-tag>
                        </div>
                      </div>

                      <el-table :data="entry.result.schema || []" border stripe max-height="360">
                        <el-table-column prop="name" label="字段名" min-width="180" />
                        <el-table-column prop="type" label="类型" width="160" />
                        <el-table-column prop="label" label="标签" min-width="160" />
                        <el-table-column label="能力" min-width="180">
                          <template #default="{ row }">
                            <div class="capability-tags">
                              <el-tag v-if="row.isTimestamp" size="small" type="success">时间</el-tag>
                              <el-tag v-if="row.isStatsDimension" size="small" type="warning">维度</el-tag>
                              <el-tag v-if="row.isContentField" size="small" type="info">内容</el-tag>
                            </div>
                          </template>
                        </el-table-column>
                      </el-table>
                    </template>

                    <template v-else-if="entry.result.type === 'logs'">
                      <div class="result-header">
                        <h3>日志列表</h3>
                        <div class="summary-tags">
                          <el-tag>{{ entry.result.timeRangeLabel || '时间范围' }}</el-tag>
                          <el-tag type="success">总数 {{ entry.result.total || 0 }}</el-tag>
                          <el-tag v-if="entry.result.summary?.keyword" type="warning">
                            关键词 {{ entry.result.summary.keyword }}
                          </el-tag>
                        </div>
                      </div>

                      <el-table :data="entry.result.logs || []" border stripe max-height="420">
                        <el-table-column prop="timestamp" label="时间" width="180" />
                        <el-table-column prop="severity" label="级别" width="100" />
                        <el-table-column prop="hostname" label="主机" width="150" />
                        <el-table-column prop="appname" label="应用" width="140" />
                        <el-table-column prop="message" label="消息" min-width="420">
                          <template #default="{ row }">
                            <span class="log-message">{{ row.message || row.raw || '-' }}</span>
                          </template>
                        </el-table-column>
                      </el-table>
                    </template>

                    <template v-else-if="entry.result.type === 'timeseries'">
                      <div class="result-header">
                        <h3>日志趋势</h3>
                        <div class="summary-tags">
                          <el-tag>{{ entry.result.timeRangeLabel || '时间范围' }}</el-tag>
                          <el-tag type="primary">{{ entry.result.granularity }}</el-tag>
                          <el-tag v-if="entry.result.summary?.totalCount != null" type="success">
                            总量 {{ entry.result.summary.totalCount }}
                          </el-tag>
                          <el-tag v-if="entry.result.summary?.peakTimestamp" type="warning">
                            峰值 {{ entry.result.summary.peakCount }} @ {{ entry.result.summary.peakTimestamp }}
                          </el-tag>
                        </div>
                      </div>

                      <TimeSeriesChart :data="entry.result.series || []" />
                    </template>

                    <template v-else-if="entry.result.type === 'text2sql'">
                      <div class="result-header">
                        <h3>自然语言统计查询</h3>
                        <div class="summary-tags">
                          <el-tag type="success">{{ resolveText2SqlResultType(entry.result) }}</el-tag>
                          <el-tag v-if="entry.result.summary?.tableName">表 {{ entry.result.summary.tableName }}</el-tag>
                          <el-tag v-if="entry.result.total != null">返回 {{ entry.result.total }} 行</el-tag>
                          <el-tag v-if="entry.result.totalExecutionTime != null" type="warning">
                            总耗时 {{ entry.result.totalExecutionTime.toFixed(2) }}s
                          </el-tag>
                        </div>
                      </div>

                      <AiQueryResultCard
                        v-if="entry.result.rawResult !== undefined && entry.result.rawResult !== null"
                        :result="entry.result.rawResult"
                        :sql="entry.result.sql"
                        :execution-time="entry.result.totalExecutionTime"
                        :result-type="resolveText2SqlResultType(entry.result)"
                      />

                      <el-table
                        v-if="entry.result.rows?.length"
                        :data="entry.result.rows"
                        border
                        stripe
                        max-height="420"
                        class="sql-query-table"
                      >
                        <el-table-column
                          v-for="column in getText2SqlColumns(entry.result.rows)"
                          :key="column.prop"
                          :prop="column.prop"
                          :label="column.label"
                          :min-width="column.minWidth"
                        >
                          <template #default="{ row }">
                            <span>{{ formatText2SqlCell(row[column.prop], column.isTime, column.isNumber) }}</span>
                          </template>
                        </el-table-column>
                      </el-table>
                    </template>
                  </div>

                  <div v-if="entry.suggestions?.length && !entry.loading" class="suggestion-list">
                    <el-button
                      v-for="suggestion in entry.suggestions"
                      :key="`${entry.id}-${suggestion}`"
                      size="small"
                      plain
                      @click="handleSend(suggestion)"
                    >
                      {{ suggestion }}
                    </el-button>
                  </div>
                </div>
              </div>
            </div>

            <div class="composer">
              <el-input
                ref="composerInputRef"
                v-model="draft"
                type="textarea"
                :autosize="{ minRows: 3, maxRows: 6 }"
                resize="none"
                placeholder="例如：最近1小时错误日志；查看字段结构；按 severity 统计最近24小时数量"
                @keydown.enter.exact.prevent="handleSend()"
              />

              <div class="composer-footer">
                <div class="composer-hint">
                  <span v-if="selectedDatasource">
                    当前数据源：{{ selectedDatasource.name }}
                  </span>
                  <span v-else>
                    请先选择一个可查询数据源
                  </span>
                </div>

                <div class="composer-actions">
                  <div class="streaming-toggle">
                    <span>流式返回</span>
                    <el-switch
                      v-model="streamingEnabled"
                      size="small"
                      inline-prompt
                      active-text="开"
                      inactive-text="关"
                      :disabled="sending"
                    />
                  </div>
                  <el-button plain @click="draft = ''">清空</el-button>
                  <el-button type="primary" :loading="sending" @click="handleSend()">
                    <el-icon><Promotion /></el-icon>
                    发送
                  </el-button>
                </div>
              </div>
            </div>
          </el-card>
        </section>
      </div>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Connection,
  Cpu,
  Delete,
  Document,
  InfoFilled,
  Loading,
  MagicStick,
  Message,
  Plus,
  Promotion,
  RefreshRight,
  User
} from '@element-plus/icons-vue'
import * as yaml from 'js-yaml'
import AppLayout from '@/components/layout/AppLayout.vue'
import AiQueryResultCard from '@/components/ai-query-result/AiQueryResultCard.vue'
import TimeSeriesChart from '@/components/ai-query-result/TimeSeriesChart.vue'
import {
  chatWithAgent,
  deleteAgentConversation,
  getAgentConversation,
  listAgentConversations,
  sendAgentEmail,
  streamChatWithAgent,
  type AgentChatResponse,
  type AgentConversationDetail,
  type AgentConversationEntry,
  type AgentConversationSummary,
  type AgentEmailResponse,
  type AgentResult,
  type AgentStreamEvent,
  type AgentToolCall
} from '@/api/agent'
import { configComponentApi, type ConfigComponent } from '@/api/vector'

interface ChatEntry {
  id: string
  role: 'user' | 'assistant'
  content: string
  loading?: boolean
  system?: boolean
  toolCalls?: AgentToolCall[]
  result?: AgentResult
  suggestions?: string[]
}

interface ApiResult<T> {
  code?: number
  message?: string
  data?: T
}

const quickPrompts = [
  '查看字段结构',
  '最近1小时错误日志',
  '搜索包含 "timeout" 的日志',
  '看最近24小时日志趋势',
  '最近1天的数据有多少条',
  '按 severity 统计最近24小时数量'
]
const STREAMING_PREFERENCE_KEY = 'agent-streaming-enabled'

const readStreamingPreference = () => {
  if (typeof window === 'undefined') {
    return true
  }
  const stored = window.localStorage.getItem(STREAMING_PREFERENCE_KEY)
  return stored == null ? true : stored !== 'false'
}

const datasourceLoading = ref(false)
const historyLoading = ref(false)
const conversationLoading = ref(false)
const sending = ref(false)
const deletingSessionId = ref('')
const emailingEntryId = ref('')

const datasources = ref<ConfigComponent[]>([])
const conversations = ref<AgentConversationSummary[]>([])
const selectedDatasourceId = ref('')
const selectedConversationId = ref('')
const sessionId = ref('')
const draft = ref('')
const streamingEnabled = ref(readStreamingPreference())
const messageContainerRef = ref<HTMLElement>()
const composerInputRef = ref<any>()
const suppressDatasourceReset = ref(false)
const autoScrollEnabled = ref(true)
const autoScrollThreshold = 80

const createSessionId = () => `agent-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`

const buildWelcomeEntry = (): ChatEntry => ({
  id: `assistant-welcome-${Date.now()}`,
  role: 'assistant',
  system: true,
  content: '我现在能帮你读取字段结构、查询日志列表、查看日志趋势；如果当前数据源是 ClickHouse，还可以做自然语言统计查询并生成 SQL。直接提问即可。',
  suggestions: quickPrompts
})

const messages = ref<ChatEntry[]>([buildWelcomeEntry()])

const selectedDatasource = computed(() =>
  datasources.value.find(item => item.id === selectedDatasourceId.value) || null
)

const activeConversation = computed(() =>
  conversations.value.find(item => item.sessionId === selectedConversationId.value) || null
)

const currentConversationTitle = computed(() => {
  if (activeConversation.value?.title) {
    return activeConversation.value.title
  }
  const firstUserMessage = messages.value.find(item => item.role === 'user' && item.content.trim())
  return firstUserMessage?.content || '新对话'
})

const currentConversationState = computed(() => selectedConversationId.value ? '已保存' : '草稿')

const currentConversationSubtitle = computed(() => {
  if (activeConversation.value?.lastMessageAt || activeConversation.value?.updatedAt) {
    return formatConversationTime(activeConversation.value.lastMessageAt || activeConversation.value.updatedAt)
  }
  return '开始提问后会自动保存'
})

const selectedTableName = computed(() => getTableName(selectedDatasource.value?.configYaml))

const unwrapResult = <T>(response: ApiResult<T> | T) => {
  if (response && typeof response === 'object' && 'data' in (response as ApiResult<T>)) {
    return (response as ApiResult<T>).data as T
  }
  return response as T
}

const itemLabel = (item: ConfigComponent) => {
  const tableName = getTableName(item.configYaml)
  return tableName ? `${item.name} (${tableName})` : item.name
}

const getTableName = (configYaml?: string) => {
  if (!configYaml) return ''
  try {
    const parsed = yaml.load(configYaml) as Record<string, any>
    return parsed?.table || parsed?.index || ''
  } catch {
    return ''
  }
}

const resolveText2SqlResultType = (result?: AgentResult): 'metric' | 'category' | 'timeseries' | 'list' => {
  return result?.queryResultType || 'list'
}

const formatColumnLabel = (field: string) => {
  return field
    .split('_')
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ')
}

const getText2SqlColumns = (rows?: Array<Record<string, any>>) => {
  if (!rows?.length) {
    return []
  }

  const firstRow = rows[0]!
  return Object.keys(firstRow).map((key) => ({
    prop: key,
    label: formatColumnLabel(key),
    minWidth: /message|raw|sql/i.test(key) ? 280 : 140,
    isTime: /time|date|timestamp/i.test(key),
    isNumber: typeof firstRow[key] === 'number'
  }))
}

const formatText2SqlCell = (value: any, isTime: boolean, isNumber: boolean) => {
  if (value == null) {
    return '-'
  }

  if (isTime) {
    const date = new Date(value)
    if (!Number.isNaN(date.getTime())) {
      return date.toLocaleString('zh-CN')
    }
  }

  if (isNumber && typeof value === 'number') {
    return value.toLocaleString()
  }

  return String(value)
}

const formatConversationTime = (value?: string) => {
  if (!value) {
    return '刚刚'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  const now = new Date()
  const sameDay = now.toDateString() === date.toDateString()
  if (sameDay) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }

  if (now.getFullYear() === date.getFullYear()) {
    return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
  }

  return date.toLocaleString('zh-CN')
}

const truncateText = (value?: string, maxLength = 15) => {
  const normalized = (value || '').trim()
  if (!normalized) {
    return ''
  }
  return normalized.length > maxLength ? `${normalized.slice(0, maxLength)}...` : normalized
}

const formatConversationLabel = (conversation: AgentConversationSummary) => {
  return truncateText(conversation.title || conversation.preview || '未命名对话', 15) || '未命名对话'
}

const escapeHtml = (value: string) => {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

const sanitizeUrl = (value: string) => {
  const normalized = value.trim()
  return /^(https?:\/\/|mailto:)/i.test(normalized) ? escapeHtml(normalized) : '#'
}

/**
 * 智能助手会返回 Markdown 文本，但当前项目没有现成渲染器。
 * 这里做一层轻量、安全的前端转换：先转义 HTML，再按常用 Markdown 语法生成展示结构。
 */
const renderInlineMarkdown = (value: string) => {
  const placeholders: string[] = []
  const savePlaceholder = (html: string) => {
    const token = `@@MD_${placeholders.length}@@`
    placeholders.push(html)
    return token
  }

  let rendered = value

  rendered = rendered.replace(/`([^`\n]+)`/g, (_, code: string) => {
    return savePlaceholder(`<code>${escapeHtml(code)}</code>`)
  })

  rendered = rendered.replace(/\[([^\]]+)\]\(([^)]+)\)/g, (_, label: string, url: string) => {
    return savePlaceholder(
      `<a href="${sanitizeUrl(url)}" target="_blank" rel="noreferrer noopener">${escapeHtml(label)}</a>`
    )
  })

  rendered = escapeHtml(rendered)
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/(^|[^*])\*([^*\n]+)\*/g, '$1<em>$2</em>')

  placeholders.forEach((html, index) => {
    rendered = rendered.replace(`@@MD_${index}@@`, html)
  })

  return rendered
}

const renderMarkdown = (value?: string) => {
  if (!value) {
    return ''
  }

  const codeBlocks: string[] = []
  const normalized = value.replace(/\r\n/g, '\n')
  const withCodePlaceholders = normalized.replace(/```([\w-]+)?\n?([\s\S]*?)```/g, (_, language: string, code: string) => {
    const token = `@@BLOCK_${codeBlocks.length}@@`
    const languageLabel = language ? `<div class="code-language">${escapeHtml(language)}</div>` : ''
    codeBlocks.push(
      `<pre><code>${languageLabel}${escapeHtml(code.trim())}</code></pre>`
    )
    return token
  })

  const lines = withCodePlaceholders.split('\n')
  const fragments: string[] = []
  const paragraphBuffer: string[] = []
  let listType: 'ul' | 'ol' | null = null
  let listItems: string[] = []

  const flushParagraph = () => {
    if (!paragraphBuffer.length) {
      return
    }
    fragments.push(`<p>${paragraphBuffer.map(line => renderInlineMarkdown(line)).join('<br>')}</p>`)
    paragraphBuffer.length = 0
  }

  const flushList = () => {
    if (!listType || !listItems.length) {
      listType = null
      listItems = []
      return
    }
    fragments.push(`<${listType}>${listItems.join('')}</${listType}>`)
    listType = null
    listItems = []
  }

  lines.forEach((rawLine) => {
    const line = rawLine.trimEnd()
    const trimmed = line.trim()

    if (!trimmed) {
      flushParagraph()
      flushList()
      return
    }

    const blockMatch = trimmed.match(/^@@BLOCK_(\d+)@@$/)
    if (blockMatch) {
      flushParagraph()
      flushList()
      fragments.push(codeBlocks[Number(blockMatch[1])] || '')
      return
    }

    const headingMatch = trimmed.match(/^(#{1,6})\s+(.*)$/)
    if (headingMatch) {
      flushParagraph()
      flushList()
      const headingMarks = headingMatch[1] ?? '#'
      const headingContent = headingMatch[2] ?? ''
      const level = Math.min(headingMarks.length, 6)
      fragments.push(`<h${level}>${renderInlineMarkdown(headingContent)}</h${level}>`)
      return
    }

    const quoteMatch = trimmed.match(/^>\s?(.*)$/)
    if (quoteMatch) {
      flushParagraph()
      flushList()
      fragments.push(`<blockquote>${renderInlineMarkdown(quoteMatch[1] ?? '')}</blockquote>`)
      return
    }

    const unorderedMatch = trimmed.match(/^[-*+]\s+(.*)$/)
    if (unorderedMatch) {
      flushParagraph()
      if (listType !== 'ul') {
        flushList()
        listType = 'ul'
      }
      listItems.push(`<li>${renderInlineMarkdown(unorderedMatch[1] ?? '')}</li>`)
      return
    }

    const orderedMatch = trimmed.match(/^\d+\.\s+(.*)$/)
    if (orderedMatch) {
      flushParagraph()
      if (listType !== 'ol') {
        flushList()
        listType = 'ol'
      }
      listItems.push(`<li>${renderInlineMarkdown(orderedMatch[1] ?? '')}</li>`)
      return
    }

    if (listType) {
      flushList()
    }
    paragraphBuffer.push(trimmed)
  })

  flushParagraph()
  flushList()

  return fragments.join('')
}

const buildAssistantEntry = (response?: AgentChatResponse): ChatEntry => ({
  id: `assistant-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
  role: 'assistant',
  loading: false,
  content: response?.success ? (response.answer || '已完成处理') : (response?.error || '处理失败'),
  toolCalls: response?.toolCalls || [],
  result: response?.result,
  suggestions: response?.suggestions || []
})

const upsertStreamingToolCall = (entry: ChatEntry, incoming?: AgentToolCall) => {
  if (!incoming) {
    return
  }
  const list = [...(entry.toolCalls || [])]
  const index = list.findIndex((item) => {
    if (incoming.toolCallId && item.toolCallId) {
      return incoming.toolCallId === item.toolCallId
    }
    return item.toolName === incoming.toolName && item.status === 'running'
  })

  if (index >= 0) {
    list[index] = { ...list[index], ...incoming }
  } else {
    list.push({ ...incoming })
  }
  entry.toolCalls = list
}

const applyStreamEvent = (entry: ChatEntry, event: AgentStreamEvent) => {
  switch (event.type) {
    case 'started':
      if (event.sessionId) {
        sessionId.value = event.sessionId
        selectedConversationId.value = event.sessionId
      }
      break
    case 'token':
      if (event.delta) {
        entry.loading = false
        entry.content += event.delta
      }
      break
    case 'tool_started':
    case 'tool_finished':
      upsertStreamingToolCall(entry, event.toolCall)
      break
    case 'done':
      if (event.response?.sessionId) {
        sessionId.value = event.response.sessionId
        selectedConversationId.value = event.response.sessionId
      }
      Object.assign(entry, buildAssistantEntry(event.response), { id: entry.id })
      break
    case 'error':
      entry.loading = false
      entry.content = entry.content
        ? `${entry.content}\n\n${event.message || '流式响应失败'}`
        : (event.message || '流式响应失败')
      break
  }
  void scrollToBottom()
}

const mapConversationMessages = (entries?: AgentConversationEntry[]): ChatEntry[] => {
  if (!entries?.length) {
    return [buildWelcomeEntry()]
  }

  return entries.map((entry) => ({
    id: `history-${entry.id}`,
    role: entry.role,
    content: entry.content || '',
    toolCalls: entry.toolCalls || [],
    result: entry.result,
    suggestions: entry.suggestions || []
  }))
}

const scrollToBottom = async () => {
  await nextTick()
  const container = messageContainerRef.value
  if (!container) {
    return
  }
  if (autoScrollEnabled.value) {
    container.scrollTop = container.scrollHeight
  }
}

const forceScrollToBottom = async () => {
  await nextTick()
  const container = messageContainerRef.value
  if (!container) {
    return
  }
  container.scrollTop = container.scrollHeight
}

const isNearBottom = () => {
  const container = messageContainerRef.value
  if (!container) {
    return true
  }
  const distance = container.scrollHeight - container.scrollTop - container.clientHeight
  return distance <= autoScrollThreshold
}

const handleMessageScroll = () => {
  autoScrollEnabled.value = isNearBottom()
}

const focusComposerInput = async () => {
  await nextTick()
  if (composerInputRef.value?.focus) {
    composerInputRef.value.focus()
  }
}

const loadDatasources = async () => {
  datasourceLoading.value = true
  try {
    const response = await configComponentApi.getQueryableDataSources()
    const data = Array.isArray((response as any)?.data)
      ? (response as any).data
      : Array.isArray(response)
        ? response
        : []

    datasources.value = data
    if (!selectedDatasourceId.value && datasources.value[0]) {
      selectedDatasourceId.value = datasources.value[0].id
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '加载可查询数据源失败')
  } finally {
    datasourceLoading.value = false
  }
}

const loadConversationList = async () => {
  historyLoading.value = true
  try {
    const response = unwrapResult<AgentConversationSummary[]>(
      await listAgentConversations() as ApiResult<AgentConversationSummary[]>
    )
    conversations.value = Array.isArray(response) ? response : []
  } catch (error: any) {
    conversations.value = []
    ElMessage.error(error?.message || '加载历史对话失败')
  } finally {
    historyLoading.value = false
  }
}

const refreshConversationList = async () => {
  await loadConversationList()
}

const startNewConversation = (showMessage = false) => {
  selectedConversationId.value = ''
  sessionId.value = createSessionId()
  draft.value = ''
  messages.value = [buildWelcomeEntry()]
  if (showMessage) {
    ElMessage.success('已开始新对话')
  }
  void forceScrollToBottom()
  void focusComposerInput()
}

const usePrompt = (prompt: string) => {
  draft.value = prompt
}

const openConversation = async (conversation: AgentConversationSummary) => {
  if (!conversation?.sessionId || conversationLoading.value) {
    return
  }
  if (conversation.sessionId === selectedConversationId.value) {
    return
  }

  conversationLoading.value = true
  suppressDatasourceReset.value = true

  try {
    const detail = unwrapResult<AgentConversationDetail>(
      await getAgentConversation(conversation.sessionId) as ApiResult<AgentConversationDetail>
    )

    selectedConversationId.value = detail.sessionId
    sessionId.value = detail.sessionId
    if (detail.datasourceId) {
      selectedDatasourceId.value = detail.datasourceId
    }
    messages.value = mapConversationMessages(detail.messages)
  } catch (error: any) {
    ElMessage.error(error?.message || '加载历史对话失败')
  } finally {
    await nextTick()
    suppressDatasourceReset.value = false
    conversationLoading.value = false
    await forceScrollToBottom()
    await focusComposerInput()
  }
}

const handleDeleteConversation = async (conversation: AgentConversationSummary | string) => {
  const targetSessionId = typeof conversation === 'string' ? conversation : conversation.sessionId
  if (!targetSessionId) {
    return
  }

  try {
    await ElMessageBox.confirm('删除后不可恢复，是否继续？', '删除历史对话', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

  deletingSessionId.value = targetSessionId
  try {
    await deleteAgentConversation(targetSessionId)
    conversations.value = conversations.value.filter(item => item.sessionId !== targetSessionId)

    if (selectedConversationId.value === targetSessionId) {
      startNewConversation()
    }

    ElMessage.success('历史对话已删除')
  } catch (error: any) {
    ElMessage.error(error?.message || '删除历史对话失败')
  } finally {
    deletingSessionId.value = ''
  }
}

const handleSendEmail = async (entry: ChatEntry) => {
  if (!entry.content || emailingEntryId.value) {
    return
  }

  emailingEntryId.value = entry.id
  try {
    const response = unwrapResult<AgentEmailResponse>(
      await sendAgentEmail({
        sessionId: selectedConversationId.value || sessionId.value,
        conversationTitle: currentConversationTitle.value,
        datasourceName: selectedDatasource.value?.name || activeConversation.value?.datasourceName,
        content: entry.content,
        toolCalls: entry.toolCalls || []
      }) as ApiResult<AgentEmailResponse>
    )

    ElMessage.success(`已发送到 ${response.recipient}`)
  } catch (error: any) {
    ElMessage.error(error?.message || '邮件发送失败')
  } finally {
    emailingEntryId.value = ''
  }
}

const handleSend = async (preset?: string) => {
  if (sending.value) {
    return
  }

  const content = (preset || draft.value).trim()
  if (!content) {
    ElMessage.warning('请输入问题')
    return
  }
  if (!selectedDatasourceId.value) {
    ElMessage.warning('请先选择一个可查询数据源')
    return
  }

  sending.value = true
  draft.value = ''

  const userEntry: ChatEntry = {
    id: `user-${Date.now()}`,
    role: 'user',
    content
  }
  const pendingEntry: ChatEntry = {
    id: `assistant-${Date.now()}`,
    role: 'assistant',
    content: '',
    loading: true
  }

  messages.value.push(userEntry, pendingEntry)
  await forceScrollToBottom()

  try {
    const payload = {
        message: content,
        datasourceId: selectedDatasourceId.value,
        sessionId: sessionId.value
      }

    let response: AgentChatResponse
    if (streamingEnabled.value) {
      response = await streamChatWithAgent(payload, {
        onEvent: (event) => applyStreamEvent(pendingEntry, event)
      })
    } else {
      response = unwrapResult<AgentChatResponse>(
        await chatWithAgent(payload) as ApiResult<AgentChatResponse>
      )
      if (response.sessionId) {
        sessionId.value = response.sessionId
        selectedConversationId.value = response.sessionId
      }
    }

    Object.assign(pendingEntry, buildAssistantEntry(response), { id: pendingEntry.id })
    await loadConversationList()
  } catch (error: any) {
    pendingEntry.loading = false
    pendingEntry.content = pendingEntry.content
      ? `${pendingEntry.content}\n\n${error?.message || '请求失败，请稍后重试。'}`
      : (error?.message || '请求失败，请稍后重试。')
  } finally {
    sending.value = false
    await scrollToBottom()
  }
}

onMounted(async () => {
  startNewConversation()
  await Promise.all([loadDatasources(), loadConversationList()])
  if (conversations.value[0]) {
    await openConversation(conversations.value[0])
  }
  await focusComposerInput()
})

watch(streamingEnabled, (value) => {
  if (typeof window === 'undefined') {
    return
  }
  window.localStorage.setItem(STREAMING_PREFERENCE_KEY, String(value))
})

watch(selectedDatasourceId, (value, oldValue) => {
  if (!value || !oldValue || value === oldValue || suppressDatasourceReset.value) {
    return
  }

  const hasConversationContent = messages.value.some(entry => entry.role === 'user')
  startNewConversation()
  if (hasConversationContent) {
    ElMessage.info('已切换数据源，开始新对话')
  }
})
</script>

<style scoped lang="scss">
.agent-page {
  --agent-ink: #0f172a;
  --agent-muted: #5f6f85;
  --agent-border: rgba(94, 116, 146, 0.18);
  --agent-card: rgba(255, 255, 255, 0.84);
  --agent-shadow: 0 18px 48px rgba(15, 23, 42, 0.08);
  padding: clamp(12px, 1.4vw, 20px);
  height: 100%;
  min-height: 100%;
  display: flex;
  flex-direction: column;
  gap: 14px;
  width: 100%;
  max-width: 1680px;
  margin: 0 auto;
  overflow: auto;
  font-family: 'Avenir Next', 'PingFang SC', 'Noto Sans SC', sans-serif;
  background:
    radial-gradient(circle at top left, rgba(14, 165, 233, 0.14), transparent 26%),
    radial-gradient(circle at right center, rgba(16, 185, 129, 0.09), transparent 22%),
    linear-gradient(180deg, #f8fbff 0%, #f3f7fb 100%);
  scrollbar-gutter: stable;
}

.hero-shell {
  border-radius: 26px;
  padding: 18px 22px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  flex-shrink: 0;
  color: #f8fafc;
  background:
    linear-gradient(135deg, rgba(8, 47, 73, 0.96), rgba(15, 118, 110, 0.94)),
    linear-gradient(180deg, rgba(255, 255, 255, 0.08), transparent);
  box-shadow: 0 26px 60px rgba(15, 23, 42, 0.12);
}

.hero-copy {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 980px;

  h1 {
    margin: 0;
    font-size: clamp(24px, 2.5vw, 32px);
    line-height: 1.02;
    letter-spacing: -0.03em;
  }

  p {
    margin: 0;
    max-width: 900px;
    line-height: 1.6;
    font-size: 14px;
    color: rgba(226, 232, 240, 0.9);
  }
}

.hero-eyebrow {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: rgba(187, 247, 208, 0.92);
}

.hero-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.metric-card {
  min-height: 82px;
  border-radius: 18px;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(226, 232, 240, 0.16);
  backdrop-filter: blur(8px);
  display: flex;
  flex-direction: column;
  gap: 4px;
  justify-content: space-between;

  strong {
    font-size: 18px;
    line-height: 1.2;
  }

  small {
    color: rgba(226, 232, 240, 0.82);
    line-height: 1.45;
    font-size: 12px;
  }
}

.metric-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(191, 219, 254, 0.86);
}

.workspace-grid {
  display: grid;
  grid-template-columns: minmax(290px, 320px) minmax(0, 1fr);
  gap: 18px;
  min-height: 0;
  flex: 1;
  align-items: start;
  overflow: visible;
}

.left-rail,
.chat-panel {
  min-height: 0;
}

.left-rail {
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow: visible;
  padding-right: 4px;
}

.panel-card,
.chat-card {
  border: 1px solid var(--agent-border);
  border-radius: 22px;
  background: var(--agent-card);
  backdrop-filter: blur(16px);
  box-shadow: var(--agent-shadow);
}

.panel-card :deep(.el-card__header),
.chat-card :deep(.el-card__header) {
  padding-bottom: 8px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  color: var(--agent-ink);
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.full-width {
  width: 100%;
}

.datasource-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.datasource-summary {
  padding: 14px;
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.05), rgba(15, 23, 42, 0.02));
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.summary-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  color: #334155;

  span {
    color: #64748b;
  }
}

.history-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.history-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.history-skeleton {
  display: grid;
  gap: 12px;
}

.history-skeleton-item {
  height: 88px;
  border-radius: 18px;
  background: linear-gradient(90deg, rgba(226, 232, 240, 0.55), rgba(241, 245, 249, 0.9));
}

.draft-card,
.conversation-item {
  border-radius: 14px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  background: rgba(255, 255, 255, 0.8);
  transition: 0.2s ease;
}

.draft-card {
  padding: 12px 14px;
  cursor: pointer;

  p {
    margin: 6px 0 0;
    color: var(--agent-muted);
    line-height: 1.5;
    font-size: 12px;
  }

  &.active,
  &:hover {
    border-color: rgba(14, 165, 233, 0.35);
    box-shadow: 0 14px 34px rgba(14, 165, 233, 0.12);
    transform: translateY(-1px);
  }
}

.draft-card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--agent-ink);
}

.conversation-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.conversation-item {
  padding: 10px 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;

  &:hover {
    border-color: rgba(14, 165, 233, 0.35);
    transform: translateY(-1px);
  }

  &.active {
    border-color: rgba(15, 118, 110, 0.42);
    background: linear-gradient(180deg, rgba(236, 253, 245, 0.9), rgba(240, 249, 255, 0.9));
    box-shadow: 0 16px 34px rgba(15, 118, 110, 0.12);
  }
}

.conversation-main {
  min-width: 0;
  flex: 1;
}

.conversation-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;

  strong {
    color: var(--agent-ink);
    min-width: 0;
    flex: 1;
    font-size: 13px;
    line-height: 1.35;
  }
}

.conversation-type {
  text-transform: uppercase;
}

.conversation-meta {
  margin-top: 6px;
  display: flex;
  justify-content: space-between;
  gap: 10px;
  color: #94a3b8;
  font-size: 11px;
}

.conversation-meta span:first-child {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.delete-btn {
  color: #94a3b8;
  align-self: center;
  opacity: 0.8;
}

.prompt-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.prompt-chip {
  border: 0;
  border-radius: 999px;
  padding: 10px 14px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(240, 249, 255, 0.96));
  color: var(--agent-ink);
  cursor: pointer;
  box-shadow: inset 0 0 0 1px rgba(148, 163, 184, 0.18);
  transition: 0.18s ease;

  &:hover {
    transform: translateY(-1px);
    box-shadow:
      inset 0 0 0 1px rgba(14, 165, 233, 0.26),
      0 12px 24px rgba(14, 165, 233, 0.1);
  }
}

.usage-list {
  margin: 0;
  padding-left: 18px;
  color: #475569;
  line-height: 1.65;
  font-size: 13px;
}

.chat-panel {
  min-height: 0;
  display: flex;
}

.chat-card {
  height: 100%;
  min-height: 0;

  :deep(.el-card__body) {
    height: 100%;
    min-height: 0;
    display: flex;
    flex-direction: column;
    gap: 14px;
    padding: 18px 20px;
  }
}

.chat-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: flex-start;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.16);
  flex-shrink: 0;
}

.chat-toolbar-main {
  display: flex;
  flex-direction: column;
  gap: 8px;

  h2 {
    margin: 0;
    font-size: clamp(20px, 1.8vw, 26px);
    line-height: 1.05;
    color: var(--agent-ink);
    letter-spacing: -0.02em;
  }
}

.chat-toolbar-subtitle {
  margin: 0;
  max-width: 760px;
  color: #64748b;
  line-height: 1.55;
  font-size: 13px;
}

.chat-eyebrow {
  font-size: 12px;
  font-weight: 700;
  color: #0f766e;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.toolbar-tags,
.summary-tags,
.capability-tags,
.suggestion-list {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.chat-toolbar-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.message-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 6px 6px 0 2px;
}

.message-row {
  display: flex;
  align-items: flex-start;
  gap: 14px;

  &.user {
    flex-direction: row-reverse;

    .message-stack {
      align-items: flex-end;
    }
  }
}

.message-avatar {
  width: 42px;
  height: 42px;
  flex-shrink: 0;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, #0f172a, #1e293b);
  color: #fff;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.16);
}

.message-row.user .message-avatar {
  background: linear-gradient(180deg, #0284c7, #0369a1);
}

.message-stack {
  width: min(100%, 780px);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.message-row.user .message-stack {
  width: min(100%, 620px);
}

.message-bubble {
  border-radius: 20px;
  padding: 14px 16px;
  box-shadow: 0 14px 32px rgba(15, 23, 42, 0.06);

  &.assistant {
    color: var(--agent-ink);
    background: linear-gradient(180deg, rgba(252, 253, 255, 0.98), rgba(244, 247, 251, 0.98));
    border: 1px solid rgba(148, 163, 184, 0.18);
  }

  &.assistant.system {
    background: linear-gradient(180deg, rgba(240, 249, 255, 0.95), rgba(236, 253, 245, 0.95));
  }

  &.user {
    color: #fff;
    background: linear-gradient(180deg, #0ea5e9, #0284c7);
  }
}

.message-role {
  margin-bottom: 6px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  opacity: 0.72;
}

.message-text {
  line-height: 1.68;
  font-size: 14px;
}

.plain-text {
  white-space: pre-wrap;
}

.markdown-body {
  white-space: normal;

  :deep(p),
  :deep(ul),
  :deep(ol),
  :deep(pre),
  :deep(blockquote) {
    margin: 0 0 14px;
  }

  :deep(p:last-child),
  :deep(ul:last-child),
  :deep(ol:last-child),
  :deep(pre:last-child),
  :deep(blockquote:last-child) {
    margin-bottom: 0;
  }

  :deep(h1),
  :deep(h2),
  :deep(h3),
  :deep(h4),
  :deep(h5),
  :deep(h6) {
    margin: 0 0 12px;
    line-height: 1.35;
    color: inherit;
  }

  :deep(h1) {
    font-size: 24px;
  }

  :deep(h2) {
    font-size: 20px;
  }

  :deep(h3) {
    font-size: 18px;
  }

  :deep(ul),
  :deep(ol) {
    padding-left: 22px;
  }

  :deep(li) {
    margin-bottom: 8px;
  }

  :deep(strong) {
    font-weight: 700;
  }

  :deep(em) {
    font-style: italic;
  }

  :deep(code) {
    padding: 2px 6px;
    border-radius: 8px;
    font-size: 0.92em;
    font-family: 'SFMono-Regular', 'JetBrains Mono', monospace;
    background: rgba(15, 23, 42, 0.08);
  }

  :deep(pre) {
    overflow: auto;
    padding: 14px 16px;
    border-radius: 16px;
    background: #0f172a;
    color: #e2e8f0;
  }

  :deep(pre code) {
    padding: 0;
    border-radius: 0;
    background: transparent;
    color: inherit;
    display: block;
    white-space: pre-wrap;
    word-break: break-word;
  }

  :deep(.code-language) {
    margin-bottom: 8px;
    font-size: 12px;
    letter-spacing: 0.06em;
    text-transform: uppercase;
    color: #94a3b8;
  }

  :deep(blockquote) {
    padding: 10px 14px;
    border-left: 4px solid rgba(15, 118, 110, 0.35);
    border-radius: 0 14px 14px 0;
    background: rgba(15, 118, 110, 0.08);
    color: #334155;
  }

  :deep(a) {
    color: #0f766e;
    text-decoration: none;
    border-bottom: 1px solid rgba(15, 118, 110, 0.25);
  }
}

.loading-state {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #475569;
}

.tool-call-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 10px;
}

.message-actions {
  display: flex;
  justify-content: flex-start;
}

.message-row.user .message-actions {
  justify-content: flex-end;
}

.tool-call-card,
.result-panel {
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  background: rgba(255, 255, 255, 0.92);
}

.tool-call-card {
  padding: 12px;
}

.tool-call-header {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
  margin-bottom: 8px;
}

.tool-call-summary {
  color: #475569;
  line-height: 1.6;
  font-size: 13px;
}

.tool-call-meta {
  margin-top: 10px;
  display: flex;
  justify-content: space-between;
  gap: 8px;
  color: #94a3b8;
  font-size: 12px;
}

.result-panel {
  padding: 16px;
}

.result-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 16px;

  h3 {
    margin: 0;
    font-size: 18px;
    color: var(--agent-ink);
  }
}

.log-message {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
}

.sql-query-table {
  margin-top: 16px;
}

.composer {
  display: flex;
  flex-direction: column;
  gap: 10px;
  border-top: 1px solid rgba(148, 163, 184, 0.16);
  padding-top: 12px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0), rgba(255, 255, 255, 0.86) 36%, rgba(255, 255, 255, 0.96) 100%);
  flex-shrink: 0;
}

.composer-footer {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.composer-hint {
  color: #64748b;
  font-size: 13px;
}

.composer-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.streaming-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #64748b;
  font-size: 12px;
  white-space: nowrap;
}

@media (max-width: 1280px) {
  .agent-page {
    height: auto;
    overflow: auto;
  }

  .hero-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .workspace-grid {
    grid-template-columns: 1fr;
    overflow: visible;
  }

  .left-rail {
    overflow: visible;
  }
}

@media (max-width: 768px) {
  .agent-page {
    padding: 14px;
    height: auto;
    overflow: auto;
  }

  .hero-shell,
  .chat-toolbar,
  .composer-footer,
  .result-header,
  .panel-header {
    grid-template-columns: 1fr;
    flex-direction: column;
    align-items: stretch;
  }

  .chat-toolbar-actions,
  .panel-actions,
  .composer-actions {
    width: 100%;
  }

  .hero-metrics {
    grid-template-columns: 1fr;
  }

  .message-row,
  .message-row.user {
    flex-direction: column;
  }

  .message-stack {
    width: 100%;
  }

  .conversation-item {
    padding-right: 10px;
  }

  .chat-card :deep(.el-card__body) {
    padding: 18px;
  }
}
</style>
