import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import router from '@/router'
import { resolveRequestTimeout } from './ai-request-policy'
import { shouldShowRequestErrorToast } from './request-error-policy'
import { resolveApiErrorMessage } from './request-error-message'
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

    // 后端对业务异常做了「HTTP 状态码对齐」（400/404/409...），这些响应体里
    // 带着可读的业务提示语。若直接把原始 axios 错误 reject 出去，页面 catch 到的
    // 是 "Request failed with status code 400"，业务提示语会整条丢失。
    // 因此这里统一归一化为「带可读 message 的 Error」再抛出。
    const message = resolveApiErrorMessage(error)

    if (shouldShowRequestErrorToast(error.config as AxiosRequestConfig | undefined)) {
      ElMessage.error(message)
    }

    // 保留原始错误上的附加信息（status / response / config），只是把 message 换成可读文本，
    // 这样既有调用方读 e.message 的场景能正常工作，也不影响需要读 response 的调用方。
    if (error instanceof Error) {
      error.message = message
      return Promise.reject(error)
    }
    const normalized = new Error(message)
    return Promise.reject(Object.assign(normalized, { response: error.response, config: error.config }))
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
