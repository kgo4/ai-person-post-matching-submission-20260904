import { describe, expect, it, vi } from 'vitest'

const { post } = vi.hoisted(() => ({ post: vi.fn() }))

vi.mock('@/utils/request', () => ({
  get: vi.fn(),
  post,
  default: { get: vi.fn() },
}))

import { generateAiTest, generatePostAiTest } from './ability-source'

describe('AI test API', () => {
  it('allows question generation to use the AI request timeout budget', async () => {
    await generateAiTest(12, 34)

    expect(post).toHaveBeenCalledWith(
      '/employee/ability/ai-test/generate?empId=12&abilityTagId=34',
      undefined,
      { timeout: 120000 },
    )
  })

  it('uses the same timeout budget for post-based generation', async () => {
    await generatePostAiTest(12, 56)

    expect(post).toHaveBeenCalledWith(
      '/employee/ability/ai-test/generate-by-post?empId=12&postId=56',
      undefined,
      { timeout: 120000 },
    )
  })
})
