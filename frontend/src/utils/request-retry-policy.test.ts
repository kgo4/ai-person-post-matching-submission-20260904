import { describe, it, expect } from 'vitest'
import { isRetryable, shouldRetry, computeRetryDelay } from './request-retry-policy'

describe('isRetryable', () => {
  it('retries on network error for GET', () => {
    expect(isRetryable({ code: 'ERR_NETWORK' }, { method: 'get' })).toBe(true)
  })

  it('does not retry POST on network error without retryable flag', () => {
    expect(isRetryable({ code: 'ERR_NETWORK' }, { method: 'post' })).toBe(false)
  })

  it('retries on 503 for GET', () => {
    expect(isRetryable({ response: { status: 503 } }, { method: 'get' })).toBe(true)
  })

  it('does not retry on 401', () => {
    expect(isRetryable({ response: { status: 401 } }, { method: 'get' })).toBe(false)
  })

  it('does not retry on 404', () => {
    expect(isRetryable({ response: { status: 404 } }, { method: 'get' })).toBe(false)
  })

  it('does not retry cancelled requests', () => {
    expect(isRetryable({ code: 'ERR_CANCELED' }, { method: 'get' })).toBe(false)
    expect(isRetryable({ name: 'CanceledError' }, { method: 'get' })).toBe(false)
  })
})

describe('shouldRetry', () => {
  it('allows up to 2 retries by default', () => {
    expect(shouldRetry({}, 0)).toBe(true)
    expect(shouldRetry({}, 1)).toBe(true)
    expect(shouldRetry({}, 2)).toBe(false)
  })

  it('respects custom maxRetries', () => {
    expect(shouldRetry({ maxRetries: 1 }, 0)).toBe(true)
    expect(shouldRetry({ maxRetries: 1 }, 1)).toBe(false)
  })
})

describe('computeRetryDelay', () => {
  it('computes backoff delay', () => {
    const delay = computeRetryDelay(0)
    expect(delay).toBeGreaterThanOrEqual(300)
    expect(delay).toBeLessThanOrEqual(300 + 100)
  })
})
