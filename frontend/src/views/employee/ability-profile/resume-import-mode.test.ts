import { describe, expect, it } from 'vitest'
import { DEFAULT_RESUME_AUTO_IMPORT, canManuallyImport } from './resume-import-mode'

describe('resume import mode', () => {
  it('defaults new uploads to automatic import', () => {
    expect(DEFAULT_RESUME_AUTO_IMPORT).toBe(true)
  })

  it('allows manual import only when it was selected or automatic import failed', () => {
    expect(canManuallyImport(false, 'NOT_REQUESTED')).toBe(true)
    expect(canManuallyImport(true, 'FAILED')).toBe(true)
    expect(canManuallyImport(true, 'SUCCEEDED')).toBe(false)
    expect(canManuallyImport(true, 'REVIEW_REQUIRED')).toBe(false)
    expect(canManuallyImport(true, 'BLOCKED')).toBe(false)
  })
})
