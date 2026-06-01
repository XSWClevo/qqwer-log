import axios from 'axios'
import type { AxiosInstance, AxiosError, InternalAxiosRequestConfig, AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import { clearStoredAuthTokens, isUsableJwtToken, readStoredJwtToken } from '@/utils/jwt'

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

let refreshTokenPromise: Promise<string> | null = null

const normalizeBaseUrl = (value?: string) => String(value || '').replace(/\/$/, '')

const joinApiUrl = (baseUrl: string, path: string) => {
  if (!baseUrl) {
    return path
  }

  if (path.startsWith(`${baseUrl}/`)) {
    return path
  }

  if (baseUrl === '/api' && path.startsWith('/api/')) {
    return path
  }

  return `${baseUrl}${path}`
}

export const resolveApiUrl = (path: string) => {
  return joinApiUrl(normalizeBaseUrl(import.meta.env.VITE_API_BASE_URL), path)
}

const isAuthEndpoint = (url?: string) => {
  if (!url) {
    return false
  }
  return url.includes('/api/auth/login') || url.includes('/api/auth/refresh')
}

const redirectToLogin = () => {
  clearStoredAuthTokens()
  if (window.location.pathname !== '/login') {
    window.location.href = '/login'
  }
}

export const refreshAccessToken = async () => {
  if (refreshTokenPromise) {
    return refreshTokenPromise
  }

  const refreshToken = readStoredJwtToken('refreshToken')
  if (!refreshToken) {
    throw new Error('缺少刷新令牌')
  }

  refreshTokenPromise = axios.post(resolveApiUrl('/api/auth/refresh'), { refreshToken }, {
    headers: { 'Content-Type': 'application/json' }
  }).then((response) => {
    const payload = response.data?.data
    if (!isUsableJwtToken(payload?.accessToken) || !isUsableJwtToken(payload?.refreshToken)) {
      throw new Error('刷新令牌响应无效')
    }

    localStorage.setItem('accessToken', payload.accessToken)
    localStorage.setItem('refreshToken', payload.refreshToken)
    return payload.accessToken as string
  }).finally(() => {
    refreshTokenPromise = null
  })

  return refreshTokenPromise
}

// 创建axios实例
const service: AxiosInstance = axios.create({
  timeout: 60000, // 60秒，适配AI查询等长时间操作
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    if (config.url) {
      config.url = resolveApiUrl(config.url)
    }

    // 从localStorage获取token
    const token = localStorage.getItem('accessToken')
    if (isUsableJwtToken(token) && config.headers) {
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

      // 401/440: 未授权或会话超时，需要重新登录
      if (res.code === 401 || res.code === 440) {
        redirectToLogin()
      }

      return Promise.reject(new Error(res.message || '请求失败'))
    }

    return res
  },
  async (error: AxiosError) => {
    console.error('响应错误:', error)

    if (error.response) {
      const status = error.response.status
      const originalRequest = error.config as RetryableRequestConfig | undefined

      switch (status) {
        case 401:
          if (originalRequest && !originalRequest._retry && !isAuthEndpoint(originalRequest.url)) {
            originalRequest._retry = true
            try {
              const newAccessToken = await refreshAccessToken()
              if (originalRequest.headers) {
                originalRequest.headers.Authorization = `Bearer ${newAccessToken}`
              }
              return service(originalRequest)
            } catch {
              ElMessage.error('登录已过期，请重新登录')
              redirectToLogin()
              break
            }
          }
          ElMessage.error('未授权，请重新登录')
          redirectToLogin()
          break
        case 403:
          ElMessage.error('拒绝访问')
          break
        case 404:
          ElMessage.error('请求地址不存在')
          break
        case 440:
          ElMessage.warning('长时间未操作，会话已过期，请重新登录')
          redirectToLogin()
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
