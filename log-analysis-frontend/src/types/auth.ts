// 用户相关类型定义

export interface User {
  id: number
  username: string
  email: string
  fullName: string
  role: string
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  user: User
}

export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}
