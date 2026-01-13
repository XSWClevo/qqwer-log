import request from '@/utils/request'
import type { LoginRequest, LoginResponse, User, ApiResponse } from '@/types/auth'

/**
 * 用户登录
 */
export function login(data: LoginRequest) {
  return request<ApiResponse<LoginResponse>>({
    url: '/api/auth/login',
    method: 'post',
    data
  })
}

/**
 * 刷新令牌
 */
export function refreshToken(refreshToken: string) {
  return request<ApiResponse<LoginResponse>>({
    url: '/api/auth/refresh',
    method: 'post',
    data: { refreshToken }
  })
}

/**
 * 用户登出
 */
export function logout() {
  return request<ApiResponse<void>>({
    url: '/api/auth/logout',
    method: 'post'
  })
}

/**
 * 获取当前用户信息
 */
export function getUserInfo() {
  return request<ApiResponse<User>>({
    url: '/api/auth/user/info',
    method: 'get'
  })
}
