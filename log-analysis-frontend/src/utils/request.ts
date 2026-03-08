import axios from 'axios'
import type { AxiosInstance, AxiosError, InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import { clearStoredAuthTokens, isLikelyJwtToken } from '@/utils/jwt'

// 创建axios实例
const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 60000, // 60秒，适配AI查询等长时间操作
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 从localStorage获取token
    const token = localStorage.getItem('accessToken')
    if (isLikelyJwtToken(token) && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    } else if (token) {
      clearStoredAuthTokens()
    }
    return config
  },
  (error: AxiosError) => {
    console.error('请求错误:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data

    // 如果返回的状态码不是200，则认为是错误
    if (res.code && res.code !== 200) {
      ElMessage.error(res.message || '请求失败')

      // 401: 未授权，需要重新登录
      if (res.code === 401) {
        // 清除token
        clearStoredAuthTokens()
        // 跳转到登录页
        window.location.href = '/login'
      }

      return Promise.reject(new Error(res.message || '请求失败'))
    }

    return res
  },
  (error: AxiosError) => {
    console.error('响应错误:', error)

    if (error.response) {
      const status = error.response.status

      switch (status) {
        case 401:
          ElMessage.error('未授权，请重新登录')
          clearStoredAuthTokens()
          window.location.href = '/login'
          break
        case 403:
          ElMessage.error('拒绝访问')
          break
        case 404:
          ElMessage.error('请求地址不存在')
          break
        case 500:
          ElMessage.error('服务器内部错误')
          break
        default:
          ElMessage.error(error.message || '请求失败')
      }
    } else {
      ElMessage.error('网络连接失败')
    }

    return Promise.reject(error)
  }
)

export default service
