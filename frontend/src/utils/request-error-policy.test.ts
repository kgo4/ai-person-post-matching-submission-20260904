import { describe, expect, it } from 'vitest'
import { shouldShowRequestErrorToast } from './request-error-policy'

describe('shouldShowRequestErrorToast', () => {
  it('leaves request failures to component-level handlers by default', () => {
    expect(shouldShowRequestErrorToast()).toBe(false)
    expect(shouldShowRequestErrorToast({})).toBe(false)
  })

  it('allows callers without local handling to opt into an interceptor toast', () => {
    expect(shouldShowRequestErrorToast({ showErrorToast: true })).toBe(true)
  })
})
