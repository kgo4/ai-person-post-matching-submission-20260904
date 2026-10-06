import { describe, expect, it } from 'vitest'
import { normalizeLegacyRelativeWeights } from './weight-normalization'

describe('normalizeLegacyRelativeWeights', () => {
  it('converts legacy 0-to-1 relative weights into percentages without changing their ratio', () => {
    expect(normalizeLegacyRelativeWeights([0.8, 0.7, 0.5]))
      .toEqual([40, 35, 25])
  })

  it('leaves percentage weights unchanged', () => {
    expect(normalizeLegacyRelativeWeights([40, 35, 25]))
      .toEqual([40, 35, 25])
  })
})
