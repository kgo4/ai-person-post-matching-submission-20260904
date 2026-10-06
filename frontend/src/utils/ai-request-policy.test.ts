import { describe, expect, it } from 'vitest'
import { AI_REQUEST_TIMEOUT_MS, resolveRequestTimeout } from './ai-request-policy'

describe('resolveRequestTimeout', () => {
  it('extends the timeout for synchronous AI endpoints only', () => {
    expect(resolveRequestTimeout('/learning/ai-suggestions')).toBe(AI_REQUEST_TIMEOUT_MS)
    expect(resolveRequestTimeout('/matching/record/42/ai-report')).toBe(AI_REQUEST_TIMEOUT_MS)
    expect(resolveRequestTimeout('/employee/ability/pms/analyze?empId=1')).toBe(AI_REQUEST_TIMEOUT_MS)
    expect(resolveRequestTimeout('/employee/ability/video-interview/8/generate-questions')).toBe(AI_REQUEST_TIMEOUT_MS)
    expect(resolveRequestTimeout('/employee/ability/resume-parse/8/import')).toBe(AI_REQUEST_TIMEOUT_MS)
    expect(resolveRequestTimeout('/learning/path-enhanced/generate-by-mastery?empId=1')).toBe(AI_REQUEST_TIMEOUT_MS)
    expect(resolveRequestTimeout('/post/evolution/tasks/9/analyze')).toBe(AI_REQUEST_TIMEOUT_MS)
  })

  it('keeps the standard timeout for non-AI endpoints', () => {
    expect(resolveRequestTimeout('/matching/record/page')).toBeUndefined()
    expect(resolveRequestTimeout('/system/user/page')).toBeUndefined()
  })

  it('does not override an explicit caller timeout', () => {
    expect(resolveRequestTimeout('/learning/ai-suggestions', 10_000)).toBe(10_000)
  })
})
