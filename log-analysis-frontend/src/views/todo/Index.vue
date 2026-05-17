<template>
  <AppLayout>
    <div class="todo-page">
      <!-- 页面头部 -->
      <div class="page-header">
        <div class="header-content">
          <div class="title-section">
            <h1 class="page-title">待办事项</h1>
            <p class="page-subtitle">管理你的任务和待办清单</p>
          </div>
          <el-button type="primary" size="large" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>
            新建待办
          </el-button>
        </div>

        <!-- 统计卡片 -->
        <div class="stats-bar">
          <div class="stat-card" :class="{ active: !filters.status && !filters.overdueOnly }" @click="clearFilters">
            <span class="stat-num">{{ stats.total }}</span>
            <span class="stat-label">全部</span>
          </div>
          <div class="stat-card todo" :class="{ active: filters.status === 'TODO' }" @click="setStatusFilter('TODO')">
            <span class="stat-num">{{ stats.todoCount }}</span>
            <span class="stat-label">待处理</span>
          </div>
          <div class="stat-card in-progress" :class="{ active: filters.status === 'IN_PROGRESS' }" @click="setStatusFilter('IN_PROGRESS')">
            <span class="stat-num">{{ stats.inProgressCount }}</span>
            <span class="stat-label">进行中</span>
          </div>
          <div class="stat-card done" :class="{ active: filters.status === 'DONE' }" @click="setStatusFilter('DONE')">
            <span class="stat-num">{{ stats.doneCount }}</span>
            <span class="stat-label">已完成</span>
          </div>
          <div class="stat-card overdue" :class="{ active: filters.overdueOnly }" @click="setOverdueFilter">
            <span class="stat-num">{{ stats.overdueCount }}</span>
            <span class="stat-label">已逾期</span>
          </div>
        </div>
      </div>

      <!-- 过滤栏 -->
      <div class="filter-bar">
        <el-input
          v-model="filters.keyword"
          placeholder="搜索标题或描述..."
          :prefix-icon="Search"
          clearable
          class="search-input"
          @change="loadTodos"
        />
        <el-select v-model="filters.status" placeholder="状态" clearable class="filter-select" @change="loadTodos">
          <el-option label="待处理" value="TODO" />
          <el-option label="进行中" value="IN_PROGRESS" />
          <el-option label="已完成" value="DONE" />
        </el-select>
        <el-select v-model="filters.priority" placeholder="优先级" clearable class="filter-select" @change="loadTodos">
          <el-option label="紧急" value="URGENT" />
          <el-option label="高" value="HIGH" />
          <el-option label="中" value="MEDIUM" />
          <el-option label="低" value="LOW" />
        </el-select>
        <el-checkbox v-model="filters.overdueOnly" label="仅显示逾期" @change="loadTodos" />
      </div>

      <!-- 待办列表 -->
      <div class="list-container" v-loading="loading">
        <div v-if="todos.length === 0 && !loading" class="empty-state">
          <el-icon class="empty-icon"><Tickets /></el-icon>
          <p>暂无待办事项</p>
          <el-button type="primary" @click="openCreateDialog">新建待办</el-button>
        </div>

        <div v-else class="todo-list">
          <div
            v-for="item in todos"
            :key="item.id"
            class="todo-card"
            :class="[`status-${item.status.toLowerCase()}`, { overdue: isOverdue(item) }]"
          >
            <!-- 左侧优先级色条 -->
            <div class="priority-bar" :class="`priority-${item.priority.toLowerCase()}`" />

            <div class="card-body">
              <div class="card-main">
                <!-- 标题行 -->
                <div class="card-title-row">
                  <el-checkbox
                    :model-value="item.status === 'DONE'"
                    @change="(val) => handleQuickComplete(item, val as boolean)"
                    class="done-checkbox"
                  />
                  <span class="todo-title" :class="{ 'is-done': item.status === 'DONE' }">{{ item.title }}</span>
                  <div class="card-badges">
                    <el-tag :type="priorityTagType(item.priority)" size="small" class="priority-tag">
                      {{ priorityLabel(item.priority) }}
                    </el-tag>
                    <el-tag :type="statusTagType(item.status)" size="small">
                      {{ statusLabel(item.status) }}
                    </el-tag>
                  </div>
                </div>

                <!-- 描述 -->
                <div v-if="item.description" class="todo-description">{{ item.description }}</div>

                <!-- 标签 -->
                <div v-if="item.tags && item.tags.length > 0" class="todo-tags">
                  <el-tag
                    v-for="tag in item.tags"
                    :key="tag"
                    size="small"
                    type="info"
                    effect="plain"
                  >{{ tag }}</el-tag>
                </div>
              </div>

              <div class="card-footer">
                <!-- 时间信息 -->
                <div class="time-info">
                  <span v-if="item.dueAt" class="due-date" :class="{ 'is-overdue': isOverdue(item) }">
                    <el-icon><Clock /></el-icon>
                    截止 {{ formatDate(item.dueAt) }}
                  </span>
                  <span v-if="item.completedAt" class="completed-date">
                    <el-icon><CircleCheck /></el-icon>
                    完成于 {{ formatDate(item.completedAt) }}
                  </span>
                </div>

                <!-- 操作按钮 -->
                <div class="card-actions">
                  <el-dropdown @command="(cmd: string) => handleAction(cmd, item)">
                    <el-button text size="small" :icon="MoreFilled" />
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="edit">
                          <el-icon><Edit /></el-icon>编辑
                        </el-dropdown-item>
                        <template v-if="item.status !== 'TODO'">
                          <el-dropdown-item command="status-TODO">
                            <el-icon><RefreshLeft /></el-icon>标记为待处理
                          </el-dropdown-item>
                        </template>
                        <template v-if="item.status !== 'IN_PROGRESS'">
                          <el-dropdown-item command="status-IN_PROGRESS">
                            <el-icon><Loading /></el-icon>标记为进行中
                          </el-dropdown-item>
                        </template>
                        <template v-if="item.status !== 'DONE'">
                          <el-dropdown-item command="status-DONE">
                            <el-icon><CircleCheck /></el-icon>标记为已完成
                          </el-dropdown-item>
                        </template>
                        <el-dropdown-item command="delete" divided>
                          <el-icon><Delete /></el-icon>删除
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 分页 -->
        <div v-if="pagination.total > pagination.pageSize" class="pagination-bar">
          <el-pagination
            v-model:current-page="pagination.current"
            v-model:page-size="pagination.pageSize"
            :page-sizes="[20, 50, 100]"
            :total="pagination.total"
            layout="total, sizes, prev, pager, next"
            @change="loadTodos"
          />
        </div>
      </div>

      <!-- 创建/编辑对话框 -->
      <el-dialog
        v-model="dialogVisible"
        :title="editingId ? '编辑待办' : '新建待办'"
        width="560px"
        :close-on-click-modal="false"
        @closed="resetForm"
      >
        <el-form ref="formRef" :model="form" :rules="formRules" label-width="80px" label-position="left">
          <el-form-item label="标题" prop="title">
            <el-input v-model="form.title" placeholder="输入待办标题" maxlength="255" show-word-limit />
          </el-form-item>
          <el-form-item label="描述">
            <el-input
              v-model="form.description"
              type="textarea"
              placeholder="输入描述（可选）"
              :rows="3"
              maxlength="2000"
            />
          </el-form-item>
          <el-form-item label="优先级">
            <el-select v-model="form.priority" style="width: 100%">
              <el-option label="紧急" value="URGENT" />
              <el-option label="高" value="HIGH" />
              <el-option label="中" value="MEDIUM" />
              <el-option label="低" value="LOW" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="editingId" label="状态">
            <el-select v-model="form.status" style="width: 100%">
              <el-option label="待处理" value="TODO" />
              <el-option label="进行中" value="IN_PROGRESS" />
              <el-option label="已完成" value="DONE" />
            </el-select>
          </el-form-item>
          <el-form-item label="截止时间">
            <el-date-picker
              v-model="form.dueAt"
              type="datetime"
              placeholder="选择截止时间（可选）"
              style="width: 100%"
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DD HH:mm:ss"
            />
          </el-form-item>
          <el-form-item label="标签">
            <div class="tag-input-area">
              <el-tag
                v-for="tag in form.tags"
                :key="tag"
                closable
                @close="removeTag(tag)"
                class="form-tag"
              >{{ tag }}</el-tag>
              <el-input
                v-if="tagInputVisible"
                ref="tagInputRef"
                v-model="tagInputValue"
                size="small"
                class="tag-input"
                @keyup.enter="confirmTag"
                @blur="confirmTag"
              />
              <el-button v-else size="small" @click="showTagInput" class="add-tag-btn">
                <el-icon><Plus /></el-icon> 添加标签
              </el-button>
            </div>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            {{ editingId ? '保存' : '创建' }}
          </el-button>
        </template>
      </el-dialog>
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, reactive, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, InputInstance } from 'element-plus'
import {
  Plus,
  Search,
  Edit,
  Delete,
  MoreFilled,
  Clock,
  CircleCheck,
  Tickets,
  RefreshLeft,
  Loading
} from '@element-plus/icons-vue'
import AppLayout from '@/components/layout/AppLayout.vue'
import {
  getTodoStats,
  listTodos,
  createTodo,
  updateTodo,
  updateTodoStatus,
  deleteTodo,
  type TodoItem,
  type TodoStatsDTO
} from '@/api/todo'

// ---- 状态 ----
const loading = ref(false)
const submitting = ref(false)
const todos = ref<TodoItem[]>([])
const stats = reactive<TodoStatsDTO>({
  total: 0,
  todoCount: 0,
  inProgressCount: 0,
  doneCount: 0,
  overdueCount: 0
})
const pagination = reactive({ current: 1, pageSize: 20, total: 0 })

const filters = reactive({
  keyword: '',
  status: '',
  priority: '',
  overdueOnly: false
})

// ---- 对话框 ----
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  title: '',
  description: '',
  priority: 'MEDIUM',
  status: 'TODO',
  dueAt: '',
  tags: [] as string[]
})
const formRules: FormRules = {
  title: [{ required: true, message: '标题不能为空', trigger: 'blur' }]
}

// ---- 标签输入 ----
const tagInputVisible = ref(false)
const tagInputValue = ref('')
const tagInputRef = ref<InputInstance>()

// ---- 数据加载 ----
const loadStats = async () => {
  try {
    const res = await getTodoStats()
    Object.assign(stats, res.data)
  } catch {
    // ignore
  }
}

const loadTodos = async () => {
  loading.value = true
  try {
    const res = await listTodos({
      keyword: filters.keyword || undefined,
      status: filters.status || undefined,
      priority: filters.priority || undefined,
      overdueOnly: filters.overdueOnly || undefined,
      pageNum: pagination.current,
      pageSize: pagination.pageSize
    })
    const data = res.data || res
    todos.value = data.records || []
    pagination.total = data.total || 0
  } catch (e: any) {
    ElMessage.error('加载失败：' + (e.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

const refresh = () => {
  loadStats()
  loadTodos()
}

onMounted(refresh)

// ---- 过滤操作 ----
const clearFilters = () => {
  filters.status = ''
  filters.overdueOnly = false
  pagination.current = 1
  loadTodos()
}

const setStatusFilter = (status: string) => {
  filters.status = filters.status === status ? '' : status
  filters.overdueOnly = false
  pagination.current = 1
  loadTodos()
}

const setOverdueFilter = () => {
  filters.overdueOnly = !filters.overdueOnly
  filters.status = ''
  pagination.current = 1
  loadTodos()
}

// ---- 对话框操作 ----
const openCreateDialog = () => {
  editingId.value = null
  dialogVisible.value = true
}

const openEditDialog = (item: TodoItem) => {
  editingId.value = item.id
  form.title = item.title
  form.description = item.description || ''
  form.priority = item.priority
  form.status = item.status
  form.dueAt = item.dueAt ? formatDateForPicker(item.dueAt) : ''
  form.tags = item.tags ? [...item.tags] : []
  dialogVisible.value = true
}

const resetForm = () => {
  formRef.value?.resetFields()
  form.title = ''
  form.description = ''
  form.priority = 'MEDIUM'
  form.status = 'TODO'
  form.dueAt = ''
  form.tags = []
  editingId.value = null
  tagInputVisible.value = false
  tagInputValue.value = ''
}

const handleSubmit = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    const payload = {
      title: form.title.trim(),
      description: form.description || undefined,
      priority: form.priority,
      status: form.status,
      dueAt: form.dueAt || undefined,
      tags: form.tags.length > 0 ? form.tags : undefined
    }
    if (editingId.value) {
      await updateTodo(editingId.value, payload)
      ElMessage.success('更新成功')
    } else {
      await createTodo(payload)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    refresh()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

// ---- 操作处理 ----
const handleQuickComplete = async (item: TodoItem, done: boolean) => {
  const newStatus = done ? 'DONE' : 'TODO'
  try {
    await updateTodoStatus(item.id, newStatus)
    item.status = newStatus
    loadStats()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  }
}

const handleAction = async (command: string, item: TodoItem) => {
  if (command === 'edit') {
    openEditDialog(item)
    return
  }
  if (command.startsWith('status-')) {
    const newStatus = command.replace('status-', '')
    try {
      await updateTodoStatus(item.id, newStatus)
      ElMessage.success('状态已更新')
      refresh()
    } catch (e: any) {
      ElMessage.error(e.message || '操作失败')
    }
    return
  }
  if (command === 'delete') {
    try {
      await ElMessageBox.confirm(`确定要删除"${item.title}"吗？`, '确认删除', { type: 'warning' })
      await deleteTodo(item.id)
      ElMessage.success('删除成功')
      refresh()
    } catch (e: any) {
      if (e !== 'cancel') {
        ElMessage.error(e.message || '删除失败')
      }
    }
  }
}

// ---- 标签输入 ----
const showTagInput = () => {
  tagInputVisible.value = true
  nextTick(() => tagInputRef.value?.focus())
}

const confirmTag = () => {
  const val = tagInputValue.value.trim()
  if (val && !form.tags.includes(val)) {
    form.tags.push(val)
  }
  tagInputVisible.value = false
  tagInputValue.value = ''
}

const removeTag = (tag: string) => {
  form.tags = form.tags.filter(t => t !== tag)
}

// ---- 工具函数 ----
const isOverdue = (item: TodoItem) => {
  if (!item.dueAt || item.status === 'DONE') return false
  return new Date(item.dueAt) < new Date()
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - d.getTime()
  const diffDays = Math.floor(diffMs / 86400000)
  if (diffDays === 0) return '今天 ' + d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  if (diffDays === 1 && diffMs > 0) return '昨天'
  if (diffDays === -1) return '明天'
  return d.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

const formatDateForPicker = (dateStr: string) => {
  const d = new Date(dateStr)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const priorityLabel = (p: string) => ({ URGENT: '紧急', HIGH: '高', MEDIUM: '中', LOW: '低' }[p] || p)
const priorityTagType = (p: string): any => ({ URGENT: 'danger', HIGH: 'warning', MEDIUM: '', LOW: 'info' }[p] || '')
const statusLabel = (s: string) => ({ TODO: '待处理', IN_PROGRESS: '进行中', DONE: '已完成' }[s] || s)
const statusTagType = (s: string): any => ({ TODO: 'info', IN_PROGRESS: 'warning', DONE: 'success' }[s] || 'info')
</script>

<style scoped lang="scss">
.todo-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #F0F2F5;
}

.page-header {
  background: #FFFFFF;
  padding: 24px 32px 0;
  border-bottom: 1px solid #E8E8E8;

  .header-content {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
  }

  .title-section {
    .page-title {
      font-size: 24px;
      font-weight: 600;
      color: #262626;
      margin: 0 0 6px;
    }
    .page-subtitle {
      font-size: 14px;
      color: #8C8C8C;
      margin: 0;
    }
  }
}

.stats-bar {
  display: flex;
  gap: 0;
  border-top: 1px solid #F0F0F0;

  .stat-card {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 14px 0;
    cursor: pointer;
    border-bottom: 3px solid transparent;
    transition: all 0.2s;

    &:hover {
      background: #FAFAFA;
    }

    &.active {
      border-bottom-color: #1677FF;
      .stat-num { color: #1677FF; }
    }

    &.todo.active { border-bottom-color: #8C8C8C; }
    &.in-progress.active { border-bottom-color: #FA8C16; }
    &.done.active { border-bottom-color: #52C41A; }
    &.overdue.active { border-bottom-color: #FF4D4F; }

    .stat-num {
      font-size: 22px;
      font-weight: 700;
      color: #262626;
      line-height: 1;
      margin-bottom: 4px;
    }

    &.overdue .stat-num { color: #FF4D4F; }

    .stat-label {
      font-size: 12px;
      color: #8C8C8C;
    }
  }
}

.filter-bar {
  background: #FFFFFF;
  padding: 14px 32px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid #E8E8E8;

  .search-input {
    flex: 1;
    max-width: 360px;
  }

  .filter-select {
    width: 130px;
  }
}

.list-container {
  flex: 1;
  padding: 20px 32px;
  overflow: auto;
  min-height: 0;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
  color: #BFBFBF;

  .empty-icon {
    font-size: 56px;
    margin-bottom: 16px;
    color: #D9D9D9;
  }

  p {
    font-size: 15px;
    margin: 0 0 20px;
  }
}

.todo-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.todo-card {
  display: flex;
  background: #FFFFFF;
  border-radius: 8px;
  border: 1px solid #F0F0F0;
  overflow: hidden;
  transition: box-shadow 0.2s, border-color 0.2s;

  &:hover {
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
    border-color: #D9D9D9;
  }

  &.status-done {
    opacity: 0.7;
  }

  &.overdue {
    border-left: none;
    .priority-bar { background: #FF4D4F !important; }
  }
}

.priority-bar {
  width: 4px;
  flex-shrink: 0;
  background: #D9D9D9;

  &.priority-urgent { background: #FF4D4F; }
  &.priority-high   { background: #FA8C16; }
  &.priority-medium { background: #1677FF; }
  &.priority-low    { background: #8C8C8C; }
}

.card-body {
  flex: 1;
  padding: 14px 16px;
  min-width: 0;
}

.card-main {
  margin-bottom: 10px;
}

.card-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;

  .done-checkbox {
    flex-shrink: 0;
  }

  .todo-title {
    font-size: 15px;
    font-weight: 500;
    color: #262626;
    flex: 1;
    min-width: 0;

    &.is-done {
      text-decoration: line-through;
      color: #BFBFBF;
    }
  }

  .card-badges {
    display: flex;
    gap: 6px;
    flex-shrink: 0;
  }
}

.todo-description {
  margin: 8px 0 0 26px;
  font-size: 13px;
  color: #8C8C8C;
  line-height: 1.5;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.todo-tags {
  margin: 8px 0 0 26px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
}

.time-info {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 12px;
  color: #BFBFBF;

  .due-date,
  .completed-date {
    display: flex;
    align-items: center;
    gap: 4px;

    &.is-overdue {
      color: #FF4D4F;
      font-weight: 500;
    }
  }
}

.card-actions {
  flex-shrink: 0;
}

.pagination-bar {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

// 表单标签输入
.tag-input-area {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  min-height: 32px;
}

.form-tag {
  cursor: default;
}

.tag-input {
  width: 100px;
}

.add-tag-btn {
  border-style: dashed;
}
</style>
