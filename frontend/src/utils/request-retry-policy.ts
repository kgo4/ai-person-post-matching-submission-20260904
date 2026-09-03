const RETRYABLE_STATUS_CODES = new Set([408, 429, 500, 502, 503, 504])

const NO_RETRY_STATUS_CODES = new Set([401, 403, 404, 400, 409, 422])

function jitter(max: number): number {
  return Math.floor(Math.random() * max)
}

function backoff(attempt: number): number {
  // min(300 * 2^attempt + jitter(0..100), 2000)
  return Math.min(300 * Math.pow(2, attempt) + jitter(100), 2000)
}

export interface RetryConfig {
  retryable?: boolean
  maxRetries?: number
  __retryCount?: number
}

export function isRetryable(error: any, config: RetryConfig & { method?: string }): boolean {
  // Do not retry cancelled requests
  if (error?.code === 'ERR_CANCELED' || error?.name === 'CanceledError' || error?.message?.includes('cancel')) {
    return false
  }

  // Network error (no response) - retry safe methods
  if (!error.response) {
    return isSafeMethod(config.method)
  }

  const status = error.response.status
  if (NO_RETRY_STATUS_CODES.has(status)) {
    return false
  }
  if (RETRYABLE_STATUS_CODES.has(status)) {
    return isSafeMethod(config.method) || (config.retryable === true)
  }
  return false
}

function isSafeMethod(method?: string): boolean {
  return method === 'get' || method === 'head' || method === 'options' || !method
}

export function shouldRetry(config: RetryConfig & { method?: string }, attempt: number): boolean {
  const maxRetries = config.maxRetries ?? 2
  return attempt < maxRetries
}

export function computeRetryDelay(attempt: number): number {
  return backoff(attempt)
}
