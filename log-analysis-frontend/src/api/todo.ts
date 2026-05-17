import request from '@/utils/request'

export interface TodoItem {
  id: number
  title: string
  description?: string
  status: 'TODO' | 'IN_PROGRESS' | 'DONE'
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'
  dueAt?: string
  completedAt?: string
  tags?: string[]
  createdBy: number
  createdAt: string
  updatedAt: string
}

export interface TodoStatsDTO {
  total: number
  todoCount: number
  inProgressCount: number
  doneCount: number
  overdueCount: number
}

export interface CreateTodoRequest {
  title: string
  description?: string
  priority?: string
  dueAt?: string
  tags?: string[]
}

export interface UpdateTodoRequest {
  title?: string
  description?: string
  status?: string
  priority?: string
  dueAt?: string
  tags?: string[]
}

export function getTodoStats() {
  return request({
    url: '/api/todos/stats',
    method: 'get'
  })
}

export function listTodos(params: {
  keyword?: string
  status?: string
  priority?: string
  overdueOnly?: boolean
  pageNum?: number
  pageSize?: number
}) {
  return request({
    url: '/api/todos',
    method: 'get',
    params
  })
}

export function getTodoById(id: number) {
  return request({
    url: `/api/todos/${id}`,
    method: 'get'
  })
}

export function createTodo(data: CreateTodoRequest) {
  return request({
    url: '/api/todos',
    method: 'post',
    data
  })
}

export function updateTodo(id: number, data: UpdateTodoRequest) {
  return request({
    url: `/api/todos/${id}`,
    method: 'put',
    data
  })
}

export function updateTodoStatus(id: number, status: string) {
  return request({
    url: `/api/todos/${id}/status`,
    method: 'patch',
    data: { status }
  })
}

export function deleteTodo(id: number) {
  return request({
    url: `/api/todos/${id}`,
    method: 'delete'
  })
}
