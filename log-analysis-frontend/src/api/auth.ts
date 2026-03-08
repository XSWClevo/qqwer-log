import request from '@/utils/request'
import type { LoginRequest, LoginResponse, User, ApiResponse } from '@/types/auth'

/**
 * 用户登录
 */
export function login(data: LoginRequest) {
  return request({
    url: '/api/auth/login',
    method: 'post',
    data
  }) as Promise<ApiResponse<LoginResponse>>
}

/**
 * 刷新令牌
 */
export function refreshToken(refreshToken: string) {
  return request({
    url: '/api/auth/refresh',
    method: 'post',
    data: { refreshToken }
  }) as Promise<ApiResponse<LoginResponse>>
}

/**
 * 用户登出
 */
export function logout() {
  return request({
    url: '/api/auth/logout',
    method: 'post'
  }) as Promise<ApiResponse<void>>
}

/**
 * 获取当前用户信息
 */
export function getUserInfo() {
  return request({
    url: '/api/auth/user/info',
    method: 'get'
  }) as Promise<ApiResponse<User>>
}
