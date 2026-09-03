import assert from 'node:assert/strict'
import {
  DEFAULT_DIMENSION_WEIGHTS,
  buildScoringWeightUpdate,
  getWeightTotal,
  validateDimensionWeights,
} from './logic.ts'

assert.equal(getWeightTotal(DEFAULT_DIMENSION_WEIGHTS), 100)
assert.equal(validateDimensionWeights(DEFAULT_DIMENSION_WEIGHTS), null)
assert.equal(
  validateDimensionWeights({ ...DEFAULT_DIMENSION_WEIGHTS, aiWeight: 25, abilityWeight: 60 }),
  'AI 权重不能超过 20%',
)
assert.match(
  validateDimensionWeights({ ...DEFAULT_DIMENSION_WEIGHTS, abilityWeight: 60 }),
  /必须等于 100%/,
)

const percentagePayload = buildScoringWeightUpdate(
  { abilityWeight: 65, semanticWeight: 15, evidenceWeight: 10, aiWeight: 10 },
  true,
)
assert.deepEqual(
  percentagePayload,
  { abilityWeight: 65, semanticWeight: 15, evidenceWeight: 10, aiWeight: 10, whitelistBypassHardRules: true },
)
