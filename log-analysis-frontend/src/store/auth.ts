import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User, LoginRequest } from '@/types/auth'
import { login as loginApi, logout as logoutApi, getUserInfo } from '@/api/auth'
import { ElMessage } from 'element-plus'

export const useAuthStore = defineStore('auth', () => {
  // 状态
  const accessToken = ref<string>(localStorage.getItem('accessToken') || '')
  const refreshToken = ref<string>(localStorage.getItem('refreshToken') || '')
  const user = ref<User | null>(null)

  // 计算属性
  const isAuthenticated = computed(() => !!accessToken.value)

  // 登录
  const login = async (loginData: LoginRequest) => {
    try {
      const response = await loginApi(loginData)
      const { data } = response

      // 保存token
      accessToken.value = data.accessToken
      refreshToken.value = data.refreshToken
      user.value = data.user

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
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')

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
