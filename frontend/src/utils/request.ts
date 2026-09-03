import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import router from '@/router'
import { resolveRequestTimeout } from './ai-request-policy'
import { shouldShowRequestErrorToast } from './request-error-policy'
import { isRetryable, shouldRetry, computeRetryDelay, type RetryConfig } from './request-retry-policy'

declare module 'axios' {
  export interface AxiosRequestConfig {
    showErrorToast?: boolean
  }
}

// 后端API响应统一格式
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

// 分页响应格式
export type { PageResult } from '@/types/common'

// 创建axios实例
const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
  paramsSerializer: {
    serialize: (params) => {
      const parts: string[] = []
      for (const [key, value] of Object.entries(params)) {
        if (value == null) continue
        if (Array.isArray(value)) {
          for (const item of value) {
            parts.push(encodeURIComponent(key) + '=' + encodeURIComponent(String(item)))
          }
        } else {
          parts.push(encodeURIComponent(key) + '=' + encodeURIComponent(String(value)))
        }
      }
      return parts.join('&')
    },
  },
})

// 请求拦截器
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// Retry interceptor — registered FIRST so its reject handler runs FIRST (LIFO order).
// This ensures retries happen before the user-facing error toast.
service.interceptors.response.use(
  (response) => response,
  async (error) => {
    const config = error.config as (typeof error.config & RetryConfig)

    if (!config) return Promise.reject(error)

    // Initialize retry count
    config.__retryCount = config.__retryCount ?? 0

    if (!isRetryable(error, config)) {
      return Promise.reject(error)
    }

    if (!shouldRetry(config, config.__retryCount)) {
      return Promise.reject(error)
    }

    config.__retryCount++
    const delay = computeRetryDelay(config.__retryCount)

    await new Promise(resolve => setTimeout(resolve, delay))
    return service(config)
  }
)

// 响应拦截器 — registered SECOND so its reject handler runs only for non-retried errors.
service.interceptors.response.use(
  // 注意：此处刻意解包 data 返回 ApiResponse（而非 AxiosResponse），
  // 由下方 get/post 包装函数将类型收敛回 ApiResponse<T>。
  (response: AxiosResponse<ApiResponse>) => {
    const res = response.data

    // 二进制数据直接返回
    const responseType = response.config?.responseType
    if (responseType === 'blob' || responseType === 'arraybuffer') {
      return response
    }

    // 业务错误码处理
    if (res.code !== 200) {
      if (shouldShowRequestErrorToast(response.config)) {
        ElMessage.error(res.message || '请求失败')
      }

      // 401: 未授权 -> 跳转登录
      if (res.code === 401) {
        const userStore = useUserStore()
        userStore.logout()
        router.push('/login')
      }

      return Promise.reject(new Error(res.message || '请求失败'))
    }

    return res as unknown as AxiosResponse
  },
  (error) => {
    if (error.response?.status === 401) {
      const userStore = useUserStore()
      userStore.logout()
      router.push('/login')
    }

    if (shouldShowRequestErrorToast(error.config as AxiosRequestConfig | undefined)) {
      let message = '网络异常，请稍后重试'

      if (error.response) {
        const status = error.response.status
        switch (status) {
          case 401: {
            message = '登录已过期，请重新登录'
            break
          }
          case 403:
            message = '没有权限访问该资源'
            break
          case 404:
            message = '请求的资源不存在'
            break
          case 500:
            message = '服务器内部错误'
            break
          default:
            message = error.response.data?.message || `请求错误 (${status})`
        }
      } else if (error.message.includes('timeout')) {
        message = '请求超时，请稍后重试'
      }

      ElMessage.error(message)
    }
    return Promise.reject(error)
  }
)

// 封装GET请求
export function get<T = unknown>(url: string, params?: Record<string, unknown>, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
  return service.get(url, { params, ...withRequestTimeout(url, config) }) as Promise<ApiResponse<T>>
}

// 封装POST请求
export function post<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
  return service.post(url, data, withRequestTimeout(url, config)) as Promise<ApiResponse<T>>
}

// 封装PUT请求
export function put<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
  return service.put(url, data, withRequestTimeout(url, config)) as Promise<ApiResponse<T>>
}

// 封装DELETE请求
export function del<T = unknown>(url: string, params?: Record<string, unknown>, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
  return service.delete(url, { params, ...withRequestTimeout(url, config) }) as Promise<ApiResponse<T>>
}

function withRequestTimeout(url: string, config?: AxiosRequestConfig): AxiosRequestConfig | undefined {
  const timeout = resolveRequestTimeout(url, config?.timeout)
  return timeout === undefined ? config : { ...config, timeout }
}

export default service
