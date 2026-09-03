import assert from 'node:assert/strict'
import { shouldShowBubbleLabel } from './bubbleLabeling.ts'

const base = {
  id: 1,
  tagId: 1,
  tagName: '标签A',
  tagCategory: 'TECHNICAL',
  usedByPostCount: 1,
  usedByEmpCount: 1,
  heatScore: 10,
  statDate: '2026-05-28',
}

assert.equal(shouldShowBubbleLabel(base, 0), true)
assert.equal(shouldShowBubbleLabel({ ...base, heatScore: 85 }, 5), true)
assert.equal(shouldShowBubbleLabel({ ...base, usedByPostCount: 16 }, 5), true)
assert.equal(shouldShowBubbleLabel({ ...base, usedByEmpCount: 10 }, 5), true)
assert.equal(shouldShowBubbleLabel(base, 5), false)

console.log('bubbleLabeling tests passed')
