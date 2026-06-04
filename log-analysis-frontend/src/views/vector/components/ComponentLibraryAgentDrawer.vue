<template>
  <el-drawer
    v-model="visible"
    direction="rtl"
    size="460px"
    class="component-agent-drawer"
    :with-header="false"
    append-to-body
  >
    <div class="agent-drawer-shell">
      <header class="agent-drawer-header">
        <div class="agent-title-row">
          <div class="agent-mark">
            <el-icon><Cpu /></el-icon>
          </div>
          <div>
            <h3>日志助手</h3>
            <p>组件库 · 创建日志解析组件</p>
          </div>
        </div>
        <el-button size="small" text @click="resetConversation">
          <el-icon><RefreshRight /></el-icon>
          新对话
        </el-button>
      </header>

      <section class="context-panel">
        <div>
          <span>当前页面</span>
          <strong>组件库</strong>
        </div>
        <div>
          <span>当前任务</span>
          <strong>创建日志解析组件</strong>
        </div>
      </section>

      <section class="datasource-panel">
        <div class="datasource-panel-header">
          <div>
            <span>目标数据源</span>
            <strong>{{ selectedDatasourceLabel }}</strong>
          </div>
          <el-button size="small" text :loading="datasourceLoading" @click="loadDatasources(true)">
            <el-icon><RefreshRight /></el-icon>
            刷新
          </el-button>
        </div>
        <el-select
          v-model="selectedDatasourceId"
          class="datasource-select"
          filterable
          clearable
          placeholder="选择 ClickHouse 数据源"
          :loading="datasourceLoading"
          :disabled="datasourceLoading"
        >
          <el-option
            v-for="item in datasources"
            :key="item.id"
            :label="formatDatasourceLabel(item)"
            :value="item.id"
          >
            <div class="datasource-option">
              <span>{{ item.name }}</span>
              <small>{{ formatDatasourceEndpoint(item) }}</small>
            </div>
          </el-option>
        </el-select>
        <div v-if="selectedDatasource" class="datasource-detail">
          <span>{{ selectedDatasource.type }}</span>
          <span>{{ formatDatasourceEndpoint(selectedDatasource) }}</span>
        </div>
        <el-alert
          v-else-if="!datasourceLoading"
          type="warning"
          show-icon
          :closable="false"
          title="请选择目标 ClickHouse 数据源后再让助手生成入库计划。"
        />
        <router-link
          v-if="!datasourceLoading && datasources.length === 0"
          class="datasource-manage-link"
          to="/datasources"
        >
          去数据源管理创建 ClickHouse 数据源
        </router-link>
      </section>

      <main ref="messageListRef" class="agent-message-list">
        <article
          v-for="entry in messages"
          :key="entry.id"
          class="agent-message"
          :class="entry.role"
        >
          <div class="message-role">{{ entry.role === 'assistant' ? '日志助手' : '你' }}</div>
          <div class="message-body">
            <el-icon v-if="entry.loading" class="is-loading"><MagicStick /></el-icon>
            <span>{{ entry.content }}</span>
          </div>

          <section v-if="entry.response?.result" class="result-card">
            <div class="result-card-header">
              <strong>{{ getResultTitle(entry.response.result) }}</strong>
              <el-tag size="small" :type="entry.response.result.success === false ? 'danger' : 'success'">
                {{ entry.response.result.success === false ? '失败' : '可继续' }}
              </el-tag>
            </div>

            <div class="result-meta-grid">
              <div v-if="entry.response.result.tableName">
                <span>目标表</span>
                <strong>{{ entry.response.result.tableName }}</strong>
              </div>
              <div v-if="entry.response.result.fields?.length">
                <span>字段数</span>
                <strong>{{ entry.response.result.fields.length }}</strong>
              </div>
              <div v-if="entry.response.result.sourceType">
                <span>日志来源</span>
                <strong>{{ formatSourceType(entry.response.result.sourceType) }}</strong>
              </div>
              <div v-if="entry.response.result.visualConfigName || entry.response.result.visualConfigId">
                <span>编排配置</span>
                <strong>{{ entry.response.result.visualConfigName || entry.response.result.visualConfigId }}</strong>
              </div>
            </div>

            <el-alert
              v-if="entry.response.result.error"
              type="error"
              show-icon
              :closable="false"
              :title="entry.response.result.error"
            />

            <div v-if="entry.response.result.warnings?.length" class="warning-list">
              <el-alert
                v-for="warning in entry.response.result.warnings"
                :key="warning"
                type="warning"
                show-icon
                :closable="false"
                :title="warning"
              />
            </div>

            <div v-if="entry.response.result.type === 'vector_component_plan'" class="result-actions">
              <el-button
                type="primary"
                size="small"
                :disabled="!entry.response.result.planId"
                :loading="committingPlanId === entry.response.result.planId"
                @click="commitPlan(entry.response.result.planId)"
              >
                <el-icon><Check /></el-icon>
                确认创建组件
              </el-button>
              <el-button size="small" plain @click="focusComposer">
                继续调整
              </el-button>
            </div>
          </section>

          <div v-if="entry.response?.suggestions?.length" class="suggestion-list">
            <div class="suggestion-list-header">
              <span>建议可编辑</span>
              <small>点击填入输入框</small>
            </div>
            <el-button
              v-for="suggestion in entry.response.suggestions"
              :key="`${entry.id}-${suggestion}`"
              size="small"
              plain
              :disabled="sending"
              :title="`填入后编辑：${suggestion}`"
              @click="applySuggestion(suggestion)"
            >
              <span class="suggestion-text">{{ suggestion }}</span>
              <span class="suggestion-action">
                <el-icon><EditPen /></el-icon>
                编辑
              </span>
            </el-button>
          </div>
        </article>
      </main>

      <div class="quick-prompts">
        <button
          v-for="prompt in quickPrompts"
          :key="prompt"
          type="button"
          :disabled="sending || !selectedDatasourceId"
          @click="sendMessage(prompt)"
        >
          {{ prompt }}
        </button>
      </div>

      <form class="agent-composer" @submit.prevent="submitDraft">
        <el-input
          ref="composerRef"
          v-model="draft"
          type="textarea"
          resize="none"
          :autosize="{ minRows: 3, maxRows: 6 }"
          :disabled="sending"
          placeholder="粘贴日志样本，或说明来源、表名、入库数据源"
          @keydown.enter.exact.prevent="submitDraft"
        />
        <el-button
          type="primary"
          native-type="submit"
          :loading="sending"
          :disabled="!draft.trim() || !selectedDatasourceId"
        >
          发送
        </el-button>
      </form>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type InputInstance } from 'element-plus'
import { Check, Cpu, EditPen, MagicStick, RefreshRight } from '@element-plus/icons-vue'
import {
  chatWithAgent,
  commitVectorComponentPlan,
  type AgentChatRequest,
  type AgentChatResponse,
  type AgentResult
} from '@/api/agent'
import { listDatasourcesByType, type Datasource } from '@/api/datasource'

interface ApiResult<T> {
  code?: number
  message?: string
  data?: T
}

interface ChatEntry {
  id: string
  role: 'user' | 'assistant'
  content: string
  loading?: boolean
  response?: AgentChatResponse
}

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'created', payload: { tableName: string, remapComponentId?: string, sinkComponentId?: string }): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const quickPrompts = [
  '我想创建日志解析组件',
  '根据这条日志样本生成组件',
  '继续补充来源和表名'
]

const createSessionId = () => `component-agent-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`

const sessionId = ref(createSessionId())
const draft = ref('')
const sending = ref(false)
const committingPlanId = ref('')
const datasourceLoading = ref(false)
const datasources = ref<Datasource[]>([])
const selectedDatasourceId = ref('')
const messageListRef = ref<HTMLElement>()
const composerRef = ref<InputInstance>()
const messages = ref<ChatEntry[]>([
  {
    id: 'assistant-welcome',
    role: 'assistant',
    content: '先选择目标 ClickHouse 数据源，再粘贴日志样本。我会按组件库上下文生成预览，确认前不会直接建表、写组件或部署。'
  }
])

const selectedDatasource = computed(() =>
  datasources.value.find(item => item.id === selectedDatasourceId.value) || null
)

const selectedDatasourceLabel = computed(() =>
  selectedDatasource.value?.name || '未选择'
)

const unwrapResult = <T>(response: ApiResult<T> | T) => {
  if (response && typeof response === 'object' && 'data' in (response as ApiResult<T>)) {
    return (response as ApiResult<T>).data as T
  }
  return response as T
}

const buildAgentRequest = (message: string): AgentChatRequest => ({
  message,
  datasourceId: selectedDatasourceId.value || undefined,
  sessionId: sessionId.value,
  skillId: 'create_log_parser_component',
  pageContext: 'COMPONENT_LIBRARY',
  routePath: '/vector/components',
  surfaceContext: {
    pageName: '组件库',
    task: 'CREATE_LOG_PARSER_COMPONENT',
    targetDatasourceId: selectedDatasourceId.value || undefined,
    targetDatasourceName: selectedDatasource.value?.name,
    targetDatasourceType: selectedDatasource.value?.type
  }
})

const submitDraft = () => {
  const content = draft.value.trim()
  if (!content) {
    return
  }
  draft.value = ''
  void sendMessage(content)
}

const applySuggestion = async (content: string) => {
  const normalizedContent = content.trim()
  if (!normalizedContent || sending.value) {
    return
  }
  draft.value = normalizedContent
  await nextTick()
  composerRef.value?.focus()
}

const sendMessage = async (content: string) => {
  const normalizedContent = content.trim()
  if (!normalizedContent || sending.value) {
    return
  }
  if (!selectedDatasourceId.value) {
    ElMessage.warning('请先选择目标 ClickHouse 数据源')
    return
  }

  const userEntry: ChatEntry = {
    id: `user-${Date.now()}`,
    role: 'user',
    content: normalizedContent
  }
  const pendingEntry: ChatEntry = {
    id: `assistant-${Date.now()}`,
    role: 'assistant',
    content: '正在分析请求...',
    loading: true
  }
  messages.value.push(userEntry, pendingEntry)
  await scrollToBottom()

  sending.value = true
  try {
    const response = unwrapResult<AgentChatResponse>(
      await chatWithAgent(buildAgentRequest(normalizedContent)) as ApiResult<AgentChatResponse>
    )
    pendingEntry.loading = false
    pendingEntry.response = response
    pendingEntry.content = response.success
      ? (response.answer || '已完成处理')
      : (response.error || '处理失败')
    if (response.sessionId) {
      sessionId.value = response.sessionId
    }
  } catch (error: any) {
    pendingEntry.loading = false
    pendingEntry.content = error?.message || '请求失败，请稍后重试'
  } finally {
    sending.value = false
    await scrollToBottom()
  }
}

const commitPlan = async (planId?: string) => {
  if (!planId || committingPlanId.value) {
    return
  }
  try {
    await ElMessageBox.confirm(
      '确认创建表、Source、Remap、Sink 和可部署编排配置吗？确认后仍不会自动部署。',
      '确认创建组件',
      {
        type: 'warning',
        confirmButtonText: '确认创建组件',
        cancelButtonText: '取消'
      }
    )
  } catch {
    return
  }

  committingPlanId.value = planId
  const pendingEntry: ChatEntry = {
    id: `assistant-commit-${Date.now()}`,
    role: 'assistant',
    content: '正在确认创建组件...',
    loading: true
  }
  messages.value.push(pendingEntry)
  await scrollToBottom()

  try {
    const response = unwrapResult<AgentChatResponse>(
      await commitVectorComponentPlan(planId, sessionId.value) as ApiResult<AgentChatResponse>
    )
    pendingEntry.loading = false
    pendingEntry.response = response
    pendingEntry.content = response.success
      ? (response.answer || '组件创建完成')
      : (response.error || '组件创建失败')

    if (response.success && response.result?.type === 'vector_component_commit') {
      emit('created', {
        tableName: response.result.tableName || '',
        remapComponentId: response.result.remapComponentId,
        sinkComponentId: response.result.sinkComponentId
      })
      ElMessage.success('组件库已刷新')
    }
  } catch (error: any) {
    pendingEntry.loading = false
    pendingEntry.content = error?.message || '确认创建失败'
  } finally {
    committingPlanId.value = ''
    await scrollToBottom()
  }
}

const resetConversation = () => {
  sessionId.value = createSessionId()
  draft.value = ''
  messages.value = [
    {
      id: 'assistant-welcome',
      role: 'assistant',
      content: '先选择目标 ClickHouse 数据源，再粘贴日志样本。我会按组件库上下文生成预览，确认前不会直接建表、写组件或部署。'
    }
  ]
}

const focusComposer = () => {
  void nextTick(() => composerRef.value?.focus())
}

const scrollToBottom = async () => {
  await nextTick()
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  }
}

const getResultTitle = (result: AgentResult) => {
  if (result.type === 'vector_component_requirements') return '日志解析创建向导'
  if (result.type === 'vector_component_plan') return '组件生成预览'
  if (result.type === 'vector_component_commit') return '组件创建结果'
  return '处理结果'
}

const formatSourceType = (value: string) => {
  const map: Record<string, string> = {
    file: '文件',
    syslog: 'Syslog',
    socket: 'Socket',
    kafka: 'Kafka'
  }
  return map[value] || value
}

const formatDatasourceEndpoint = (datasource: Datasource) => {
  const database = datasource.databaseName ? `/${datasource.databaseName}` : ''
  return `${datasource.host}:${datasource.port}${database}`
}

const formatDatasourceLabel = (datasource: Datasource) => {
  return `${datasource.name} · ${formatDatasourceEndpoint(datasource)}`
}

const loadDatasources = async (showFeedback = false) => {
  if (datasourceLoading.value) {
    return
  }
  datasourceLoading.value = true
  try {
    const response = await listDatasourcesByType('clickhouse')
    const data = Array.isArray((response as any)?.data)
      ? (response as any).data
      : Array.isArray(response)
        ? response
        : []
    datasources.value = data
    if (selectedDatasourceId.value && !datasources.value.some(item => item.id === selectedDatasourceId.value)) {
      selectedDatasourceId.value = ''
    }
    const [onlyDatasource] = datasources.value
    if (!selectedDatasourceId.value && datasources.value.length === 1 && onlyDatasource) {
      selectedDatasourceId.value = onlyDatasource.id
    }
    if (showFeedback) {
      if (datasources.value.length) {
        ElMessage.success('已刷新 ClickHouse 数据源')
      } else {
        ElMessage.warning('未找到 ClickHouse 数据源')
      }
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '加载 ClickHouse 数据源失败')
  } finally {
    datasourceLoading.value = false
  }
}

watch(visible, (value) => {
  if (value && datasources.value.length === 0) {
    void loadDatasources()
  }
}, { immediate: true })
</script>

<style scoped lang="scss">
.component-agent-drawer :deep(.el-drawer__body) {
  padding: 0;
  background: var(--macos-bg-secondary);
}

.agent-drawer-shell {
  height: 100%;
  display: grid;
  grid-template-rows: auto auto auto 1fr auto auto;
  min-height: 0;
  color: var(--macos-text-primary);
}

.agent-drawer-header {
  padding: 18px 18px 14px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid var(--macos-border);
  background: var(--macos-card-bg);
}

.agent-title-row {
  display: flex;
  align-items: center;
  gap: 12px;

  h3 {
    margin: 0;
    font-size: 17px;
    font-weight: 700;
    line-height: 1.3;
  }

  p {
    margin: 4px 0 0;
    font-size: 12px;
    color: var(--macos-text-secondary);
  }
}

.agent-mark {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  color: var(--macos-teal);
  background: var(--macos-teal-light);
}

.context-panel {
  margin: 12px 14px;
  padding: 12px;
  border: 1px solid var(--macos-border);
  border-radius: 8px;
  background: var(--macos-card-bg);
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;

  div {
    min-width: 0;
  }

  span {
    display: block;
    color: var(--macos-text-secondary);
    font-size: 12px;
    margin-bottom: 4px;
  }

  strong {
    display: block;
    color: var(--macos-text-primary);
    font-size: 13px;
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.datasource-panel {
  margin: 0 14px 12px;
  padding: 12px;
  border: 1px solid var(--macos-border);
  border-radius: 8px;
  background: var(--macos-card-bg);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.datasource-panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;

  > div {
    min-width: 0;
  }

  span {
    display: block;
    color: var(--macos-text-secondary);
    font-size: 12px;
    margin-bottom: 4px;
  }

  strong {
    display: block;
    color: var(--macos-text-primary);
    font-size: 13px;
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.datasource-select {
  width: 100%;
}

.datasource-option {
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;

  span {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  small {
    flex: 0 0 auto;
    color: var(--macos-text-secondary);
    font-size: 12px;
  }
}

.datasource-detail {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;

  span {
    max-width: 100%;
    padding: 3px 8px;
    border-radius: 999px;
    background: var(--macos-fill-secondary);
    color: var(--macos-text-secondary);
    font-size: 12px;
    line-height: 1.4;
    overflow-wrap: anywhere;
  }
}

.datasource-manage-link {
  width: fit-content;
  max-width: 100%;
  color: var(--macos-blue);
  font-size: 12px;
  font-weight: 600;
  text-decoration: none;

  &:hover {
    color: var(--macos-blue-hover);
    text-decoration: underline;
  }
}

.agent-message-list {
  min-height: 0;
  overflow: auto;
  padding: 2px 14px 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.agent-message {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;

  &.user {
    align-items: flex-end;

    .message-body {
      background: var(--macos-blue-hover);
      color: var(--el-color-white);
      border-color: var(--macos-blue-hover);
    }
  }
}

.message-role {
  font-size: 12px;
  color: var(--macos-text-secondary);
}

.message-body {
  max-width: 100%;
  border: 1px solid var(--macos-border);
  border-radius: 8px;
  padding: 10px 12px;
  background: var(--macos-card-bg);
  color: var(--macos-text-primary);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  display: flex;
  gap: 8px;
}

.result-card {
  width: 100%;
  border: 1px solid var(--macos-border);
  border-radius: 8px;
  background: var(--macos-card-bg);
  padding: 12px;
}

.result-card-header,
.result-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.result-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin: 12px 0;

  div {
    min-width: 0;
    padding: 8px;
    border-radius: 6px;
    background: var(--macos-fill-secondary);
  }

  span {
    display: block;
    font-size: 12px;
    color: var(--macos-text-secondary);
    margin-bottom: 4px;
  }

  strong {
    display: block;
    font-size: 13px;
    color: var(--macos-text-primary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.warning-list,
.suggestion-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 10px;
}

.suggestion-list {
  align-items: stretch;

  .suggestion-list-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    color: var(--macos-text-secondary);
    font-size: 12px;

    span {
      color: var(--macos-text-primary);
      font-weight: 600;
    }

    small {
      font-size: 12px;
    }
  }

  :deep(.el-button) {
    width: 100%;
    height: auto;
    min-height: 30px;
    max-width: 100%;
    margin-left: 0;
    justify-content: flex-start;
    padding: 6px 10px;
    white-space: normal;
    text-align: left;
    line-height: 1.4;
  }

  :deep(.el-button > span) {
    display: flex;
    align-items: center;
    gap: 10px;
    width: 100%;
    min-width: 0;
    white-space: normal;
    word-break: break-word;
  }
}

.suggestion-text {
  flex: 1;
  min-width: 0;
}

.suggestion-action {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 4px;
  color: var(--macos-blue);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.quick-prompts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 14px 0;

  button {
    max-width: 100%;
    border: 1px solid var(--macos-border);
    border-radius: 999px;
    background: var(--macos-card-bg);
    color: var(--macos-text-secondary);
    font-size: 12px;
    padding: 6px 10px;
    white-space: normal;
    line-height: 1.4;
    cursor: pointer;

    &:hover {
      border-color: var(--macos-teal);
      color: var(--macos-teal);
      background: var(--macos-teal-light);
    }

    &:disabled {
      cursor: not-allowed;
      color: var(--macos-text-tertiary);
      border-color: var(--macos-border);
      background: var(--macos-fill-secondary);
      opacity: 0.75;
    }
  }
}

.agent-composer {
  padding: 12px 14px 14px;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 10px;
  align-items: end;
  background: var(--macos-bg-secondary);
}

@media (max-width: 720px) {
  .agent-drawer-shell {
    width: 100vw;
  }

  .context-panel,
  .datasource-panel,
  .result-meta-grid {
    grid-template-columns: 1fr;
  }
}
</style>
