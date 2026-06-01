import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User, LoginRequest, LoginResponse } from '@/types/auth'
import { login as loginApi, logout as logoutApi, getUserInfo } from '@/api/auth'
import { ElMessage } from 'element-plus'
import { clearStoredAuthTokens, readStoredJwtToken, isUsableJwtToken } from '@/utils/jwt'

export const useAuthStore = defineStore('auth', () => {
  if (!isUsableJwtToken(localStorage.getItem('accessToken')) && localStorage.getItem('accessToken')) {
    clearStoredAuthTokens()
  }

  const normalizeLoginUser = (userInfo: LoginResponse['userInfo']): User => ({
    id: userInfo.id,
    username: userInfo.username,
    email: userInfo.email,
    fullName: userInfo.fullName,
    role: userInfo.role,
    enabled: true,
    createdAt: '',
    updatedAt: ''
  })

  // 状态
  const accessToken = ref<string>(readStoredJwtToken('accessToken'))
  const refreshToken = ref<string>(readStoredJwtToken('refreshToken'))
  const user = ref<User | null>(null)

  // 计算属性
  const isAuthenticated = computed(() => isUsableJwtToken(accessToken.value))

  // 登录
  const login = async (loginData: LoginRequest) => {
    try {
      const response = await loginApi(loginData)
      const { data } = response

      // 保存token
      accessToken.value = data.accessToken
      refreshToken.value = data.refreshToken
      user.value = normalizeLoginUser(data.userInfo)

      // 存储到localStorage
      localStorage.setItem('accessToken', data.accessToken)
      localStorage.setItem('refreshToken', data.refreshToken)

      ElMessage.success('登录成功')
      return true
    } catch (error) {
      ElMessage.error('登录失败')
      return false
    }
  }

  // 登出
  const logout = async () => {
    try {
      await logoutApi()
    } catch (error) {
      console.error('登出失败:', error)
    } finally {
      // 清除状态
      accessToken.value = ''
      refreshToken.value = ''
      user.value = null

      // 清除localStorage
      clearStoredAuthTokens()

      ElMessage.success('已登出')
    }
  }

  // 获取用户信息
  const fetchUserInfo = async () => {
    try {
      const response = await getUserInfo()
      user.value = response.data
      return true
    } catch (error) {
      console.error('获取用户信息失败:', error)
      return false
    }
  }

  return {
    accessToken,
    refreshToken,
    user,
    isAuthenticated,
    login,
    logout,
    fetchUserInfo
  }
})
