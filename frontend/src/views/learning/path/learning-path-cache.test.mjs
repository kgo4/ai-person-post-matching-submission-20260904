import assert from 'node:assert/strict'
import { selectCachedAiLearningSuggestion } from './learning-path-cache.ts'

assert.deepEqual(
  selectCachedAiLearningSuggestion([
    { matchingRecordId: 12, suggestions: [] },
    { matchingRecordId: 12, suggestions: [{ abilityName: 'Java', steps: [] }] },
  ]),
  { matchingRecordId: 12, suggestions: [{ abilityName: 'Java', steps: [] }] },
  'restores the latest non-empty cached AI learning suggestion',
)

assert.equal(
  selectCachedAiLearningSuggestion([]),
  null,
  'does not fabricate a cached suggestion when none exists',
)

console.log('learning path cache tests passed')
